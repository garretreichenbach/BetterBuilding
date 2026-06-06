package videogoose.betterbuilding.command;

import api.common.GameClient;
import api.mod.StarMod;
import api.utils.game.PlayerUtils;
import api.utils.game.chat.CommandInterface;
import org.schema.game.client.controller.manager.ingame.BuildToolsManager;
import org.schema.game.client.controller.manager.ingame.CopyPasteMode;
import org.schema.game.common.data.player.PlayerState;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import videogoose.betterbuilding.BetterBuilding;
import videogoose.betterbuilding.ai.AIConfig;
import videogoose.betterbuilding.gen.BlockPalette;
import videogoose.betterbuilding.gen.TemplateGenerator;
import videogoose.betterbuilding.gen.TemplateStore;
import videogoose.betterbuilding.gen.VoxelTemplate;

/**
 * Phase-2 (v1): generate a ship from a description. The LLM writes a Lua program
 * that the sandboxed {@link videogoose.betterbuilding.gen.LuaExecutor} runs into
 * a {@link VoxelTemplate}; the result is saved and loaded into PASTE mode.
 *
 * <p>Palette = your hotbar blocks. Dimensions are AI-chosen unless you append
 * {@code WxHxL}. Usage: {@code /bb_generate <description> [WxHxL]}
 */
public class GenerateCommand implements CommandInterface {

	private static final File GEN_DIR = new File("./BetterBuilding/generated");
	private static final Pattern DIMS = Pattern.compile("^(\\d+)x(\\d+)x(\\d+)$", Pattern.CASE_INSENSITIVE);

	@Override
	public String getCommand() {
		return "bb_generate";
	}

	@Override
	public String[] getAliases() {
		return new String[] {"/bb_generate"};
	}

	@Override
	public String getDescription() {
		return "Generate a ship from a description (LLM writes Lua -> voxels).\n" +
				"Palette = your hotbar blocks (put building blocks in your hotbar first).\n" +
				"Dimensions are AI-chosen unless you append WxHxL.\n" +
				"Usage: /bb_generate <description> [WxHxL]";
	}

	@Override
	public boolean isAdminOnly() {
		return false;
	}

	@Override
	public boolean onCommand(PlayerState sender, String[] args) {
		try {
			if (args.length == 0) {
				PlayerUtils.sendMessage(sender, "[BB] Usage: /bb_generate <description> [WxHxL]");
				return true;
			}

			// Optional trailing WxHxL.
			int[] dims = null;
			int end = args.length;
			Matcher m = DIMS.matcher(args[args.length - 1]);
			if (m.matches()) {
				dims = new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))};
				end = args.length - 1;
			}

			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < end; i++) {
				if (sb.length() > 0) sb.append(' ');
				sb.append(args[i]);
			}
			final String description = sb.toString().replaceAll("^\"|\"$", "").trim();
			if (description.isEmpty()) {
				PlayerUtils.sendMessage(sender, "[BB] Provide a description.");
				return true;
			}

			final Map<String, Short> palette = BlockPalette.fromHotbar();
			if (palette.isEmpty()) {
				PlayerUtils.sendMessage(sender, "[BB] Your hotbar is empty — put building blocks in it; they become the palette.");
				return true;
			}

			final int[] finalDims = dims;
			PlayerUtils.sendMessage(sender, "[BB] Generating \"" + description + "\" with " + palette.size()
					+ " palette block(s)...");

			new Thread(() -> {
				try {
					TemplateGenerator.StatusCallback cb = msg -> PlayerUtils.sendMessage(sender, "[BB] " + msg);
					VoxelTemplate t = TemplateGenerator.generate(AIConfig.load(), description, finalDims, palette, cb);

					File f = TemplateStore.save(GEN_DIR, t);
					BetterBuilding.getInstance().logInfo("[BB] generated -> " + f.getAbsolutePath()
							+ " (" + t.blockCount() + " blocks)");

					BuildToolsManager btm = GameClient.getPICM().getBuildToolsManager();
					btm.loadCopyArea(f);
					btm.setCopyPasteMode(CopyPasteMode.PASTE);

					PlayerUtils.sendMessage(sender, "[BB] Done: " + t.getName() + " (" + t.blockCount() + " blocks, "
							+ t.dimX() + "x" + t.dimY() + "x" + t.dimZ() + "). Loaded into PASTE mode.");
				} catch (Exception e) {
					PlayerUtils.sendMessage(sender, "[BB] generate failed: " + e.getMessage());
					BetterBuilding.getInstance().logException("generate failed", e);
				}
			}, "BetterBuilding-Generate").start();

		} catch (Exception e) {
			PlayerUtils.sendMessage(sender, "[BB] generate failed: " + e.getMessage());
			BetterBuilding.getInstance().logException("generate failed", e);
		}
		return true;
	}

	@Override
	public void serverAction(@Nullable PlayerState sender, String[] args) {
	}

	@Override
	public StarMod getMod() {
		return BetterBuilding.getInstance();
	}
}
