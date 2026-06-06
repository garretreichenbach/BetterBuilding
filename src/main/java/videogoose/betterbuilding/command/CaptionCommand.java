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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import videogoose.betterbuilding.BetterBuilding;
import videogoose.betterbuilding.ai.AIClient;
import videogoose.betterbuilding.ai.AIConfig;
import videogoose.betterbuilding.gen.BlueprintReader;
import videogoose.betterbuilding.gen.TemplateStore;
import videogoose.betterbuilding.gen.VoxelTemplate;
import videogoose.betterbuilding.gen.render.BlockColors;
import videogoose.betterbuilding.gen.render.Renderer;
import videogoose.betterbuilding.gen.render.VoxelTemplateView;

/**
 * Phase-1: render a ship and caption it with the local Qwen3-VL model — the
 * first real use of the VLM. With no argument, captions the entity you're in
 * build mode on; with a name, the captured {@code .smtpl}.
 *
 * <p>Writes the render to {@code BetterBuilding/renders/<name>.png} and the
 * caption to {@code BetterBuilding/captions/<name>.json}. Endpoint/model live in
 * {@code BetterBuilding/ai.properties}. Runs on a background thread (the model
 * call is slow).
 */
public class CaptionCommand implements CommandInterface {

	private static final File CAPTURE_DIR = new File("./BetterBuilding/captured");
	private static final File RENDER_DIR = new File("./BetterBuilding/renders");
	private static final File CAPTION_DIR = new File("./BetterBuilding/captions");
	private static final int TARGET_SIZE = 768;

	private static final String SYSTEM_PROMPT =
			"You are a StarMade starship analyst. You are shown an isometric render of a voxel-built " +
			"ship or station. Catalogue it for a searchable design library. Respond with ONLY a JSON " +
			"object, no prose, with these fields:\n" +
			"  \"role\": one of fighter, bomber, corvette, frigate, destroyer, cruiser, battleship, " +
			"carrier, freighter, miner, shuttle, station, turret, unknown\n" +
			"  \"size_class\": one of small, medium, large, capital\n" +
			"  \"silhouette\": short phrase for the overall shape (e.g. \"long arrow-shaped hull with swept wings\")\n" +
			"  \"key_features\": array of short strings (e.g. [\"twin engines\", \"forward cockpit\", \"dorsal fin\"])\n" +
			"  \"palette\": array of the dominant colours you see (e.g. [\"grey\", \"red accents\"])\n" +
			"  \"style\": short phrase (e.g. \"military\", \"industrial\", \"sleek\", \"blocky\")\n" +
			"  \"description\": one or two sentences a builder would search for.\n" +
			"Judge only from the render; do not invent interior detail you cannot see.";

	@Override
	public String getCommand() {
		return "bb_caption";
	}

	@Override
	public String[] getAliases() {
		return new String[] {"/bb_caption"};
	}

	@Override
	public String getDescription() {
		return "Render a ship and caption it with the local VLM (Qwen3-VL).\n" +
				"No arg: the entity you're in build mode on. With a name: a captured .smtpl.\n" +
				"Endpoint/model: BetterBuilding/ai.properties. Usage: /bb_caption [name]";
	}

	@Override
	public boolean isAdminOnly() {
		return false;
	}

	@Override
	public boolean onCommand(PlayerState sender, String[] args) {
		try {
			String arg = args.length > 0 ? args[0].replaceAll("^\"|\"$", "").trim() : "";

			final VoxelTemplate t;
			final String name;
			if (arg.isEmpty()) {
				SegmentController sc = GameClient.getPICM().getSegmentControlManager().getSegmentController();
				if (sc == null) {
					PlayerUtils.sendMessage(sender, "[BB] No build-mode entity, and no template name given.");
					return true;
				}
				name = sanitize(sc.getRealName(), "entity");
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
				PlayerUtils.sendMessage(sender, "[BB] Nothing to caption (0 blocks).");
				return true;
			}

			// Render now (needs the game's block config); send/caption on a worker thread.
			BufferedImage img = Renderer.renderIso(new VoxelTemplateView(t, new BlockColors()), TARGET_SIZE);
			RENDER_DIR.mkdirs();
			File png = new File(RENDER_DIR, name + ".png");
			ImageIO.write(img, "png", png);
			final byte[] pngBytes = Files.readAllBytes(png.toPath());

			final String userText = "Caption this StarMade build. Name hint: \"" + name + "\". "
					+ "Dimensions (WxHxL): " + t.dimX() + "x" + t.dimY() + "x" + t.dimZ()
					+ ", " + t.blockCount() + " blocks.";

			PlayerUtils.sendMessage(sender, "[BB] Rendered \"" + name + "\"; asking the VLM... (this can take a while)");

			new Thread(() -> {
				try {
					AIClient client = new AIClient(AIConfig.load());
					String caption = client.captionImage(SYSTEM_PROMPT, userText, pngBytes);

					CAPTION_DIR.mkdirs();
					File out = new File(CAPTION_DIR, name + ".json");
					try (FileWriter w = new FileWriter(out, StandardCharsets.UTF_8)) {
						w.write(caption);
					}
					BetterBuilding.getInstance().logInfo("[BB] caption " + name + ":\n" + caption);
					PlayerUtils.sendMessage(sender, "[BB] Caption for \"" + name + "\" -> " + out.getName() + ":");
					for (String line : trimForChat(caption)) {
						PlayerUtils.sendMessage(sender, line);
					}
				} catch (Exception e) {
					PlayerUtils.sendMessage(sender, "[BB] caption failed: " + e.getMessage());
					BetterBuilding.getInstance().logException("caption failed", e);
				}
			}, "BetterBuilding-Caption").start();

		} catch (Exception e) {
			PlayerUtils.sendMessage(sender, "[BB] caption failed: " + e.getMessage());
			BetterBuilding.getInstance().logException("caption failed", e);
		}
		return true;
	}

	/** Chat lines have a length limit; break the caption into chunks. */
	private static String[] trimForChat(String s) {
		String flat = s.replaceAll("\\s+", " ").trim();
		final int max = 180;
		int n = (flat.length() + max - 1) / max;
		String[] out = new String[Math.max(1, n)];
		for (int i = 0; i < out.length; i++) {
			int from = i * max;
			out[i] = flat.substring(from, Math.min(flat.length(), from + max));
		}
		return out;
	}

	private static String sanitize(String s, String fallback) {
		if (s == null) return fallback;
		String c = s.replaceAll("[^a-zA-Z0-9_\\- ]", "_").trim();
		return c.isEmpty() ? fallback : c;
	}

	@Override
	public void serverAction(@Nullable PlayerState sender, String[] args) {
	}

	@Override
	public StarMod getMod() {
		return BetterBuilding.getInstance();
	}
}
