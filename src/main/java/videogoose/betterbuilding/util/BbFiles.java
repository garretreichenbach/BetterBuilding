package videogoose.betterbuilding.util;

import java.io.File;

/**
 * Resolves the on-disk BetterBuilding content folders.
 *
 * <p>Everything lives under the mod's standard data folder
 * ({@code moddata/BetterBuilding/}), alongside {@code settings.yml} and logs —
 * the idiomatic StarMade location returned by {@code getSkeleton().getResourcesFolder()}.
 * Call {@link #init(File)} once on enable; until then a sensible default is used.
 */
public final class BbFiles {

	private static File root = new File("moddata", "BetterBuilding");

	private BbFiles() {}

	/** Set the content root (the mod's resources folder). */
	public static void init(File resourcesFolder) {
		if (resourcesFolder != null) {
			root = resourcesFolder;
		}
	}

	public static File root() {
		return root;
	}

	public static File styles() {
		return new File(root, "styles");
	}

	public static File palettes() {
		return new File(root, "palettes");
	}

	public static File templates() {
		return new File(root, "templates");
	}

	/** Create the content folder tree if it does not yet exist. */
	public static void bootstrap() {
		styles().mkdirs();
		palettes().mkdirs();
		templates().mkdirs();
	}

	/** Names (without {@code .json}) of palettes in the palettes folder, sorted. */
	public static java.util.List<String> listPaletteNames() {
		File[] files = palettes().listFiles((d, n) -> n.toLowerCase(java.util.Locale.ROOT).endsWith(".json"));
		java.util.List<String> names = new java.util.ArrayList<>();
		if (files != null) {
			for (File f : files) {
				String n = f.getName();
				names.add(n.substring(0, n.length() - ".json".length()));
			}
		}
		java.util.Collections.sort(names);
		return names;
	}
}
