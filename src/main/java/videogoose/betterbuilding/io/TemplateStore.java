package videogoose.betterbuilding.io;

import org.schema.game.client.controller.manager.ingame.CopyArea;
import videogoose.betterbuilding.content.TemplateSidecar;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Reads and writes BetterBuilding templates: a native StarMade {@code .smtpl}
 * block region (delegated to {@link CopyArea}, which preserves block type,
 * orientation, connections and inventory verbatim) plus a JSON sidecar carrying
 * the palette-independent slot metadata.
 */
public final class TemplateStore {

	private TemplateStore() {}

	/**
	 * Save a copy area and its sidecar into {@code dir} as {@code <name>.smtpl}
	 * and {@code <name>.json}.
	 *
	 * <p>{@link CopyArea#save(String)} only writes into the engine's
	 * {@code ./templates/} folder, so we save there and relocate the file — this
	 * keeps full-fidelity serialisation rather than re-implementing it.
	 *
	 * @return the written {@code .smtpl} file.
	 */
	public static File save(File dir, String name, CopyArea area, TemplateSidecar sidecar) throws IOException {
		dir.mkdirs();
		area.save(name);
		File engineFile = new File("./templates", name + ".smtpl");
		File target = new File(dir, name + ".smtpl");
		Files.move(engineFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
		if (sidecar != null) {
			sidecar.save(new File(dir, name + ".json"));
		}
		return target;
	}

	/** Load a {@code .smtpl} file into a fresh {@link CopyArea}. */
	public static CopyArea load(File smtpl) throws IOException {
		CopyArea area = new CopyArea();
		area.load(smtpl);
		return area;
	}

	/** Load the sidecar for a template file, or {@code null} if none exists. */
	public static TemplateSidecar loadSidecar(File smtpl) throws IOException {
		String n = smtpl.getName();
		if (n.endsWith(".smtpl")) n = n.substring(0, n.length() - ".smtpl".length());
		return TemplateSidecar.load(new File(smtpl.getParentFile(), n + ".json"));
	}
}
