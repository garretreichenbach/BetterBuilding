package videogoose.betterbuilding.data.commands;

import api.common.GameClient;
import api.mod.StarMod;
import api.utils.game.PlayerUtils;
import api.utils.game.chat.CommandInterface;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.controller.manager.ingame.BuildToolsManager;
import org.schema.game.client.controller.manager.ingame.CopyArea;
import org.schema.game.client.controller.manager.ingame.CopyPasteMode;
import org.schema.game.common.data.player.PlayerState;
import videogoose.betterbuilding.BetterBuilding;
import videogoose.betterbuilding.data.template.TemplateGenerator;
import videogoose.betterbuilding.data.template.TemplateMetaData;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Chat command that generates a building template using AI via LM Studio.
 * Usage: /bb_generate <description> [template_refs...]
 * The description is the first argument. Any additional quoted template names
 * are loaded as style references for the AI.
 */
public class GenerateTemplateCommand implements CommandInterface {

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
		return "Generates a building template using AI based on your description and the size of your current selection.\n" +
				"Usage: /bb_generate <description> [\"ref_template_1\",\"ref_template_2\",...]\n" +
				"Example: /bb_generate \"small fighter ship\" \"my_corvette\",\"scout_ship\"";
	}

	@Override
	public boolean isAdminOnly() {
		return false;
	}

	@Override
	public boolean onCommand(PlayerState sender, String[] args) {
		try {
			if(args.length < 1) {
				PlayerUtils.sendMessage(sender, "Usage: /bb_generate <description> [\"ref1\",\"ref2\",...]");
				return true;
			}

			Vector3i size = getBuildToolsManager().getSize();
			if(size.x > TemplateGenerator.DEFAULT_MAX_DIM || size.y > TemplateGenerator.DEFAULT_MAX_DIM || size.z > TemplateGenerator.DEFAULT_MAX_DIM) {
				PlayerUtils.sendMessage(sender, "Selection size exceeds maximum allowed dimensions of " +
						TemplateGenerator.DEFAULT_MAX_DIM + "x" + TemplateGenerator.DEFAULT_MAX_DIM + "x" + TemplateGenerator.DEFAULT_MAX_DIM + ".");
				return true;
			}

			String description = args[0].replaceAll("^\"|\"$", "").trim();
			List<TemplateMetaData> references = new ArrayList<>();
			if(args.length > 1) {
				references.addAll(getTemplates(parseNames(args[1])));
			}
			int[] outputDims = {size.x, size.y, size.z};

			PlayerUtils.sendMessage(sender, "Generating template via AI... This may take a moment.");

			new Thread(() -> {
				try {
					TemplateMetaData generated = TemplateGenerator.generate(references, outputDims, description);
					CopyArea copyArea = generated.toRawTemplate();
					copyArea.save(generated.getName());
					BuildToolsManager btm = getBuildToolsManager();
					btm.loadCopyArea(new File("./templates", generated.getName() + ".smtpl"));
					btm.setCopyPasteMode(CopyPasteMode.PASTE);
					PlayerUtils.sendMessage(sender, "Template generated: " + generated.getName());
				} catch(Exception exception) {
					PlayerUtils.sendMessage(sender, "Template generation failed: " + exception.getMessage());
					BetterBuilding.getInstance().logException("Template generation failed", exception);
				}
			}, "BetterBuilding-Generate").start();

		} catch(Exception exception) {
			PlayerUtils.sendMessage(sender, "Template generation failed: " + exception.getMessage());
			BetterBuilding.getInstance().logException("Template generation failed", exception);
		}
		return true;
	}

	private List<TemplateMetaData> getTemplates(List<String> templateNames) throws Exception {
		File templatesFolder = new File("./templates");
		List<TemplateMetaData> templates = new ArrayList<>();
		for(String templateName : templateNames) {
			File templateFile = new File(templatesFolder, templateName + ".smtpl");
			if(templateFile.exists()) {
				getBuildToolsManager().loadCopyArea(templateName);
				CopyArea area = getBuildToolsManager().getCopyArea();
				if(area != null) {
					templates.add(TemplateMetaData.fromRawTemplate(templateName, area));
				}
			}
		}
		return templates;
	}

	private List<String> parseNames(String arg) {
		arg = arg.trim();
		if(arg.startsWith("\"") && arg.endsWith("\"")) {
			arg = arg.substring(1, arg.length() - 1);
		}
		String[] namesArray = arg.split("\",\"");
		for(int i = 0; i < namesArray.length; i++) {
			namesArray[i] = namesArray[i].replaceAll("^\"|\"$", "").trim();
		}
		return Arrays.asList(namesArray);
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
}
