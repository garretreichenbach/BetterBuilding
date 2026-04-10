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
 *
 *   system  -> the same system prompt {@link TemplateGenerator} uses at inference,
 *              so the model is trained against exactly the prompt it will see live.
 *   user    -> the build instructions ({@link TemplateGenerator#buildUserPrompt}).
 *   user    -> alternating turns: each "model" turn contains one or more
 *   model      <tool_call>...</tool_call> blocks; the following "user" turn
 *              echoes back the matching <tool_response>...</tool_response>
 *              blocks. Wire format lives in {@link ToolCallFormat}.
 *
 * The conversation uses only system / user / assistant roles (no `tool` role
 * and no structured `tool_calls` field), so any chat template that supports
 * a basic three-role chat can carry it — including Gemma's user/model template.
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
	 *
	 * The output mirrors what an inference loop would actually look like:
	 * one tool call per assistant turn, each followed by a user turn carrying
	 * the corresponding tool response. Both share the prompt builders in
	 * {@link TemplateGenerator} so training and inference can never drift.
	 */
	private static String buildTrainingExample(TemplateMetaData template) {
		String palette = BlockPalette.toJsonPaletteString();

		// Decompose the template into tool calls
		List<ToolCall> toolCalls = decompose(template);

		// Add the finish call
		toolCalls.add(new ToolCall("finish", "{\"name\":\"" + escapeJson(template.getName()) + "\"}"));

		// Build the conversation
		JsonArray messages = new JsonArray();

		// System message — pulled from TemplateGenerator so it's identical to inference
		JsonObject sysMsg = new JsonObject();
		sysMsg.addProperty("role", "system");
		sysMsg.addProperty("content", TemplateGenerator.buildSystemPrompt());
		messages.add(sysMsg);

		// User message — also from TemplateGenerator. The exporter has no real
		// description for the template, so we synthesise one from the file name.
		String description = template.getName().replace("_", " ").replace("-", " ");
		JsonObject userMsg = new JsonObject();
		userMsg.addProperty("role", "user");
		userMsg.addProperty("content",
				TemplateGenerator.buildUserPrompt(null, template.getDimensions(), description, palette));
		messages.add(userMsg);

		// Alternating assistant / user turns. Each assistant turn emits one
		// <tool_call> block; the following user turn echoes the matching
		// <tool_response>. This is the format ToolCallFormat parses at inference.
		int opsCount = 0;
		for(ToolCall tc : toolCalls) {
			JsonObject assistantMsg = new JsonObject();
			assistantMsg.addProperty("role", "assistant");
			assistantMsg.addProperty("content",
					ToolCallFormat.serializeCallWithRawArgs(tc.name, tc.arguments));
			messages.add(assistantMsg);

			String result;
			if("finish".equals(tc.name)) {
				result = "Template finalized with " + opsCount + " operations.";
			} else {
				result = tc.expectedResult;
				opsCount++;
			}

			JsonObject toolResultMsg = new JsonObject();
			toolResultMsg.addProperty("role", "user");
			toolResultMsg.addProperty("content", ToolCallFormat.serializeResponse(result));
			messages.add(toolResultMsg);
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
