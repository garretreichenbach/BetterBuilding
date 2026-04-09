package videogoose.betterbuilding.data.commands;

import api.mod.StarMod;
import api.utils.game.PlayerUtils;
import api.utils.game.chat.CommandInterface;
import org.schema.game.common.data.player.PlayerState;
import videogoose.betterbuilding.BetterBuilding;
import videogoose.betterbuilding.data.template.TrainingDataExporter;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Chat command that exports templates to a JSONL file for fine-tuning an LLM.
 * Usage: /bb_export_training [template_patterns...] [-o output_filename]
 * Supports wildcards: *fighter*, engine_*, etc.
 * Defaults to all templates (*) if no patterns are given.
 */
public class ExportTrainingDataCommand implements CommandInterface {

	@Override
	public String getCommand() {
		return "bb_export_training";
	}

	@Override
	public String[] getAliases() {
		return new String[]{"/bb_export_training"};
	}

	@Override
	public String getDescription() {
		return "Exports templates as JSONL training data for AI fine-tuning.\n" +
				"Usage: /bb_export_training [template_patterns...] [-o output_filename]\n" +
				"Supports wildcards: *fighter*, engine_*, small_?_ship\n" +
				"Examples:\n" +
				"  /bb_export_training                    - exports all templates\n" +
				"  /bb_export_training \"*fighter*\" \"*corvette*\"  - exports matching templates\n" +
				"  /bb_export_training \"*\" -o ships.jsonl - exports all to ships.jsonl";
	}

	@Override
	public boolean isAdminOnly() {
		return false;
	}

	@Override
	public boolean onCommand(PlayerState sender, String[] args) {
		List<String> patterns = new ArrayList<>();
		String outputName = "training_data.jsonl";

		// Parse args: template patterns and optional -o flag
		for(int i = 0; i < args.length; i++) {
			if("-o".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
				outputName = args[++i].replaceAll("^\"|\"$", "").trim();
			} else {
				// Parse as template pattern(s), stripping quotes and splitting on commas
				String arg = args[i].replaceAll("^\"|\"$", "").trim();
				String[] split = arg.split(",");
				for(String s : split) {
					String trimmed = s.replaceAll("^\"|\"$", "").trim();
					if(!trimmed.isEmpty()) patterns.add(trimmed);
				}
			}
		}

		// Default to all templates if no patterns given
		if(patterns.isEmpty()) patterns.add("*");

		if(!outputName.endsWith(".jsonl")) outputName += ".jsonl";

		File templateDir = new File("./templates");
		File outputFile = new File("./" + outputName);

		if(!templateDir.isDirectory()) {
			PlayerUtils.sendMessage(sender, "No templates directory found.");
			return true;
		}

		PlayerUtils.sendMessage(sender, "Exporting training data for patterns: " + patterns + " ...");

		final String finalOutputName = outputName;
		final List<String> finalPatterns = patterns;
		new Thread(() -> {
			try {
				int count = TrainingDataExporter.exportAll(templateDir, outputFile, finalPatterns);
				PlayerUtils.sendMessage(sender, "Exported " + count + " templates to " + finalOutputName);
			} catch(Exception e) {
				PlayerUtils.sendMessage(sender, "Export failed: " + e.getMessage());
				BetterBuilding.getInstance().logException("Training data export failed", e);
			}
		}, "BetterBuilding-ExportTraining").start();

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
