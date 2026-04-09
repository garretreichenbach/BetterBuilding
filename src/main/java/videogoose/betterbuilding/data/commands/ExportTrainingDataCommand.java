package videogoose.betterbuilding.data.commands;

import api.mod.StarMod;
import api.utils.game.PlayerUtils;
import api.utils.game.chat.CommandInterface;
import org.schema.game.common.data.player.PlayerState;
import videogoose.betterbuilding.BetterBuilding;
import videogoose.betterbuilding.data.template.BlueprintReader;
import videogoose.betterbuilding.data.template.TrainingDataExporter;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Chat command that exports templates and/or blueprints to a JSONL file for fine-tuning.
 * Usage: /bb_export_training [patterns...] [-blueprints] [-o output_filename]
 * -blueprints: Scan full blueprints instead of templates
 * Supports wildcards: *fighter*, engine_*, etc.
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
		return "Exports templates/blueprints as JSONL training data for AI fine-tuning.\n" +
				"Usage: /bb_export_training [patterns...] [-blueprints] [-o output_filename]\n" +
				"Flags:\n" +
				"  -blueprints: Scan full blueprints instead of templates\n" +
				"  -o <file>:   Output filename (default: training_data.jsonl)\n" +
				"Examples:\n" +
				"  /bb_export_training                         - exports all templates\n" +
				"  /bb_export_training -blueprints              - exports all blueprints\n" +
				"  /bb_export_training -blueprints \"*fighter*\"  - exports matching blueprints\n" +
				"  /bb_export_training -blueprints -o bp.jsonl  - custom output file";
	}

	@Override
	public boolean isAdminOnly() {
		return false;
	}

	@Override
	public boolean onCommand(PlayerState sender, String[] args) {
		List<String> patterns = new ArrayList<>();
		String outputName = "training_data.jsonl";
		boolean useBlueprints = false;

		for(int i = 0; i < args.length; i++) {
			if("-o".equalsIgnoreCase(args[i]) && i + 1 < args.length) {
				outputName = args[++i].replaceAll("^\"|\"$", "").trim();
			} else if("-blueprints".equalsIgnoreCase(args[i]) || "-bp".equalsIgnoreCase(args[i])) {
				useBlueprints = true;
			} else {
				String arg = args[i].replaceAll("^\"|\"$", "").trim();
				String[] split = arg.split(",");
				for(String s : split) {
					String trimmed = s.replaceAll("^\"|\"$", "").trim();
					if(!trimmed.isEmpty()) patterns.add(trimmed);
				}
			}
		}

		if(patterns.isEmpty()) patterns.add("*");
		if(!outputName.endsWith(".jsonl")) outputName += ".jsonl";

		File outputFile = new File("./" + outputName);
		final String finalOutputName = outputName;
		final List<String> finalPatterns = patterns;
		final boolean finalUseBlueprints = useBlueprints;

		if(useBlueprints) {
			File blueprintDir = new File("./blueprints");
			if(!blueprintDir.isDirectory()) {
				PlayerUtils.sendMessage(sender, "No blueprints directory found.");
				return true;
			}

			// Count matching blueprints first
			int matchCount = 0;
			for(String pattern : patterns) {
				matchCount += BlueprintReader.resolveWildcard(blueprintDir, pattern).size();
			}
			PlayerUtils.sendMessage(sender, "Exporting training data from " + matchCount +
					" blueprint(s) for patterns: " + patterns + " ...");

			new Thread(() -> {
				try {
					int count = TrainingDataExporter.exportBlueprints(blueprintDir, outputFile, finalPatterns);
					PlayerUtils.sendMessage(sender, "Exported " + count + " blueprints to " + finalOutputName);
				} catch(Exception e) {
					PlayerUtils.sendMessage(sender, "Export failed: " + e.getMessage());
					BetterBuilding.getInstance().logException("Blueprint export failed", e);
				}
			}, "BetterBuilding-ExportTraining").start();
		} else {
			File templateDir = new File("./templates");
			if(!templateDir.isDirectory()) {
				PlayerUtils.sendMessage(sender, "No templates directory found.");
				return true;
			}

			PlayerUtils.sendMessage(sender, "Exporting training data for patterns: " + patterns + " ...");

			new Thread(() -> {
				try {
					int count = TrainingDataExporter.exportAll(templateDir, outputFile, finalPatterns);
					PlayerUtils.sendMessage(sender, "Exported " + count + " templates to " + finalOutputName);
				} catch(Exception e) {
					PlayerUtils.sendMessage(sender, "Export failed: " + e.getMessage());
					BetterBuilding.getInstance().logException("Training data export failed", e);
				}
			}, "BetterBuilding-ExportTraining").start();
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
