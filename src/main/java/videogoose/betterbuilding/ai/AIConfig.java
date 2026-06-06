package videogoose.betterbuilding.ai;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

/**
 * AI endpoint settings for the local LM Studio (Qwen3-VL) server, loaded from
 * {@code ./BetterBuilding/ai.properties}. On first run the file is written with
 * defaults so the user can edit the endpoint/model without recompiling.
 */
public final class AIConfig {

	private static final File FILE = new File("./BetterBuilding/ai.properties");

	public final String baseUrl;
	public final String model;
	public final String embedModel;
	public final double temperature;
	public final int maxTokens;
	public final int timeoutMs;

	private AIConfig(String baseUrl, String model, String embedModel, double temperature, int maxTokens, int timeoutMs) {
		this.baseUrl = baseUrl;
		this.model = model;
		this.embedModel = embedModel;
		this.temperature = temperature;
		this.maxTokens = maxTokens;
		this.timeoutMs = timeoutMs;
	}

	public static AIConfig load() {
		Properties p = new Properties();
		// Defaults (LM Studio on the Windows box).
		p.setProperty("base_url", "http://100.97.5.58:1234/v1");
		p.setProperty("model", "qwen/qwen3-vl-30b");
		p.setProperty("embed_model", "text-embedding-nomic-embed-text-v1.5");
		p.setProperty("temperature", "0.4");
		p.setProperty("max_tokens", "700");
		p.setProperty("timeout_ms", "180000");

		if (FILE.isFile()) {
			try (InputStream in = new FileInputStream(FILE)) {
				p.load(in);
			} catch (Exception ignored) {
				// fall back to defaults
			}
		} else {
			try {
				if (FILE.getParentFile() != null) FILE.getParentFile().mkdirs();
				try (OutputStream out = new FileOutputStream(FILE)) {
					p.store(out, "BetterBuilding AI settings (LM Studio / Qwen3-VL). Edit base_url/model as needed.");
				}
			} catch (Exception ignored) {
				// non-fatal
			}
		}

		return new AIConfig(
				p.getProperty("base_url"),
				p.getProperty("model"),
				p.getProperty("embed_model"),
				parseDouble(p.getProperty("temperature"), 0.4),
				parseInt(p.getProperty("max_tokens"), 700),
				parseInt(p.getProperty("timeout_ms"), 180000));
	}

	private static double parseDouble(String s, double def) {
		try {
			return Double.parseDouble(s.trim());
		} catch (Exception e) {
			return def;
		}
	}

	private static int parseInt(String s, int def) {
		try {
			return Integer.parseInt(s.trim());
		} catch (Exception e) {
			return def;
		}
	}
}
