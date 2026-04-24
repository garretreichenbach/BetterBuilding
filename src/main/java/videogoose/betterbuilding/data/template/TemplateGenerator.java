package videogoose.betterbuilding.data.template;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import videogoose.betterbuilding.BetterBuilding;
import videogoose.betterbuilding.manager.ConfigManager;
import videogoose.betterbuilding.manager.AIClient;

import java.util.List;
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
	private static final int MAX_RETRIES = 5;

	private static final Pattern LUA_FENCE_PATTERN = Pattern.compile(
			"```lua\\s*\\n(.*?)```", Pattern.DOTALL);
	private static final Pattern GENERIC_FENCE_PATTERN = Pattern.compile(
			"```\\s*\\n(.*?)```", Pattern.DOTALL);

	public static TemplateMetaData generate(List<TemplateMetaData> references, int[] outputDims, String description, Set<Short> hotbarTypes) throws Exception {
		validateDimensions(outputDims);

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

		String palette = (hotbarTypes != null && !hotbarTypes.isEmpty())
				? BlockPalette.toHotbarPaletteString(hotbarTypes)
				: BlockPalette.toJsonPaletteString();

		JsonArray messages = new JsonArray();
		messages.add(makeMessage("system", buildSystemPrompt()));
		messages.add(makeMessage("user", buildUserPrompt(references, outputDims, description, palette)));

		BetterBuilding.getInstance().logInfo("Requesting Lua build script via " + providerName + "...");

		TemplateMetaData template = null;
		String lastError = null;

		for(int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
			if(attempt > 0) {
				BetterBuilding.getInstance().logInfo("Retry " + attempt + "/" + MAX_RETRIES + " — error was: " + lastError);
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

			template = new TemplateMetaData("ai_generated", outputDims);
			LuaExecutor executor = new LuaExecutor(template, outputDims);

			try {
				executor.execute(luaCode);
			} catch(Exception e) {
				lastError = e.getMessage();
				template = null;
				continue;
			}

			if(executor.getTotalOps() == 0) {
				lastError = "Script ran but did not call any building functions (fill, place, etc.). Your code must call at least one building primitive.";
				template = null;
				continue;
			}

			// Count non-air blocks
			int blockCount = 0;
			short[] types = template.getBlockTypes();
			for(short t : types) {
				if(t != 0) blockCount++;
			}
			if(blockCount == 0) {
				lastError = "Script called building functions but resulted in 0 non-air blocks. Check your block_type IDs and coordinates.";
				template = null;
				continue;
			}

			// Apply template name if set via set_name()
			if(executor.getTemplateName() != null && !executor.getTemplateName().isEmpty()) {
				template.setName(executor.getTemplateName());
			}

			BetterBuilding.getInstance().logInfo("Lua script executed successfully: " + executor.getTotalOps() + " operations, " + blockCount + " blocks placed");
			break;
		}

		if(template == null) {
			throw new Exception("Failed to generate template after " + (MAX_RETRIES + 1) + " attempts. Last error: " + lastError);
		}

		return template;
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
				"COORDINATE SYSTEM:\n" +
				"- All coordinates are 0-indexed within the given dimensions [x,y,z]\n" +
				"- Z axis: forward/backward. +Z = nose/front, -Z = tail/rear. This is the ship's LENGTH axis\n" +
				"- Y axis: up/down. +Y = top, -Y = bottom. This is the ship's HEIGHT axis\n" +
				"- X axis: left/right. +X = starboard (right), -X = port (left). This is the ship's WIDTH axis\n" +
				"- The CENTER of the X axis is the ship's spine/midline. Most ships should be symmetric about this axis\n\n" +
				"BLOCK TYPES:\n" +
				"- block_type values are integer IDs from the provided block palette\n" +
				"- Store block type IDs in local variables for readability\n\n" +
				"ORIENTATION SYSTEM:\n" +
				"- Use the `orient` table for all orientation values — NEVER use raw integer orientation values\n" +
				"- Basic blocks (full cubes): orient.FRONT(+Z), orient.BACK(-Z), orient.TOP(+Y), orient.BOTTOM(-Y), orient.RIGHT(+X), orient.LEFT(-X)\n" +
				"- Weapons should face orient.FRONT, thrusters should face orient.BACK\n\n" +
				"SHAPE BLOCKS AND THEIR ORIENTATIONS — USE THESE EXTENSIVELY:\n" +
				"- WEDGE: slope/ramp shape (half a cube cut diagonally). 12 orientations.\n" +
				"  Named orient.wedge_{surface}_{slope_direction}: surface is where the slope is (top/bottom/side),\n" +
				"  slope_direction is which way it descends (front/back/left/right).\n" +
				"  Example: orient.wedge_top_front = slope on top surface descending toward front (+Z).\n" +
				"  Use to taper edges and create angled surfaces instead of flat walls.\n" +
				"- CORNER: triangular spike, 1/8 of a cube. 24 orientations.\n" +
				"  Named orient.corner_{axis}_{dir1}_{dir2}: the point/spike is at that corner of the cube.\n" +
				"  Example: orient.corner_top_front_right = spike pointing toward top-front-right corner.\n" +
				"  Use at vertices and tips where two wedges meet.\n" +
				"- TETRA: 1/4 pyramid. 8 orientations.\n" +
				"  Named orient.tetra_{vertical}_{forward}_{lateral}: the point is at that corner.\n" +
				"  Example: orient.tetra_top_front_right = pyramid point at top-front-right.\n" +
				"  Use for sharp pointed tips like noses and wing tips.\n" +
				"- HEPTA: 7/8 block (cube with one corner chamfered). 8 orientations.\n" +
				"  Named orient.hepta_{vertical}_{forward}_{lateral}: the CUT corner is at that position.\n" +
				"  Example: orient.hepta_top_front_right = corner chamfer at top-front-right.\n" +
				"  Use for subtle chamfers and gentle transitions.\n" +
				"- SLAB: half-height blocks. Use basic orient.TOP/BOTTOM/etc. for face direction.\n" +
				"  Use for fine detailing and thin features.\n\n" +
				"DESIGN PRINCIPLES - CRITICAL:\n" +
				"- NEVER build a plain box or rectangular prism. Every design must have tapered, angled, or curved surfaces\n" +
				"- Ships should taper toward the nose (+Z end) using wedges and corners to form a pointed or streamlined front\n" +
				"- Ships should taper toward the rear (-Z end) around the engines\n" +
				"- Use wedges along ALL edges where hull meets open space to eliminate boxy 90-degree corners\n" +
				"- Vary the cross-section along the Z axis: narrower at front and back, wider in the middle\n" +
				"- Use at least 2-3 different hull/armor colors or tiers to create visual contrast and panel lines\n" +
				"- Add asymmetric or protruding features: wings, fins, nacelles, antenna masts, turret mounts\n" +
				"- Sections should be hollow so users can fill them with system blocks\n\n" +
				"BUILDING METHODOLOGY:\n" +
				"1. SKELETON: Start by building the central spine/fuselage as a long shape tapered at both ends\n" +
				"2. HULL: Add the main hull around the spine, varying width and height along the Z axis\n" +
				"3. SHAPING: Replace all boxy edges with wedges, corners, and hepta blocks. This is the most important step\n" +
				"4. FEATURES: Add wings, fins, nacelles, cockpit canopy, engine housings, weapon mounts\n" +
				"5. DETAILING: Add panel lines using different hull tiers, accent lights, glass for cockpits\n" +
				"6. CLEANUP: Clear any interior blocks for hollow sections, ensure symmetry on X axis\n\n" +
				"COMMON PATTERNS:\n" +
				"- Tapered nose: Use cone() with tip_radius=0 pointing along +Z, or build manually with tetra/wedge blocks\n" +
				"- Fuselage: Use cylinder() or cone() for the main body, then detail with wedge/hepta blocks\n" +
				"- Wing: A thin slab of hull extending on the X axis, with wedge leading/trailing edges\n" +
				"- Engine nacelle: Use cylinder() with hollow=true, cap ends with ellipsoid() or cone()\n" +
				"- Cockpit: ellipsoid() with glass blocks, cut in half with clear() for a canopy\n" +
				"- Rounded hull: Use ellipsoid() for the overall shape, then carve details with clear() and fill()\n\n" +
				"TECHNIQUE NOTES:\n" +
				"- Use ellipsoid(), cylinder(), and cone() to create organic rounded shapes FIRST, then detail with block functions\n" +
				"- Later calls overwrite earlier ones, so layer details on top of base shapes\n" +
				"- Use mirror() to get perfect bilateral symmetry: build one side, then mirror across X\n" +
				"- Combine shapes: e.g. cone() for nose + cylinder() for body + ellipsoid() for cockpit dome\n" +
				"- Use Lua loops and math to create repeating patterns, gradual tapers, and complex geometry\n" +
				"- Store block type IDs in local variables for readability";

	static String buildUserPrompt(List<TemplateMetaData> references, int[] dims, String description, String palette) {
		StringBuilder sb = new StringBuilder();
		sb.append("Build a StarMade template with these specifications:\n\n");
		sb.append("DIMENSIONS: ").append(dims[0]).append("x").append(dims[1]).append("x").append(dims[2])
				.append(" (Width x Height x Length)\n");
		sb.append("X midpoint (symmetry axis): ").append(dims[0] / 2).append("\n");

		if(description != null && !description.isEmpty()) {
			sb.append("DESCRIPTION: ").append(description).append("\n");
		}

		sb.append("\nAVAILABLE BLOCK PALETTE (ONLY use block_type IDs from this list):\n");
		sb.append(palette).append("\n");

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

		sb.append("\nREMINDER: Do NOT build a plain box. Use wedge, corner, hepta, and tetra blocks from the palette ");
		sb.append("to create tapered, angled surfaces. Every edge where hull meets air should use shape blocks. ");
		sb.append("Start with the overall tapered shape, then add features and details.\n");
		sb.append("Output ONLY a ```lua code block, no prose.");
		return sb.toString();
	}

}
