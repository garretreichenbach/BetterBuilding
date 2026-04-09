package videogoose.betterbuilding.data.template;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import videogoose.betterbuilding.BetterBuilding;

import java.io.File;
import java.io.FileWriter;
import java.util.*;
import java.util.List;

/**
 * Decomposes existing .smtpl templates into tool-call sequences and exports
 * them as JSONL training data for fine-tuning an LLM with Unsloth or similar.
 *
 * Each template becomes one JSONL line containing a full conversation:
 * system prompt -> user prompt -> (assistant tool_calls + tool results)... -> finish
 */
public class TrainingDataExporter {

	private static final int MIN_FILL_VOLUME = 4; // Don't bother with fill for tiny regions

	/**
	 * Export .smtpl files matching the given template names to a JSONL training file.
	 * Names can include wildcard patterns (* and ?).
	 * Returns the number of templates exported.
	 */
	public static int exportAll(File templateDir, File outputFile, List<String> templateNames) throws Exception {
		// Resolve all names/patterns to actual template names
		Set<String> resolved = new LinkedHashSet<>();
		for(String name : templateNames) {
			if(name.contains("*") || name.contains("?")) {
				resolved.addAll(videogoose.betterbuilding.data.commands.GenerateTemplateCommand.resolveWildcard(name));
			} else {
				File f = new File(templateDir, name + ".smtpl");
				if(f.exists()) resolved.add(name);
			}
		}

		BetterBuilding.getInstance().logInfo("Resolved " + resolved.size() + " template(s) to export");

		if(resolved.isEmpty()) {
			// List what's actually in the directory for debugging
			File[] allFiles = templateDir.listFiles((dir, name) -> name.endsWith(".smtpl"));
			int total = allFiles != null ? allFiles.length : 0;
			throw new Exception("No matching .smtpl files found in " + templateDir.getAbsolutePath() +
					" (directory contains " + total + " .smtpl files)");
		}

		int count = 0;
		int skippedSmall = 0;
		int failed = 0;
		try(FileWriter writer = new FileWriter(outputFile)) {
			for(String name : resolved) {
				try {
					File file = new File(templateDir, name + ".smtpl");
					org.schema.game.client.controller.manager.ingame.CopyArea area =
							new org.schema.game.client.controller.manager.ingame.CopyArea();
					area.load(file);
					TemplateMetaData template = TemplateMetaData.fromRawTemplate(name, area);

					int blockCount = countNonAir(template);
					if(blockCount < 4) {
						BetterBuilding.getInstance().logInfo("Skipping " + name + " (only " + blockCount + " blocks)");
						skippedSmall++;
						continue;
					}

					String jsonLine = buildTrainingExample(template);
					writer.write(jsonLine);
					writer.write("\n");
					count++;
					BetterBuilding.getInstance().logInfo("Exported: " + name + " (" + blockCount + " blocks)");
				} catch(Exception e) {
					failed++;
					BetterBuilding.getInstance().logWarning("Failed to export " + name + ": " + e.getMessage());
				}
			}
		}

		BetterBuilding.getInstance().logInfo("Export complete: " + count + " exported, " +
				skippedSmall + " skipped (too small), " + failed + " failed");
		return count;
	}

	/**
	 * Export blueprints matching the given patterns to a JSONL training file.
	 * Reads full StarMade blueprint directories and decomposes them into tool-call sequences.
	 */
	public static int exportBlueprints(File blueprintDir, File outputFile, List<String> patterns) throws Exception {
		Set<String> resolved = new LinkedHashSet<>();
		for(String pattern : patterns) {
			resolved.addAll(BlueprintReader.resolveWildcard(blueprintDir, pattern));
		}

		BetterBuilding.getInstance().logInfo("Resolved " + resolved.size() + " blueprint(s) to export");

		if(resolved.isEmpty()) {
			List<String> all = BlueprintReader.listBlueprints(blueprintDir);
			throw new Exception("No matching blueprints found (directory contains " + all.size() + " blueprints)");
		}

		int count = 0;
		int skippedSmall = 0;
		int skippedLarge = 0;
		int failed = 0;
		try(FileWriter writer = new FileWriter(outputFile)) {
			for(String name : resolved) {
				try {
					BetterBuilding.getInstance().logInfo("Reading blueprint: " + name + " ...");
					TemplateMetaData template = BlueprintReader.readBlueprint(blueprintDir, name);

					if(template == null) {
						BetterBuilding.getInstance().logInfo("Skipping " + name + " (empty)");
						skippedSmall++;
						continue;
					}

					int blockCount = countNonAir(template);
					if(blockCount < 4) {
						BetterBuilding.getInstance().logInfo("Skipping " + name + " (only " + blockCount + " blocks)");
						skippedSmall++;
						continue;
					}

					int[] dims = template.getDimensions();
					// Skip extremely large blueprints that would create unmanageably long training sequences
					if(dims[0] > 256 || dims[1] > 256 || dims[2] > 256) {
						BetterBuilding.getInstance().logInfo("Skipping " + name + " (too large: " +
								dims[0] + "x" + dims[1] + "x" + dims[2] + ")");
						skippedLarge++;
						continue;
					}

					String jsonLine = buildTrainingExample(template);
					writer.write(jsonLine);
					writer.write("\n");
					count++;
					BetterBuilding.getInstance().logInfo("Exported: " + name + " (" + blockCount + " blocks, " +
							dims[0] + "x" + dims[1] + "x" + dims[2] + ")");
				} catch(Exception e) {
					failed++;
					BetterBuilding.getInstance().logWarning("Failed to export blueprint " + name + ": " + e.getMessage());
				}
			}
		}

		BetterBuilding.getInstance().logInfo("Blueprint export complete: " + count + " exported, " +
				skippedSmall + " skipped (too small/empty), " + skippedLarge + " skipped (too large), " + failed + " failed");
		return count;
	}

	/**
	 * Build a single training example (one JSONL line) from a template.
	 */
	private static String buildTrainingExample(TemplateMetaData template) {
		int[] dims = template.getDimensions();
		String palette = BlockPalette.toJsonPaletteString();

		// Decompose the template into tool calls
		List<ToolCall> toolCalls = decompose(template);

		// Add the finish call
		toolCalls.add(new ToolCall("finish", "{\"name\":\"" + escapeJson(template.getName()) + "\"}"));

		// Build the conversation
		JsonArray messages = new JsonArray();

		// System message - use the same prompt the generator uses
		JsonObject sysMsg = new JsonObject();
		sysMsg.addProperty("role", "system");
		sysMsg.addProperty("content", buildTrainingSystemPrompt());
		messages.add(sysMsg);

		// User message
		JsonObject userMsg = new JsonObject();
		userMsg.addProperty("role", "user");
		userMsg.addProperty("content", buildTrainingUserPrompt(template, palette));
		messages.add(userMsg);

		// Assistant tool calls and tool results, grouped into batches
		// We simulate the agentic loop: each iteration has 1 tool call + 1 tool result
		int callId = 1;
		int opsCount = 0;
		for(ToolCall tc : toolCalls) {
			String id = "call_" + callId++;

			// Assistant message with tool_calls
			JsonObject assistantMsg = new JsonObject();
			assistantMsg.addProperty("role", "assistant");
			assistantMsg.add("content", null);

			JsonArray toolCallsArray = new JsonArray();
			JsonObject toolCallObj = new JsonObject();
			toolCallObj.addProperty("id", id);
			toolCallObj.addProperty("type", "function");
			JsonObject fnObj = new JsonObject();
			fnObj.addProperty("name", tc.name);
			fnObj.addProperty("arguments", tc.arguments);
			toolCallObj.add("function", fnObj);
			toolCallsArray.add(toolCallObj);
			assistantMsg.add("tool_calls", toolCallsArray);
			messages.add(assistantMsg);

			// Tool result
			JsonObject toolResult = new JsonObject();
			toolResult.addProperty("role", "tool");
			toolResult.addProperty("tool_call_id", id);
			if("finish".equals(tc.name)) {
				toolResult.addProperty("content", "Template finalized with " + opsCount + " operations.");
			} else {
				toolResult.addProperty("content", tc.expectedResult);
				opsCount++;
			}
			messages.add(toolResult);
		}

		JsonObject example = new JsonObject();
		example.add("messages", messages);
		return example.toString();
	}

	// --- Decomposition ---

	/**
	 * Decompose a template into a sequence of tool calls that would reproduce it.
	 * Uses greedy maximal-rectangle packing: finds largest solid rectangles first,
	 * then handles remaining blocks individually.
	 */
	static List<ToolCall> decompose(TemplateMetaData template) {
		int[] dims = template.getDimensions();
		int totalSize = dims[0] * dims[1] * dims[2];

		// Track which positions have been accounted for
		boolean[] consumed = new boolean[totalSize];
		List<ToolCall> calls = new ArrayList<>();

		// Phase 1: Find large rectangular fills (greedy, largest first)
		boolean foundAny;
		do {
			foundAny = false;
			BestRect best = findLargestRect(template, consumed, dims);
			if(best != null && best.volume() >= MIN_FILL_VOLUME) {
				// Check if this region is a hollow shell
				if(best.volume() >= 27 && isHollowShell(template, best, dims)) {
					calls.add(makeShellCall(best));
				} else {
					calls.add(makeFillCall(best));
				}
				markConsumed(consumed, best, dims);
				foundAny = true;
			}
		} while(foundAny);

		// Phase 2: Find lines (sequences of same block along an axis)
		findLines(template, consumed, dims, calls);

		// Phase 3: Remaining blocks as individual place calls
		for(int z = 0; z < dims[2]; z++) {
			for(int y = 0; y < dims[1]; y++) {
				for(int x = 0; x < dims[0]; x++) {
					int idx = TemplateMetaData.index(x, y, z, dims);
					if(consumed[idx]) continue;
					short type = template.getBlockTypes()[idx];
					if(type == 0) continue;
					byte orient = template.getBlockOrientations()[idx];
					calls.add(makePlaceCall(x, y, z, type, orient));
					consumed[idx] = true;
				}
			}
		}

		return calls;
	}

	/**
	 * Find the largest axis-aligned rectangular region of the same block type
	 * and orientation that hasn't been consumed yet.
	 */
	private static BestRect findLargestRect(TemplateMetaData template, boolean[] consumed, int[] dims) {
		BestRect best = null;

		for(int z = 0; z < dims[2]; z++) {
			for(int y = 0; y < dims[1]; y++) {
				for(int x = 0; x < dims[0]; x++) {
					int idx = TemplateMetaData.index(x, y, z, dims);
					if(consumed[idx]) continue;
					short type = template.getBlockTypes()[idx];
					if(type == 0) continue;
					byte orient = template.getBlockOrientations()[idx];

					// Try to expand from this seed
					BestRect rect = expandRect(template, consumed, dims, x, y, z, type, orient);
					if(best == null || rect.volume() > best.volume()) {
						best = rect;
					}
				}
			}
		}
		return best;
	}

	/**
	 * Expand a rectangle from a seed point, greedily extending in X, then Y, then Z.
	 */
	private static BestRect expandRect(TemplateMetaData template, boolean[] consumed, int[] dims,
										int sx, int sy, int sz, short type, byte orient) {
		// Expand in X
		int ex = sx;
		while(ex + 1 < dims[0] && canInclude(template, consumed, dims, ex + 1, sy, sz, type, orient)) {
			ex++;
		}

		// Expand in Y
		int ey = sy;
		outer_y:
		while(ey + 1 < dims[1]) {
			for(int x = sx; x <= ex; x++) {
				if(!canInclude(template, consumed, dims, x, ey + 1, sz, type, orient)) break outer_y;
			}
			ey++;
		}

		// Expand in Z
		int ez = sz;
		outer_z:
		while(ez + 1 < dims[2]) {
			for(int y = sy; y <= ey; y++) {
				for(int x = sx; x <= ex; x++) {
					if(!canInclude(template, consumed, dims, x, y, ez + 1, type, orient)) break outer_z;
				}
			}
			ez++;
		}

		return new BestRect(sx, sy, sz, ex, ey, ez, type, orient);
	}

	private static boolean canInclude(TemplateMetaData template, boolean[] consumed, int[] dims,
									  int x, int y, int z, short type, byte orient) {
		int idx = TemplateMetaData.index(x, y, z, dims);
		if(consumed[idx]) return false;
		return template.getBlockTypes()[idx] == type && template.getBlockOrientations()[idx] == orient;
	}

	/**
	 * Check if a rectangular region is actually a hollow shell (walls only, interior is air).
	 */
	private static boolean isHollowShell(TemplateMetaData template, BestRect rect, int[] dims) {
		int sx = rect.maxX - rect.minX;
		int sy = rect.maxY - rect.minY;
		int sz = rect.maxZ - rect.minZ;
		if(sx < 2 || sy < 2 || sz < 2) return false; // Too thin to be hollow

		// Check that interior blocks are air in the original template
		int airCount = 0;
		int interiorCount = 0;
		for(int x = rect.minX + 1; x < rect.maxX; x++) {
			for(int y = rect.minY + 1; y < rect.maxY; y++) {
				for(int z = rect.minZ + 1; z < rect.maxZ; z++) {
					interiorCount++;
					if(template.getBlockTypes()[TemplateMetaData.index(x, y, z, dims)] == 0) {
						airCount++;
					}
				}
			}
		}
		// It's a shell if most of the interior is air
		return interiorCount > 0 && airCount >= interiorCount * 0.8;
	}

	/**
	 * Find lines of 3+ same-type blocks along each axis.
	 */
	private static void findLines(TemplateMetaData template, boolean[] consumed, int[] dims, List<ToolCall> calls) {
		// Check along each axis
		int[][] axes = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}; // X, Y, Z directions

		for(int[] axis : axes) {
			for(int z = 0; z < dims[2]; z++) {
				for(int y = 0; y < dims[1]; y++) {
					for(int x = 0; x < dims[0]; x++) {
						int idx = TemplateMetaData.index(x, y, z, dims);
						if(consumed[idx]) continue;
						short type = template.getBlockTypes()[idx];
						if(type == 0) continue;
						byte orient = template.getBlockOrientations()[idx];

						// Try to extend along this axis
						int len = 1;
						while(true) {
							int nx = x + axis[0] * len;
							int ny = y + axis[1] * len;
							int nz = z + axis[2] * len;
							if(nx >= dims[0] || ny >= dims[1] || nz >= dims[2]) break;
							int nIdx = TemplateMetaData.index(nx, ny, nz, dims);
							if(consumed[nIdx]) break;
							if(template.getBlockTypes()[nIdx] != type || template.getBlockOrientations()[nIdx] != orient) break;
							len++;
						}

						if(len >= 3) {
							int ex = x + axis[0] * (len - 1);
							int ey = y + axis[1] * (len - 1);
							int ez = z + axis[2] * (len - 1);
							calls.add(makeLineCall(x, y, z, ex, ey, ez, type, orient));
							for(int i = 0; i < len; i++) {
								int cx = x + axis[0] * i;
								int cy = y + axis[1] * i;
								int cz = z + axis[2] * i;
								consumed[TemplateMetaData.index(cx, cy, cz, dims)] = true;
							}
						}
					}
				}
			}
		}
	}

	private static void markConsumed(boolean[] consumed, BestRect rect, int[] dims) {
		for(int x = rect.minX; x <= rect.maxX; x++) {
			for(int y = rect.minY; y <= rect.maxY; y++) {
				for(int z = rect.minZ; z <= rect.maxZ; z++) {
					int idx = TemplateMetaData.index(x, y, z, dims);
					// For shells, only mark the shell blocks (not interior air)
					consumed[idx] = true;
				}
			}
		}
	}

	// --- Tool call builders ---

	private static ToolCall makeFillCall(BestRect r) {
		String args = String.format(
				"{\"from_x\":%d,\"from_y\":%d,\"from_z\":%d,\"to_x\":%d,\"to_y\":%d,\"to_z\":%d,\"block_type\":%d%s}",
				r.minX, r.minY, r.minZ, r.maxX, r.maxY, r.maxZ, r.type,
				r.orient != 0 ? ",\"orientation\":" + r.orient : "");
		return new ToolCall("fill", args, "Filled " + r.volume() + " blocks");
	}

	private static ToolCall makeShellCall(BestRect r) {
		String args = String.format(
				"{\"from_x\":%d,\"from_y\":%d,\"from_z\":%d,\"to_x\":%d,\"to_y\":%d,\"to_z\":%d,\"block_type\":%d%s}",
				r.minX, r.minY, r.minZ, r.maxX, r.maxY, r.maxZ, r.type,
				r.orient != 0 ? ",\"orientation\":" + r.orient : "");
		int shellCount = r.volume() - (r.maxX - r.minX - 1) * (r.maxY - r.minY - 1) * (r.maxZ - r.minZ - 1);
		return new ToolCall("shell", args, "Shell placed " + shellCount + " blocks");
	}

	private static ToolCall makeLineCall(int x1, int y1, int z1, int x2, int y2, int z2, short type, byte orient) {
		String args = String.format(
				"{\"from_x\":%d,\"from_y\":%d,\"from_z\":%d,\"to_x\":%d,\"to_y\":%d,\"to_z\":%d,\"block_type\":%d%s}",
				x1, y1, z1, x2, y2, z2, type,
				orient != 0 ? ",\"orientation\":" + orient : "");
		int len = Math.max(Math.abs(x2 - x1), Math.max(Math.abs(y2 - y1), Math.abs(z2 - z1))) + 1;
		return new ToolCall("line", args, "Line placed " + len + " blocks");
	}

	private static ToolCall makePlaceCall(int x, int y, int z, short type, byte orient) {
		String args = String.format(
				"{\"x\":%d,\"y\":%d,\"z\":%d,\"block_type\":%d%s}",
				x, y, z, type,
				orient != 0 ? ",\"orientation\":" + orient : "");
		return new ToolCall("place", args, "Placed block at " + x + "," + y + "," + z);
	}

	// --- Prompt builders (training versions) ---

	private static String buildTrainingSystemPrompt() {
		// Same system prompt used by TemplateGenerator so the fine-tuned model
		// learns to respond to the same instructions it will see at inference time
		return "You are an expert StarMade spaceship and station designer AI. You build 3D voxel templates by calling tools.\n\n" +
				"COORDINATE SYSTEM:\n" +
				"- All coordinates are 0-indexed within the given dimensions [x,y,z]\n" +
				"- Z axis: forward/backward. +Z = nose/front, -Z = tail/rear. This is the ship's LENGTH axis\n" +
				"- Y axis: up/down. +Y = top, -Y = bottom. This is the ship's HEIGHT axis\n" +
				"- X axis: left/right. +X = starboard (right), -X = port (left). This is the ship's WIDTH axis\n" +
				"- The CENTER of the X axis is the ship's spine/midline. Most ships should be symmetric about this axis\n\n" +
				"BLOCK TYPES & ORIENTATION:\n" +
				"- block_type values are integer IDs from the provided block palette\n" +
				"- Orientation values 0-5 control which direction a block faces:\n" +
				"  0=front(+Z), 1=back(-Z), 2=top(+Y), 3=bottom(-Y), 4=right(+X), 5=left(-X)\n" +
				"- Weapons should face FORWARD (orientation 0)\n" +
				"- Thrusters should face BACK (orientation 1)\n\n" +
				"SHAPE BLOCKS - USE THESE EXTENSIVELY:\n" +
				"- WEDGE blocks are slope/ramp shapes. Use them to taper edges and create angled surfaces instead of flat walls\n" +
				"- CORNER blocks fill 3-sided corners where two wedges meet. Use at tips and vertices\n" +
				"- HEPTA blocks are 7/8 blocks (a cube with one corner cut). Use for subtle chamfers and gentle transitions\n" +
				"- TETRA blocks are 1/4 pyramids. Use for sharp pointed tips like noses and wing tips\n" +
				"- SLAB blocks are half-height blocks. Use for fine detailing and thin features\n" +
				"- Orient shape blocks so the cut/slope faces the correct direction using the orientation parameter\n\n" +
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
				"TECHNIQUE NOTES:\n" +
				"- Use ellipsoid, cylinder, and cone tools to create organic rounded shapes FIRST, then detail with block tools\n" +
				"- Later tool calls overwrite earlier ones, so layer details on top of base shapes\n" +
				"- Use the mirror tool to get perfect bilateral symmetry: build one side, then mirror across X\n" +
				"- Combine shapes: e.g. cone for nose + cylinder for body + ellipsoid for cockpit dome\n" +
				"- Call 'finish' when you are done building";
	}

	private static String buildTrainingUserPrompt(TemplateMetaData template, String palette) {
		int[] dims = template.getDimensions();
		// Use the template name as a rough description (replace underscores with spaces)
		String description = template.getName().replace("_", " ").replace("-", " ");

		StringBuilder sb = new StringBuilder();
		sb.append("Build a StarMade template with these specifications:\n\n");
		sb.append("DIMENSIONS: ").append(dims[0]).append("x").append(dims[1]).append("x").append(dims[2])
				.append(" (Width x Height x Length)\n");
		sb.append("X midpoint (symmetry axis): ").append(dims[0] / 2).append("\n");
		sb.append("DESCRIPTION: ").append(description).append("\n");
		sb.append("\nAVAILABLE BLOCK PALETTE (ONLY use block_type IDs from this list):\n");
		sb.append(palette).append("\n");
		sb.append("\nREMINDER: Do NOT build a plain box. Use wedge, corner, hepta, and tetra blocks from the palette ");
		sb.append("to create tapered, angled surfaces. Every edge where hull meets air should use shape blocks. ");
		sb.append("Start with the overall tapered shape, then add features and details.\n");
		sb.append("Begin building now, then call 'finish' with a name when done.");
		return sb.toString();
	}

	// --- Utility ---

	private static int countNonAir(TemplateMetaData template) {
		int count = 0;
		for(short type : template.getBlockTypes()) {
			if(type != 0) count++;
		}
		return count;
	}

	private static String escapeJson(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	// --- Inner classes ---

	static class ToolCall {
		final String name;
		final String arguments;
		final String expectedResult;

		ToolCall(String name, String arguments) {
			this(name, arguments, "");
		}

		ToolCall(String name, String arguments, String expectedResult) {
			this.name = name;
			this.arguments = arguments;
			this.expectedResult = expectedResult;
		}
	}

	static class BestRect {
		final int minX, minY, minZ, maxX, maxY, maxZ;
		final short type;
		final byte orient;

		BestRect(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, short type, byte orient) {
			this.minX = minX; this.minY = minY; this.minZ = minZ;
			this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
			this.type = type; this.orient = orient;
		}

		int volume() {
			return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
		}
	}
}
