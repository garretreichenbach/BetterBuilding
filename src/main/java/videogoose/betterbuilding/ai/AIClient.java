package videogoose.betterbuilding.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Minimal OpenAI-compatible chat client for the local LM Studio (Qwen3-VL)
 * server. Supports a single vision turn: a system prompt + a user turn carrying
 * text and one PNG image (as a base64 {@code data:} URL), as the OpenAI vision
 * API expects.
 */
public final class AIClient {

	private final AIConfig cfg;

	public AIClient(AIConfig cfg) {
		this.cfg = cfg;
	}

	/**
	 * Send {@code systemPrompt} + (text, image) and return the assistant's text.
	 *
	 * @param pngBytes a PNG-encoded image
	 */
	public String captionImage(String systemPrompt, String userText, byte[] pngBytes) throws IOException {
		return chatVision(systemPrompt, userText, pngBytes, cfg.temperature, cfg.maxTokens);
	}

	/**
	 * Vision chat with explicit temperature / token budget. Lets the generator show
	 * the model a render of its own build (so it can see and fix flaws), with the
	 * larger token budget code generation needs.
	 */
	public String chatVision(String systemPrompt, String userText, byte[] pngBytes,
							 double temperature, int maxTokens) throws IOException {
		String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(pngBytes);

		JsonArray messages = new JsonArray();
		messages.add(textMessage("system", systemPrompt));

		JsonArray content = new JsonArray();
		content.add(textPart(userText));
		content.add(imagePart(dataUrl));
		JsonObject userMsg = new JsonObject();
		userMsg.addProperty("role", "user");
		userMsg.add("content", content);
		messages.add(userMsg);

		JsonObject req = new JsonObject();
		req.addProperty("model", cfg.model);
		req.addProperty("temperature", temperature);
		req.addProperty("max_tokens", maxTokens);
		req.addProperty("stream", false);
		req.add("messages", messages);

		return extractContent(post("/chat/completions", req));
	}

	/**
	 * Vision chat with multiple images (e.g. reference ships) plus text. Images are
	 * appended after the text in one user turn.
	 */
	public String chatVisionMulti(String systemPrompt, String userText, List<byte[]> images,
								  double temperature, int maxTokens) throws IOException {
		JsonArray messages = new JsonArray();
		messages.add(textMessage("system", systemPrompt));

		JsonArray content = new JsonArray();
		content.add(textPart(userText));
		for (byte[] png : images) {
			content.add(imagePart("data:image/png;base64," + Base64.getEncoder().encodeToString(png)));
		}
		JsonObject userMsg = new JsonObject();
		userMsg.addProperty("role", "user");
		userMsg.add("content", content);
		messages.add(userMsg);

		JsonObject req = new JsonObject();
		req.addProperty("model", cfg.model);
		req.addProperty("temperature", temperature);
		req.addProperty("max_tokens", maxTokens);
		req.addProperty("stream", false);
		req.add("messages", messages);

		return extractContent(post("/chat/completions", req));
	}

	/**
	 * Plain text chat completion (no image). Used for code generation, where we
	 * want more output tokens and a different temperature than captioning.
	 */
	public String chat(String systemPrompt, String userText, double temperature, int maxTokens) throws IOException {
		JsonArray messages = new JsonArray();
		messages.add(textMessage("system", systemPrompt));
		messages.add(textMessage("user", userText));

		JsonObject req = new JsonObject();
		req.addProperty("model", cfg.model);
		req.addProperty("temperature", temperature);
		req.addProperty("max_tokens", maxTokens);
		req.addProperty("stream", false);
		req.add("messages", messages);

		return extractContent(post("/chat/completions", req));
	}

	/**
	 * Embed text via the configured embedding model ({@code embed_model}). Requires
	 * LM Studio to have an embedding model loaded.
	 */
	public float[] embed(String text) throws IOException {
		JsonObject req = new JsonObject();
		req.addProperty("model", cfg.embedModel);
		req.addProperty("input", text);

		JsonObject response = post("/embeddings", req);
		try {
			JsonArray emb = response.getAsJsonArray("data").get(0).getAsJsonObject().getAsJsonArray("embedding");
			float[] v = new float[emb.size()];
			for (int i = 0; i < v.length; i++) v[i] = emb.get(i).getAsFloat();
			return v;
		} catch (Exception e) {
			throw new IOException("Unexpected /embeddings response (is an embedding model loaded?): " + response);
		}
	}

	// --- HTTP ---

	private JsonObject post(String path, JsonObject body) throws IOException {
		String endpoint = cfg.baseUrl.endsWith("/")
				? cfg.baseUrl.substring(0, cfg.baseUrl.length() - 1) + path
				: cfg.baseUrl + path;

		HttpURLConnection conn = null;
		try {
			conn = (HttpURLConnection) new URL(endpoint).openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("Authorization", "Bearer lm-studio");
			conn.setDoOutput(true);
			conn.setConnectTimeout(Math.min(cfg.timeoutMs, 15000));
			conn.setReadTimeout(cfg.timeoutMs);

			byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
			conn.setFixedLengthStreamingMode(payload.length);
			try (OutputStream os = conn.getOutputStream()) {
				os.write(payload);
			}

			int status = conn.getResponseCode();
			if (status != 200) {
				throw new IOException("LM Studio HTTP " + status + ": " + readStream(conn.getErrorStream()));
			}
			String resp = readStream(conn.getInputStream());
			return new JsonParser().parse(resp).getAsJsonObject();
		} finally {
			if (conn != null) conn.disconnect();
		}
	}

	private static String extractContent(JsonObject response) throws IOException {
		try {
			return response.getAsJsonArray("choices").get(0).getAsJsonObject()
					.getAsJsonObject("message").get("content").getAsString();
		} catch (Exception e) {
			throw new IOException("Unexpected response shape: " + response);
		}
	}

	// --- JSON builders ---

	private static JsonObject textMessage(String role, String text) {
		JsonObject m = new JsonObject();
		m.addProperty("role", role);
		m.addProperty("content", text);
		return m;
	}

	private static JsonObject textPart(String text) {
		JsonObject p = new JsonObject();
		p.addProperty("type", "text");
		p.addProperty("text", text);
		return p;
	}

	private static JsonObject imagePart(String dataUrl) {
		JsonObject url = new JsonObject();
		url.addProperty("url", dataUrl);
		JsonObject p = new JsonObject();
		p.addProperty("type", "image_url");
		p.add("image_url", url);
		return p;
	}

	private static String readStream(InputStream stream) throws IOException {
		if (stream == null) return "";
		StringBuilder sb = new StringBuilder();
		try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) sb.append(line).append('\n');
		}
		return sb.toString();
	}
}
