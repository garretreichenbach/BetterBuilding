package videogoose.betterbuilding.command;

import api.utils.game.PlayerUtils;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.controller.manager.ingame.BuildToolsManager;
import org.schema.game.client.controller.manager.ingame.CopyArea;
import org.schema.game.common.data.player.PlayerState;
import videogoose.betterbuilding.content.TemplateSidecar;
import videogoose.betterbuilding.io.TemplateStore;
import videogoose.betterbuilding.util.BbFiles;
import videogoose.betterbuilding.util.BuildAccess;

import java.io.File;
import java.util.Locale;

/**
 * {@code /bb capture <name> [slot]} — save the current build-mode copy selection
 * as a template (.smtpl + sidecar) in the BetterBuilding global template store.
 */
public class CaptureSub implements SubCommand {

	@Override
	public String name() {
		return "capture";
	}

	@Override
	public String usage() {
		return "capture <name> [slot]";
	}

	@Override
	public String description() {
		return "Save the current build-mode copy selection as a template.";
	}

	@Override
	public void run(PlayerState sender, String[] args) throws Exception {
		if (args.length < 1) {
			PlayerUtils.sendMessage(sender, "Usage: /bb " + usage());
			return;
		}
		String tname = args[0];
		String slot = args.length > 1 ? args[1].toUpperCase(Locale.ROOT) : "ROOM";

		BuildToolsManager btm = BuildAccess.buildTools();
		CopyArea area = btm != null ? btm.getCopyArea() : null;
		if (area == null || area.getPieces().isEmpty()) {
			PlayerUtils.sendMessage(sender,
					"No copy selection. In build mode, select a region and copy it (Ctrl+C), then run /bb capture.");
			return;
		}

		TemplateSidecar sidecar = new TemplateSidecar();
		sidecar.slot = slot;
		Vector3i sz = area.getSize();
		sidecar.size = new int[]{sz.x, sz.y, sz.z};

		File out = TemplateStore.save(BbFiles.templates(), tname, area, sidecar);
		PlayerUtils.sendMessage(sender, "[bb] Captured " + area.getPieces().size()
				+ " blocks to " + out.getPath()
				+ " (slot=" + slot + ", size=" + sz.x + "x" + sz.y + "x" + sz.z + ")");
	}
}
