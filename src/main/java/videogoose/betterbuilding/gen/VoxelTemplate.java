package videogoose.betterbuilding.gen;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.controller.manager.ingame.CopyArea;
import org.schema.game.common.data.VoidSegmentPiece;
import org.schema.game.common.data.world.SegmentData4Byte;

/**
 * Dense 3D voxel grid of StarMade blocks: each cell holds a block type id
 * (short, 0 = air) and an orientation byte. This is the in-memory build target
 * that code (and later, LLM-generated Lua) writes into, then converts to a
 * native StarMade {@link CopyArea} for save/paste.
 *
 * <p>Coordinate convention (matches the rebuilt generation pipeline):
 * X = width, Y = height (up), Z = length. Index is {@code x + y*dx + z*dx*dy},
 * the same packing StarMade's CopyArea bounds use.
 *
 * <p>The CopyArea construction here is the load-bearing, in-game-verified recipe
 * recovered from the project's earlier template code: one sparse
 * {@link VoidSegmentPiece} per non-air cell, with data packed via
 * {@link SegmentData#makeDataInt(short, byte)} (type + orientation).
 */
public final class VoxelTemplate {

	/** Full-health hitpoint byte (max value of the 7-bit hp field) = 100% HP. */
	private static final int FULL_HP_BYTE = (1 << SegmentData4Byte.hpBits) - 1;

	private final int dx, dy, dz;
	private final short[] types;
	private final byte[] orients;
	private String name;

	public VoxelTemplate(String name, int dx, int dy, int dz) {
		if(dx <= 0 || dy <= 0 || dz <= 0) {
			throw new IllegalArgumentException("dimensions must be > 0, got " + dx + "x" + dy + "x" + dz);
		}
		this.name = name;
		this.dx = dx;
		this.dy = dy;
		this.dz = dz;
		int n = dx * dy * dz;
		types = new short[n];
		orients = new byte[n];
	}

	// --- dimensions / identity ---

	/**
	 * Build a VoxelTemplate from a {@link CopyArea} (e.g. a captured region or a
	 * loaded {@code .smtpl}). Pieces are sparse; bounds come from the area, with a
	 * fallback to the pieces' own bounding box when the stored bounds are
	 * unpopulated (loaded templates sometimes leave min/max at the origin).
	 */
	public static VoxelTemplate fromCopyArea(String name, CopyArea area) {
		ObjectArrayList<VoidSegmentPiece> pieces = area.getPieces();

		// The pieces' own voidPos is the source of truth. The engine's
		// CopyArea.copyArea(...) stores pieces relative-to-min (0-origin) while
		// leaving area.min/max at the absolute scan bounds — inconsistent spaces —
		// so we derive the bounding box from the pieces and only fall back to
		// area.min/max when there are no pieces.
		int minX, minY, minZ, maxX, maxY, maxZ;
		if(!pieces.isEmpty()) {
			minX = minY = minZ = Integer.MAX_VALUE;
			maxX = maxY = maxZ = Integer.MIN_VALUE;
			for(int i = 0; i < pieces.size(); i++) {
				VoidSegmentPiece p = pieces.get(i);
				minX = Math.min(minX, p.voidPos.x);
				maxX = Math.max(maxX, p.voidPos.x);
				minY = Math.min(minY, p.voidPos.y);
				maxY = Math.max(maxY, p.voidPos.y);
				minZ = Math.min(minZ, p.voidPos.z);
				maxZ = Math.max(maxZ, p.voidPos.z);
			}
		} else {
			minX = area.min.x; minY = area.min.y; minZ = area.min.z;
			maxX = area.max.x; maxY = area.max.y; maxZ = area.max.z;
			if(maxX < minX || maxY < minY || maxZ < minZ) {
				minX = minY = minZ = 0;
				maxX = maxY = maxZ = 0;
			}
		}

		int sx = maxX - minX + 1;
		int sy = maxY - minY + 1;
		int sz = maxZ - minZ + 1;
		VoxelTemplate t = new VoxelTemplate(name, Math.max(1, sx), Math.max(1, sy), Math.max(1, sz));
		for(int i = 0; i < pieces.size(); i++) {
			VoidSegmentPiece p = pieces.get(i);
			int rx = p.voidPos.x - minX, ry = p.voidPos.y - minY, rz = p.voidPos.z - minZ;
			t.set(rx, ry, rz, p.getType(), p.getOrientation());
		}
		return t;
	}

	public int dimX() {
		return dx;
	}

	public int dimY() {
		return dy;
	}

	public int dimZ() {
		return dz;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean inBounds(int x, int y, int z) {
		return x >= 0 && x < dx && y >= 0 && y < dy && z >= 0 && z < dz;
	}

	// --- cell access ---

	public int index(int x, int y, int z) {
		return x + y * dx + z * dx * dy;
	}

	/**
	 * Returns the block type at the cell, or 0 (air) if out of bounds.
	 */
	public short getType(int x, int y, int z) {
		if(!inBounds(x, y, z)) return 0;
		return types[index(x, y, z)];
	}

	/**
	 * Returns the orientation byte at the cell, or 0 if out of bounds.
	 */
	public byte getOrientation(int x, int y, int z) {
		if(!inBounds(x, y, z)) return 0;
		return orients[index(x, y, z)];
	}

	/**
	 * Place a single block. Out-of-bounds writes are silently ignored.
	 */
	public void set(int x, int y, int z, short type, byte orientation) {
		if(!inBounds(x, y, z)) return;
		int i = index(x, y, z);
		types[i] = type;
		orients[i] = orientation;
	}

	public void set(int x, int y, int z, short type) {
		set(x, y, z, type, (byte) 0);
	}

	// --- aliases used by LuaExecutor: independent type/orientation setters ---

	public short getTypeAt(int x, int y, int z) {
		return getType(x, y, z);
	}

	public byte getOrientationAt(int x, int y, int z) {
		return getOrientation(x, y, z);
	}

	public void setTypeAt(int x, int y, int z, short type) {
		if (inBounds(x, y, z)) types[index(x, y, z)] = type;
	}

	public void setOrientationAt(int x, int y, int z, byte orientation) {
		if (inBounds(x, y, z)) orients[index(x, y, z)] = orientation;
	}

	// --- convenience builders (the rich primitive set lives later in LuaExecutor) ---

	/**
	 * Clear a cell to air.
	 */
	public void clear(int x, int y, int z) {
		set(x, y, z, (short) 0, (byte) 0);
	}

	/**
	 * Fill an inclusive box [x0..x1]x[y0..y1]x[z0..z1] with a block. Returns cells written.
	 */
	public int fill(int x0, int y0, int z0, int x1, int y1, int z1, short type, byte orientation) {
		int lo_x = Math.min(x0, x1), hi_x = Math.max(x0, x1);
		int lo_y = Math.min(y0, y1), hi_y = Math.max(y0, y1);
		int lo_z = Math.min(z0, z1), hi_z = Math.max(z0, z1);
		int count = 0;
		for(int z = lo_z; z <= hi_z; z++) {
			for(int y = lo_y; y <= hi_y; y++) {
				for(int x = lo_x; x <= hi_x; x++) {
					if(!inBounds(x, y, z)) continue;
					int i = index(x, y, z);
					types[i] = type;
					orients[i] = orientation;
					count++;
				}
			}
		}
		return count;
	}

	// --- CopyArea bridge ---

	/**
	 * Number of non-air cells.
	 */
	public int blockCount() {
		int c = 0;
		for(short t : types) if(t != 0) c++;
		return c;
	}

	/**
	 * Convert to a native StarMade {@link CopyArea} (sparse: one piece per non-air
	 * cell). The result can be saved via {@link TemplateStore} or handed directly
	 * to {@code BuildToolsManager.setCopyArea(...)} for pasting.
	 */
	public CopyArea toCopyArea() {
		CopyArea area = new CopyArea();
		area.min = new Vector3i(0, 0, 0);
		area.max = new Vector3i(dx - 1, dy - 1, dz - 1);
		ObjectArrayList<VoidSegmentPiece> pieces = area.getPieces();
		for(int z = 0; z < dz; z++) {
			for(int y = 0; y < dy; y++) {
				for(int x = 0; x < dx; x++) {
					int i = index(x, y, z);
					short type = types[i];
					if(type == 0) continue;
					VoidSegmentPiece piece = new VoidSegmentPiece();
					piece.voidPos.set(x, y, z);
					// Pack via the piece's own setters so we stay correct under the current
					// SegmentData4Byte layout (type 13b, hp 7b, active 1b, orientation 5b).
					// Hand-packing with the legacy SegmentData.makeDataInt put orientation in
					// the wrong bits and truncated 13-bit type ids.
					piece.setDataByReference(0);
					piece.setType(type);
					piece.setOrientation(orients[i]);
					piece.setHitpointsByte(FULL_HP_BYTE);
					pieces.add(piece);
				}
			}
		}
		return area;
	}
}
