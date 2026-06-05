package videogoose.betterbuilding.command;

import api.utils.game.PlayerUtils;
import org.schema.game.common.data.player.PlayerState;
import videogoose.betterbuilding.content.Palette;
import videogoose.betterbuilding.util.BbFiles;

import java.io.File;

/**
 * {@code /bb reload} — re-scan styles and palettes from disk and report what
 * loads (and surface any palette errors). A fuller hot-reload of in-memory style
 * state lands with Milestone 2.
 */
public class ReloadSub implements SubCommand {

	@Override
	public String name() {
		return "reload";
	}

	@Override
	public String usage() {
		return "reload";
	}

	@Override
	public String description() {
		return "Re-scan styles and palettes from disk.";
	}

	@Override
	public void run(PlayerState sender, String[] args) {
		File[] palettes = BbFiles.palettes().listFiles((d, n) -> n.endsWith(".json"));
		File[] styles = BbFiles.styles().listFiles(File::isDirectory);

		int ok = 0;
		int bad = 0;
		if (palettes != null) {
			for (File f : palettes) {
				try {
					Palette.load(f);
					ok++;
				} catch (Exception e) {
					bad++;
					PlayerUtils.sendMessage(sender, "[bb] Palette " + f.getName() + ": " + e.getMessage());
				}
			}
		}
		int styleCount = styles != null ? styles.length : 0;
		PlayerUtils.sendMessage(sender, "[bb] Reloaded: " + ok + " palette(s) ok, "
				+ bad + " with errors, " + styleCount + " style folder(s).");
	}
}
