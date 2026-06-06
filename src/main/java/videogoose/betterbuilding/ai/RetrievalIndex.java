package videogoose.betterbuilding.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A small embedding-backed retrieval index over ship captions, so generation can
 * be primed with the most similar real ships. Each entry stores the ship name, a
 * couple of structured fields for display, and the caption's embedding vector.
 * Persisted as {@code BetterBuilding/index.json}.
 */
public final class RetrievalIndex {

	public static final class Entry {
		public final String name;
		public final String role;
		public final String sizeClass;
		public final String text;   // the text that was embedded (for prompt priming / display)
		public final float[] vec;

		public Entry(String name, String role, String sizeClass, String text, float[] vec) {
			this.name = name;
			this.role = role;
			this.sizeClass = sizeClass;
			this.text = text;
			this.vec = vec;
		}
	}

	public static final class Hit {
		public final Entry entry;
		public final double score;

		public Hit(Entry entry, double score) {
			this.entry = entry;
			this.score = score;
		}
	}

	private final List<Entry> entries = new ArrayList<>();

	public int size() {
		return entries.size();
	}

	// --- build from captions ---

	/**
	 * Build entries from every {@code *.json} caption in {@code captionDir},
	 * embedding each via the client. Returns the number indexed.
	 */
	public int buildFromCaptions(File captionDir, AIClient client, ProgressSink progress) throws Exception {
		entries.clear();
		File[] files = captionDir.listFiles((d, n) -> n.endsWith(".json"));
		if (files == null || files.length == 0) return 0;

		int done = 0;
		for (File f : files) {
			String name = f.getName().substring(0, f.getName().length() - ".json".length());
			String raw = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
			Parsed parsed = parseCaption(raw);
			float[] vec = client.embed(parsed.embedText);
			entries.add(new Entry(name, parsed.role, parsed.sizeClass, parsed.embedText, vec));
			done++;
			if (progress != null) progress.onProgress(done, files.length, name);
		}
		return done;
	}

	// --- query ---

	public List<Hit> query(float[] queryVec, int k) {
		List<Hit> hits = new ArrayList<>();
		for (Entry e : entries) {
			hits.add(new Hit(e, cosine(queryVec, e.vec)));
		}
		hits.sort(Comparator.comparingDouble((Hit h) -> h.score).reversed());
		return hits.subList(0, Math.min(k, hits.size()));
	}

	// --- persistence ---

	public void save(File file) throws Exception {
		if (file.getParentFile() != null) file.getParentFile().mkdirs();
		JsonArray arr = new JsonArray();
		for (Entry e : entries) {
			JsonObject o = new JsonObject();
			o.addProperty("name", e.name);
			o.addProperty("role", e.role);
			o.addProperty("size_class", e.sizeClass);
			o.addProperty("text", e.text);
			JsonArray v = new JsonArray();
			for (float f : e.vec) v.add(new JsonPrimitive(f));
			o.add("vec", v);
			arr.add(o);
		}
		try (FileWriter w = new FileWriter(file, StandardCharsets.UTF_8)) {
			w.write(arr.toString());
		}
	}

	public void load(File file) throws Exception {
		entries.clear();
		if (!file.isFile()) return;
		String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
		JsonArray arr = new JsonParser().parse(raw).getAsJsonArray();
		for (JsonElement el : arr) {
			JsonObject o = el.getAsJsonObject();
			JsonArray v = o.getAsJsonArray("vec");
			float[] vec = new float[v.size()];
			for (int i = 0; i < vec.length; i++) vec[i] = v.get(i).getAsFloat();
			entries.add(new Entry(
					str(o, "name"), str(o, "role"), str(o, "size_class"), str(o, "text"), vec));
		}
	}

	// --- helpers ---

	private static double cosine(float[] a, float[] b) {
		int n = Math.min(a.length, b.length);
		double dot = 0, na = 0, nb = 0;
		for (int i = 0; i < n; i++) {
			dot += a[i] * b[i];
			na += a[i] * a[i];
			nb += b[i] * b[i];
		}
		if (na == 0 || nb == 0) return 0;
		return dot / (Math.sqrt(na) * Math.sqrt(nb));
	}

	private static String str(JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
	}

	private static final class Parsed {
		final String role, sizeClass, embedText;

		Parsed(String role, String sizeClass, String embedText) {
			this.role = role;
			this.sizeClass = sizeClass;
			this.embedText = embedText;
		}
	}

	/** Lenient: pull structured fields if the caption is JSON, else embed the raw text. */
	private static Parsed parseCaption(String raw) {
		String json = extractJsonObject(raw);
		if (json != null) {
			try {
				JsonObject o = new JsonParser().parse(json).getAsJsonObject();
				String role = str(o, "role");
				String size = str(o, "size_class");
				StringBuilder sb = new StringBuilder();
				append(sb, role);
				append(sb, size);
				append(sb, str(o, "silhouette"));
				append(sb, joinArray(o, "key_features"));
				append(sb, joinArray(o, "palette"));
				append(sb, str(o, "style"));
				append(sb, str(o, "description"));
				String text = sb.toString().trim();
				if (!text.isEmpty()) return new Parsed(role, size, text);
			} catch (Exception ignored) {
				// fall through
			}
		}
		return new Parsed("", "", raw.replaceAll("\\s+", " ").trim());
	}

	private static void append(StringBuilder sb, String s) {
		if (s != null && !s.isEmpty()) {
			if (sb.length() > 0) sb.append(". ");
			sb.append(s);
		}
	}

	private static String joinArray(JsonObject o, String key) {
		if (!o.has(key) || !o.get(key).isJsonArray()) return "";
		StringBuilder sb = new StringBuilder();
		for (JsonElement e : o.getAsJsonArray(key)) {
			if (sb.length() > 0) sb.append(", ");
			sb.append(e.getAsString());
		}
		return sb.toString();
	}

	private static String extractJsonObject(String s) {
		int a = s.indexOf('{');
		int b = s.lastIndexOf('}');
		return (a >= 0 && b > a) ? s.substring(a, b + 1) : null;
	}

	/** Progress callback for long index builds. */
	public interface ProgressSink {
		void onProgress(int done, int total, String name);
	}
}
