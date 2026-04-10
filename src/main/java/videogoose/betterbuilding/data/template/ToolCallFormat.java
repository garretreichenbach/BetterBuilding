package videogoose.betterbuilding.data.template;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wire format for tool calls when fine-tuning / inferring against models that
 * do not support OpenAI structured `tool_calls` (notably Gemma).
 *
 * Both the training data exporter and the live inference loop must use these
 * exact strings or the fine-tune will be useless. Do not introduce variants
 * here without updating both call sites in lock step.
 *
 * Format:
 *
 *   Assistant turn (one or more calls per response):
 *     <tool_call>
 *     {"name": "fill", "arguments": {"from_x": 0, ...}}
 *     </tool_call>
 *
 *   Tool result (delivered back as a `user` role message because Gemma's
 *   chat template only knows user/model roles):
 *     <tool_response>
 *     Filled 216 blocks
 *     </tool_response>
 *
 * The format is deliberately the Hermes/Mistral convention: small models pick
 * it up well during fine-tuning and it's all plain text, so any chat template
 * that supports user/assistant turns can carry it.
 */
public class ToolCallFormat {

	public static final String CALL_OPEN = "<tool_call>";
	public static final String CALL_CLOSE = "</tool_call>";
	public static final String RESPONSE_OPEN = "<tool_response>";
	public static final String RESPONSE_CLOSE = "</tool_response>";

	// Non-greedy match across newlines.
	private static final Pattern CALL_PATTERN = Pattern.compile(
			Pattern.quote(CALL_OPEN) + "\\s*(.*?)\\s*" + Pattern.quote(CALL_CLOSE),
			Pattern.DOTALL);

	private ToolCallFormat() {}

	// --- Serialization ---

	/**
	 * Serialize a single tool call as a {@code <tool_call>...</tool_call>} block.
	 * The arguments object is rendered as compact JSON.
	 */
	public static String serializeCall(String name, JsonObject arguments) {
		JsonObject obj = new JsonObject();
		obj.addProperty("name", name);
		obj.add("arguments", arguments != null ? arguments : new JsonObject());
		return CALL_OPEN + "\n" + obj.toString() + "\n" + CALL_CLOSE;
	}

	/**
	 * Variant for callers that already have the arguments serialized as a JSON
	 * string (e.g. the exporter, which builds them by hand). Pass {@code null}
	 * or {@code "{}"} for no-arg calls.
	 */
	public static String serializeCallWithRawArgs(String name, String argumentsJson) {
		String args = (argumentsJson == null || argumentsJson.isEmpty()) ? "{}" : argumentsJson;
		// Tight string concat — we trust the caller to have produced valid JSON.
		return CALL_OPEN + "\n{\"name\":\"" + escapeJson(name) + "\",\"arguments\":" + args + "}\n" + CALL_CLOSE;
	}

	/**
	 * Wrap a tool result so the model learns to recognise the boundary between
	 * its own output and feedback from the environment.
	 */
	public static String serializeResponse(String result) {
		return RESPONSE_OPEN + "\n" + (result == null ? "" : result) + "\n" + RESPONSE_CLOSE;
	}

	// --- Parsing ---

	/**
	 * One parsed tool call from the model's response text.
	 */
	public static class ParsedCall {
		public final String name;
		public final JsonObject arguments;

		public ParsedCall(String name, JsonObject arguments) {
			this.name = name;
			this.arguments = arguments;
		}
	}

	/**
	 * Parse every {@code <tool_call>...</tool_call>} block out of an assistant
	 * response. Malformed blocks are skipped (and logged via the caller's
	 * choice — this method does not throw on a single bad call so one parse
	 * error doesn't stall the whole loop).
	 *
	 * Returns an empty list if no valid calls are found, in which case the
	 * caller should treat the response as "model is done".
	 */
	public static List<ParsedCall> parse(String content) {
		List<ParsedCall> out = new ArrayList<>();
		if(content == null || content.isEmpty()) return out;

		Matcher m = CALL_PATTERN.matcher(content);
		while(m.find()) {
			String inner = m.group(1).trim();
			if(inner.isEmpty()) continue;
			try {
				JsonElement el = new JsonParser().parse(inner);
				if(!el.isJsonObject()) continue;
				JsonObject obj = el.getAsJsonObject();
				if(!obj.has("name")) continue;
				String name = obj.get("name").getAsString();
				JsonObject args;
				if(obj.has("arguments") && obj.get("arguments").isJsonObject()) {
					args = obj.getAsJsonObject("arguments");
				} else if(obj.has("arguments") && obj.get("arguments").isJsonPrimitive()) {
					// Some models double-encode arguments as a string. Handle that.
					String s = obj.get("arguments").getAsString();
					JsonElement reparsed = new JsonParser().parse(s);
					args = reparsed.isJsonObject() ? reparsed.getAsJsonObject() : new JsonObject();
				} else {
					args = new JsonObject();
				}
				out.add(new ParsedCall(name, args));
			} catch(Exception ignored) {
				// Skip malformed call, keep going.
			}
		}
		return out;
	}

	private static String escapeJson(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
