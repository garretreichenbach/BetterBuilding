package videogoose.betterbuilding.command;

import api.common.GameClient;
import api.mod.StarMod;
import api.utils.game.PlayerUtils;
import api.utils.game.chat.CommandInterface;
import org.schema.game.client.controller.manager.ingame.BuildToolsManager;
import org.schema.game.client.controller.manager.ingame.CopyPasteMode;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.data.player.PlayerState;

import javax.annotation.Nullable;
import java.io.File;

import videogoose.betterbuilding.BetterBuilding;
import videogoose.betterbuilding.gen.BlueprintReader;
import videogoose.betterbuilding.gen.TemplateStore;
import videogoose.betterbuilding.gen.VoxelTemplate;

/**
 * Phase-1 ingestion test: capture the entity you're currently in build mode on
 * into a {@link VoxelTemplate} (via the engine's lossless CopyArea path) and save
 * it as a {@code .smtpl}. Validates the read half of "spawn-and-read" on real
 * ships before batch auto-spawn is wired up.
 *
 * <p>Usage: spawn/load a blueprint (catalog UI), enter its build mode, run
 * {@code /bb_capture [name]}.
 */
public class CaptureCommand implements CommandInterface {

	private static final File CAPTURE_DIR = new File("./BetterBuilding/captured");

	@Override
	public String getCommand() {
		return "bb_capture";
	}

	@Override
	public String[] getAliases() {
		return new String[] {"/bb_capture"};
	}

	@Override
	public String getDescription() {
		return "Capture the entity you're in build mode on into a VoxelTemplate and save it.\n" +
				"Enter build mode on a ship/station first.\n" +
				"Usage: /bb_capture [name]";
	}

	@Override
	public boolean isAdminOnly() {
		return false;
	}

	@Override
	public boolean onCommand(PlayerState sender, String[] args) {
		try {
			SegmentController sc = GameClient.getPICM().getSegmentControlManager().getSegmentController();
			if (sc == null) {
				PlayerUtils.sendMessage(sender, "[BB] No build-mode entity. Enter build mode on a ship/station first.");
				return true;
			}

			String name = (args.length > 0 && !args[0].isEmpty())
					? args[0].replaceAll("^\"|\"$", "").trim()
					: sanitize(sc.getRealName());
			if (name == null || name.isEmpty()) name = "captured";

			VoxelTemplate t = BlueprintReader.fromSegmentController(name, sc);
			if (t.blockCount() == 0) {
				PlayerUtils.sendMessage(sender, "[BB] Captured 0 blocks — entity may not be fully loaded.");
				return true;
			}

			File f = TemplateStore.save(CAPTURE_DIR, t);
			BetterBuilding.getInstance().logInfo("[BB] captured " + name + " -> " + f.getAbsolutePath()
					+ " (" + t.blockCount() + " blocks, " + t.dimX() + "x" + t.dimY() + "x" + t.dimZ() + ")");

			// Load the captured template into PASTE mode so it's immediately visible
			// (and stampable, to verify the round-trip).
			BuildToolsManager btm = GameClient.getPICM().getBuildToolsManager();
			btm.loadCopyArea(f);
			btm.setCopyPasteMode(CopyPasteMode.PASTE);

			PlayerUtils.sendMessage(sender, "[BB] Captured \"" + name + "\": " + t.blockCount() + " blocks, "
					+ t.dimX() + "x" + t.dimY() + "x" + t.dimZ() + " (WxHxL). Loaded into PASTE mode -> " + f.getName());
		} catch (Exception e) {
			PlayerUtils.sendMessage(sender, "[BB] capture failed: " + e.getMessage());
			BetterBuilding.getInstance().logException("capture failed", e);
		}
		return true;
	}

	private static String sanitize(String s) {
		if (s == null) return null;
		return s.replaceAll("[^a-zA-Z0-9_\\- ]", "_").trim();
	}

	@Override
	public void serverAction(@Nullable PlayerState sender, String[] args) {
	}

	@Override
	public StarMod getMod() {
		return BetterBuilding.getInstance();
	}
}
