package videogoose.betterbuilding.gen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.luaj.vm2.LuaError;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import videogoose.betterbuilding.ai.AIClient;
import videogoose.betterbuilding.ai.AIConfig;
import videogoose.betterbuilding.ai.RetrievalIndex;
import videogoose.betterbuilding.gen.render.BlockColors;
import videogoose.betterbuilding.gen.render.Renderer;
import videogoose.betterbuilding.gen.render.VoxelTemplateView;

/**
 * Generates a {@link VoxelTemplate} from a natural-language description, adapting
 * minebench's voxel.exec pattern: the LLM writes a Lua program that calls the
 * building primitives in {@link LuaExecutor}; we run it sandboxed and, on a Lua
 * error or an empty result, re-prompt with the error (single-shot with repair).
 *
 * <p>The render→VLM→critique loop is layered on top of this later; v1 is
 * generate → execute → repair-on-error.
 */
public final class TemplateGenerator {

	public static final int MAX_DIM = 128;
	private static final int MAX_ATTEMPTS = 6;
	private static final double GEN_TEMPERATURE = 0.7;
	private static final int GEN_MAX_TOKENS = 3500;
	private static final boolean ENABLE_CRITIQUE = true;
	private static final int RENDER_SIZE = 768;
	private static final int ACCEPT_SCORE = 7;     // critique score (0-10) good enough to accept
	private static final int PLATEAU_ROUNDS = 2;   // stop after this many critiques with no improvement
	private static final int MAX_FEEDBACK_ISSUES = 3;

	public interface StatusCallback {
		void onStatus(String message);
	}

	private static final Pattern LUA_FENCE = Pattern.compile("```lua\\s*\\n(.*?)```", Pattern.DOTALL);
	private static final Pattern ANY_FENCE = Pattern.compile("```\\s*\\n(.*?)```", Pattern.DOTALL);

	private TemplateGenerator() {}

	public static VoxelTemplate generate(AIConfig cfg, String description, int[] dims,
										 Map<String, Short> palette, StatusCallback status) throws Exception {
		AIClient client = new AIClient(cfg);

		if (dims == null) {
			status.onStatus("Choosing dimensions...");
			dims = chooseDimensions(client, description);
			status.onStatus("Dimensions: " + dims[0] + "x" + dims[1] + "x" + dims[2] + " (WxHxL)");
		}
		clamp(dims);

		String paletteStr = BlockPalette.toPromptString(palette);
		if (paletteStr.isEmpty()) {
			throw new Exception("Palette is empty — put building blocks in your hotbar first.");
		}

		References refs = retrieveReferences(client, description, status);
		String system = buildSystemPrompt(palette);
		String baseUser = buildUserPrompt(dims, description, paletteStr, refs.text);

		String lastLua = null;
		String feedback = null;     // error / quality issues / visual critique to repair against
		byte[] lastRender = null;   // PNG of the previous build — shown to the model on repair
		double temp = GEN_TEMPERATURE;
		VoxelTemplate best = null;  // best build so far (highest critique score, or last valid)
		int bestScore = -1;
		int sinceImprovement = 0;

		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			status.onStatus("Generating (attempt " + attempt + "/" + MAX_ATTEMPTS + ")...");
			String user = (feedback == null) ? baseUser : baseUser + repairSuffix(lastLua, feedback);

			// Initial attempt: show real reference ships (vision). Repairs: show the model its own build.
			String content;
			if (attempt == 1 && !refs.images.isEmpty()) {
				content = client.chatVisionMulti(system,
						user + "\n\nThe attached images are renders of real reference ships similar to the request. "
								+ "Match their overall form, proportions, feature placement, and use of multiple colours "
								+ "(do not copy them exactly).",
						refs.images, temp, GEN_MAX_TOKENS);
			} else if (lastRender != null) {
				content = client.chatVision(system,
						user + "\n\nThe attached image is an isometric render of YOUR current build. "
								+ "Study it and reshape the boxy/flat/terraced parts.",
						lastRender, temp, GEN_MAX_TOKENS);
			} else {
				content = client.chat(system, user, temp, GEN_MAX_TOKENS);
			}

			String lua = extractLuaCode(content);
			if (lua == null || lua.trim().isEmpty()) {
				feedback = "No Lua code block found. Wrap your code in ```lua ... ``` fences.";
				continue;
			}
			if (lua.equals(lastLua)) {
				// Model resubmitted identical code — diversify.
				temp = Math.min(1.0, temp + 0.15);
			}
			lastLua = lua;

			VoxelTemplate t = new VoxelTemplate(defaultName(description), dims[0], dims[1], dims[2]);
			LuaExecutor exec = new LuaExecutor(t, dims, palette);
			try {
				exec.execute(lua);
			} catch (LuaError e) {
				feedback = luaErrorFeedback(e.getMessage(), lua);
				status.onStatus("Lua error, retrying: " + shortError(e.getMessage()));
				temp = Math.min(1.0, temp + 0.05);
				continue;
			}
			if (exec.getTemplateName() != null && !exec.getTemplateName().isEmpty()) {
				t.setName(BlockPalette.luaKey(exec.getTemplateName()).toLowerCase());
			}
			if (t.blockCount() == 0) {
				feedback = "The script ran but placed 0 blocks. Actually place blocks across the volume.";
				continue;
			}

			// Render this build once; reuse for both the critique and the next repair's vision input.
			byte[] png = null;
			try {
				png = renderPng(t);
			} catch (Exception e) {
				status.onStatus("Render failed (" + shortError(e.getMessage()) + ").");
			}
			lastRender = png;

			// 1) Cheap geometric gate (before spending a VLM call).
			List<String> issues = QualityGate.check(t);
			if (!issues.isEmpty() && attempt < MAX_ATTEMPTS) {
				if (best == null) best = t;
				feedback = "Geometry problems to fix:\n- " + String.join("\n- ", issues)
						+ "\nMake substantial changes; do not resubmit similar code.";
				status.onStatus("Quality gate: " + issues.size() + " issue(s), repairing...");
				temp = Math.min(1.0, temp + 0.05);
				continue;
			}

			if (!ENABLE_CRITIQUE || png == null) {
				status.onStatus("Done: " + t.blockCount() + " blocks, " + exec.getTotalOps() + " ops.");
				return t;
			}

			// 2) Scored visual critique (reuse the render). Keep the best; stop on plateau.
			status.onStatus("Critiquing (" + t.blockCount() + " blocks)...");
			Critique c;
			try {
				c = critique(client, png, description);
			} catch (Exception e) {
				status.onStatus("Critique skipped (" + shortError(e.getMessage()) + "); using this build.");
				return t;
			}

			if (c.score > bestScore) {
				bestScore = c.score;
				best = t;
				sinceImprovement = 0;
			} else {
				sinceImprovement++;
			}

			if (c.score >= ACCEPT_SCORE) {
				status.onStatus("Accepted (critique " + c.score + "/10): " + t.blockCount() + " blocks.");
				return t;
			}
			if (attempt >= MAX_ATTEMPTS || sinceImprovement >= PLATEAU_ROUNDS) {
				status.onStatus("Plateaued (best " + bestScore + "/10); returning best build.");
				break;
			}

			List<String> top = c.issues.size() > MAX_FEEDBACK_ISSUES
					? c.issues.subList(0, MAX_FEEDBACK_ISSUES) : c.issues;
			feedback = "Your build scored " + c.score + "/10. Make SUBSTANTIAL changes to fix the top problems:\n- "
					+ String.join("\n- ", top) + "\nRethink the shape; do not resubmit similar code.";
			status.onStatus("Critique " + c.score + "/10, refining (" + c.issues.size() + " issues)...");
			temp = Math.min(1.0, temp + 0.05);
		}

		if (best != null) {
			status.onStatus("Returning best build (score " + bestScore + "/10, " + best.blockCount() + " blocks).");
			return best;
		}
		throw new Exception("Generation failed after " + MAX_ATTEMPTS + " attempts. Last issue: " + feedback);
	}

	// --- visual critique ---

	private static final String CRITIQUE_SYSTEM =
			"You are a StarMade ship design critic. You are shown an isometric render of a GENERATED voxel ship "
			+ "(rendered as plain cubes, so ignore fine block-shape detail; judge overall form). Rate how well it "
			+ "matches the request and how clean the silhouette is, then list the most important fixable flaws. "
			+ "Look for: large featureless boxes/slabs, an over-rounded 'blobby' shape from overusing ovals, a "
			+ "single flat colour with no panel/accent contrast, untapered nose/tail, wrong proportions, asymmetry, "
			+ "and floating/disconnected chunks. Respond with ONLY JSON: "
			+ "{\"score\": 0-10, \"issues\": [\"short actionable fix\", ...]}. "
			+ "score 10 = a clean, recognizable, well-proportioned version of the request; "
			+ "7-9 = good with minor flaws; 4-6 = recognizable but with clear problems; 0-3 = unrecognizable or a "
			+ "featureless blob. Order issues by importance.";

	private static final class Critique {
		final int score;
		final List<String> issues;

		Critique(int score, List<String> issues) {
			this.score = score;
			this.issues = issues;
		}
	}

	private static byte[] renderPng(VoxelTemplate t) throws Exception {
		BufferedImage img = Renderer.renderIso(new VoxelTemplateView(t, new BlockColors()), RENDER_SIZE);
		return toPng(img);
	}

	private static Critique critique(AIClient client, byte[] png, String description) throws Exception {
		String user = "This build was supposed to be: \"" + description + "\". Critique it.";
		String reply = client.captionImage(CRITIQUE_SYSTEM, user, png);
		return parseCritique(reply);
	}

	private static Critique parseCritique(String reply) {
		int score = 5; // neutral default if unparseable
		List<String> issues = new ArrayList<>();
		try {
			int a = reply.indexOf('{'), b = reply.lastIndexOf('}');
			if (a >= 0 && b > a) {
				JsonObject o = new JsonParser().parse(reply.substring(a, b + 1)).getAsJsonObject();
				if (o.has("score")) {
					score = Math.max(0, Math.min(10, (int) Math.round(o.get("score").getAsDouble())));
				}
				if (o.has("issues") && o.get("issues").isJsonArray()) {
					for (JsonElement e : o.getAsJsonArray("issues")) issues.add(e.getAsString());
				}
			}
		} catch (Exception ignored) {
			// unparseable critique → neutral score, no actionable issues
		}
		return new Critique(score, issues);
	}

	private static byte[] toPng(BufferedImage img) throws Exception {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write(img, "png", baos);
		return baos.toByteArray();
	}

	// --- dimension choice ---

	private static int[] chooseDimensions(AIClient client, String description) throws Exception {
		String sys = "You choose dimensions for a StarMade voxel ship. Reply with ONLY three integers: "
				+ "width height length (X Y Z), space-separated. X=width, Y=height, Z=length. "
				+ "Guidelines: fighters 10-30, corvettes 30-70, frigates 70-120, larger up to " + MAX_DIM + ". "
				+ "Ships are usually longer (Z) than wide (X) and taller (Y). Example: 16 10 28";
		for (int attempt = 0; attempt < 3; attempt++) {
			String content = client.chat(sys, description, 0.2, 32);
			String[] parts = content.replaceAll("[^0-9 ]", " ").trim().split("\\s+");
			if (parts.length >= 3) {
				try {
					return new int[]{
							Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
				} catch (NumberFormatException ignored) {
					// retry
				}
			}
		}
		return new int[]{16, 10, 28};
	}

	private static void clamp(int[] dims) {
		for (int i = 0; i < 3; i++) dims[i] = Math.max(2, Math.min(MAX_DIM, dims[i]));
	}

	// --- prompts ---

	static String buildSystemPrompt(Map<String, Short> palette) {
		return SYSTEM_PROMPT_BODY + "\n\n" + LUA_API_DOCS + "\n\n" + buildExample(palette);
	}

	/** A worked example built from the ACTUAL palette names, so it never references a block the player lacks. */
	private static String buildExample(Map<String, Short> palette) {
		List<String> keys = new ArrayList<>(palette.keySet());
		String hull = keys.isEmpty() ? "HULL" : keys.get(0);
		String accent = keys.size() > 1 ? keys.get(1) : hull;
		String cockpit = keys.size() > 2 ? keys.get(2) : accent;
		return "## WORKED EXAMPLE — uses YOUR palette names; study the call patterns and copy the structure:\n"
				+ "```lua\n"
				+ "set_name(\"example_fighter\")\n"
				+ "local mid = math.floor(dims.x / 2)\n"
				+ "-- fuselage: tapered cone along Z (nose at high Z); tip_r=0, hollow=false\n"
				+ "cone(mid, 4, 0, mid, 4, dims.z - 1, 3, blocks." + hull + ", 0, false)\n"
				+ "-- cockpit canopy near the nose (accent colour)\n"
				+ "ellipsoid(mid, 6, dims.z - 6, 2, 2, 3, blocks." + cockpit + ", false)\n"
				+ "-- one wing on the +X side (mirrored later), in an accent colour; leading edge wedged\n"
				+ "fill(mid + 1, 4, 8, mid + 6, 4, 16, blocks." + accent + ")\n"
				+ "for x = mid + 1, mid + 6 do\n"
				+ "  place(x, 4, 16, blocks." + hull + ", orient.wedge_top_front)\n"
				+ "end\n"
				+ "-- engine pod\n"
				+ "cylinder(mid + 4, 4, 0, mid + 4, 4, 4, 1, blocks." + accent + ", false)\n"
				+ "-- finish: mirror for symmetry, hollow the interior, smooth the steps\n"
				+ "mirror(\"X\")\n"
				+ "hollow(0, 0, 0, dims.x - 1, dims.y - 1, dims.z - 1, 1)\n"
				+ "smooth(0, 0, 0, dims.x - 1, dims.y - 1, dims.z - 1, 1)\n"
				+ "```\n"
				+ "Note the exact argument counts and the use of multiple palette colours. Only names from the "
				+ "palette exist — there is no generic 'glass' or 'hull' unless it is listed.";
	}

	static String buildUserPrompt(int[] dims, String description, String palette, String references) {
		StringBuilder sb = new StringBuilder();
		sb.append("Build a StarMade ship template.\n\n");
		sb.append("DESCRIPTION: ").append(description).append("\n");
		sb.append("DIMENSIONS: ").append(dims[0]).append("x").append(dims[1]).append("x").append(dims[2])
				.append(" (Width x Height x Length). Coordinates 0..dim-1 on each axis.\n");
		sb.append("X symmetry center: ").append(dims[0] / 2).append(" (build one half, then mirror(\"X\")).\n");
		if (references != null && !references.isEmpty()) {
			sb.append(references);
		}
		sb.append("\nAVAILABLE BLOCKS — use EXACTLY these via blocks.NAME:\n").append(palette).append("\n");
		sb.append("Use SEVERAL of these colors (main hull + darker accents + a bright cockpit/light), not just one.\n");
		sb.append("Do NOT invent block names; only the names above exist.\n\n");
		sb.append("Output ONLY a single ```lua code block, no prose.");
		return sb.toString();
	}

	private static final class References {
		final String text;
		final List<byte[]> images;

		References(String text, List<byte[]> images) {
			this.text = text;
			this.images = images;
		}

		static References empty() {
			return new References("", new ArrayList<>());
		}
	}

	/**
	 * Pull the most similar captioned ships from the retrieval index to prime the
	 * generation prompt — both their caption text and their saved renders (shown as
	 * images on the first attempt). Best-effort: no index / no embedding model / any
	 * error → empty, and generation proceeds unprimed.
	 */
	private static References retrieveReferences(AIClient client, String description, StatusCallback status) {
		try {
			RetrievalIndex idx = new RetrievalIndex();
			idx.load(new File("./BetterBuilding/index.json"));
			if (idx.size() == 0) return References.empty();
			float[] q = client.embed(description);
			List<RetrievalIndex.Hit> hits = idx.query(q, 2);
			if (hits.isEmpty()) return References.empty();

			StringBuilder sb = new StringBuilder(
					"\nREFERENCE SHIPS — real builds similar to the request (renders attached). Match their "
					+ "proportions, silhouette, feature placement, shape variety, and use of multiple colors "
					+ "(do NOT copy them literally):\n");
			List<byte[]> images = new ArrayList<>();
			int n = 1;
			for (RetrievalIndex.Hit h : hits) {
				sb.append(n++).append(". ").append(h.entry.text).append("\n");
				File png = new File("./BetterBuilding/renders/" + h.entry.name + ".png");
				if (png.isFile()) {
					try {
						images.add(Files.readAllBytes(png.toPath()));
					} catch (Exception ignored) {
						// skip this image, keep the text
					}
				}
			}
			status.onStatus("Primed with " + hits.size() + " reference(s)"
					+ (images.isEmpty() ? "" : " + " + images.size() + " image(s)") + ".");
			return new References(sb.toString(), images);
		} catch (Exception e) {
			return References.empty();
		}
	}

	private static final Pattern LUA_LINE = Pattern.compile("build:(\\d+)");

	/** Turn a LuaJ error into actionable feedback by quoting the exact offending line of the model's code. */
	private static String luaErrorFeedback(String error, String lua) {
		StringBuilder sb = new StringBuilder("ERROR: ").append(error == null ? "(unknown)" : error).append("\n");
		Matcher m = LUA_LINE.matcher(error == null ? "" : error);
		if (m.find() && lua != null) {
			int ln = Integer.parseInt(m.group(1));
			String[] lines = lua.split("\n", -1);
			if (ln >= 1 && ln <= lines.length) {
				sb.append("The error is on THIS line of your code (line ").append(ln).append("):\n    ")
						.append(lines[ln - 1].trim()).append("\n");
			}
		}
		sb.append("Common causes: a missing/extra argument, an undefined variable (typo), or a name not in the "
				+ "palette. Fix that exact call, then output the corrected full program.");
		return sb.toString();
	}

	private static String repairSuffix(String prevLua, String error) {
		StringBuilder sb = new StringBuilder();
		sb.append("\n\n--- Your previous attempt failed. ---\nERROR: ").append(error).append("\n");
		if (prevLua != null) {
			String trimmed = prevLua.length() > 6000 ? prevLua.substring(0, 6000) : prevLua;
			sb.append("PREVIOUS CODE:\n").append(trimmed).append("\n");
		}
		sb.append("Fix the problem and output corrected Lua only.");
		return sb.toString();
	}

	// --- Lua extraction ---

	static String extractLuaCode(String content) {
		if (content == null || content.isEmpty()) return null;
		Matcher m = LUA_FENCE.matcher(content);
		if (m.find()) return m.group(1).trim();
		m = ANY_FENCE.matcher(content);
		if (m.find()) return m.group(1).trim();
		String t = content.trim();
		if (t.startsWith("--") || t.startsWith("local ") || t.startsWith("fill(") || t.startsWith("set_name(")
				|| t.startsWith("for ") || t.startsWith("place(") || t.startsWith("cone(")) {
			return t;
		}
		return null;
	}

	private static String defaultName(String description) {
		String key = BlockPalette.luaKey(description).toLowerCase();
		if (key.length() > 40) key = key.substring(0, 40);
		return key.isEmpty() ? "ai_generated" : key;
	}

	private static String shortError(String e) {
		if (e == null) return "(unknown)";
		return e.length() > 120 ? e.substring(0, 120) : e;
	}

	// --- prompt text ---

	private static final String SYSTEM_PROMPT_BODY =
			"You are an expert StarMade ship designer. You build 3D voxel ships by writing a Lua program "
			+ "that calls building primitives. THINK about the silhouette before writing code; the best ships "
			+ "are well-proportioned and recognizable, not boxes.\n\n"
			+ "CRITICAL RULES:\n"
			+ "1. Only use block names from the palette in the user prompt, as blocks.NAME. Unknown names crash the script.\n"
			+ "2. Use orient.NAME for orientations (never raw integers).\n"
			+ "3. Lua syntax (common crashes): use and/or/not (not &&/||/!), math.abs() (not abs()), "
			+ "~= for not-equal, and 'for i = 1, 10 do' (comma, not 'to').\n"
			+ "4. dims is already a global (dims.x, dims.y, dims.z). Do not redefine it.\n"
			+ "5. Call primitives directly. No wrapper functions, no pseudo-code, no TODOs. Every line must do something.\n\n"
			+ "COORDINATES: 0-indexed. x=[0,dims.x-1] width, y=[0,dims.y-1] height (up), z=[0,dims.z-1] length "
			+ "(nose = high Z, tail = low Z). Symmetry center = dims.x/2.\n\n"
			+ "ORIENTATIONS: orient.FRONT(+Z), orient.BACK(-Z), orient.TOP(+Y), orient.BOTTOM(-Y), orient.RIGHT(+X), "
			+ "orient.LEFT(-X); plus orient.wedge_{surface}_{dir}, orient.corner_{axis}_{d1}_{d2}, "
			+ "orient.tetra_{v}_{f}_{l}, orient.hepta_{v}_{f}_{l}.\n\n"
			+ "DESIGN: Never a plain box, and never just one big ellipsoid blob. Vary your shapes: cone for the "
			+ "tapered fuselage, box/fill for hull sections and decks, cylinders for engines/nacelles, wedges for "
			+ "beveled edges — ellipsoid is for cockpits/domes, not the whole ship. Taper the nose and tail, add "
			+ "wings/fins/engines/cockpit, then mirror(\"X\"). Use AT LEAST 3 different block colors: a main hull "
			+ "colour, a darker colour for panels/accents, and a bright colour for the cockpit/lights. A one-colour "
			+ "ship looks unfinished.\n\n"
			+ "SHAPING & FINISHING (do not skip): stacking fills/ellipsoids of different sizes makes ugly stair-steps "
			+ "('terracing'). After roughing the hull, BLEND it: call smooth() over the hull region (1-2 iterations) to "
			+ "round steps, use erode()/dilate() to refine the outline, and ALWAYS call hollow() so the ship is a shaped "
			+ "shell, not a solid block. End your program with these finishing passes. A smooth, hollow, tapered hull "
			+ "beats a big solid blocky one every time.\n\n"
			+ "If an isometric render of your current build is attached, study it: find the boxy/flat/terraced parts and "
			+ "specifically reshape THOSE areas — do not just rebuild the same thing.";

	private static final String LUA_API_DOCS =
			"## LUA API\nWrite Lua that calls these. All builders return the number of blocks affected. "
			+ "Optional args in [].\n"
			+ "Globals: dims.x/y/z; blocks.NAME (palette ids); orient.NAME (orientations).\n\n"
			+ "BASIC:\n"
			+ "- place(x,y,z, block [,orient])\n"
			+ "- fill(x0,y0,z0, x1,y1,z1, block [,orient])\n"
			+ "- shell(x0,y0,z0, x1,y1,z1, block [,thickness [,orient]])  -- hollow box (walls)\n"
			+ "- line(x0,y0,z0, x1,y1,z1, block [,orient])\n"
			+ "- clear(x0,y0,z0, x1,y1,z1)  -- set region to air\n\n"
			+ "SHAPES:\n"
			+ "- ellipsoid(cx,cy,cz, rx,ry,rz, block [,hollow [,orient]])  -- sphere if radii equal\n"
			+ "- cylinder(x0,y0,z0, x1,y1,z1, radius, block [,hollow [,orient]])\n"
			+ "- cone(x0,y0,z0, x1,y1,z1, base_r, block [,tip_r [,hollow [,orient]]])\n"
			+ "- torus(cx,cy,cz, major_r, minor_r, block [,axis [,hollow [,orient]]])  -- axis \"X\"|\"Y\"|\"Z\"\n"
			+ "- pyramid(x0,y0,z0, x1,y1,z1, block [,hollow [,orient]])  -- tapers up +Y\n"
			+ "- arc(cx,cy,cz, radius, start_deg, end_deg, block [,axis [,thickness [,orient]]])\n\n"
			+ "TRANSFORM:\n"
			+ "- copy(x0,y0,z0, x1,y1,z1, dx,dy,dz)  -- paste region at dest (non-air only)\n"
			+ "- replace(x0,y0,z0, x1,y1,z1, old_block, new_block [,new_orient])\n"
			+ "- rotate(x0,y0,z0, x1,y1,z1, axis, degrees)  -- axis \"X\"|\"Y\"|\"Z\"; degrees 90/180/270\n"
			+ "- mirror([axis [,x0,y0,z0, x1,y1,z1]])  -- default axis \"X\"; copies into empty cells only\n\n"
			+ "SHAPING:\n"
			+ "- smooth(x0,y0,z0, x1,y1,z1 [,iters])      -- fill pockets, remove floaters\n"
			+ "- erode(x0,y0,z0, x1,y1,z1 [,iters])       -- shrink outer layer\n"
			+ "- dilate(x0,y0,z0, x1,y1,z1, block [,iters [,orient]])  -- grow outward\n"
			+ "- flatten(x0,y0,z0, x1,y1,z1 [,axis [,mode]])  -- mode \"max\"|\"min\"\n"
			+ "- hollow(x0,y0,z0, x1,y1,z1 [,thickness])  -- carve interior of existing geometry\n\n"
			+ "DETAIL/QUERY:\n"
			+ "- gradient(x0,y0,z0, x1,y1,z1, block_a, block_b [,axis [,orient]])\n"
			+ "- noise(x0,y0,z0, x1,y1,z1, block, density [,seed [,orient]])  -- density 0..1\n"
			+ "- scatter_surface(x0,y0,z0, x1,y1,z1, block, density [,seed [,orient]])  -- greebles on surfaces\n"
			+ "- extrude(x0,z0, x1,z1, src_y, height, block [,orient [,copy_existing]])\n"
			+ "- flood_fill(x,y,z, block [,orient [,max_blocks]])\n"
			+ "- get_block(x,y,z) -> type, orient   -- 0,0 if air/out of bounds\n"
			+ "- count_blocks(x0,y0,z0, x1,y1,z1 [,block])\n"
			+ "- set_name(name)  -- snake_case\n\n"
			+ "NOTE: 'hollow' is a boolean (true/false) and only goes in the slot shown. For cone, tip_r (a NUMBER) "
			+ "comes before hollow: e.g. cone(x0,y0,z0,x1,y1,z1, base_r, block, 0, true). Don't put true/false in a number slot.\n\n"
			+ "OUTPUT: your entire reply is ONE ```lua ... ``` code block. No prose. Use loops/math/variables freely.";
}
