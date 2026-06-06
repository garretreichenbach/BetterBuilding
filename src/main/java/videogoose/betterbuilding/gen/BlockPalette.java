package videogoose.betterbuilding.gen;

import api.common.GameClient;
import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.game.common.data.player.inventory.Inventory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The set of blocks the generator may use, as Lua-friendly NAME → type-id pairs
 * (exposed to the LLM as {@code blocks.NAME}). Built from the player's hotbar:
 * robust (real, valid ids — no block-name guessing across game/mod versions) and
 * it gives the user direct control over the generation palette.
 */
public final class BlockPalette {

	private BlockPalette() {}

	/**
	 * Resolve the player's active hotbar slots to a NAME → type-id map. Names are
	 * the blocks' in-game names, upper-snake-cased for use as Lua identifiers.
	 */
	public static Map<String, Short> fromHotbar() {
		Map<String, Short> palette = new LinkedHashMap<>();
		Inventory inv = GameClient.getClientState().getPlayer().getInventory();
		for (int i = 0; i < inv.getActiveSlotsMax(); i++) {
			if (inv.isSlotEmpty(i)) continue;
			short type = inv.getType(i);
			if (!ElementKeyMap.isValidType(type)) continue;
			ElementInformation info = ElementKeyMap.getInfo(type);
			if (info == null) continue;
			String key = luaKey(info.getName());
			if (!key.isEmpty()) palette.putIfAbsent(key, type);
		}
		return palette;
	}

	/** A human/LLM-readable listing of the palette for the prompt. */
	public static String toPromptString(Map<String, Short> palette) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (String key : palette.keySet()) {
			if (!first) sb.append(", ");
			sb.append(key);
			first = false;
		}
		return sb.toString();
	}

	/** Upper-snake-case a block name into a valid Lua identifier key. */
	public static String luaKey(String name) {
		if (name == null) return "";
		String k = name.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
		// Lua identifiers can't start with a digit.
		if (!k.isEmpty() && Character.isDigit(k.charAt(0))) k = "B_" + k;
		return k;
	}
}
