package videogoose.betterbuilding.content;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.element.ElementKeyMap;
import videogoose.betterbuilding.slot.Slot;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/**
 * Writes a palette JSON file from an in-memory slot -> base-block mapping.
 * Block ids are stored by their untranslated config name so the file stays
 * readable and locale-stable (matching {@link Palette}'s loader).
 */
public final class PaletteWriter {

	private PaletteWriter() {}

	public static void write(File file, String displayName, Map<Slot, Short> baseBySlot) throws IOException {
		JsonObject root = new JsonObject();
		root.addProperty("name", displayName);
		JsonObject slots = new JsonObject();
		for (Map.Entry<Slot, Short> e : baseBySlot.entrySet()) {
			short id = e.getValue();
			if (id <= 0) continue; // unassigned slot
			ElementInformation info = ElementKeyMap.getInfoFast(id);
			if (info == null) continue;
			JsonObject entry = new JsonObject();
			entry.addProperty("block", info.getNameUntranslated());
			slots.add(e.getKey().name(), entry);
		}
		root.add("slots", slots);
		String json = new GsonBuilder().setPrettyPrinting().create().toJson(root);
		file.getParentFile().mkdirs();
		Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
	}
}
