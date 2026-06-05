package videogoose.betterbuilding.command;

import api.common.GameClient;
import api.mod.StarMod;
import api.utils.game.PlayerUtils;
import api.utils.game.chat.CommandInterface;
import org.schema.game.client.controller.manager.ingame.BuildToolsManager;
import org.schema.game.client.controller.manager.ingame.CopyPasteMode;
import org.schema.game.common.data.element.ElementKeyMap;
import org.schema.game.common.data.player.PlayerState;

import javax.annotation.Nullable;
import java.io.File;

import videogoose.betterbuilding.BetterBuilding;
import videogoose.betterbuilding.gen.TemplateStore;
import videogoose.betterbuilding.gen.VoxelTemplate;

/**
 * Phase-0 plumbing proof: build a synthetic, intentionally asymmetric template
 * in code, run it through the full output path
 * ({@code VoxelTemplate → CopyArea → save(.smtpl) → load → PASTE}), and hand it
 * to the build tools for pasting. No LLM involved.
 *
 * <p>If the pasted shape matches the description below — correct size, correct
 * chirality, blocks intact — the entire output path is trustworthy and the
 * generation stack (Lua executor, model, retrieval, vision loop) can be built
 * on top of it with confidence.
 *
 * <p>Usage: select any block in your hotbar, enter build mode, then run
 * {@code /bb_gentest}. The template is loaded into PASTE mode at your cursor.
 */
public class GenTestCommand implements CommandInterface {

	/** Where generated templates are written (under the StarMade working dir). */
	private static final File TEMPLATE_DIR = new File("./BetterBuilding/templates");

	@Override
	public String getCommand() {
		return "bb_gentest";
	}

	@Override
	public String[] getAliases() {
		return new String[] {"bb_gentest"};
	}

	@Override
	public String getDescription() {
		return "Phase-0 test: build a synthetic asymmetric template in code and load it into PASTE mode.\n" +
				"Select a block first; the shape is built from your selected block type.\n" +
				"Usage: /bb_gentest";
	}

	@Override
	public boolean isAdminOnly() {
		return false;
	}

	@Override
	public boolean onCommand(PlayerState sender, String[] args) {
		try {
			short type = GameClient.getPICM().getSelectedTypeWithSub();
			if (type == 0 || !ElementKeyMap.isValidType(type)) {
				PlayerUtils.sendMessage(sender, "[BB] Select a valid block in your hotbar first, then run /bb_gentest.");
				return true;
			}

			VoxelTemplate t = buildTestShape(type);

			// Full output path: VoxelTemplate -> CopyArea -> save .smtpl -> load -> paste.
			File smtpl = TemplateStore.save(TEMPLATE_DIR, t);
			BetterBuilding.getInstance().logInfo("[BB] gentest wrote " + smtpl.getAbsolutePath()
					+ " (" + t.blockCount() + " blocks)");

			BuildToolsManager btm = GameClient.getPICM().getBuildToolsManager();
			btm.loadCopyArea(smtpl);
			btm.setCopyPasteMode(CopyPasteMode.PASTE);

			PlayerUtils.sendMessage(sender, "[BB] gentest template loaded into PASTE mode (" + t.blockCount()
					+ " blocks, " + t.dimX() + "x" + t.dimY() + "x" + t.dimZ() + "). "
					+ "Expect: a hull box with a notch cut from the +X/top/front corner and a tall fin "
					+ "on the +X side only. If chirality/size look right, the output path is verified.");
		} catch (Exception e) {
			PlayerUtils.sendMessage(sender, "[BB] gentest failed: " + e.getMessage());
			BetterBuilding.getInstance().logException("gentest failed", e);
		}
		return true;
	}

	/**
	 * An intentionally asymmetric shape so position, chirality, and bounds are all
	 * visually verifiable in-game (a symmetric box would hide mirror/axis bugs):
	 *   - 9x7x14 solid hull box (sitting at the bottom of the grid),
	 *   - a 3x3x3 notch carved out of the +X / +Y / +Z (top-front-right) corner,
	 *   - a 1-wide fin standing up above the +X face, near the back.
	 * The grid is sized {@code hullY + finHeight} tall so the fin is in-bounds.
	 */
	private static VoxelTemplate buildTestShape(short type) {
		int hullX = 9, hullY = 7, hullZ = 14;
		int finHeight = 3;
		VoxelTemplate t = new VoxelTemplate("bb_gentest", hullX, hullY + finHeight, hullZ);

		// Solid hull at the bottom of the grid.
		t.fill(0, 0, 0, hullX - 1, hullY - 1, hullZ - 1, type, (byte) 0);

		// Carve a corner notch (top-front-right of the hull) to break all three symmetries.
		for (int x = hullX - 3; x < hullX; x++) {
			for (int y = hullY - 3; y < hullY; y++) {
				for (int z = hullZ - 3; z < hullZ; z++) {
					t.clear(x, y, z);
				}
			}
		}

		// A tall thin fin standing on top of the +X edge, toward the back (low Z).
		int finX = hullX - 1;
		for (int y = hullY; y < hullY + finHeight; y++) {
			for (int z = 2; z <= 5; z++) {
				t.set(finX, y, z, type, (byte) 0);
			}
		}

		return t;
	}

	@Override
	public void serverAction(@Nullable PlayerState sender, String[] args) {
	}

	@Override
	public StarMod getMod() {
		return BetterBuilding.getInstance();
	}
}
