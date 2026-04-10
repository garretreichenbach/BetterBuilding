package videogoose.betterbuilding.data.commands;

import api.common.GameClient;
import api.mod.StarMod;
import api.utils.game.PlayerUtils;
import api.utils.game.chat.CommandInterface;
import org.schema.game.client.controller.manager.ingame.BuildToolsManager;
import org.schema.game.client.controller.manager.ingame.CopyArea;
import org.schema.game.client.controller.manager.ingame.CopyPasteMode;
import org.schema.game.client.view.gui.advanced.AdvancedGUIElement;
import org.schema.game.client.view.gui.advanced.AdvancedGUIGroup;
import org.schema.game.client.view.gui.advancedbuildmode.AdvancedBuildMode;
import org.schema.game.client.view.gui.advancedbuildmode.AdvancedBuildModeSelection;
import org.schema.game.common.data.player.PlayerState;
import videogoose.betterbuilding.BetterBuilding;
import videogoose.betterbuilding.data.template.TemplateMetaData;
import videogoose.betterbuilding.data.wfc.WfcGenerator;
import videogoose.betterbuilding.data.wfc.WfcOverlappingGenerator;
import videogoose.betterbuilding.data.wfc.WfcPatternRuleset;
import videogoose.betterbuilding.data.wfc.WfcRuleset;

import javax.annotation.Nullable;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Chat command that generates a building template using Wave Function Collapse
 * over a corpus of existing example templates from ./templates/.
 *
 * Usage: /bb_wfc <sizeX> <sizeY> <sizeZ> [-seed N] [-templates pattern] [-restarts N]
 *
 * The result is saved as a new template named wfc_<timestamp>.smtpl, loaded
 * into the build-paste buffer, and shown in the advanced build mode list.
 */
public class GenerateWfcCommand implements CommandInterface {

	private static final int DEFAULT_RESTARTS = 10;
	private static final String MODE_OVERLAP = "overlap";
	private static final String MODE_BLOCK = "block";

	@Override
	public String getCommand() {
		return "bb_wfc";
	}

	@Override
	public String[] getAliases() {
		return new String[]{"/bb_wfc"};
	}

	@Override
	public String getDescription() {
		return "Generate a template via Wave Function Collapse from existing templates.\n" +
				"Usage: /bb_wfc <sizeX> <sizeY> <sizeZ> [-mode overlap|block] [-seed N] [-templates pattern] [-restarts N] [-minfreq N]\n" +
				"  -mode <m>:         'overlap' (2x2x2 patterns, default) or 'block' (1x1x1)\n" +
				"  -seed N:           RNG seed (default: current time)\n" +
				"  -templates <glob>: Only learn from templates matching this glob (default: *)\n" +
				"  -restarts N:       Max restarts on contradiction (default: " + DEFAULT_RESTARTS + ")\n" +
				"  -minfreq N:        Drop overlap patterns seen fewer than N times (default: 1)\n" +
				"Example: /bb_wfc 12 8 12 -templates \"*turret*\" -minfreq 2 -seed 42";
	}

	@Override
	public boolean isAdminOnly() {
		return false;
	}

	@Override
	public boolean onCommand(PlayerState sender, String[] args) {
		if(args.length < 3) {
			PlayerUtils.sendMessage(sender, "Usage: /bb_wfc <sizeX> <sizeY> <sizeZ> [-seed N] [-templates pattern] [-restarts N]");
			return true;
		}

		int sizeX, sizeY, sizeZ;
		try {
			sizeX = Integer.parseInt(args[0]);
			sizeY = Integer.parseInt(args[1]);
			sizeZ = Integer.parseInt(args[2]);
		} catch(NumberFormatException e) {
			PlayerUtils.sendMessage(sender, "First three arguments must be integer dimensions.");
			return true;
		}
		if(sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
			PlayerUtils.sendMessage(sender, "Dimensions must be positive.");
			return true;
		}

		long seed = System.currentTimeMillis();
		String templatePattern = "*";
		int restarts = DEFAULT_RESTARTS;
		String mode = MODE_OVERLAP;
		int minFreq = 1;

		for(int i = 3; i < args.length; i++) {
			if("-seed".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
				try { seed = Long.parseLong(args[++i]); }
				catch(NumberFormatException e) {
					PlayerUtils.sendMessage(sender, "Invalid -seed value.");
					return true;
				}
			} else if("-templates".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
				templatePattern = args[++i].replaceAll("^\"|\"$", "").trim();
			} else if("-restarts".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
				try { restarts = Integer.parseInt(args[++i]); }
				catch(NumberFormatException e) {
					PlayerUtils.sendMessage(sender, "Invalid -restarts value.");
					return true;
				}
			} else if("-mode".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
				String requested = args[++i].toLowerCase();
				if(!requested.equals(MODE_OVERLAP) && !requested.equals(MODE_BLOCK)) {
					PlayerUtils.sendMessage(sender, "Invalid -mode value (use 'overlap' or 'block').");
					return true;
				}
				mode = requested;
			} else if("-minfreq".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
				try { minFreq = Math.max(1, Integer.parseInt(args[++i])); }
				catch(NumberFormatException e) {
					PlayerUtils.sendMessage(sender, "Invalid -minfreq value.");
					return true;
				}
			}
		}

		final long finalSeed = seed;
		final String finalPattern = templatePattern;
		final int finalRestarts = restarts;
		final String finalMode = mode;
		final int finalMinFreq = minFreq;
		final int fx = sizeX, fy = sizeY, fz = sizeZ;

		PlayerUtils.sendMessage(sender, "Loading template corpus matching '" + finalPattern + "' ...");

		new Thread(() -> {
			try {
				List<TemplateMetaData> corpus = loadCorpus(finalPattern);
				if(corpus.isEmpty()) {
					PlayerUtils.sendMessage(sender, "No templates matched pattern '" + finalPattern + "'.");
					return;
				}
				PlayerUtils.sendMessage(sender, "Learning " + finalMode + " ruleset from " + corpus.size() + " template(s)...");

				String name = "wfc_" + System.currentTimeMillis();
				TemplateMetaData result;
				if(finalMode.equals(MODE_BLOCK)) {
					WfcRuleset ruleset = WfcRuleset.learn(corpus);
					BetterBuilding.getInstance().logInfo("WFC " + ruleset.toString());
					PlayerUtils.sendMessage(sender, "Running block-WFC over " + fx + "x" + fy + "x" + fz + "...");
					WfcGenerator generator = new WfcGenerator(ruleset, fx, fy, fz, finalSeed, finalRestarts);
					result = generator.generate(name);
				} else {
					WfcPatternRuleset ruleset = WfcPatternRuleset.learn(corpus, 2, finalMinFreq);
					BetterBuilding.getInstance().logInfo("WFC " + ruleset.toString());
					PlayerUtils.sendMessage(sender, "Running overlap-WFC (" + ruleset.patternCount() + " patterns) over " + fx + "x" + fy + "x" + fz + "...");
					WfcOverlappingGenerator generator = new WfcOverlappingGenerator(ruleset, fx, fy, fz, finalSeed, finalRestarts);
					result = generator.generate(name);
				}

				CopyArea copyArea = result.toRawTemplate();
				copyArea.save(result.getName());

				BuildToolsManager btm = getBuildToolsManager();
				btm.loadCopyArea(new File("./templates", result.getName() + ".smtpl"));
				btm.setCopyPasteMode(CopyPasteMode.PASTE);
				refreshAdvancedBuildModeTemplateList();

				PlayerUtils.sendMessage(sender, "Generated " + result.getName());
			} catch(Exception e) {
				PlayerUtils.sendMessage(sender, "WFC generation failed: " + e.getMessage());
				BetterBuilding.getInstance().logException("WFC generation failed", e);
			}
		}, "BetterBuilding-WFC").start();

		return true;
	}

	private List<TemplateMetaData> loadCorpus(String pattern) throws Exception {
		List<TemplateMetaData> templates = new ArrayList<>();
		List<String> matches = GenerateTemplateCommand.resolveWildcard(pattern);
		for(String match : matches) {
			File templateFile = new File("./templates", match + ".smtpl");
			if(!templateFile.exists()) continue;
			try {
				CopyArea area = new CopyArea();
				area.load(templateFile);
				templates.add(TemplateMetaData.fromRawTemplate(match, area));
			} catch(Exception e) {
				BetterBuilding.getInstance().logWarning("Failed to load " + match + ": " + e.getMessage());
			}
		}
		return templates;
	}

	@Override
	public void serverAction(@Nullable PlayerState sender, String[] args) {
	}

	@Override
	public StarMod getMod() {
		return BetterBuilding.getInstance();
	}

	private static BuildToolsManager getBuildToolsManager() {
		return GameClient.getClientState().getGlobalGameControlManager().getIngameControlManager().getPlayerGameControlManager().getPlayerIntercationManager().getBuildToolsManager();
	}

	@SuppressWarnings("unchecked")
	private static void refreshAdvancedBuildModeTemplateList() {
		try {
			AdvancedBuildMode abm = GameClient.getClientState().getWorldDrawer().getGuiDrawer().getPlayerPanel().advancedBuildMode;
			Field groupField = AdvancedGUIElement.class.getDeclaredField("group");
			groupField.setAccessible(true);
			List<AdvancedGUIGroup> groups = (List<AdvancedGUIGroup>) groupField.get(abm);
			for(AdvancedGUIGroup group : groups) {
				if(group instanceof AdvancedBuildModeSelection) {
					Field dirtyField = AdvancedBuildModeSelection.class.getDeclaredField("dirty");
					dirtyField.setAccessible(true);
					dirtyField.setBoolean(group, true);
					break;
				}
			}
		} catch(Exception e) {
			BetterBuilding.getInstance().logWarning("Failed to refresh template list: " + e.getMessage());
		}
	}
}
