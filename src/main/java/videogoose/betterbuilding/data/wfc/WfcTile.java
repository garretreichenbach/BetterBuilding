package videogoose.betterbuilding.data.wfc;

/**
 * A single (blockType, orientation) pair treated as one tile by the WFC solver.
 *
 * Air (type 0) is a valid tile so the solver can place empty space.
 * Equality and hash key off both fields, so a rotated wedge is a different
 * tile from its un-rotated twin — this matters in StarMade because the same
 * wedge type at different orientations means very different geometry.
 */
public final class WfcTile {

	public static final WfcTile AIR = new WfcTile((short) 0, (byte) 0);

	private final short type;
	private final byte orientation;
	private final int hash;

	public WfcTile(short type, byte orientation) {
		this.type = type;
		this.orientation = orientation;
		this.hash = (type & 0xFFFF) * 31 + (orientation & 0xFF);
	}

	public short getType() {
		return type;
	}

	public byte getOrientation() {
		return orientation;
	}

	public boolean isAir() {
		return type == 0;
	}

	@Override
	public boolean equals(Object o) {
		if(this == o) return true;
		if(!(o instanceof WfcTile)) return false;
		WfcTile other = (WfcTile) o;
		return type == other.type && orientation == other.orientation;
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public String toString() {
		return "WfcTile(type=" + type + ", orient=" + orientation + ")";
	}
}
