package videogoose.betterbuilding.data.template;

import api.utils.element.Blocks;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import videogoose.betterbuilding.BetterBuilding;
import videogoose.betterbuilding.manager.ConfigManager;
import videogoose.betterbuilding.manager.LMStudioClient;

import java.util.List;

/**
 * Generates building templates via a local LM Studio model using structured JSON.
 * Sends a generation request describing dimensions, block palette, and optional
 * reference templates, then interprets the AI's structured operation response
 * (fill, shell, place, line, clear) into TemplateMetaData voxel data.
 */
public class TemplateGenerator {

	public static final int DEFAULT_MAX_DIM = 64;

	/**
	 * Generate a new TemplateMetaData using the local LM Studio AI model.
	 * @param references optional list of reference templates for style inspiration (may be null/empty)
	 * @param outputDims [x,y,z] dimensions for the generated template
	 * @param description free-text description of what to build (e.g. "small fighter ship")
	 * @return generated TemplateMetaData
	 */
	public static TemplateMetaData generate(List<TemplateMetaData> references, int[] outputDims, String description) throws Exception {
		validateDimensions(outputDims);

		boolean useOllama = "ollama".equals(ConfigManager.getProvider());
		String url = useOllama ? ConfigManager.getOllamaUrl() : ConfigManager.getLMStudioUrl();
		String model = useOllama ? ConfigManager.getOllamaModel() : ConfigManager.getLMStudioModel();
		String providerName = useOllama ? "Ollama" : "LM Studio";

		LMStudioClient client = new LMStudioClient(
				url,
				model,
				ConfigManager.getLMStudioTemperature(),
				ConfigManager.getLMStudioMaxTokens(),
				ConfigManager.getLMStudioTimeout()
		);

		String systemPrompt = buildSystemPrompt();
		String userPrompt = buildUserPrompt(references, outputDims, description);
		JsonObject responseSchema = buildResponseSchema();

		BetterBuilding.getInstance().logInfo("Sending template generation request to " + providerName + "...");
		JsonObject aiResponse = client.chatCompletion(systemPrompt, userPrompt, responseSchema);
		BetterBuilding.getInstance().logInfo("Received " + providerName + " response, building template...");

		String name = "ai_generated_" + System.currentTimeMillis();
		if(aiResponse.has("name")) {
			String aiName = aiResponse.get("name").getAsString().trim()
					.replaceAll("[^a-zA-Z0-9_\\-]", "_")
					.replaceAll("_+", "_");
			if(!aiName.isEmpty()) name = aiName;
		}

		TemplateMetaData result = new TemplateMetaData(name, outputDims);
		applyOperations(result, aiResponse, outputDims);

		return result;
	}

	private static void validateDimensions(int[] dims) {
		if(dims == null || dims.length != 3) throw new IllegalArgumentException("outputDims must be length 3");
		for(int d : dims) {
			if(d <= 0) throw new IllegalArgumentException("outputDims must be > 0");
			if(d > DEFAULT_MAX_DIM) throw new IllegalArgumentException("outputDims exceed max " + DEFAULT_MAX_DIM);
		}
	}

	// --- Prompt construction ---

	private static String buildSystemPrompt() {
		return "You are a StarMade spaceship and station designer AI. You generate 3D building templates " +
				"as structured JSON operations. You understand StarMade block types and how spaceships are constructed.\n\n" +
				"RULES:\n" +
				"- All coordinates are 0-indexed within the given dimensions [x,y,z]\n" +
				"- block_type values are integer IDs from the StarMade Blocks enum (provided in the palette)\n" +
				"- Block type 0 (EMPTY_SPACE) means air/empty\n" +
				"- Orientation values 0-5 represent the 6 cardinal facing directions (0=front,1=back,2=top,3=bottom,4=left,5=right) for standard blocks\n" +
				//"- Every ship MUST have exactly one SHIP_CORE (" + Blocks.SHIP_CORE.getId() + ") as its center\n" +
				//"- Ships need THRUSTER_MODULE (" + Blocks.THRUSTER_MODULE.getId() + ") at the rear for propulsion\n" +
				//"- POWER_CAPACITOR (" + Blocks.POWER_CAPACITOR.getId() + ") provides energy\n" +
				//"- SHIELD_CAPACITOR (" + Blocks.SHIELD_CAPACITOR.getId() + ") and SHIELD_RECHARGER (" + Blocks.SHIELD_RECHARGER.getId() + ") provide defense\n" +
				//"- Weapon systems need a computer block (e.g. CANNON_COMPUTER=" + Blocks.CANNON_COMPUTER.getId() + ") linked to modules (CANNON_BARREL=" + Blocks.CANNON_BARREL.getId() + ")\n" +
				"- Sections generated should be hollow, unless otherwise specified, so users can fill them with system blocks\n" +
				"- Use hull blocks (GREY_HULL=" + Blocks.GREY_HULL.getId() + ", GREY_STANDARD_ARMOR=" + Blocks.GREY_STANDARD_ARMOR.getId() + ", etc.) for the main body\n" +
				"- Wedge/corner/hepta/tetra/slab variants exist for shaping (e.g. GREY_HULL_WEDGE=" + Blocks.GREY_HULL_WEDGE.getId() + ")\n" +
				"- GLASS (" + Blocks.GLASS.getId() + ") is good for cockpit windows\n" +
				"- Create aerodynamic, visually interesting shapes\n\n" +
				"AVAILABLE OPERATIONS:\n" +
				"- fill: fills a rectangular region with a block type ID\n" +
				"- shell: creates a hollow shell (outer walls only) of a rectangular region\n" +
				"- place: places individual blocks at specific positions\n" +
				"- line: draws a line of blocks between two points\n" +
				"- clear: sets a rectangular region to air (type 0)\n\n" +
				"Operations are applied in order, so later operations overwrite earlier ones.\n\n" +
				"NAME:\n" +
				"- Also provide a short, descriptive snake_case name for the template (e.g. \"small_fighter\", \"orbital_station\")";
	}

	private static String buildUserPrompt(List<TemplateMetaData> references, int[] dims, String description) {
		StringBuilder sb = new StringBuilder();
		sb.append("Generate a StarMade building template with these specifications:\n\n");
		sb.append("DIMENSIONS: ").append(dims[0]).append("x").append(dims[1]).append("x").append(dims[2]).append(" (X,Y,Z)\n");

		if(description != null && !description.isEmpty()) {
			sb.append("DESCRIPTION: ").append(description).append("\n");
		}

		sb.append("\nAVAILABLE BLOCK PALETTE:\n");
		sb.append(BlockPalette.toJsonPaletteString()).append("\n");

		if(references != null && !references.isEmpty()) {
			sb.append("\nREFERENCE TEMPLATES (use as style/composition inspiration):\n");
			for(TemplateMetaData ref : references) {
				int[] refDims = ref.getDimensions();
				sb.append("- \"").append(ref.getName()).append("\" (")
						.append(refDims[0]).append("x").append(refDims[1]).append("x").append(refDims[2])
						.append(") block composition: ").append(BlockPalette.summarizeTemplate(ref)).append("\n");
			}
		}

		sb.append("\nGenerate the operations array to build this template. ");
		sb.append("Place the Core block near the center. Make the design fill the space well ");
		sb.append("and look like a coherent StarMade structure.");

		return sb.toString();
	}

	// --- Response schema for LM Studio structured output ---

	static JsonObject buildResponseSchema() {
		JsonObject schema = new JsonObject();
		schema.addProperty("type", "object");

		JsonObject properties = new JsonObject();

		JsonObject operationsArray = new JsonObject();
		operationsArray.addProperty("type", "array");

		JsonObject operationItem = new JsonObject();
		operationItem.addProperty("type", "object");

		JsonObject opProps = new JsonObject();

		JsonObject opType = new JsonObject();
		opType.addProperty("type", "string");
		JsonArray enumValues = new JsonArray();
		enumValues.add(new JsonPrimitive("fill"));
		enumValues.add(new JsonPrimitive("shell"));
		enumValues.add(new JsonPrimitive("place"));
		enumValues.add(new JsonPrimitive("line"));
		enumValues.add(new JsonPrimitive("clear"));
		opType.add("enum", enumValues);
		opProps.add("op_type", opType);

		JsonObject blockType = new JsonObject();
		blockType.addProperty("type", "integer");
		opProps.add("block_type", blockType);

		JsonObject orientation = new JsonObject();
		orientation.addProperty("type", "integer");
		opProps.add("orientation", orientation);

		JsonObject intItem = new JsonObject();
		intItem.addProperty("type", "integer");

		JsonObject fromArr = new JsonObject();
		fromArr.addProperty("type", "array");
		fromArr.add("items", intItem);
		opProps.add("from", fromArr);

		JsonObject toArr = new JsonObject();
		toArr.addProperty("type", "array");
		toArr.add("items", intItem);
		opProps.add("to", toArr);

		JsonObject thickness = new JsonObject();
		thickness.addProperty("type", "integer");
		opProps.add("thickness", thickness);

		JsonObject blocksArr = new JsonObject();
		blocksArr.addProperty("type", "array");
		JsonObject blockItem = new JsonObject();
		blockItem.addProperty("type", "object");
		JsonObject blockItemProps = new JsonObject();

		JsonObject posArr = new JsonObject();
		posArr.addProperty("type", "array");
		posArr.add("items", intItem);
		blockItemProps.add("pos", posArr);

		JsonObject bType = new JsonObject();
		bType.addProperty("type", "integer");
		blockItemProps.add("type", bType);

		JsonObject bOrient = new JsonObject();
		bOrient.addProperty("type", "integer");
		blockItemProps.add("orientation", bOrient);

		blockItem.add("properties", blockItemProps);
		blocksArr.add("items", blockItem);
		opProps.add("blocks", blocksArr);

		operationItem.add("properties", opProps);
		JsonArray requiredOp = new JsonArray();
		requiredOp.add(new JsonPrimitive("op_type"));
		operationItem.add("required", requiredOp);

		operationsArray.add("items", operationItem);
		properties.add("operations", operationsArray);

		JsonObject nameField = new JsonObject();
		nameField.addProperty("type", "string");
		properties.add("name", nameField);

		schema.add("properties", properties);
		JsonArray required = new JsonArray();
		required.add(new JsonPrimitive("name"));
		required.add(new JsonPrimitive("operations"));
		schema.add("required", required);

		return schema;
	}

	// --- Operation application ---

	private static void applyOperations(TemplateMetaData template, JsonObject response, int[] dims) {
		if(!response.has("operations")) {
			BetterBuilding.getInstance().logWarning("AI response missing 'operations' array");
			return;
		}

		JsonArray operations = response.getAsJsonArray("operations");
		int appliedCount = 0;

		for(JsonElement element : operations) {
			try {
				JsonObject op = element.getAsJsonObject();
				String opType = op.get("op_type").getAsString();

				switch(opType) {
					case "fill":
						applyFill(template, op, dims);
						break;
					case "shell":
						applyShell(template, op, dims);
						break;
					case "place":
						applyPlace(template, op, dims);
						break;
					case "line":
						applyLine(template, op, dims);
						break;
					case "clear":
						applyClear(template, op, dims);
						break;
					default:
						BetterBuilding.getInstance().logWarning("Unknown operation type: " + opType);
						continue;
				}
				appliedCount++;
			} catch(Exception e) {
				BetterBuilding.getInstance().logWarning("Failed to apply operation: " + e.getMessage());
			}
		}

		BetterBuilding.getInstance().logInfo("Applied " + appliedCount + "/" + operations.size() + " operations");
	}

	private static void applyFill(TemplateMetaData template, JsonObject op, int[] dims) {
		int[] from = getCoordArray(op, "from");
		int[] to = getCoordArray(op, "to");
		short type = op.has("block_type") ? op.get("block_type").getAsShort() : 0;
		byte orient = op.has("orientation") ? op.get("orientation").getAsByte() : 0;

		for(int x = Math.min(from[0], to[0]); x <= Math.max(from[0], to[0]); x++) {
			for(int y = Math.min(from[1], to[1]); y <= Math.max(from[1], to[1]); y++) {
				for(int z = Math.min(from[2], to[2]); z <= Math.max(from[2], to[2]); z++) {
					if(inBounds(x, y, z, dims)) {
						template.setTypeAt(x, y, z, type);
						template.setOrientationAt(x, y, z, orient);
					}
				}
			}
		}
	}

	private static void applyShell(TemplateMetaData template, JsonObject op, int[] dims) {
		int[] from = getCoordArray(op, "from");
		int[] to = getCoordArray(op, "to");
		short type = op.has("block_type") ? op.get("block_type").getAsShort() : 0;
		byte orient = op.has("orientation") ? op.get("orientation").getAsByte() : 0;
		int thickness = op.has("thickness") ? op.get("thickness").getAsInt() : 1;

		int minX = Math.min(from[0], to[0]), maxX = Math.max(from[0], to[0]);
		int minY = Math.min(from[1], to[1]), maxY = Math.max(from[1], to[1]);
		int minZ = Math.min(from[2], to[2]), maxZ = Math.max(from[2], to[2]);

		for(int x = minX; x <= maxX; x++) {
			for(int y = minY; y <= maxY; y++) {
				for(int z = minZ; z <= maxZ; z++) {
					boolean onShell = (x - minX < thickness) || (maxX - x < thickness) ||
							(y - minY < thickness) || (maxY - y < thickness) ||
							(z - minZ < thickness) || (maxZ - z < thickness);
					if(onShell && inBounds(x, y, z, dims)) {
						template.setTypeAt(x, y, z, type);
						template.setOrientationAt(x, y, z, orient);
					}
				}
			}
		}
	}

	private static void applyPlace(TemplateMetaData template, JsonObject op, int[] dims) {
		if(op.has("blocks")) {
			JsonArray blocks = op.getAsJsonArray("blocks");
			for(JsonElement blockEl : blocks) {
				JsonObject block = blockEl.getAsJsonObject();
				int[] pos = getCoordArray(block, "pos");
				short type = block.has("type") ? block.get("type").getAsShort() : 0;
				byte orient = block.has("orientation") ? block.get("orientation").getAsByte() : 0;
				if(inBounds(pos[0], pos[1], pos[2], dims)) {
					template.setTypeAt(pos[0], pos[1], pos[2], type);
					template.setOrientationAt(pos[0], pos[1], pos[2], orient);
				}
			}
		} else if(op.has("from") && op.has("to")) {
			applyFill(template, op, dims);
		}
	}

	private static void applyLine(TemplateMetaData template, JsonObject op, int[] dims) {
		int[] from = getCoordArray(op, "from");
		int[] to = getCoordArray(op, "to");
		short type = op.has("block_type") ? op.get("block_type").getAsShort() : 0;
		byte orient = op.has("orientation") ? op.get("orientation").getAsByte() : 0;

		int dx = Math.abs(to[0] - from[0]);
		int dy = Math.abs(to[1] - from[1]);
		int dz = Math.abs(to[2] - from[2]);
		int sx = from[0] < to[0] ? 1 : -1;
		int sy = from[1] < to[1] ? 1 : -1;
		int sz = from[2] < to[2] ? 1 : -1;

		int dm = Math.max(dx, Math.max(dy, dz));
		int x = from[0], y = from[1], z = from[2];
		int xErr = dm / 2, yErr = dm / 2, zErr = dm / 2;

		for(int i = 0; i <= dm; i++) {
			if(inBounds(x, y, z, dims)) {
				template.setTypeAt(x, y, z, type);
				template.setOrientationAt(x, y, z, orient);
			}
			xErr -= dx;
			if(xErr < 0) { xErr += dm; x += sx; }
			yErr -= dy;
			if(yErr < 0) { yErr += dm; y += sy; }
			zErr -= dz;
			if(zErr < 0) { zErr += dm; z += sz; }
		}
	}

	private static void applyClear(TemplateMetaData template, JsonObject op, int[] dims) {
		int[] from = getCoordArray(op, "from");
		int[] to = getCoordArray(op, "to");

		for(int x = Math.min(from[0], to[0]); x <= Math.max(from[0], to[0]); x++) {
			for(int y = Math.min(from[1], to[1]); y <= Math.max(from[1], to[1]); y++) {
				for(int z = Math.min(from[2], to[2]); z <= Math.max(from[2], to[2]); z++) {
					if(inBounds(x, y, z, dims)) {
						template.setTypeAt(x, y, z, (short) 0);
						template.setOrientationAt(x, y, z, (byte) 0);
					}
				}
			}
		}
	}

	private static int[] getCoordArray(JsonObject obj, String key) {
		JsonArray arr = obj.getAsJsonArray(key);
		return new int[] {arr.get(0).getAsInt(), arr.get(1).getAsInt(), arr.get(2).getAsInt()};
	}

	private static boolean inBounds(int x, int y, int z, int[] dims) {
		return x >= 0 && x < dims[0] && y >= 0 && y < dims[1] && z >= 0 && z < dims[2];
	}
}
