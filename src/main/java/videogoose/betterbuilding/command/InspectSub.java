package videogoose.betterbuilding.command;

import api.utils.game.PlayerUtils;
import org.schema.game.client.controller.manager.ingame.BuildToolsManager;
import org.schema.game.client.controller.manager.ingame.CopyArea;
import org.schema.game.common.data.VoidSegmentPiece;
import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.game.common.data.player.PlayerState;
import videogoose.betterbuilding.content.PaletteLibrary;
import videogoose.betterbuilding.slot.ShapeInference;
import videogoose.betterbuilding.slot.Slot;
import videogoose.betterbuilding.util.BuildAccess;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /bb inspect} — list the distinct base blocks in the current copy
 * selection, aggregated by shape family, flagged as mapped (to a slot) or
 * unmapped by the known palettes. Makes palette authoring "see what this ship
 * needs" instead of guess-and-check.
 */
public class InspectSub implements SubCommand {

	private static final int MAX_ROWS = 15;

	@Override
	public String name() {
		return "inspect";
	}

	@Override
	public String usage() {
		return "inspect";
	}

	@Override
	public String description() {
		return "List block types in the copy selection and whether a palette maps them.";
	}

	@Override
	public void run(PlayerState sender, String[] args) {
		BuildToolsManager btm = BuildAccess.buildTools();
		CopyArea area = btm != null ? btm.getCopyArea() : null;
		if (area == null || area.getPieces().isEmpty()) {
			PlayerUtils.sendMessage(sender, "[bb] Copy a region first (select in build mode, Ctrl+C), then run /bb inspect.");
			return;
		}

		Map<Short, Slot> slotByBase = PaletteLibrary.sourceIndex();

		// Aggregate counts by base block (so shape variants fold into one entry).
		Map<Short, Integer> countByBase = new LinkedHashMap<>();
		int totalBlocks = 0;
		for (VoidSegmentPiece p : area.getPieces()) {
			short type = p.getType();
			if (type == 0) continue;
			totalBlocks++;
			short base = ShapeInference.baseOf(type);
			countByBase.merge(base, 1, Integer::sum);
		}

		List<Map.Entry<Short, Integer>> mapped = new ArrayList<>();
		List<Map.Entry<Short, Integer>> unmapped = new ArrayList<>();
		for (Map.Entry<Short, Integer> e : countByBase.entrySet()) {
			(slotByBase.containsKey(e.getKey()) ? mapped : unmapped).add(e);
		}
		Comparator<Map.Entry<Short, Integer>> byCountDesc = (a, b) -> Integer.compare(b.getValue(), a.getValue());
		mapped.sort(byCountDesc);
		unmapped.sort(byCountDesc);

		PlayerUtils.sendMessage(sender, "[bb] Selection: " + totalBlocks + " blocks, "
				+ countByBase.size() + " base type(s) — " + mapped.size() + " mapped, " + unmapped.size() + " unmapped.");

		if (!mapped.isEmpty()) {
			PlayerUtils.sendMessage(sender, "[bb] mapped:");
			emit(sender, mapped, slotByBase);
		}
		if (!unmapped.isEmpty()) {
			PlayerUtils.sendMessage(sender, "[bb] unmapped (add these to a palette to reskin them):");
			emit(sender, unmapped, null);
		}
	}

	private void emit(PlayerState sender, List<Map.Entry<Short, Integer>> rows, Map<Short, Slot> slotByBase) {
		int shown = 0;
		for (Map.Entry<Short, Integer> e : rows) {
			if (shown >= MAX_ROWS) {
				PlayerUtils.sendMessage(sender, "   ...and " + (rows.size() - shown) + " more.");
				break;
			}
			String name = blockName(e.getKey());
			String suffix = slotByBase != null ? " -> " + slotByBase.get(e.getKey()) : "";
			PlayerUtils.sendMessage(sender, "   " + name + " x" + e.getValue() + suffix);
			shown++;
		}
	}

	private String blockName(short type) {
		ElementInformation info = ElementKeyMap.getInfoFast(type);
		return info != null ? info.getName() : ("type " + type);
	}
}
