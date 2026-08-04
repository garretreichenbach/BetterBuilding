package videogoose.betterbuilding.io;

import java.util.ArrayList;
import java.util.List;

import videogoose.betterbuilding.annotation.Annotation;

/**
 * On-disk shape of an exported annotation set.
 * <p>
 * Deliberately portable: it must apply to a <i>copy</i> of a ship, not just the instance it
 * was exported from. That rules out two things.
 * <ul>
 * <li>No world coordinates anywhere. Anchors carry core-relative block coordinates, which
 *     stay correct however the entity is positioned or rotated.
 * <li>No dependence on the source entity's UID. {@link #sourceEntity} is a label for
 *     humans; import re-targets every anchor to whatever entity the player is on.
 * </ul>
 * Core-relative also means the file survives a {@code Chunk32} mismatch between installs,
 * which raw absolute block coordinates would not.
 */
public class AnnotationFile {

	public static final String FORMAT = "betterbuilding.annotations";
	public static final int FORMAT_VERSION = 1;

	public String format = FORMAT;
	public int formatVersion = FORMAT_VERSION;

	/** Informational only. Never used to match or resolve an entity on import. */
	public String sourceEntity;

	/** Core-relative extent of the source entity's blocks, for a soft sanity check. */
	public float[] sourceBounds;

	/** Layers present in this file, so an importer can report them before applying. */
	public List<String> layers = new ArrayList<String>();

	public List<Annotation> annotations = new ArrayList<Annotation>();

	/** @return null if this looks like a valid file of a version we understand, else why not. */
	public String validate() {
		if(!FORMAT.equals(format)) {
			return "not a BetterBuilding annotation file";
		}
		if(formatVersion > FORMAT_VERSION) {
			return "file is version " + formatVersion + ", this mod understands up to " + FORMAT_VERSION;
		}
		if(annotations == null || annotations.isEmpty()) {
			return "file contains no annotations";
		}
		return null;
	}
}
