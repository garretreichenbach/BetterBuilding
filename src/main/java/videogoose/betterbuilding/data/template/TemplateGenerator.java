package videogoose.betterbuilding.data.template;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import videogoose.betterbuilding.BetterBuilding;
import videogoose.betterbuilding.manager.ConfigManager;
import videogoose.betterbuilding.manager.AIClient;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates building templates by having an LLM output Lua code that calls
 * building primitives. The code is executed in a sandboxed LuaJ runtime.
 * On failure, the error is fed back to the LLM for a retry (up to MAX_RETRIES).
 */
public class TemplateGenerator {

	public static final int DEFAULT_MAX_DIM = 64;
	private static final int MAX_RETRIES = 3;

	private static final Pattern LUA_FENCE_PATTERN = Pattern.compile(
			"```lua\\s*\\n(.*?)```", Pattern.DOTALL);
	private static final Pattern GENERIC_FENCE_PATTERN = Pattern.compile(
			"```\\s*\\n(.*?)```", Pattern.DOTALL);

	private static final String[] BUILD_PHASES = {
			"PHASE 1 — SKELETON: Build the central spine/fuselage. Use cone() or cylinder() to create a long tapered " +
					"shape along the Z axis. This is the core silhouette — keep it simple, just the main body shape. " +
					"Call set_name() with a descriptive name.",
			"PHASE 2 — HULL & SHAPING: Add hull plating around the skeleton. Use fill() to add bulk where needed, " +
					"then replace boxy edges with wedge, corner, and hepta blocks. Taper the nose and tail.",
			"PHASE 3 — FEATURES: Add wings, fins, nacelles, cockpit canopy, engine housings, weapon mounts. " +
					"Build features on ONE side of the X axis only — mirror() will be used later for symmetry.",
			"PHASE 4 — DETAILING & CLEANUP: Add panel lines using different block colors, accent lights, glass for cockpits. " +
					"Use mirror(\"X\") for bilateral symmetry. Use hollow() to carve out the interior. Final polish."
	};

	public static TemplateMetaData generate(List<TemplateMetaData> references, int[] outputDims, String description, Set<Short> hotbarTypes) throws Exception {
		// outputDims may be null — if so, the AI will choose dimensions before building

		String provider = ConfigManager.getProvider();
		String url, model, providerName, apiKey;
		float temperature;
		int maxTokens, timeout;

		switch(provider) {
			case "ollama":
				url = ConfigManager.getOllamaUrl();
				model = ConfigManager.getOllamaModel();
				providerName = "Ollama";
				apiKey = null;
				temperature = ConfigManager.getLMStudioTemperature();
				maxTokens = ConfigManager.getLMStudioMaxTokens();
				timeout = ConfigManager.getLMStudioTimeout();
				break;
			case "api":
				url = ConfigManager.getApiUrl();
				model = ConfigManager.getApiModel();
				providerName = "API (" + url + ")";
				apiKey = ConfigManager.getApiKey();
				temperature = ConfigManager.getApiTemperature();
				maxTokens = ConfigManager.getApiMaxTokens();
				timeout = ConfigManager.getApiTimeout();
				if(apiKey == null || apiKey.isEmpty()) {
					throw new Exception("API key is required for the 'api' provider. Set 'api-key' in config.yml.");
				}
				break;
			default: // lmstudio
				url = ConfigManager.getLMStudioUrl();
				model = ConfigManager.getLMStudioModel();
				providerName = "LM Studio";
				apiKey = null;
				temperature = ConfigManager.getLMStudioTemperature();
				maxTokens = ConfigManager.getLMStudioMaxTokens();
				timeout = ConfigManager.getLMStudioTimeout();
				break;
		}

		AIClient client = new AIClient(url, model, temperature, maxTokens, timeout, apiKey);

		Map<String, Short> paletteMap = (hotbarTypes != null && !hotbarTypes.isEmpty())
				? BlockPalette.toHotbarPaletteMap(hotbarTypes)
				: BlockPalette.toPaletteMap();
		String paletteString = palettMapToString(paletteMap);

		// If no dimensions provided, ask the AI to choose them
		if(outputDims == null) {
			outputDims = chooseDimensions(client, description);
			BetterBuilding.getInstance().logInfo("AI chose dimensions: " + outputDims[0] + "x" + outputDims[1] + "x" + outputDims[2]);
		}
		validateDimensions(outputDims);

		JsonArray messages = new JsonArray();
		messages.add(makeMessage("system", buildSystemPrompt()));
		messages.add(makeMessage("user", buildUserPrompt(references, outputDims, description, paletteString)));

		TemplateMetaData template = new TemplateMetaData("ai_generated", outputDims);
		int totalOps = 0;

		BetterBuilding.getInstance().logInfo("Starting multi-phase generation via " + providerName + "...");

		for(int phase = 0; phase < BUILD_PHASES.length; phase++) {
			BetterBuilding.getInstance().logInfo("=== " + BUILD_PHASES[phase].substring(0, BUILD_PHASES[phase].indexOf(':') + 1) + " ===");

			// Build the phase prompt with current template state
			StringBuilder phasePrompt = new StringBuilder();
			phasePrompt.append(BUILD_PHASES[phase]).append("\n\n");

			if(phase > 0) {
				// Show what's been built so far
				int blockCount = countNonAir(template);
				phasePrompt.append("CURRENT STATE (").append(blockCount).append(" blocks placed so far):\n");
				phasePrompt.append("Block counts: ").append(BlockPalette.summarizeTemplate(template)).append("\n");
				phasePrompt.append("Cross-sections:\n").append(BlockPalette.structuralSummary(template)).append("\n");
			}

			phasePrompt.append("Write a ```lua code block for THIS PHASE ONLY. ");
			phasePrompt.append("The template already contains the work from previous phases — build on top of it.");

			messages.add(makeMessage("user", phasePrompt.toString()));

			// Try this phase with retries
			boolean phaseSuccess = false;
			String lastError = null;
			for(int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
				if(attempt > 0) {
					BetterBuilding.getInstance().logInfo("  Retry " + attempt + "/" + MAX_RETRIES + " — error: " + lastError);
					messages.add(makeMessage("user",
							"Your Lua code produced an error. Fix the issue and output corrected Lua code.\n\nError:\n" + lastError));
				}

				JsonObject assistantMsg = client.chatCompletionWithTools(messages, null);
				messages.add(assistantMsg);

				String content = assistantMsg.has("content") && !assistantMsg.get("content").isJsonNull()
						? assistantMsg.get("content").getAsString()
						: "";

				String luaCode = extractLuaCode(content);
				if(luaCode == null || luaCode.trim().isEmpty()) {
					lastError = "No Lua code found in response. Wrap your code in ```lua ... ``` fences.";
					continue;
				}

				LuaExecutor executor = new LuaExecutor(template, outputDims, paletteMap);
				try {
					executor.execute(luaCode);
				} catch(Exception e) {
					lastError = e.getMessage();
					BetterBuilding.getInstance().logWarning("Lua error (phase " + (phase + 1) + ", attempt " + (attempt + 1) + "): " + lastError);
					BetterBuilding.getInstance().logWarning("Failed script:\n" + luaCode);
					continue;
				}

				if(executor.getTemplateName() != null && !executor.getTemplateName().isEmpty()) {
					template.setName(executor.getTemplateName());
				}

				int opsThisPhase = executor.getTotalOps();
				totalOps += opsThisPhase;

				int blockCount = countNonAir(template);
				BetterBuilding.getInstance().logInfo("  Phase " + (phase + 1) + " complete: " + opsThisPhase + " ops, " + blockCount + " total blocks");

				// Add a confirmation message so the LLM knows the phase succeeded
				messages.add(makeMessage("user", "Phase completed successfully. " + blockCount + " blocks now placed."));
				phaseSuccess = true;
				break;
			}

			if(!phaseSuccess) {
				BetterBuilding.getInstance().logWarning("Phase " + (phase + 1) + " failed after " + (MAX_RETRIES + 1) + " attempts, skipping to next phase.");
			}
		}

		if(totalOps == 0 || countNonAir(template) == 0) {
			throw new Exception("All phases failed to place any blocks.");
		}

		BetterBuilding.getInstance().logInfo("Generation complete: " + totalOps + " total ops, " + countNonAir(template) + " blocks");
		return template;
	}

	private static int countNonAir(TemplateMetaData template) {
		int count = 0;
		for(short t : template.getBlockTypes()) {
			if(t != 0) count++;
		}
		return count;
	}

	/**
	 * Ask the AI to choose appropriate dimensions for the build.
	 * Returns [x, y, z] dimensions. Falls back to a default if parsing fails.
	 */
	private static int[] chooseDimensions(AIClient client, String description) throws Exception {
		JsonArray messages = new JsonArray();
		messages.add(makeMessage("system",
				"You choose dimensions for StarMade voxel templates. " +
						"Given a description, respond with ONLY three integers: width height length (X Y Z), space-separated.\n" +
						"X = width (left/right), Y = height (up/down), Z = length (front/back).\n" +
						"Guidelines: fighters 10-30, corvettes 30-70, frigates 70-150, capital ships 150+. Max 200 per axis.\n" +
						"Ships are usually longer (Z) than wide (X) and taller (Y). Stations can be more cubic.\n" +
						"Respond with ONLY the three numbers, nothing else. Example: 12 8 24"));
		messages.add(makeMessage("user", description));

		for(int attempt = 0; attempt < 3; attempt++) {
			JsonObject response = client.chatCompletionWithTools(messages, null);
			String content = response.has("content") && !response.get("content").isJsonNull()
					? response.get("content").getAsString().trim()
					: "";

			// Parse three integers from the response
			String[] parts = content.replaceAll("[^0-9 ]", " ").trim().split("\\s+");
			if(parts.length >= 3) {
				try {
					int x = Math.max(2, Math.min(DEFAULT_MAX_DIM, Integer.parseInt(parts[0])));
					int y = Math.max(2, Math.min(DEFAULT_MAX_DIM, Integer.parseInt(parts[1])));
					int z = Math.max(2, Math.min(DEFAULT_MAX_DIM, Integer.parseInt(parts[2])));
					return new int[]{x, y, z};
				} catch(NumberFormatException ignored) {}
			}

			messages.add(response);
			messages.add(makeMessage("user", "Invalid response. Reply with ONLY three integers: width height length. Example: 12 8 24"));
		}

		// Fallback: reasonable default
		BetterBuilding.getInstance().logWarning("AI failed to choose dimensions, using default 16x10x24");
		return new int[]{16, 10, 24};
	}

	/**
	 * Extract Lua code from the LLM response.
	 * Tries ```lua fences first, then generic ``` fences, then the raw content.
	 */
	static String extractLuaCode(String content) {
		if(content == null || content.isEmpty()) return null;

		// Try ```lua ... ``` fences
		Matcher m = LUA_FENCE_PATTERN.matcher(content);
		if(m.find()) return m.group(1).trim();

		// Try generic ``` ... ``` fences
		m = GENERIC_FENCE_PATTERN.matcher(content);
		if(m.find()) return m.group(1).trim();

		// If the content looks like raw Lua (starts with common keywords), use it directly
		String trimmed = content.trim();
		if(trimmed.startsWith("--") || trimmed.startsWith("local ") ||
				trimmed.startsWith("fill(") || trimmed.startsWith("shell(") ||
				trimmed.startsWith("place(") || trimmed.startsWith("ellipsoid(") ||
				trimmed.startsWith("cylinder(") || trimmed.startsWith("cone(") ||
				trimmed.startsWith("for ") || trimmed.startsWith("function ")) {
			return trimmed;
		}

		return null;
	}

	private static String palettMapToString(Map<String, Short> map) {
		StringBuilder sb = new StringBuilder("{");
		boolean first = true;
		for(Map.Entry<String, Short> entry : map.entrySet()) {
			if(!first) sb.append(",");
			sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
			first = false;
		}
		sb.append("}");
		return sb.toString();
	}

	private static void validateDimensions(int[] dims) {
		if(dims == null || dims.length != 3) throw new IllegalArgumentException("outputDims must be length 3");
		for(int d : dims) {
			if(d <= 0) throw new IllegalArgumentException("outputDims must be > 0");
			if(d > DEFAULT_MAX_DIM) throw new IllegalArgumentException("outputDims exceed max " + DEFAULT_MAX_DIM);
		}
	}

	private static JsonObject makeMessage(String role, String content) {
		JsonObject msg = new JsonObject();
		msg.addProperty("role", role);
		msg.addProperty("content", content);
		return msg;
	}

	// --- Prompt ---

	static String buildSystemPrompt() {
		return SYSTEM_PROMPT_BODY + "\n\n" + buildLuaApiDocs();
	}

	static String buildLuaApiDocs() {
		StringBuilder sb = new StringBuilder();
		sb.append("## LUA API\n");
		sb.append("You build templates by writing Lua code. Your code will be executed in a sandboxed\n");
		sb.append("environment with the following building functions and globals available.\n\n");
		sb.append("### Globals\n");
		sb.append("- `dims.x`, `dims.y`, `dims.z` — the template dimensions\n");
		sb.append("- `blocks` — table of block type IDs by name (e.g. blocks.GREY_HULL, blocks.BLACK_STANDARD_ARMOR)\n");
		sb.append("- `orient` — table of named orientation constants (see ORIENTATION SYSTEM above)\n");
		sb.append("  Basic: orient.FRONT, orient.BACK, orient.TOP, orient.BOTTOM, orient.RIGHT, orient.LEFT\n");
		sb.append("  Wedge: orient.wedge_top_front, orient.wedge_top_right, orient.wedge_top_back, orient.wedge_top_left,\n");
		sb.append("         orient.wedge_bottom_front/right/back/left, orient.wedge_side_front/right/back/left\n");
		sb.append("  Corner: orient.corner_{axis}_{dir1}_{dir2} (24 values, e.g. orient.corner_top_front_right)\n");
		sb.append("  Tetra: orient.tetra_{vert}_{fwd}_{lat} (8 values, e.g. orient.tetra_top_front_right)\n");
		sb.append("  Hepta: orient.hepta_{vert}_{fwd}_{lat} (8 values, e.g. orient.hepta_top_front_right)\n\n");
		sb.append("### Building Functions\n");
		sb.append("All building functions return the number of blocks affected.\n\n");

		sb.append("#### Basic\n");
		sb.append("- `fill(from_x, from_y, from_z, to_x, to_y, to_z, block_type [, orientation])`\n");
		sb.append("    Fill a rectangular region with a block type.\n\n");
		sb.append("- `shell(from_x, from_y, from_z, to_x, to_y, to_z, block_type [, thickness [, orientation]])`\n");
		sb.append("    Create a hollow shell (walls only). Default thickness=1.\n\n");
		sb.append("- `place(x, y, z, block_type [, orientation])`\n");
		sb.append("    Place a single block.\n\n");
		sb.append("- `line(from_x, from_y, from_z, to_x, to_y, to_z, block_type [, orientation])`\n");
		sb.append("    Draw a line of blocks between two points.\n\n");
		sb.append("- `clear(from_x, from_y, from_z, to_x, to_y, to_z)`\n");
		sb.append("    Clear a rectangular region to air.\n\n");

		sb.append("#### Shapes\n");
		sb.append("- `ellipsoid(center_x, center_y, center_z, radius_x, radius_y, radius_z, block_type [, hollow [, orientation]])`\n");
		sb.append("    Create an ellipsoid. Set all radii equal for a sphere. hollow=true for shell only.\n\n");
		sb.append("- `cylinder(from_x, from_y, from_z, to_x, to_y, to_z, radius, block_type [, hollow [, orientation]])`\n");
		sb.append("    Create a cylinder along an arbitrary axis.\n\n");
		sb.append("- `cone(from_x, from_y, from_z, to_x, to_y, to_z, base_radius, block_type [, tip_radius [, hollow [, orientation]]])`\n");
		sb.append("    Create a cone/tapered cylinder. Default tip_radius=0.\n\n");
		sb.append("- `torus(center_x, center_y, center_z, major_radius, minor_radius, block_type [, axis [, hollow [, orientation]]])`\n");
		sb.append("    Create a torus (donut/ring). axis=\"X\"|\"Y\"|\"Z\" (default \"Y\"). Use for engine rings, station rings.\n\n");
		sb.append("- `pyramid(from_x, from_y, from_z, to_x, to_y, to_z, block_type [, hollow [, orientation]])`\n");
		sb.append("    Create a stepped pyramid that tapers upward (+Y). Base is the XZ footprint of the region.\n\n");
		sb.append("- `arc(center_x, center_y, center_z, radius, start_deg, end_deg, block_type [, axis [, thickness [, orientation]]])`\n");
		sb.append("    Create a partial circle/arch. Angles in degrees. axis=\"X\"|\"Y\"|\"Z\" (default \"Y\"). Default thickness=1.\n\n");

		sb.append("#### Transforms\n");
		sb.append("- `copy(from_x, from_y, from_z, to_x, to_y, to_z, dest_x, dest_y, dest_z)`\n");
		sb.append("    Copy a region and paste it at the destination. Only copies non-air blocks.\n\n");
		sb.append("- `replace(from_x, from_y, from_z, to_x, to_y, to_z, old_type, new_type [, new_orientation])`\n");
		sb.append("    Replace all blocks of old_type with new_type in a region. If new_orientation is omitted, keeps original.\n\n");
		sb.append("- `rotate(from_x, from_y, from_z, to_x, to_y, to_z, axis, degrees)`\n");
		sb.append("    Rotate a region in-place. axis=\"X\"|\"Y\"|\"Z\", degrees must be 90, 180, or 270.\n\n");
		sb.append("- `mirror([axis [, from_x, from_y, from_z, to_x, to_y, to_z]])`\n");
		sb.append("    Mirror existing blocks across an axis. Default axis=\"X\" (bilateral symmetry).\n");
		sb.append("    Only copies into empty spaces.\n\n");

		sb.append("#### Shaping\n");
		sb.append("- `smooth(from_x, from_y, from_z, to_x, to_y, to_z [, iterations])`\n");
		sb.append("    Smooth a region: fills air pockets surrounded by 4+ solid neighbors, removes floating blocks with 0-1 neighbors.\n\n");
		sb.append("- `erode(from_x, from_y, from_z, to_x, to_y, to_z [, iterations])`\n");
		sb.append("    Remove the outermost layer of blocks in a region. Use to shrink/round shapes.\n\n");
		sb.append("- `dilate(from_x, from_y, from_z, to_x, to_y, to_z, block_type [, iterations [, orientation]])`\n");
		sb.append("    Grow a shape outward: fills air adjacent to any solid block. Use to thicken/expand shapes.\n\n");
		sb.append("- `flatten(from_x, from_y, from_z, to_x, to_y, to_z [, axis [, mode]])`\n");
		sb.append("    Flatten a region along an axis. axis=\"X\"|\"Y\"|\"Z\" (default \"Y\"). mode=\"max\" (default) or \"min\".\n");
		sb.append("    In \"max\" mode, all columns are extended to match the tallest. In \"min\" mode, trimmed to the shortest.\n\n");

		sb.append("#### Query\n");
		sb.append("- `get_block(x, y, z)` — returns block_type, orientation (two values). Returns 0,0 for air or out of bounds.\n");
		sb.append("    Example: `local t, o = get_block(5, 3, 10)`\n\n");
		sb.append("- `count_blocks(from_x, from_y, from_z, to_x, to_y, to_z [, block_type])`\n");
		sb.append("    Count non-air blocks in a region. If block_type is given, only counts that type.\n\n");

		sb.append("#### Utility\n");
		sb.append("- `set_name(name)` — set the template name (snake_case recommended).\n\n");
		sb.append("- `noise(from_x, from_y, from_z, to_x, to_y, to_z, block_type, density [, seed [, orientation]])`\n");
		sb.append("    Scatter blocks randomly in a region. density is 0.0 to 1.0. Optional integer seed for reproducibility.\n\n");
		sb.append("- `hollow(from_x, from_y, from_z, to_x, to_y, to_z [, thickness])`\n");
		sb.append("    Carve out the interior of existing geometry, leaving walls of the given thickness (default 1).\n");
		sb.append("    Unlike shell() which builds a shell, this removes the inside of already-placed blocks.\n\n");
		sb.append("- `gradient(from_x, from_y, from_z, to_x, to_y, to_z, type_a, type_b [, axis [, orientation]])`\n");
		sb.append("    Fill a region transitioning from type_a to type_b along an axis (default \"Z\").\n");
		sb.append("    First half uses type_a, second half uses type_b. Use for color transitions across hull panels.\n\n");
		sb.append("- `scatter_surface(from_x, from_y, from_z, to_x, to_y, to_z, block_type, density [, seed [, orientation]])`\n");
		sb.append("    Place blocks randomly on exposed surfaces of existing geometry (air blocks adjacent to solids).\n");
		sb.append("    density is 0.0 to 1.0. Use for greebles, surface detail nubs, antenna arrays.\n\n");
		sb.append("- `extrude(from_x, from_z, to_x, to_z, src_y, height, block_type [, orientation [, copy_existing]])`\n");
		sb.append("    Extrude upward (or downward if height is negative) from a Y level.\n");
		sb.append("    If copy_existing=true, copies the block types found at src_y instead of using block_type.\n\n");
		sb.append("- `flood_fill(x, y, z, block_type [, orientation [, max_blocks]])`\n");
		sb.append("    Fill connected air space starting from a point. Stops at solid blocks and bounds.\n");
		sb.append("    max_blocks limits the fill (default 100000) to prevent runaway fills on open spaces.\n\n");

		sb.append("### Output Format\n");
		sb.append("Your ENTIRE response must be a single Lua code block wrapped in ```lua ... ``` fences.\n");
		sb.append("Do not include any prose, explanation, or commentary outside the code block.\n");
		sb.append("You can use Lua features like variables, loops, math, and functions to build procedurally.\n");
		return sb.toString();
	}

	private static final String SYSTEM_PROMPT_BODY =
			"You are an expert StarMade spaceship and station designer AI. You write Lua code to build 3D voxel templates.\n\n" +
				"CRITICAL RULES — VIOLATING THESE WILL CAUSE ERRORS:\n" +
				"1. ONLY use block names from the BLOCK PALETTE in the user prompt. Access them as blocks.EXACT_NAME.\n" +
				"   If a name is not in the palette, it does NOT exist. Do not guess or invent block names.\n" +
				"2. Use orient.NAME for orientations — never raw integers.\n" +
				"3. Lua syntax reminders — these are COMMON MISTAKES that WILL crash your script:\n" +
				"   - `and`/`or`/`not` — NOT `&&`/`||`/`!`\n" +
				"   - `math.abs()` — NOT `abs()`\n" +
				"   - `for i = 1, 10 do` — NOT `for i = 1 to 10 do` (use COMMA, not 'to')\n" +
				"   - `~=` for not-equal — NOT `!=`\n" +
				"4. Do NOT redefine `dims`. It is already a global with dims.x, dims.y, dims.z set for you.\n" +
				"5. Call the provided building primitives DIRECTLY. Do not write wrapper functions or abstractions.\n" +
				"6. Every line of code must DO something. No pseudo-code, no TODOs, no \"omitted for brevity\".\n\n" +
				"COORDINATE SYSTEM:\n" +
				"- Coordinates are 0-indexed: x=[0, dims.x-1], y=[0, dims.y-1], z=[0, dims.z-1]\n" +
				"- Z = length (nose=high Z, tail=low Z), Y = height (up=high Y), X = width (symmetry center = dims.x/2)\n\n" +
				"ORIENTATION SYSTEM:\n" +
				"- Basic: orient.FRONT(+Z), orient.BACK(-Z), orient.TOP(+Y), orient.BOTTOM(-Y), orient.RIGHT(+X), orient.LEFT(-X)\n" +
				"- Wedge (12): orient.wedge_{surface}_{direction} — e.g. orient.wedge_top_front\n" +
				"- Corner (24): orient.corner_{axis}_{dir1}_{dir2} — e.g. orient.corner_top_front_right\n" +
				"- Tetra (8): orient.tetra_{vert}_{fwd}_{lat} — e.g. orient.tetra_top_front_right\n" +
				"- Hepta (8): orient.hepta_{vert}_{fwd}_{lat} — e.g. orient.hepta_top_front_right\n\n" +
				"DESIGN PRINCIPLES:\n" +
				"- Never build a plain box. Taper toward nose and tail. Use wedges on all edges where hull meets air.\n" +
				"- Use shape primitives (cone, ellipsoid, cylinder) for the base form, then add detail.\n" +
				"- Build one half, then mirror(\"X\") for symmetry. Later calls overwrite earlier ones.\n" +
				"- Use at least 2-3 block colors/tiers for visual contrast.\n\n" +
				"EXAMPLE — a small 16x8x32 fighter (adapt block names to match your palette):\n" +
				"```lua\n" +
				"set_name(\"example_fighter\")\n" +
				"local mid = math.floor(dims.x / 2)\n\n" +
				"-- Fuselage: tapered cone along Z axis\n" +
				"cone(mid, 3, 0, mid, 3, dims.z - 1, 3, blocks.GREY_HULL, 1)\n\n" +
				"-- Cockpit canopy\n" +
				"ellipsoid(mid, 5, dims.z - 8, 2, 2, 3, blocks.GLASS)\n\n" +
				"-- Wings: flat slabs on one side, then mirror\n" +
				"fill(mid + 3, 3, 8, mid + 7, 3, 18, blocks.GREY_STANDARD_ARMOR)\n" +
				"-- Wing leading edge wedges\n" +
				"for x = mid + 3, mid + 7 do\n" +
				"    place(x, 3, 18, blocks.GREY_STANDARD_ARMOR_WEDGE, orient.wedge_top_front)\n" +
				"    place(x, 3, 8, blocks.GREY_STANDARD_ARMOR_WEDGE, orient.wedge_top_back)\n" +
				"end\n\n" +
				"-- Engine nacelle on one side\n" +
				"cylinder(mid + 5, 3, 2, mid + 5, 3, 6, 1, blocks.BLACK_STANDARD_ARMOR, true)\n\n" +
				"-- Mirror everything to the other side\n" +
				"mirror(\"X\")\n\n" +
				"-- Hollow out the interior\n" +
				"hollow(0, 0, 0, dims.x - 1, dims.y - 1, dims.z - 1, 1)\n" +
				"```\n\n" +
				"Follow this pattern: direct primitive calls, no wrappers, no pseudo-code.";

	static String buildUserPrompt(List<TemplateMetaData> references, int[] dims, String description, String palette) {
		StringBuilder sb = new StringBuilder();
		sb.append("Build a StarMade template with these specifications:\n\n");
		sb.append("DIMENSIONS: ").append(dims[0]).append("x").append(dims[1]).append("x").append(dims[2])
				.append(" (Width x Height x Length)\n");
		sb.append("X midpoint (symmetry axis): ").append(dims[0] / 2).append("\n");

		if(description != null && !description.isEmpty()) {
			sb.append("DESCRIPTION: ").append(description).append("\n");
		}

		sb.append("\nAVAILABLE BLOCK PALETTE — use EXACTLY these names via blocks.NAME (e.g. blocks.GREY_HULL):\n");
		sb.append(palette).append("\n");
		sb.append("DO NOT invent block names. If a name is not listed above, it does not exist and will cause an error.\n");

		if(references != null && !references.isEmpty()) {
			sb.append("\nREFERENCE TEMPLATES (use as style/composition inspiration):\n");
			for(TemplateMetaData ref : references) {
				int[] refDims = ref.getDimensions();
				sb.append("--- \"").append(ref.getName()).append("\" (")
						.append(refDims[0]).append("x").append(refDims[1]).append("x").append(refDims[2]).append(") ---\n");
				sb.append("Block counts: ").append(BlockPalette.summarizeTemplate(ref)).append("\n");
				sb.append("Structure (cross-sections, .=air):\n");
				sb.append(BlockPalette.structuralSummary(ref)).append("\n");
			}
		}

		sb.append("\nREMINDERS:\n");
		sb.append("- Use ONLY block names from the palette above. Accessing a nonexistent name crashes the script.\n");
		sb.append("- Call building primitives directly. No wrapper functions, no helper functions, no abstractions.\n");
		sb.append("- Every line must do something. No pseudo-code, no TODOs, no placeholders.\n");
		sb.append("- Lua syntax: use `and`/`or`/`not`, use `math.abs()`, NOT `&&`/`||`/`abs()`.\n");
		sb.append("- Build with shape primitives first (cone, ellipsoid, cylinder), then add detail.\n");
		sb.append("- Output ONLY a ```lua code block. No prose before or after.");
		return sb.toString();
	}

}
