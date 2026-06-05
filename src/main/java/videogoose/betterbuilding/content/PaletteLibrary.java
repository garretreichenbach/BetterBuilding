package videogoose.betterbuilding.content;

import videogoose.betterbuilding.slot.Slot;
import videogoose.betterbuilding.util.BbFiles;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads palettes from the palettes folder and builds cross-palette indexes.
 */
public final class PaletteLibrary {

	private PaletteLibrary() {}

	public static List<String> names() {
		return BbFiles.listPaletteNames();
	}

	public static File file(String name) {
		String fn = name.endsWith(".json") ? name : name + ".json";
		return new File(BbFiles.palettes(), fn);
	}

	public static Palette load(String name) throws Exception {
		return Palette.load(file(name));
	}

	/**
	 * Merge the slot mappings of every palette into a base-block -> slot index,
	 * used to lift concrete blocks back into slot space. First palette
	 * (alphabetical) wins on collision, for determinism.
	 */
	public static Map<Short, Slot> sourceIndex() {
		Map<Short, Slot> index = new HashMap<>();
		for (String pname : names()) {
			try {
				Palette p = load(pname);
				for (Map.Entry<Slot, Short> e : p.baseMap().entrySet()) {
					index.putIfAbsent(e.getValue(), e.getKey());
				}
			} catch (Exception ignored) {
				// a broken palette shouldn't block the others
			}
		}
		return index;
	}
}
