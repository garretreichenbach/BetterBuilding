package videogoose.betterbuilding.manager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * HTTP client for communicating with LM Studio / Ollama OpenAI-compatible API.
 * Supports both simple chat completions and tool-calling conversations.
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
	 * Send a chat completion request with tool definitions.
	 * Returns the raw choices[0].message object so the caller can inspect
	 * tool_calls vs content.
	 */
	public JsonObject chatCompletionWithTools(JsonArray messages, JsonArray tools) throws IOException {
		String endpoint = baseUrl + "/v1/chat/completions";

		JsonObject request = new JsonObject();
		request.addProperty("model", model);
		request.addProperty("temperature", temperature);
		request.addProperty("max_tokens", maxTokens);
		request.addProperty("stream", false);
		request.add("messages", messages);
		if(tools != null && tools.size() > 0) {
			request.add("tools", tools);
		}

		return sendRequest(endpoint, request);
	}

	/**
	 * Test connectivity to the server.
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

	private JsonObject sendRequest(String endpoint, JsonObject requestBody) throws IOException {
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
				throw new IOException("API returned HTTP " + status + ": " + errorBody);
			}

			String responseBody = readStream(conn.getInputStream());
			JsonObject response = new JsonParser().parse(responseBody).getAsJsonObject();
			return response.getAsJsonArray("choices")
					.get(0).getAsJsonObject()
					.getAsJsonObject("message");
		} finally {
			if(conn != null) conn.disconnect();
		}
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