package videogoose.betterbuilding.annotation;

import javax.vecmath.Vector3f;

import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.data.world.SegmentData;

import com.bulletphysics.linearmath.Transform;

/**
 * Where an annotation is pinned.
 * <p>
 * Stored in <b>core-relative block space</b>: the block coordinate minus
 * {@code SegmentData.SEG_HALF}, which places the origin at the entity core. Never world
 * space, so an annotation survives the entity moving, rotating, and docking; and never raw
 * absolute block coordinates, because {@code SEG_HALF} is 16 or 8 depending on the
 * {@code Chunk32} build flag, which would make exported coordinates mean different things
 * on different installs.
 * <p>
 * Block coordinates themselves are stable as a ship grows: they live in a fixed grid
 * anchored at the core, and {@code getMinPos()} / {@code getMaxPos()} are only a bounding
 * box. Adding blocks never renumbers existing ones.
 */
public class Anchor {

	/** {@code SegmentController.getUniqueIdentifier()} of the owning entity. */
	public String entityKey;

	/** Core-relative block position. Fractional values are allowed for sub-block precision. */
	public float x;
	public float y;
	public float z;

	/**
	 * {@code SegmentPiece.getAbsoluteIndex()} of the anchor block, or
	 * {@link #NO_BLOCK} when this anchor points at empty space (a clearance volume, a
	 * centerline). A hint used to detect orphaning, and the key the game's own per-block
	 * JSON metadata uses, which is what will make a future migration to it cheap.
	 */
	public long blockIndex = NO_BLOCK;

	public static final long NO_BLOCK = Long.MIN_VALUE;

	public Anchor() {
	}

	public Anchor(String entityKey, float x, float y, float z) {
		this.entityKey = entityKey;
		this.x = x;
		this.y = y;
		this.z = z;
	}

	/**
	 * Builds an anchor from an absolute block coordinate, converting to core-relative.
	 */
	public static Anchor fromAbsoluteBlock(String entityKey, int absX, int absY, int absZ) {
		return new Anchor(entityKey,
				absX - SegmentData.SEG_HALF,
				absY - SegmentData.SEG_HALF,
				absZ - SegmentData.SEG_HALF);
	}

	/**
	 * Resolves this anchor to a world position for the given entity.
	 * <p>
	 * Mirrors the client branch of
	 * {@code SegmentController.getAbsoluteElementWorldPositionShifted}, minus the
	 * {@code SEG_HALF} subtraction, which is already baked into the stored coordinate.
	 *
	 * @return {@code out}, or null if the entity has no usable client transform yet
	 */
	public Vector3f toWorld(SegmentController controller, Vector3f out) {
		Transform t = controller.getWorldTransformOnClient();
		if(t == null) {
			return null;
		}
		out.set(x, y, z);
		t.basis.transform(out);
		out.add(t.origin);
		return out;
	}

	@Override
	public String toString() {
		return "Anchor[" + entityKey + " @ " + x + "," + y + "," + z + "]";
	}
}
