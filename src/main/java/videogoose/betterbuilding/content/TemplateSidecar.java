package videogoose.betterbuilding.content;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-template sidecar metadata stored next to a {@code .smtpl} file as
 * {@code <name>.json}. Describes the template in palette-independent slot space.
 *
 * <p>Schema (PLAN_SUMMARY.md &sect;1.4): {@code slot}, {@code size}, {@code sockets},
 * {@code anchor}, {@code tags}.
 */
public final class TemplateSidecar {

	/** Module type this template fills (e.g. "ROOM", "CORRIDOR"). */
	public String slot = "ROOM";
	/** Bounding box [x, y, z]. */
	public int[] size = new int[]{0, 0, 0};
	/** Origin cell for placement. */
	public int[] anchor = new int[]{0, 0, 0};
	/** Connection points. */
	public List<Socket> sockets = new ArrayList<>();
	/** Arbitrary classifier tags (e.g. "interior"). */
	public List<String> tags = new ArrayList<>();

	/** A single connection point on one face of the template. */
	public static final class Socket {
		/** Z_POS, Z_NEG, X_POS, X_NEG, Y_POS, Y_NEG. */
		public String face;
		/** Connection type (e.g. "CORRIDOR") — matched against style adjacency rules. */
		public String type;

		public Socket() {}

		public Socket(String face, String type) {
			this.face = face;
			this.type = type;
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public void save(File f) throws IOException {
		Files.write(f.toPath(), GSON.toJson(this).getBytes(StandardCharsets.UTF_8));
	}

	/** @return the sidecar, or {@code null} if the file does not exist. */
	public static TemplateSidecar load(File f) throws IOException {
		if (!f.exists()) return null;
		String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
		return GSON.fromJson(json, TemplateSidecar.class);
	}
}
