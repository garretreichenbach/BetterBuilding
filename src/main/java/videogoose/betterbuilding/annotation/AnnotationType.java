package videogoose.betterbuilding.annotation;

/**
 * The kind of drawable an annotation is. Kept as a discriminator on a single flat
 * {@link Annotation} type rather than a class hierarchy, so serialisation stays simple
 * and the renderer stays a single switch.
 */
public enum AnnotationType {
	/** Text pinned directly at the anchor. */
	LABEL,
	/** Text offset from the anchor, with a line drawn back to it. */
	LEADER_LABEL,
	/** Two anchors, a line between them, and a live length readout. */
	DIMENSION
}
