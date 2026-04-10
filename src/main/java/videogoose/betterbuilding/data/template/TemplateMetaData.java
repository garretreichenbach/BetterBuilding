package videogoose.betterbuilding.data.template;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.schema.common.util.linAlg.Vector3i;
import org.schema.game.client.controller.manager.ingame.CopyArea;
import org.schema.game.common.data.VoidSegmentPiece;
import org.schema.game.common.data.world.SegmentData;

import java.util.Arrays;

/**
 * Represents metadata for a saved template, including its name, dimensions, block types, and orientations.
 */
public class TemplateMetaData {

	private String name;
	private int[] dimensions;
	private short[] blockTypes;
	private byte[] blockOrientations;

	private TemplateMetaData() {
		// Default constructor for internal use
	}

	/**
	 * Public constructor that allocates backing arrays for the provided dimensions.
	 * Dimensions are expected to be [x, y, z].
	 */
	public TemplateMetaData(String name, int[] dims) {
		if(dims == null || dims.length != 3) throw new IllegalArgumentException("dims must be length 3");
		this.name = name;
		dimensions = Arrays.copyOf(dims, 3);
		int size = dims[0] * dims[1] * dims[2];
		blockTypes = new short[size];
		blockOrientations = new byte[size];
		// default already 0 (air / orientation 0)
	}

	public static TemplateMetaData fromRawTemplate(String name, CopyArea area) {
		TemplateMetaData templateMetaData = new TemplateMetaData();
		templateMetaData.name = name;

		ObjectArrayList<VoidSegmentPiece> pieces = area.getPieces();

		// area.min/area.max are not always populated when CopyArea is loaded from
		// disk via load() — older / saved templates leave them as (0,0,0), which
		// makes the dimensions collapse to 1x1x1 and silently drops every piece
		// whose voidPos isn't the origin. Compute the bounding box from the pieces
		// themselves and prefer the larger of (file-stored bounds, piece bounds).
		int minX = area.min.x, minY = area.min.y, minZ = area.min.z;
		int maxX = area.max.x, maxY = area.max.y, maxZ = area.max.z;
		boolean storedBoundsValid = (maxX >= minX && maxY >= minY && maxZ >= minZ);
		if(!pieces.isEmpty()) {
			int pMinX = Integer.MAX_VALUE, pMinY = Integer.MAX_VALUE, pMinZ = Integer.MAX_VALUE;
			int pMaxX = Integer.MIN_VALUE, pMaxY = Integer.MIN_VALUE, pMaxZ = Integer.MIN_VALUE;
			for(int i = 0; i < pieces.size(); i++) {
				VoidSegmentPiece piece = pieces.get(i);
				if(piece.voidPos.x < pMinX) pMinX = piece.voidPos.x;
				if(piece.voidPos.y < pMinY) pMinY = piece.voidPos.y;
				if(piece.voidPos.z < pMinZ) pMinZ = piece.voidPos.z;
				if(piece.voidPos.x > pMaxX) pMaxX = piece.voidPos.x;
				if(piece.voidPos.y > pMaxY) pMaxY = piece.voidPos.y;
				if(piece.voidPos.z > pMaxZ) pMaxZ = piece.voidPos.z;
			}
			// If stored bounds wouldn't contain all pieces, use the piece bounds.
			if(!storedBoundsValid
					|| pMinX < minX || pMinY < minY || pMinZ < minZ
					|| pMaxX > maxX || pMaxY > maxY || pMaxZ > maxZ) {
				minX = pMinX; minY = pMinY; minZ = pMinZ;
				maxX = pMaxX; maxY = pMaxY; maxZ = pMaxZ;
			}
		}

		int sizeX = maxX - minX + 1;
		int sizeY = maxY - minY + 1;
		int sizeZ = maxZ - minZ + 1;
		templateMetaData.dimensions = new int[] {sizeX, sizeY, sizeZ};
		int totalSize = sizeX * sizeY * sizeZ;
		templateMetaData.blockTypes = new short[totalSize];
		templateMetaData.blockOrientations = new byte[totalSize];
		// Pieces are sparse — only non-empty blocks are stored, each with its own voidPos
		for(int i = 0; i < pieces.size(); i++) {
			VoidSegmentPiece piece = pieces.get(i);
			int rx = piece.voidPos.x - minX;
			int ry = piece.voidPos.y - minY;
			int rz = piece.voidPos.z - minZ;
			if(rx < 0 || rx >= sizeX || ry < 0 || ry >= sizeY || rz < 0 || rz >= sizeZ) continue;
			int index = rx + ry * sizeX + rz * sizeX * sizeY;
			templateMetaData.blockTypes[index] = piece.getType();
			templateMetaData.blockOrientations[index] = piece.getOrientation();
		}
		return templateMetaData;
	}

	/**
	 * Converts 3D coordinates to the flat backing index. Expects 0 <= x < dims[0], etc.
	 */
	public static int index(int x, int y, int z, int[] dims) {
		return x + y * dims[0] + z * dims[0] * dims[1];
	}

	public CopyArea toRawTemplate() {
		CopyArea copyArea = new CopyArea();
		copyArea.min = new Vector3i(0, 0, 0);
		copyArea.max = new Vector3i(dimensions[0] - 1, dimensions[1] - 1, dimensions[2] - 1);
		for(int x = 0; x < dimensions[0]; x++) {
			for(int y = 0; y < dimensions[1]; y++) {
				for(int z = 0; z < dimensions[2]; z++) {
					int index = x + y * dimensions[0] + z * dimensions[0] * dimensions[1];
					int type = blockTypes[index];
					byte orientation = blockOrientations[index];
					if(type != 0) {
						VoidSegmentPiece piece = new VoidSegmentPiece();
						piece.voidPos.set(x, y, z);
						piece.setDataByReference(SegmentData.makeDataInt((short) type, orientation));
						copyArea.getPieces().add(piece);
					}
				}
			}
		}
		return copyArea;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int[] getDimensions() {
		return Arrays.copyOf(dimensions, dimensions.length);
	}

	public short[] getBlockTypes() {
		return blockTypes;
	}

	public byte[] getBlockOrientations() {
		return blockOrientations;
	}

	public short getTypeAt(int x, int y, int z) {
		int idx = index(x, y, z, dimensions);
		return blockTypes[idx];
	}

	public byte getOrientationAt(int x, int y, int z) {
		int idx = index(x, y, z, dimensions);
		return blockOrientations[idx];
	}

	public void setTypeAt(int x, int y, int z, short type) {
		int idx = index(x, y, z, dimensions);
		blockTypes[idx] = type;
	}

	public void setOrientationAt(int x, int y, int z, byte orientation) {
		int idx = index(x, y, z, dimensions);
		blockOrientations[idx] = orientation;
	}
}
