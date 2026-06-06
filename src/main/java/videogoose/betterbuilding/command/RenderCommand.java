package videogoose.betterbuilding.command;

import api.common.GameClient;
import api.mod.StarMod;
import api.utils.game.PlayerUtils;
import api.utils.game.chat.CommandInterface;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.data.player.PlayerState;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

import videogoose.betterbuilding.BetterBuilding;
import videogoose.betterbuilding.gen.BlueprintReader;
import videogoose.betterbuilding.gen.TemplateStore;
import videogoose.betterbuilding.gen.VoxelTemplate;
import videogoose.betterbuilding.gen.render.BlockColors;
import videogoose.betterbuilding.gen.render.Renderer;
import videogoose.betterbuilding.gen.render.VoxelTemplateView;

/**
 * Phase-1: render a ship to an isometric PNG (for VLM captioning / the future
 * critique loop). With no argument, renders the entity you're in build mode on;
 * with a name, loads that captured {@code .smtpl} and renders it.
 *
 * <p>Usage: {@code /bb_render [name]} → writes {@code BetterBuilding/renders/<name>.png}.
 */
public class RenderCommand implements CommandInterface {

	private static final File CAPTURE_DIR = new File("./BetterBuilding/captured");
	private static final File RENDER_DIR = new File("./BetterBuilding/renders");
	private static final int TARGET_SIZE = 768;

	@Override
	public String getCommand() {
		return "bb_render";
	}

	@Override
	public String[] getAliases() {
		return new String[] {"/bb_render"};
	}

	@Override
	public String getDescription() {
		return "Render a ship to an isometric PNG.\n" +
				"No arg: renders the entity you're in build mode on.\n" +
				"With a name: renders BetterBuilding/captured/<name>.smtpl.\n" +
				"Usage: /bb_render [name]";
	}

	@Override
	public boolean isAdminOnly() {
		return false;
	}

	@Override
	public boolean onCommand(PlayerState sender, String[] args) {
		try {
			String arg = args.length > 0 ? args[0].replaceAll("^\"|\"$", "").trim() : "";

			VoxelTemplate t;
			String name;
			if (arg.isEmpty()) {
				SegmentController sc = GameClient.getPICM().getSegmentControlManager().getSegmentController();
				if (sc == null) {
					PlayerUtils.sendMessage(sender, "[BB] No build-mode entity, and no template name given.");
					return true;
				}
				name = sanitize(sc.getRealName());
				if (name == null || name.isEmpty()) name = "entity";
				t = BlueprintReader.fromSegmentController(name, sc);
			} else {
				name = arg;
				File smtpl = new File(CAPTURE_DIR, name + ".smtpl");
				if (!smtpl.exists()) {
					PlayerUtils.sendMessage(sender, "[BB] Not found: " + smtpl.getPath());
					return true;
				}
				t = VoxelTemplate.fromCopyArea(name, TemplateStore.load(smtpl));
			}

			if (t.blockCount() == 0) {
				PlayerUtils.sendMessage(sender, "[BB] Nothing to render (0 blocks).");
				return true;
			}

			BufferedImage img = Renderer.renderIso(new VoxelTemplateView(t, new BlockColors()), TARGET_SIZE);
			RENDER_DIR.mkdirs();
			File out = new File(RENDER_DIR, name + ".png");
			ImageIO.write(img, "png", out);

			BetterBuilding.getInstance().logInfo("[BB] rendered " + name + " -> " + out.getAbsolutePath()
					+ " (" + img.getWidth() + "x" + img.getHeight() + ")");
			PlayerUtils.sendMessage(sender, "[BB] Rendered \"" + name + "\" (" + t.blockCount() + " blocks) -> "
					+ out.getName() + " (" + img.getWidth() + "x" + img.getHeight() + ")");
		} catch (Exception e) {
			PlayerUtils.sendMessage(sender, "[BB] render failed: " + e.getMessage());
			BetterBuilding.getInstance().logException("render failed", e);
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
