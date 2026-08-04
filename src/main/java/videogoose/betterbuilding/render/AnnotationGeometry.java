package videogoose.betterbuilding.render;

import javax.vecmath.Vector3f;

import org.schema.game.common.controller.SegmentController;

import com.bulletphysics.linearmath.Transform;

import videogoose.betterbuilding.annotation.Annotation;

/**
 * Shared position maths for annotations, so the world-pass geometry and the GUI-pass label
 * agree on where things are. If these diverged, a leader line would point somewhere its
 * own text is not.
 */
public final class AnnotationGeometry {

	private AnnotationGeometry() {
	}

	/**
	 * Where the text for an annotation sits in world space.
	 * <ul>
	 * <li>{@code LABEL} - at the anchor.
	 * <li>{@code LEADER_LABEL} - at the anchor plus the offset, rotated into entity space
	 *     so the label keeps its position relative to the ship as the ship turns.
	 * <li>{@code DIMENSION} - midway between the two anchors.
	 * </ul>
	 *
	 * @param anchorWorld the already-resolved world position of {@code a.anchor}
	 * @return {@code out}
	 */
	public static Vector3f labelPosition(SegmentController c, Annotation a, Vector3f anchorWorld, Vector3f out) {
		switch(a.type) {
			case LEADER_LABEL:
				out.set(a.offsetX, a.offsetY, a.offsetZ);
				Transform t = c.getWorldTransformOnClient();
				if(t != null) {
					t.basis.transform(out);
				}
				out.add(anchorWorld);
				return out;
			case DIMENSION:
				if(a.anchorB != null && a.anchorB.toWorld(c, out) != null) {
					out.add(anchorWorld);
					out.scale(0.5f);
					return out;
				}
				out.set(anchorWorld);
				return out;
			case LABEL:
			default:
				out.set(anchorWorld);
				return out;
		}
	}

	/**
	 * Centre-to-centre distance between a dimension's two anchors, in blocks. Anchors are
	 * stored in block units and a block is one world unit ({@code Element.BLOCK_SIZE == 1}),
	 * so this is both the block distance and the length of the drawn line, and is
	 * unaffected by the entity's transform.
	 *
	 * @return the distance, or -1 if this is not a two-anchor annotation
	 */
	public static float measureBlocks(Annotation a) {
		if(a.anchor == null || a.anchorB == null) {
			return -1;
		}
		float dx = a.anchorB.x - a.anchor.x;
		float dy = a.anchorB.y - a.anchor.y;
		float dz = a.anchorB.z - a.anchor.z;
		return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** True when the two anchors differ along at most one axis. */
	public static boolean isAxisAligned(Annotation a) {
		if(a.anchor == null || a.anchorB == null) {
			return false;
		}
		int differing = 0;
		if(differs(a.anchor.x, a.anchorB.x)) differing++;
		if(differs(a.anchor.y, a.anchorB.y)) differing++;
		if(differs(a.anchor.z, a.anchorB.z)) differing++;
		return differing <= 1;
	}

	/**
	 * Number of blocks an axis-aligned dimension covers, counting both end blocks.
	 * <p>
	 * This is deliberately not the same as {@link #measureBlocks}: picking the blocks at
	 * x=0 and x=4 gives a centre-to-centre distance of 4, but covers 5 blocks. When a
	 * builder measures a hull run they are asking how many blocks it takes to build, so
	 * the inclusive count is the useful answer. Only well defined when the anchors are
	 * axis-aligned, which is why a diagonal measurement falls back to reporting distance.
	 *
	 * @return the inclusive block count, or -1 if not applicable
	 */
	public static int spanBlocks(Annotation a) {
		if(!isAxisAligned(a)) {
			return -1;
		}
		return Math.round(measureBlocks(a)) + 1;
	}

	private static boolean differs(float a, float b) {
		return Math.abs(a - b) > 0.001f;
	}
}
