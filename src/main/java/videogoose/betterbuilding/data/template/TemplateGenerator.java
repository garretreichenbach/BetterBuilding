package videogoose.betterbuilding.data.template;

import api.utils.element.Blocks;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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

		LMStudioClient client = new LMStudioClient(url, model, temperature, maxTokens, timeout, apiKey);

		String palette = (hotbarTypes != null && !hotbarTypes.isEmpty())
				? BlockPalette.toHotbarPaletteString(hotbarTypes)
				: BlockPalette.toJsonPaletteString();

		TemplateMetaData template = new TemplateMetaData("ai_generated", outputDims);
		JsonArray messages = new JsonArray();
		messages.add(makeMessage("system", buildSystemPrompt()));
		messages.add(makeMessage("user", buildUserPrompt(references, outputDims, description, palette)));

		BetterBuilding.getInstance().logInfo("Starting template generation loop via " + providerName + "...");

		int totalOps = 0;
		for(int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
			// We pass tools=null because the model is trained to emit tool calls
			// as <tool_call>...</tool_call> text inside `content`. See ToolCallFormat.
			JsonObject assistantMsg = client.chatCompletionWithTools(messages, null);
			messages.add(assistantMsg);

			String assistantContent = assistantMsg.has("content") && !assistantMsg.get("content").isJsonNull()
					? assistantMsg.get("content").getAsString()
					: "";

			List<ToolCallFormat.ParsedCall> calls = ToolCallFormat.parse(assistantContent);
			if(calls.isEmpty()) {
				BetterBuilding.getInstance().logInfo("AI emitted no tool calls (stopping after " + totalOps + " operations). Last content: " +
						(assistantContent.length() > 200 ? assistantContent.substring(0, 200) + "..." : assistantContent));
				break;
			}

			boolean finished = false;
			StringBuilder responseBuf = new StringBuilder();

			for(ToolCallFormat.ParsedCall call : calls) {
				String result;
				if("finish".equals(call.name)) {
					if(call.arguments.has("name")) {
						String aiName = call.arguments.get("name").getAsString().trim()
								.replaceAll("[^a-zA-Z0-9_\\-]", "_")
								.replaceAll("_+", "_");
						if(!aiName.isEmpty()) template.setName(aiName);
					}
					result = "Template finalized with " + totalOps + " operations.";
					finished = true;
				} else {
					result = executeTool(call.name, call.arguments, template, outputDims);
					totalOps++;
				}

				if(responseBuf.length() > 0) responseBuf.append("\n");
				responseBuf.append(ToolCallFormat.serializeResponse(result));
			}

			// All tool responses for this turn are delivered as a single user message,
			// since Gemma's chat template only knows user/model roles.
			messages.add(makeMessage("user", responseBuf.toString()));

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
				case "ellipsoid": return execEllipsoid(template, args, dims);
				case "cylinder": return execCylinder(template, args, dims);
				case "cone": return execCone(template, args, dims);
				case "mirror": return execMirror(template, args, dims);
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

	private static String execEllipsoid(TemplateMetaData template, JsonObject args, int[] dims) {
		int cx = args.get("center_x").getAsInt();
		int cy = args.get("center_y").getAsInt();
		int cz = args.get("center_z").getAsInt();
		int rx = args.get("radius_x").getAsInt();
		int ry = args.get("radius_y").getAsInt();
		int rz = args.get("radius_z").getAsInt();
		short type = args.get("block_type").getAsShort();
		byte orient = args.has("orientation") ? args.get("orientation").getAsByte() : 0;
		boolean hollow = args.has("hollow") && args.get("hollow").getAsBoolean();

		int count = 0;
		for(int x = cx - rx; x <= cx + rx; x++) {
			for(int y = cy - ry; y <= cy + ry; y++) {
				for(int z = cz - rz; z <= cz + rz; z++) {
					if(!inBounds(x, y, z, dims)) continue;
					double dx = (double)(x - cx) / Math.max(rx, 1);
					double dy = (double)(y - cy) / Math.max(ry, 1);
					double dz = (double)(z - cz) / Math.max(rz, 1);
					double dist = dx * dx + dy * dy + dz * dz;
					if(dist <= 1.0) {
						if(hollow) {
							// Check if one step inward is still inside
							double dxIn = (double)(x - cx) / Math.max(rx - 1, 1);
							double dyIn = (double)(y - cy) / Math.max(ry - 1, 1);
							double dzIn = (double)(z - cz) / Math.max(rz - 1, 1);
							if(dxIn * dxIn + dyIn * dyIn + dzIn * dzIn <= 1.0 && rx > 1 && ry > 1 && rz > 1) continue;
						}
						template.setTypeAt(x, y, z, type);
						template.setOrientationAt(x, y, z, orient);
						count++;
					}
				}
			}
		}
		return "Ellipsoid placed " + count + " blocks";
	}

	private static String execCylinder(TemplateMetaData template, JsonObject args, int[] dims) {
		int x1 = args.get("from_x").getAsInt(), y1 = args.get("from_y").getAsInt(), z1 = args.get("from_z").getAsInt();
		int x2 = args.get("to_x").getAsInt(), y2 = args.get("to_y").getAsInt(), z2 = args.get("to_z").getAsInt();
		int radius = args.get("radius").getAsInt();
		short type = args.get("block_type").getAsShort();
		byte orient = args.has("orientation") ? args.get("orientation").getAsByte() : 0;
		boolean hollow = args.has("hollow") && args.get("hollow").getAsBoolean();

		// Direction vector
		double lx = x2 - x1, ly = y2 - y1, lz = z2 - z1;
		double len = Math.sqrt(lx * lx + ly * ly + lz * lz);
		if(len < 0.001) return "Error: from and to points are the same";
		double ux = lx / len, uy = ly / len, uz = lz / len;

		// Bounding box
		int minX = Math.min(x1, x2) - radius, maxX = Math.max(x1, x2) + radius;
		int minY = Math.min(y1, y2) - radius, maxY = Math.max(y1, y2) + radius;
		int minZ = Math.min(z1, z2) - radius, maxZ = Math.max(z1, z2) + radius;

		int count = 0;
		for(int x = minX; x <= maxX; x++) {
			for(int y = minY; y <= maxY; y++) {
				for(int z = minZ; z <= maxZ; z++) {
					if(!inBounds(x, y, z, dims)) continue;
					// Project point onto axis
					double px = x - x1, py = y - y1, pz = z - z1;
					double t = px * ux + py * uy + pz * uz;
					if(t < -0.5 || t > len + 0.5) continue;
					// Distance from axis
					double projX = t * ux, projY = t * uy, projZ = t * uz;
					double distSq = (px - projX) * (px - projX) + (py - projY) * (py - projY) + (pz - projZ) * (pz - projZ);
					double r = radius + 0.5;
					if(distSq <= r * r) {
						if(hollow) {
							double rInner = radius - 0.5;
							if(rInner > 0 && distSq <= rInner * rInner && t >= 0.5 && t <= len - 0.5) continue;
						}
						template.setTypeAt(x, y, z, type);
						template.setOrientationAt(x, y, z, orient);
						count++;
					}
				}
			}
		}
		return "Cylinder placed " + count + " blocks";
	}

	private static String execCone(TemplateMetaData template, JsonObject args, int[] dims) {
		int x1 = args.get("from_x").getAsInt(), y1 = args.get("from_y").getAsInt(), z1 = args.get("from_z").getAsInt();
		int x2 = args.get("to_x").getAsInt(), y2 = args.get("to_y").getAsInt(), z2 = args.get("to_z").getAsInt();
		int baseRadius = args.get("base_radius").getAsInt();
		int tipRadius = args.has("tip_radius") ? args.get("tip_radius").getAsInt() : 0;
		short type = args.get("block_type").getAsShort();
		byte orient = args.has("orientation") ? args.get("orientation").getAsByte() : 0;
		boolean hollow = args.has("hollow") && args.get("hollow").getAsBoolean();

		double lx = x2 - x1, ly = y2 - y1, lz = z2 - z1;
		double len = Math.sqrt(lx * lx + ly * ly + lz * lz);
		if(len < 0.001) return "Error: from and to points are the same";
		double ux = lx / len, uy = ly / len, uz = lz / len;

		int maxR = Math.max(baseRadius, tipRadius);
		int minX = Math.min(x1, x2) - maxR, maxX = Math.max(x1, x2) + maxR;
		int minY = Math.min(y1, y2) - maxR, maxY = Math.max(y1, y2) + maxR;
		int minZ = Math.min(z1, z2) - maxR, maxZ = Math.max(z1, z2) + maxR;

		int count = 0;
		for(int x = minX; x <= maxX; x++) {
			for(int y = minY; y <= maxY; y++) {
				for(int z = minZ; z <= maxZ; z++) {
					if(!inBounds(x, y, z, dims)) continue;
					double px = x - x1, py = y - y1, pz = z - z1;
					double t = px * ux + py * uy + pz * uz;
					if(t < -0.5 || t > len + 0.5) continue;
					double frac = Math.max(0, Math.min(1, t / len));
					double localRadius = baseRadius + (tipRadius - baseRadius) * frac + 0.5;
					double projX = t * ux, projY = t * uy, projZ = t * uz;
					double distSq = (px - projX) * (px - projX) + (py - projY) * (py - projY) + (pz - projZ) * (pz - projZ);
					if(distSq <= localRadius * localRadius) {
						if(hollow) {
							double innerR = localRadius - 1.0;
							if(innerR > 0 && distSq <= innerR * innerR && t >= 0.5 && t <= len - 0.5) continue;
						}
						template.setTypeAt(x, y, z, type);
						template.setOrientationAt(x, y, z, orient);
						count++;
					}
				}
			}
		}
		return "Cone placed " + count + " blocks";
	}

	private static String execMirror(TemplateMetaData template, JsonObject args, int[] dims) {
		int minX = args.has("from_x") ? args.get("from_x").getAsInt() : 0;
		int minY = args.has("from_y") ? args.get("from_y").getAsInt() : 0;
		int minZ = args.has("from_z") ? args.get("from_z").getAsInt() : 0;
		int maxX = args.has("to_x") ? args.get("to_x").getAsInt() : dims[0] - 1;
		int maxY = args.has("to_y") ? args.get("to_y").getAsInt() : dims[1] - 1;
		int maxZ = args.has("to_z") ? args.get("to_z").getAsInt() : dims[2] - 1;
		// Default axis is X (bilateral symmetry)
		String axis = args.has("axis") ? args.get("axis").getAsString().toUpperCase() : "X";

		int count = 0;
		for(int x = minX; x <= maxX; x++) {
			for(int y = minY; y <= maxY; y++) {
				for(int z = minZ; z <= maxZ; z++) {
					if(!inBounds(x, y, z, dims)) continue;
					short type = template.getTypeAt(x, y, z);
					if(type == 0) continue;
					byte orient = template.getOrientationAt(x, y, z);

					int mx = x, my = y, mz = z;
					byte mo = orient;
					switch(axis) {
						case "X":
							mx = (dims[0] - 1) - x;
							// Flip left/right orientations
							if(orient == 4) mo = 5; else if(orient == 5) mo = 4;
							break;
						case "Y":
							my = (dims[1] - 1) - y;
							if(orient == 2) mo = 3; else if(orient == 3) mo = 2;
							break;
						case "Z":
							mz = (dims[2] - 1) - z;
							if(orient == 0) mo = 1; else if(orient == 1) mo = 0;
							break;
					}

					if(inBounds(mx, my, mz, dims) && template.getTypeAt(mx, my, mz) == 0) {
						template.setTypeAt(mx, my, mz, type);
						template.setOrientationAt(mx, my, mz, mo);
						count++;
					}
				}
			}
		}
		return "Mirrored " + count + " blocks across " + axis + " axis";
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

	/**
	 * The system prompt is shared between live inference and the training data
	 * exporter — it must be byte-identical in both places or the fine-tune will
	 * fight the prompt at inference time. The {@link TrainingDataExporter}
	 * calls into this method directly.
	 */
	static String buildSystemPrompt() {
		return SYSTEM_PROMPT_BODY + "\n\n" + buildToolDocs();
	}

	/**
	 * Renders every tool the agent can call as plain text, in a format the
	 * model can be trained to emit. The wire format is defined in
	 * {@link ToolCallFormat}.
	 */
	static String buildToolDocs() {
		StringBuilder sb = new StringBuilder();
		sb.append("## TOOLS\n");
		sb.append("You build templates by emitting tool calls. Each call must appear inside a\n");
		sb.append(ToolCallFormat.CALL_OPEN).append(" / ").append(ToolCallFormat.CALL_CLOSE)
				.append(" block as a single JSON object with this shape:\n");
		sb.append(ToolCallFormat.CALL_OPEN).append("\n");
		sb.append("{\"name\": \"<tool_name>\", \"arguments\": {<args>}}\n");
		sb.append(ToolCallFormat.CALL_CLOSE).append("\n\n");
		sb.append("You may emit multiple tool calls in one turn. After your turn, the result of\n");
		sb.append("each call is delivered back to you wrapped in ").append(ToolCallFormat.RESPONSE_OPEN)
				.append(" / ").append(ToolCallFormat.RESPONSE_CLOSE).append(" blocks.\n\n");
		sb.append("Available tools:\n\n");

		appendTool(sb, "fill",
				"Fill a rectangular region with a block type.",
				"from_x, from_y, from_z, to_x, to_y, to_z, block_type",
				"orientation");
		appendTool(sb, "shell",
				"Create a hollow shell of a rectangular region (walls only).",
				"from_x, from_y, from_z, to_x, to_y, to_z, block_type",
				"orientation, thickness");
		appendTool(sb, "place",
				"Place a single block at a position.",
				"x, y, z, block_type",
				"orientation");
		appendTool(sb, "line",
				"Draw a line of blocks between two points.",
				"from_x, from_y, from_z, to_x, to_y, to_z, block_type",
				"orientation");
		appendTool(sb, "clear",
				"Clear a rectangular region to air.",
				"from_x, from_y, from_z, to_x, to_y, to_z",
				"");
		appendTool(sb, "ellipsoid",
				"Create an ellipsoid (sphere, egg, or oval). Use for rounded fuselages, domes, pods, and organic shapes. Set all radii equal for a sphere.",
				"center_x, center_y, center_z, radius_x, radius_y, radius_z, block_type",
				"orientation, hollow (bool)");
		appendTool(sb, "cylinder",
				"Create a cylinder along an arbitrary axis between two points. Use for engine nacelles, barrels, tubes, and structural columns.",
				"from_x, from_y, from_z, to_x, to_y, to_z, radius, block_type",
				"orientation, hollow (bool)");
		appendTool(sb, "cone",
				"Create a cone or tapered cylinder between two points. The shape transitions from base_radius at the from-point to tip_radius at the to-point. Use for nose cones, exhaust nozzles, tapered fuselages, and pointed shapes.",
				"from_x, from_y, from_z, to_x, to_y, to_z, base_radius, block_type",
				"tip_radius, orientation, hollow (bool)");
		appendTool(sb, "mirror",
				"Mirror all existing blocks across an axis for symmetry. Defaults to X axis (bilateral ship symmetry). Only copies into empty spaces. Build one half of the ship, then mirror to get perfect symmetry.",
				"",
				"axis (X|Y|Z), from_x, from_y, from_z, to_x, to_y, to_z");
		appendTool(sb, "finish",
				"Call when the template is complete. Provide a short snake_case name for the template.",
				"name (string)",
				"");

		sb.append("\nWhen you are done building, emit a `finish` tool call. Do not write any prose ");
		sb.append("outside of tool calls — every response should consist entirely of one or more ")
				.append(ToolCallFormat.CALL_OPEN).append(" blocks.");
		return sb.toString();
	}

	private static void appendTool(StringBuilder sb, String name, String desc, String required, String optional) {
		sb.append("- ").append(name).append(": ").append(desc).append("\n");
		if(!required.isEmpty()) sb.append("    required: ").append(required).append("\n");
		if(!optional.isEmpty()) sb.append("    optional: ").append(optional).append("\n");
	}

	private static final String SYSTEM_PROMPT_BODY =
			"You are an expert StarMade spaceship and station designer AI. You build 3D voxel templates by calling tools.\n\n" +
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
				"COMMON PATTERNS:\n" +
				"- Tapered nose: Use cone tool with tip_radius=0 pointing along +Z, or build manually with tetra/wedge blocks\n" +
				"- Fuselage: Use cylinder or cone tool for the main body, then detail with wedge/hepta blocks\n" +
				"- Wing: A thin slab of hull extending on the X axis, with wedge leading/trailing edges\n" +
				"- Engine nacelle: Use cylinder tool with hollow=true, cap ends with ellipsoid or cone\n" +
				"- Cockpit: Ellipsoid with glass blocks, cut in half with clear for a canopy\n" +
				"- Rounded hull: Use ellipsoid tool for the overall shape, then carve details with clear and fill\n\n" +
				"TECHNIQUE NOTES:\n" +
				"- Use ellipsoid, cylinder, and cone tools to create organic rounded shapes FIRST, then detail with block tools\n" +
				"- Later tool calls overwrite earlier ones, so layer details on top of base shapes\n" +
				"- Use the mirror tool to get perfect bilateral symmetry: build one side, then mirror across X\n" +
				"- Combine shapes: e.g. cone for nose + cylinder for body + ellipsoid for cockpit dome\n" +
				"- Call 'finish' when you are done building";

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
		sb.append("Begin building now, then call 'finish' with a name when done.");
		return sb.toString();
	}

}
