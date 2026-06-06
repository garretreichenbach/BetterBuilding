package videogoose.betterbuilding.gen;

import org.schema.game.client.controller.manager.ingame.CopyArea;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Reads and writes BetterBuilding templates as native StarMade {@code .smtpl}
 * block regions. Serialization is delegated to the engine's {@link CopyArea},
 * which preserves block type, orientation, connections and inventory verbatim —
 * we deliberately do not hand-roll a segment codec.
 *
 * <p>{@link CopyArea#save(String)} only writes into the engine's
 * {@code ./templates/} folder, so we save there and relocate the file to keep
 * full-fidelity serialization. (Recovered from the project's earlier,
 * in-game-verified TemplateStore.)
 */
public final class TemplateStore {

	/**
	 * Engine's hardcoded save folder for CopyArea.save(name).
	 */
	private static final File ENGINE_TEMPLATE_DIR = new File("./templates");

	private TemplateStore() {
	}

	/**
	 * Save a {@link CopyArea} as {@code <name>.smtpl} into {@code dir}.
	 *
	 * @return the written {@code .smtpl} file.
	 */
	public static File save(File dir, String name, CopyArea area) throws IOException {
		dir.mkdirs();
		area.save(name);
		File engineFile = new File(ENGINE_TEMPLATE_DIR, name + ".smtpl");
		File target = new File(dir, name + ".smtpl");
		Files.move(engineFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
		return target;
	}

	/**
	 * Convenience: save a {@link VoxelTemplate} (uses its name).
	 */
	public static File save(File dir, VoxelTemplate template) throws IOException {
		return save(dir, template.getName(), template.toCopyArea());
	}

	/**
	 * Load a {@code .smtpl} file into a fresh {@link CopyArea}.
	 */
	public static CopyArea load(File smtpl) throws IOException {
		CopyArea area = new CopyArea();
		area.load(smtpl);
		return area;
	}
}
