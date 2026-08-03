package videogoose.betterbuilding.annotation;

import java.util.UUID;

import javax.vecmath.Vector4f;

/**
 * One in-world annotation: an anchored, persistent drawable owned by an entity.
 * <p>
 * A single flat type with a {@link AnnotationType} discriminator, rather than a class
 * hierarchy, so Gson serialisation needs no custom adapters and the renderer stays a
 * single switch.
 */
public class Annotation {

	public String id = UUID.randomUUID().toString();

	public AnnotationType type = AnnotationType.LABEL;

	/** Where this is pinned. Never null for a valid annotation. */
	public Anchor anchor;

	/** Second anchor, used by {@link AnnotationType#DIMENSION}. Null otherwise. */
	public Anchor anchorB;

	public String text = "";

	/** Layer name, for bulk visibility toggling. */
	public String layer = DEFAULT_LAYER;

	public static final String DEFAULT_LAYER = "default";

	/** Offset from the anchor to the label, in entity-local units. Used by leader labels. */
	public float offsetX;
	public float offsetY = 4;
	public float offsetZ;

	public float colorR = 1;
	public float colorG = 0.82f;
	public float colorB = 0.25f;
	public float colorA = 1;

	/** Beyond this distance from the camera the annotation is not drawn, in metres. */
	public float maxDistance = 150;

	public Visibility visibility = Visibility.ALWAYS;

	/**
	 * Set when the anchor block no longer exists. Orphans are kept and drawn dimmed rather
	 * than deleted; this is user-authored data and silently discarding it is worse than
	 * showing something stale.
	 */
	public boolean orphaned;

	/**
	 * True when this came from the entity's own per-block JSON metadata rather than local
	 * storage. Read-only from a client-only build, so the UI must not offer to edit it.
	 */
	public transient boolean baked;

	public enum Visibility {
		ALWAYS,
		BUILD_MODE_ONLY,
		HIDDEN
	}

	public Annotation() {
	}

	public static Annotation label(Anchor anchor, String text) {
		Annotation a = new Annotation();
		a.type = AnnotationType.LABEL;
		a.anchor = anchor;
		a.text = text;
		return a;
	}

	public static Annotation leaderLabel(Anchor anchor, String text) {
		Annotation a = new Annotation();
		a.type = AnnotationType.LEADER_LABEL;
		a.anchor = anchor;
		a.text = text;
		return a;
	}

	public static Annotation dimension(Anchor from, Anchor to) {
		Annotation a = new Annotation();
		a.type = AnnotationType.DIMENSION;
		a.anchor = from;
		a.anchorB = to;
		return a;
	}

	public Vector4f getColor(Vector4f out) {
		out.set(colorR, colorG, colorB, orphaned ? colorA * 0.4f : colorA);
		return out;
	}

	/** @return the entity this annotation belongs to, or null if it has no anchor. */
	public String getEntityKey() {
		return anchor == null ? null : anchor.entityKey;
	}

	@Override
	public String toString() {
		return "Annotation[" + type + " '" + text + "' " + anchor + "]";
	}
}
