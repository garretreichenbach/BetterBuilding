package videogoose.betterbuilding.io;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.data.world.SegmentData;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import api.mod.StarMod;
import videogoose.betterbuilding.annotation.Anchor;
import videogoose.betterbuilding.annotation.Annotation;
import videogoose.betterbuilding.annotation.AnnotationStore;

/** Reads and writes annotation sets as portable JSON. */
public class AnnotationIO {

	private static final String EXTENSION = ".json";
	private static final String UTF8 = "UTF-8";

	private final StarMod mod;
	private final AnnotationStore store;

	public AnnotationIO(StarMod mod, AnnotationStore store) {
		this.mod = mod;
		this.store = store;
	}

	private static Gson gson() {
		return new GsonBuilder().setPrettyPrinting().create();
	}

	/** {@code moddata/BetterBuilding/annotations}, created on demand. */
	public File getDirectory() {
		File dir = new File(mod.getSkeleton().getResourcesFolder(), "annotations");
		if(!dir.exists()) {
			dir.mkdirs();
		}
		return dir;
	}

	/** @return export files present, newest first. */
	public List<File> listFiles() {
		List<File> found = new ArrayList<File>();
		File[] files = getDirectory().listFiles();
		if(files == null) {
			return found;
		}
		for(File f : files) {
			if(f.isFile() && f.getName().toLowerCase().endsWith(EXTENSION)) {
				found.add(f);
			}
		}
		//newest first: the file you just wrote is the one you are most likely to want
		java.util.Collections.sort(found, new java.util.Comparator<File>() {
			@Override
			public int compare(File a, File b) {
				return Long.compare(b.lastModified(), a.lastModified());
			}
		});
		return found;
	}

	// ---- export ----

	/**
	 * Builds the file contents for an entity.
	 *
	 * @param layer only annotations in this layer, or null for all of them
	 */
	public AnnotationFile buildFile(SegmentController entity, String layer) {
		AnnotationFile file = new AnnotationFile();
		file.sourceEntity = entity.getRealName() != null ? entity.getRealName() : entity.getUniqueIdentifier();
		file.sourceBounds = coreRelativeBounds(entity);

		TreeSet<String> layers = new TreeSet<String>();
		Gson gson = gson();
		for(Annotation a : store.forEntity(entity.getUniqueIdentifier())) {
			if(layer != null && !layer.equals(a.layer)) {
				continue;
			}
			//deep copy so clearing the entity key below cannot touch the live store
			Annotation copy = gson.fromJson(gson.toJson(a), Annotation.class);
			stripEntityKey(copy);
			file.annotations.add(copy);
			if(a.layer != null) {
				layers.add(a.layer);
			}
		}
		file.layers.addAll(layers);
		return file;
	}

	/**
	 * Removes the source entity's UID from the exported anchors.
	 * <p>
	 * Import re-targets anchors anyway, so leaving it would be harmless but misleading: the
	 * format is documented as carrying no UID dependence, and a file that visibly contains
	 * one invites someone to start relying on it.
	 */
	private void stripEntityKey(Annotation a) {
		if(a.anchor != null) {
			a.anchor.entityKey = null;
		}
		if(a.anchorB != null) {
			a.anchorB.entityKey = null;
		}
	}

	public String toJson(AnnotationFile file) {
		return gson().toJson(file);
	}

	/**
	 * @param name file name without extension; sanitised
	 * @return the file written
	 */
	public File export(SegmentController entity, String layer, String name) throws IOException {
		File out = new File(getDirectory(), sanitise(name) + EXTENSION);
		Writer w = null;
		try {
			w = new OutputStreamWriter(new FileOutputStream(out), UTF8);
			w.write(toJson(buildFile(entity, layer)));
		} finally {
			close(w);
		}
		return out;
	}

	// ---- import ----

	public AnnotationFile read(File file) throws IOException {
		Reader r = null;
		try {
			r = new InputStreamReader(new FileInputStream(file), UTF8);
			return gson().fromJson(r, AnnotationFile.class);
		} finally {
			close(r);
		}
	}

	public AnnotationFile parse(String json) {
		return gson().fromJson(json, AnnotationFile.class);
	}

	/**
	 * Applies a file to an entity, re-targeting every anchor to it.
	 *
	 * @return a human-readable summary of what happened
	 */
	public String apply(AnnotationFile file, SegmentController entity, ImportMode mode) {
		String problem = file.validate();
		if(problem != null) {
			return "cannot import: " + problem;
		}
		String entityKey = entity.getUniqueIdentifier();
		if(entityKey == null) {
			return "cannot import: entity not ready";
		}

		if(mode == ImportMode.REPLACE) {
			store.removeAll(new ArrayList<Annotation>(store.forEntity(entityKey)));
		}

		Set<String> existingIds = new HashSet<String>();
		for(Annotation existing : store.forEntity(entityKey)) {
			existingIds.add(existing.id);
		}

		List<Annotation> toAdd = new ArrayList<Annotation>();
		int skipped = 0;
		int dropped = 0;
		for(Annotation a : file.annotations) {
			if(a.anchor == null) {
				dropped++;
				continue;
			}
			if(mode == ImportMode.MERGE && a.id != null && existingIds.contains(a.id)) {
				skipped++;
				continue;
			}
			if(mode == ImportMode.APPEND || a.id == null) {
				a.id = UUID.randomUUID().toString();
			}
			retarget(a, entityKey);
			toAdd.add(a);
			existingIds.add(a.id);
		}
		store.addAll(toAdd);

		StringBuilder summary = new StringBuilder();
		summary.append("imported ").append(toAdd.size());
		if(skipped > 0) {
			summary.append(", skipped ").append(skipped).append(" already present");
		}
		if(dropped > 0) {
			summary.append(", dropped ").append(dropped).append(" with no anchor");
		}
		String warning = boundsWarning(file, entity);
		if(warning != null) {
			summary.append(" - ").append(warning);
		}
		return summary.toString();
	}

	/**
	 * Points every anchor at the target entity. Coordinates are untouched: they are
	 * core-relative, so they already mean the same place on any copy of the ship.
	 */
	private void retarget(Annotation a, String entityKey) {
		if(a.anchor != null) {
			a.anchor.entityKey = entityKey;
		}
		if(a.anchorB != null) {
			a.anchorB.entityKey = entityKey;
		}
		//the source's orphan verdict says nothing about this entity's blocks
		a.orphaned = false;
	}

	/**
	 * Soft check that the file plausibly belongs to this ship. Warns rather than blocks:
	 * annotations outside the hull are legitimate (clearance volumes, centrelines), and
	 * the user may be importing onto a work in progress that has not been built out yet.
	 */
	private String boundsWarning(AnnotationFile file, SegmentController entity) {
		if(file.sourceBounds == null || file.sourceBounds.length != 6) {
			return null;
		}
		float[] target = coreRelativeBounds(entity);
		if(target == null) {
			return null;
		}
		for(int axis = 0; axis < 3; axis++) {
			if(file.sourceBounds[axis] < target[axis] - TOLERANCE
					|| file.sourceBounds[axis + 3] > target[axis + 3] + TOLERANCE) {
				return "note: this file was made for a larger entity, some annotations may sit outside the hull";
			}
		}
		return null;
	}

	private static final float TOLERANCE = 0.5f;

	/**
	 * The entity's block extent in core-relative coordinates. Min/max pos are in segment
	 * units, hence the multiply, and the shift matches how anchors are stored.
	 */
	private float[] coreRelativeBounds(SegmentController entity) {
		if(entity.getMinPos() == null || entity.getMaxPos() == null) {
			return null;
		}
		int seg = SegmentData.SEG;
		int half = SegmentData.SEG_HALF;
		return new float[] {
				entity.getMinPos().x * seg - half,
				entity.getMinPos().y * seg - half,
				entity.getMinPos().z * seg - half,
				entity.getMaxPos().x * seg - half,
				entity.getMaxPos().y * seg - half,
				entity.getMaxPos().z * seg - half
		};
	}

	private static String sanitise(String name) {
		String cleaned = name == null ? "" : name.trim().replaceAll("[^A-Za-z0-9 _.-]", "_");
		return cleaned.isEmpty() ? "annotations" : cleaned;
	}

	private static void close(java.io.Closeable c) {
		if(c != null) {
			try {
				c.close();
			} catch(IOException ignored) {
			}
		}
	}
}
