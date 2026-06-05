package videogoose.betterbuilding.command;

import api.common.GameClient;
import api.utils.game.PlayerUtils;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.controller.manager.ingame.BuildInstruction;
import org.schema.game.client.controller.manager.ingame.BuildRemoveCallback;
import org.schema.game.client.controller.manager.ingame.BuildToolsManager;
import org.schema.game.client.controller.manager.ingame.CopyArea;
import org.schema.game.client.controller.manager.ingame.PlayerInteractionControlManager;
import org.schema.game.common.controller.EditableSendableSegmentController;
import org.schema.game.common.data.SegmentPiece;
import org.schema.game.common.data.VoidSegmentPiece;
import org.schema.game.common.data.player.PlayerState;
import org.schema.game.common.data.world.Segment;
import videogoose.betterbuilding.content.Palette;
import videogoose.betterbuilding.content.PaletteLibrary;
import videogoose.betterbuilding.slot.Shape;
import videogoose.betterbuilding.slot.ShapeInference;
import videogoose.betterbuilding.slot.Slot;
import videogoose.betterbuilding.util.BbFiles;
import videogoose.betterbuilding.util.BuildAccess;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code /bb reskin <palette> [buffer]} — shape-aware re-skin of the current
 * build-mode selection.
 *
 * <p>Each block is lifted to slot space by matching its base block against the
 * slot mappings of <em>all</em> known palettes, then re-resolved through the
 * target palette while preserving shape and orientation.
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>in-place</b> (default): rewrites the blocks where they sit, over the
 *       copied region, using the same remove/replace path as the vanilla fill
 *       tool. Requires being in build mode on an entity.</li>
 *   <li><b>buffer</b> ({@code /bb reskin <palette> buffer}): mutates the copy
 *       buffer instead, so the next paste places the re-skinned blocks.</li>
 * </ul>
 * Either way, copy a region (Ctrl+C) first to mark the area.
 */
public class ReskinSub implements SubCommand {

	@Override
	public String name() {
		return "reskin";
	}

	@Override
	public String usage() {
		return "reskin <palette> [buffer]";
	}

	@Override
	public String description() {
		return "Shape-aware re-skin of the selection to a palette (in-place, or 'buffer' for paste).";
	}

	@Override
	public void run(PlayerState sender, String[] args) throws Exception {
		if (args.length < 1) {
			PlayerUtils.sendMessage(sender, "Usage: /bb " + usage() + " — copy a region (Ctrl+C) first.");
			sendPaletteList(sender);
			return;
		}
		File toFile = paletteFile(args[0]);
		if (!toFile.exists()) {
			PlayerUtils.sendMessage(sender, "[bb] No palette named '" + args[0] + "'.");
			sendPaletteList(sender);
			return;
		}
		boolean bufferMode = args.length > 1 && "buffer".equalsIgnoreCase(args[1]);

		Palette to = Palette.load(toFile);
		Map<Short, Slot> slotByBase = PaletteLibrary.sourceIndex();

		BuildToolsManager btm = BuildAccess.buildTools();
		CopyArea area = btm != null ? btm.getCopyArea() : null;
		if (area == null || area.getPieces().isEmpty() || area.min == null || area.max == null) {
			PlayerUtils.sendMessage(sender,
					"[bb] Copy a region first (select in build mode, Ctrl+C), then run this again.");
			return;
		}

		if (bufferMode) {
			applyBuffer(sender, to, slotByBase, area);
		} else {
			applyInPlace(sender, to, slotByBase, area);
		}
	}

	/** In-place: rewrite the world blocks over the copied region. */
	private void applyInPlace(PlayerState sender, Palette to, Map<Short, Slot> slotByBase, CopyArea area) {
		EditableSendableSegmentController c = BuildAccess.controlledEntity();
		if (c == null) {
			PlayerUtils.sendMessage(sender,
					"[bb] Not building on an entity — can't reskin in place. Try: /bb reskin " + to.name() + " buffer");
			return;
		}
		PlayerInteractionControlManager pim = GameClient.getPICM();
		final BuildInstruction instr = new BuildInstruction(c);
		final BuildRemoveCallback callback = new BuildRemoveCallback() {
			@Override
			public long getSelectedControllerPos() {
				return Long.MIN_VALUE;
			}

			@Override
			public void onRemove(long pos, short type) {
			}

			@Override
			public boolean canRemove(short type) {
				return true;
			}
		};
		final Set<Segment> moddedSegs = new ObjectOpenHashSet<>();

		Vector3i min = area.min;
		Vector3i max = area.max;
		Vector3i pos = new Vector3i();
		int total = 0, changed = 0, unmapped = 0, noTarget = 0;
		for (int x = min.x; x <= max.x; x++) {
			for (int y = min.y; y <= max.y; y++) {
				for (int z = min.z; z <= max.z; z++) {
					pos.set(x, y, z);
					SegmentPiece piece = c.getSegmentBuffer().getPointUnsave(pos);
					if (piece == null) continue;
					short oldType = piece.getType();
					if (oldType == 0) continue;
					total++;
					short newType = resolve(oldType, slotByBase, to);
					if (newType == -2) {
						unmapped++;
						continue;
					}
					if (newType <= 0) {
						noTarget++;
						continue;
					}
					if (newType == oldType) continue; // already the target block
					int orient = piece.getFullOrientation();
					c.remove(x, y, z, callback, true, moddedSegs, oldType, newType, orient, null, instr);
					changed++;
				}
			}
		}
		if (changed > 0) {
			pim.addToUndoStack(instr);
		}
		PlayerUtils.sendMessage(sender, "[bb] Reskinned " + changed + " of " + total
				+ " blocks in place to '" + to.name() + "'.");
		reportLeftovers(sender, changed, total, unmapped, noTarget, to);
	}

	/** Buffer: mutate the copy buffer's pieces, leaving the world untouched. */
	private void applyBuffer(PlayerState sender, Palette to, Map<Short, Slot> slotByBase, CopyArea area) {
		int total = 0, changed = 0, unmapped = 0, noTarget = 0;
		for (VoidSegmentPiece p : area.getPieces()) {
			short oldType = p.getType();
			if (oldType == 0) continue;
			total++;
			short newType = resolve(oldType, slotByBase, to);
			if (newType == -2) {
				unmapped++;
				continue;
			}
			if (newType <= 0) {
				noTarget++;
				continue;
			}
			p.setType(newType); // orientation untouched
			changed++;
		}
		area.markDrawDirty();
		PlayerUtils.sendMessage(sender, "[bb] Reskinned " + changed + " of " + total
				+ " blocks to '" + to.name() + "' in the copy buffer. Paste to place the result.");
		reportLeftovers(sender, changed, total, unmapped, noTarget, to);
	}

	/**
	 * Resolve a concrete block's re-skinned type.
	 *
	 * @return the new block type; {@code -2} if no palette maps this block;
	 * {@code -1} if the slot has no entry in the target palette.
	 */
	private short resolve(short oldType, Map<Short, Slot> slotByBase, Palette to) {
		short base = ShapeInference.baseOf(oldType);
		Slot slot = slotByBase.get(base);
		if (slot == null) return -2;
		Shape shape = ShapeInference.shapeOf(oldType);
		return to.resolve(slot, shape);
	}

	private void reportLeftovers(PlayerState sender, int changed, int total, int unmapped, int noTarget, Palette to) {
		if (changed == 0 && total > 0) {
			PlayerUtils.sendMessage(sender, "[bb] 0 changed: " + unmapped
					+ " block(s) aren't mapped by any palette"
					+ (noTarget > 0 ? ", " + noTarget + " have no entry in '" + to.name() + "'." : ".")
					+ " Built with blocks no palette knows? Run /bb reload to check for palette errors.");
		} else if (unmapped > 0 || noTarget > 0) {
			PlayerUtils.sendMessage(sender, "[bb] (" + unmapped + " unmapped, "
					+ noTarget + " missing in target — left unchanged.)");
		}
	}

	private File paletteFile(String name) {
		String fn = name.endsWith(".json") ? name : name + ".json";
		return new File(BbFiles.palettes(), fn);
	}

	private void sendPaletteList(PlayerState sender) {
		List<String> names = BbFiles.listPaletteNames();
		if (names.isEmpty()) {
			PlayerUtils.sendMessage(sender, "[bb] No palettes found in " + BbFiles.palettes().getPath() + ".");
		} else {
			PlayerUtils.sendMessage(sender, "[bb] Available palettes: " + String.join(", ", names));
		}
	}
}
