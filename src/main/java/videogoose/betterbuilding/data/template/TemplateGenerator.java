package videogoose.betterbuilding.data.template;

import api.utils.element.Blocks;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import videogoose.betterbuilding.BetterBuilding;
import videogoose.betterbuilding.manager.ConfigManager;
import videogoose.betterbuilding.manager.LMStudioClient;

import java.util.List;
import java.util.Set;

/**
 * Generates building templates via an agentic tool-calling loop.
 * The AI is given tools (fill, shell, place, line, clear, finish) and
 * iteratively builds the template one tool call at a time until it
 * calls "finish" to signal completion.
 */
public class TemplateGenerator {

	public static final int DEFAULT_MAX_DIM = 64;
	private static final int MAX_ITERATIONS = 128;

	public static TemplateMetaData generate(List<TemplateMetaData> references, int[] outputDims, String description, Set<Short> hotbarTypes) throws Exception {
		validateDimensions(outputDims);

		boolean useOllama = "ollama".equals(ConfigManager.getProvider());
		String url = useOllama ? ConfigManager.getOllamaUrl() : ConfigManager.getLMStudioUrl();
		String model = useOllama ? ConfigManager.getOllamaModel() : ConfigManager.getLMStudioModel();
		String providerName = useOllama ? "Ollama" : "LM Studio";

		LMStudioClient client = new LMStudioClient(
				url, model,
				ConfigManager.getLMStudioTemperature(),
				ConfigManager.getLMStudioMaxTokens(),
				ConfigManager.getLMStudioTimeout()
		);

		String palette = (hotbarTypes != null && !hotbarTypes.isEmpty())
				? BlockPalette.toHotbarPaletteString(hotbarTypes)
				: BlockPalette.toJsonPaletteString();

		TemplateMetaData template = new TemplateMetaData("ai_generated", outputDims);
		JsonArray tools = buildTools();
		JsonArray messages = new JsonArray();
		messages.add(makeMessage("system", buildSystemPrompt()));
		messages.add(makeMessage("user", buildUserPrompt(references, outputDims, description, palette)));

		BetterBuilding.getInstance().logInfo("Starting template generation loop via " + providerName + "...");

		int totalOps = 0;
		for(int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
			JsonObject assistantMsg = client.chatCompletionWithTools(messages, tools);
			messages.add(assistantMsg);

			if(!assistantMsg.has("tool_calls")) {
				BetterBuilding.getInstance().logInfo("AI stopped calling tools after " + totalOps + " operations");
				break;
			}

			JsonArray toolCalls = assistantMsg.getAsJsonArray("tool_calls");
			boolean finished = false;

			for(JsonElement tcEl : toolCalls) {
				JsonObject toolCall = tcEl.getAsJsonObject();
				String id = toolCall.get("id").getAsString();
				JsonObject function = toolCall.getAsJsonObject("function");
				String fnName = function.get("name").getAsString();
				JsonObject args = new JsonParser().parse(function.get("arguments").getAsString()).getAsJsonObject();

				String result;
				if("finish".equals(fnName)) {
					if(args.has("name")) {
						String aiName = args.get("name").getAsString().trim()
								.replaceAll("[^a-zA-Z0-9_\\-]", "_")
								.replaceAll("_+", "_");
						if(!aiName.isEmpty()) template.setName(aiName);
					}
					result = "Template finalized with " + totalOps + " operations.";
					finished = true;
				} else {
					result = executeTool(fnName, args, template, outputDims);
					totalOps++;
				}

				JsonObject toolResult = new JsonObject();
				toolResult.addProperty("role", "tool");
				toolResult.addProperty("tool_call_id", id);
				toolResult.addProperty("content", result);
				messages.add(toolResult);
			}

			if(finished) {
				BetterBuilding.getInstance().logInfo("Template generation finished after " + totalOps + " operations");
				break;
			}
		}

		if(totalOps == 0) {
			throw new Exception("AI did not place any blocks");
		}

		return template;
	}

	private static String executeTool(String name, JsonObject args, TemplateMetaData template, int[] dims) {
		try {
			switch(name) {
				case "fill": return execFill(template, args, dims);
				case "shell": return execShell(template, args, dims);
				case "place": return execPlace(template, args, dims);
				case "line": return execLine(template, args, dims);
				case "clear": return execClear(template, args, dims);
				default: return "Unknown tool: " + name;
			}
		} catch(Exception e) {
			return "Error: " + e.getMessage();
		}
	}

	// --- Tool execution ---

	private static String execFill(TemplateMetaData template, JsonObject args, int[] dims) {
		int[] from = getCoords(args, "from_x", "from_y", "from_z");
		int[] to = getCoords(args, "to_x", "to_y", "to_z");
		short type = args.get("block_type").getAsShort();
		byte orient = args.has("orientation") ? args.get("orientation").getAsByte() : 0;

		int count = 0;
		for(int x = Math.min(from[0], to[0]); x <= Math.max(from[0], to[0]); x++) {
			for(int y = Math.min(from[1], to[1]); y <= Math.max(from[1], to[1]); y++) {
				for(int z = Math.min(from[2], to[2]); z <= Math.max(from[2], to[2]); z++) {
					if(inBounds(x, y, z, dims)) {
						template.setTypeAt(x, y, z, type);
						template.setOrientationAt(x, y, z, orient);
						count++;
					}
				}
			}
		}
		return "Filled " + count + " blocks";
	}

	private static String execShell(TemplateMetaData template, JsonObject args, int[] dims) {
		int[] from = getCoords(args, "from_x", "from_y", "from_z");
		int[] to = getCoords(args, "to_x", "to_y", "to_z");
		short type = args.get("block_type").getAsShort();
		byte orient = args.has("orientation") ? args.get("orientation").getAsByte() : 0;
		int thickness = args.has("thickness") ? args.get("thickness").getAsInt() : 1;

		int minX = Math.min(from[0], to[0]), maxX = Math.max(from[0], to[0]);
		int minY = Math.min(from[1], to[1]), maxY = Math.max(from[1], to[1]);
		int minZ = Math.min(from[2], to[2]), maxZ = Math.max(from[2], to[2]);

		int count = 0;
		for(int x = minX; x <= maxX; x++) {
			for(int y = minY; y <= maxY; y++) {
				for(int z = minZ; z <= maxZ; z++) {
					boolean onShell = (x - minX < thickness) || (maxX - x < thickness) ||
							(y - minY < thickness) || (maxY - y < thickness) ||
							(z - minZ < thickness) || (maxZ - z < thickness);
					if(onShell && inBounds(x, y, z, dims)) {
						template.setTypeAt(x, y, z, type);
						template.setOrientationAt(x, y, z, orient);
						count++;
					}
				}
			}
		}
		return "Shell placed " + count + " blocks";
	}

	private static String execPlace(TemplateMetaData template, JsonObject args, int[] dims) {
		int x = args.get("x").getAsInt();
		int y = args.get("y").getAsInt();
		int z = args.get("z").getAsInt();
		short type = args.get("block_type").getAsShort();
		byte orient = args.has("orientation") ? args.get("orientation").getAsByte() : 0;

		if(!inBounds(x, y, z, dims)) return "Out of bounds: " + x + "," + y + "," + z;
		template.setTypeAt(x, y, z, type);
		template.setOrientationAt(x, y, z, orient);
		return "Placed block at " + x + "," + y + "," + z;
	}

	private static String execLine(TemplateMetaData template, JsonObject args, int[] dims) {
		int[] from = getCoords(args, "from_x", "from_y", "from_z");
		int[] to = getCoords(args, "to_x", "to_y", "to_z");
		short type = args.get("block_type").getAsShort();
		byte orient = args.has("orientation") ? args.get("orientation").getAsByte() : 0;

		int dx = Math.abs(to[0] - from[0]);
		int dy = Math.abs(to[1] - from[1]);
		int dz = Math.abs(to[2] - from[2]);
		int sx = from[0] < to[0] ? 1 : -1;
		int sy = from[1] < to[1] ? 1 : -1;
		int sz = from[2] < to[2] ? 1 : -1;

		int dm = Math.max(dx, Math.max(dy, dz));
		int x = from[0], y = from[1], z = from[2];
		int xErr = dm / 2, yErr = dm / 2, zErr = dm / 2;

		int count = 0;
		for(int i = 0; i <= dm; i++) {
			if(inBounds(x, y, z, dims)) {
				template.setTypeAt(x, y, z, type);
				template.setOrientationAt(x, y, z, orient);
				count++;
			}
			xErr -= dx;
			if(xErr < 0) { xErr += dm; x += sx; }
			yErr -= dy;
			if(yErr < 0) { yErr += dm; y += sy; }
			zErr -= dz;
			if(zErr < 0) { zErr += dm; z += sz; }
		}
		return "Line placed " + count + " blocks";
	}

	private static String execClear(TemplateMetaData template, JsonObject args, int[] dims) {
		int[] from = getCoords(args, "from_x", "from_y", "from_z");
		int[] to = getCoords(args, "to_x", "to_y", "to_z");

		int count = 0;
		for(int x = Math.min(from[0], to[0]); x <= Math.max(from[0], to[0]); x++) {
			for(int y = Math.min(from[1], to[1]); y <= Math.max(from[1], to[1]); y++) {
				for(int z = Math.min(from[2], to[2]); z <= Math.max(from[2], to[2]); z++) {
					if(inBounds(x, y, z, dims)) {
						template.setTypeAt(x, y, z, (short) 0);
						template.setOrientationAt(x, y, z, (byte) 0);
						count++;
					}
				}
			}
		}
		return "Cleared " + count + " blocks";
	}

	// --- Helpers ---

	private static int[] getCoords(JsonObject args, String xKey, String yKey, String zKey) {
		return new int[] {args.get(xKey).getAsInt(), args.get(yKey).getAsInt(), args.get(zKey).getAsInt()};
	}

	private static boolean inBounds(int x, int y, int z, int[] dims) {
		return x >= 0 && x < dims[0] && y >= 0 && y < dims[1] && z >= 0 && z < dims[2];
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

	private static String buildSystemPrompt() {
		return "You are a StarMade spaceship and station designer AI. You build 3D templates by calling tools.\n\n" +
				"RULES:\n" +
				"- All coordinates are 0-indexed within the given dimensions [x,y,z]\n" +
				"- The Z axis is the forward/backward axis of the ship. +Z is the nose/front, -Z is the tail/rear\n" +
				"- The Y axis is up/down. +Y is the top, -Y is the bottom\n" +
				"- The X axis is left/right. +X is starboard (right), -X is port (left)\n" +
				"- block_type values are integer IDs from the provided block palette\n" +
				"- Orientation values 0-5 control which direction a block faces:\n" +
				"  0=front(+Z), 1=back(-Z), 2=top(+Y), 3=bottom(-Y), 4=right(+X), 5=left(-X)\n" +
				"- Weapons like cannons and missiles should face FORWARD (orientation 0) to fire along +Z\n" +
				"- Thrusters should face BACK (orientation 1) to push the ship forward along +Z\n" +
				"- Sections should be hollow unless otherwise specified, so users can fill them with system blocks\n" +
				"- Wedge/corner/hepta/tetra/slab variants exist for shaping\n" +
				"- Create aerodynamic, visually interesting shapes\n" +
				"- Later tool calls overwrite earlier ones, so you can layer details on top of base shapes\n" +
				"- Build iteratively: start with the overall shape, then add details\n" +
				"- Call 'finish' when you are done building";
	}

	private static String buildUserPrompt(List<TemplateMetaData> references, int[] dims, String description, String palette) {
		StringBuilder sb = new StringBuilder();
		sb.append("Build a StarMade template with these specifications:\n\n");
		sb.append("DIMENSIONS: ").append(dims[0]).append("x").append(dims[1]).append("x").append(dims[2]).append(" (X,Y,Z)\n");

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

		sb.append("\nStart building. Use the tools to place blocks, then call 'finish' with a name when done.");
		return sb.toString();
	}

	// --- Tool definitions ---

	private static JsonArray buildTools() {
		JsonArray tools = new JsonArray();
		tools.add(makeTool("fill", "Fill a rectangular region with a block type",
				new String[]{"from_x", "from_y", "from_z", "to_x", "to_y", "to_z", "block_type"},
				new String[]{"orientation"}));
		tools.add(makeTool("shell", "Create a hollow shell of a rectangular region (walls only)",
				new String[]{"from_x", "from_y", "from_z", "to_x", "to_y", "to_z", "block_type"},
				new String[]{"orientation", "thickness"}));
		tools.add(makeTool("place", "Place a single block at a position",
				new String[]{"x", "y", "z", "block_type"},
				new String[]{"orientation"}));
		tools.add(makeTool("line", "Draw a line of blocks between two points",
				new String[]{"from_x", "from_y", "from_z", "to_x", "to_y", "to_z", "block_type"},
				new String[]{"orientation"}));
		tools.add(makeTool("clear", "Clear a rectangular region to air",
				new String[]{"from_x", "from_y", "from_z", "to_x", "to_y", "to_z"},
				new String[]{}));
		tools.add(makeFinishTool());
		return tools;
	}

	private static JsonObject makeTool(String name, String description, String[] requiredIntParams, String[] optionalIntParams) {
		JsonObject tool = new JsonObject();
		tool.addProperty("type", "function");

		JsonObject function = new JsonObject();
		function.addProperty("name", name);
		function.addProperty("description", description);

		JsonObject parameters = new JsonObject();
		parameters.addProperty("type", "object");

		JsonObject properties = new JsonObject();
		JsonArray required = new JsonArray();

		for(String param : requiredIntParams) {
			JsonObject prop = new JsonObject();
			prop.addProperty("type", "integer");
			properties.add(param, prop);
			required.add(new JsonPrimitive(param));
		}
		for(String param : optionalIntParams) {
			JsonObject prop = new JsonObject();
			prop.addProperty("type", "integer");
			properties.add(param, prop);
		}

		parameters.add("properties", properties);
		parameters.add("required", required);
		function.add("parameters", parameters);
		tool.add("function", function);
		return tool;
	}

	private static JsonObject makeFinishTool() {
		JsonObject tool = new JsonObject();
		tool.addProperty("type", "function");

		JsonObject function = new JsonObject();
		function.addProperty("name", "finish");
		function.addProperty("description", "Call when the template is complete. Provide a short snake_case name for the template.");

		JsonObject parameters = new JsonObject();
		parameters.addProperty("type", "object");

		JsonObject properties = new JsonObject();
		JsonObject nameProp = new JsonObject();
		nameProp.addProperty("type", "string");
		nameProp.addProperty("description", "Short snake_case name for the template (e.g. small_fighter, orbital_station)");
		properties.add("name", nameProp);

		parameters.add("properties", properties);
		JsonArray required = new JsonArray();
		required.add(new JsonPrimitive("name"));
		parameters.add("required", required);

		function.add("parameters", parameters);
		tool.add("function", function);
		return tool;
	}
}
