package videogoose.betterbuilding.content;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.schema.game.client.view.cubes.shapes.BlockStyle;
import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.element.ElementKeyMap;
import videogoose.betterbuilding.slot.PaletteResolver;
import videogoose.betterbuilding.slot.Shape;
import videogoose.betterbuilding.slot.Slot;
import videogoose.betterbuilding.slot.SlotRegistry;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A mapping from material {@link Slot}s to concrete StarMade base blocks.
 *
 * <p>Resolution is shape-aware: a single palette entry (the base/cube block)
 * drives the whole shape family. {@link #resolve(Slot, Shape)} mirrors the
 * vanilla FillTool swap — {@code styleIds[blockStyle.id]} for shape variants,
 * {@code slabIds[slab]} for slabs — so wedges/corners/slabs survive a re-skin.
 */
public final class Palette implements PaletteResolver {

	private final String name;
	/** slot -> base (cube) block id. */
	private final Map<Slot, Short> baseBySlot;

	private Palette(String name, Map<Slot, Short> baseBySlot) {
		this.name = name;
		this.baseBySlot = baseBySlot;
	}

	public String name() {
		return name;
	}

	public Set<Slot> slots() {
		return baseBySlot.keySet();
	}

	/** @return the base block id for a slot, or {@code -1} if absent. */
	public short base(Slot slot) {
		Short s = baseBySlot.get(slot);
		return s == null ? -1 : s;
	}

	/** Read-only view of slot -> base block id, for reverse lookups (reskin). */
	public Map<Slot, Short> baseMap() {
		return Collections.unmodifiableMap(baseBySlot);
	}

	@Override
	public short resolve(Slot slot, Shape shape) {
		Short b = baseBySlot.get(slot);
		if (b == null) return -1;
		short base = b;
		ElementInformation info = ElementKeyMap.getInfoFast(base);
		if (info == null) return -1;

		// styleIds/slabIds are COMPACT arrays (e.g. [wedge, corner, tetra, hepta]),
		// NOT indexed by blockStyle.id — so search for the variant whose actual
		// shape matches, rather than indexing positionally.
		if (shape.isSlab()) {
			short found = findVariant(info.slabIds, vi -> vi.slab == shape.slab);
			return found > 0 ? found : base; // nearest-shape fallback
		}
		if (shape.blockStyle == BlockStyle.NORMAL) return base;
		short found = findVariant(info.styleIds, vi -> vi.blockStyle == shape.blockStyle);
		return found > 0 ? found : base; // nearest-shape fallback (cube)
	}

	private static short findVariant(short[] ids, java.util.function.Predicate<ElementInformation> match) {
		if (ids == null) return -1;
		for (short vid : ids) {
			if (vid <= 0) continue;
			ElementInformation vi = ElementKeyMap.getInfoFast(vid);
			if (vi != null && match.test(vi)) return vid;
		}
		return -1;
	}

	/**
	 * Load and validate a palette JSON file. Each entry's block name is resolved
	 * via {@link ElementKeyMap} and normalised to its base block (so the
	 * shape-family arrays are present). Throws if any block name is unknown.
	 */
	public static Palette load(File file) throws IOException {
		if (!file.exists()) {
			throw new IOException("Palette not found: " + file.getPath());
		}
		String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
		JsonObject root = new JsonParser().parse(json).getAsJsonObject();
		String pname = root.has("name") ? root.get("name").getAsString() : file.getName();

		Map<Slot, Short> map = new LinkedHashMap<>();
		List<String> errors = new ArrayList<>();
		JsonObject slots = root.has("slots") ? root.getAsJsonObject("slots") : null;
		if (slots != null) {
			for (Map.Entry<String, JsonElement> e : slots.entrySet()) {
				String slotName = e.getKey();
				JsonObject entry = e.getValue().getAsJsonObject();
				if (!entry.has("block")) {
					errors.add("Slot " + slotName + " has no 'block' field");
					continue;
				}
				String block = entry.get("block").getAsString();
				ElementInformation info = lookup(block);
				if (info == null) {
					errors.add("Unknown block '" + block + "' for slot " + slotName);
					continue;
				}
				short id = info.getId();
				int src = info.getSourceReference();
				if (src != 0 && ElementKeyMap.isValidType(src)) id = (short) src;
				map.put(SlotRegistry.getOrCreate(slotName), id);
			}
		}
		if (!errors.isEmpty()) {
			throw new IOException("Palette '" + pname + "' has errors: " + String.join("; ", errors));
		}
		return new Palette(pname, map);
	}

	/**
	 * Resolve a block reference by name. Tries the (possibly translated) display
	 * name, then the untranslated config name, then a numeric type id — so
	 * palettes are stable across locales.
	 */
	private static ElementInformation lookup(String block) {
		String q = block.trim();
		try {
			ElementInformation i = ElementKeyMap.getInfoByName(q);
			if (i != null) return i;
		} catch (Exception ignored) {
		}
		// Match the untranslated config name (the name in BlockConfig.xml).
		ElementInformation[] all = ElementKeyMap.getInfoArray();
		if (all != null) {
			for (ElementInformation info : all) {
				if (info != null && q.equalsIgnoreCase(info.getNameUntranslated())) {
					return info;
				}
			}
		}
		try {
			short id = Short.parseShort(q);
			if (ElementKeyMap.isValidType(id)) return ElementKeyMap.getInfo(id);
		} catch (Exception ignored) {
		}
		return null;
	}
}
