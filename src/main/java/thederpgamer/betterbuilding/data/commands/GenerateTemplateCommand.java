package thederpgamer.betterbuilding.data.commands;

import api.common.GameClient;
import api.mod.StarMod;
import api.utils.game.PlayerUtils;
import api.utils.game.chat.CommandInterface;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.controller.manager.ingame.BuildToolsManager;
import org.schema.game.client.controller.manager.ingame.CopyArea;
import org.schema.game.common.data.player.PlayerState;
import thederpgamer.betterbuilding.BetterBuilding;
import thederpgamer.betterbuilding.data.template.TemplateGenerator;
import thederpgamer.betterbuilding.data.template.TemplateMetaData;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author VideoGoose (TheDerpGamer)
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
		return "Generates a building template using the provided template names and the size of the player's build current selection.\n" +
				"Usage: /bb_generate <\"template_1\",\"template_2\",...>";
	}

	@Override
	public boolean isAdminOnly() {
		return false;
	}

	@Override
	public boolean onCommand(PlayerState sender, String[] args) {
		try {
			Vector3i size = getBuildToolsManager().getSize();
			if(size.x <= TemplateGenerator.DEFAULT_MAX_DIM && size.y <= TemplateGenerator.DEFAULT_MAX_DIM && size.z <= TemplateGenerator.DEFAULT_MAX_DIM) {
				List<String> templateNames = parseNames(args[0]);
				List<TemplateMetaData> templates = getTemplates(templateNames);
				if(templates.isEmpty()) {
					PlayerUtils.sendMessage(sender, "No valid templates found with the provided names.");
					return true;
				}
				TemplateMetaData generated = TemplateGenerator.generate(templates, new int[] {size.x, size.y, size.z}, new TemplateGenerator.GenerationOptions());
				PlayerUtils.sendMessage(sender, "Template generated successfully with name: " + generated.getName());
				CopyArea copyArea = generated.toRawTemplate();
				copyArea.save(generated.getName());
				getBuildToolsManager().loadCopyArea(new File("./templates", generated.getName() + ".smtpl"));
			} else {
				PlayerUtils.sendMessage(sender, "Template generation failed: Selection size exceeds maximum allowed dimensions of " + TemplateGenerator.DEFAULT_MAX_DIM + "x" + TemplateGenerator.DEFAULT_MAX_DIM + "x" + TemplateGenerator.DEFAULT_MAX_DIM + ".");
			}
		} catch(Exception exception) {
			PlayerUtils.sendMessage(sender, "An error occurred while generating the template: " + exception.getMessage());
			exception.printStackTrace();
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
					TemplateMetaData templateMetaData = TemplateMetaData.fromRawTemplate(templateName, area);
					templates.add(templateMetaData);
				} else {
					throw new Exception("Failed to load copy area for template: " + templateName);
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
