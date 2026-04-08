package videogoose.betterbuilding.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * HTTP client for communicating with LM Studio's OpenAI-compatible API.
 * Uses /v1/chat/completions with structured JSON output via response_format.
 */
public class LMStudioClient {

	private final String baseUrl;
	private final String model;
	private final float temperature;
	private final int maxTokens;
	private final int timeoutMs;

	public LMStudioClient(String baseUrl, String model, float temperature, int maxTokens, int timeoutMs) {
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.model = model;
		this.temperature = temperature;
		this.maxTokens = maxTokens;
		this.timeoutMs = timeoutMs;
	}

	/**
	 * Send a chat completion request with structured JSON output.
	 * @param systemPrompt the system message providing context
	 * @param userPrompt the user message with the generation request
	 * @param jsonSchema the JSON schema to enforce on the response
	 * @return parsed JSON object from the response content
	 */
	public JsonObject chatCompletion(String systemPrompt, String userPrompt, JsonObject jsonSchema) throws IOException {
		String endpoint = baseUrl + "/v1/chat/completions";
		JsonObject requestBody = buildRequest(systemPrompt, userPrompt, jsonSchema);

		HttpURLConnection conn = null;
		try {
			URL url = new URL(endpoint);
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("Authorization", "Bearer lm-studio");
			conn.setDoOutput(true);
			conn.setConnectTimeout(timeoutMs);
			conn.setReadTimeout(timeoutMs);

			byte[] payload = requestBody.toString().getBytes(StandardCharsets.UTF_8);
			conn.setFixedLengthStreamingMode(payload.length);

			try(OutputStream os = conn.getOutputStream()) {
				os.write(payload);
			}

			int status = conn.getResponseCode();
			if(status != 200) {
				String errorBody = readStream(conn.getErrorStream());
				throw new IOException("LM Studio returned HTTP " + status + ": " + errorBody);
			}

			String responseBody = readStream(conn.getInputStream());
			JsonObject response = new JsonParser().parse(responseBody).getAsJsonObject();
			String content = response.getAsJsonArray("choices")
					.get(0).getAsJsonObject()
					.getAsJsonObject("message")
					.get("content").getAsString();

			return new JsonParser().parse(content).getAsJsonObject();
		} finally {
			if(conn != null) conn.disconnect();
		}
	}

	/**
	 * Test connectivity to the LM Studio server.
	 * @return true if the server is reachable and has models loaded
	 */
	public boolean testConnection() {
		try {
			URL url = new URL(baseUrl + "/v1/models");
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(5000);
			conn.setReadTimeout(5000);
			int status = conn.getResponseCode();
			conn.disconnect();
			return status == 200;
		} catch(Exception e) {
			return false;
		}
	}

	private JsonObject buildRequest(String systemPrompt, String userPrompt, JsonObject jsonSchema) {
		JsonObject request = new JsonObject();
		request.addProperty("model", model);
		request.addProperty("temperature", temperature);
		request.addProperty("max_tokens", maxTokens);
		request.addProperty("stream", false);

		JsonArray messages = new JsonArray();

		JsonObject sysMsg = new JsonObject();
		sysMsg.addProperty("role", "system");
		sysMsg.addProperty("content", systemPrompt);
		messages.add(sysMsg);

		JsonObject userMsg = new JsonObject();
		userMsg.addProperty("role", "user");
		userMsg.addProperty("content", userPrompt);
		messages.add(userMsg);

		request.add("messages", messages);

		if(jsonSchema != null) {
			JsonObject responseFormat = new JsonObject();
			responseFormat.addProperty("type", "json_schema");
			JsonObject schemaWrapper = new JsonObject();
			schemaWrapper.addProperty("name", "template_generation");
			schemaWrapper.addProperty("strict", "true");
			schemaWrapper.add("schema", jsonSchema);
			responseFormat.add("json_schema", schemaWrapper);
			request.add("response_format", responseFormat);
		}

		return request;
	}

	private String readStream(InputStream stream) throws IOException {
		if(stream == null) return "";
		StringBuilder sb = new StringBuilder();
		try(BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			String line;
			while((line = reader.readLine()) != null) {
				sb.append(line);
			}
		}
		return sb.toString();
	}
}
