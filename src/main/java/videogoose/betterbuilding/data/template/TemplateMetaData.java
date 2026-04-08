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
		int sizeX = area.max.x - area.min.x + 1;
		int sizeY = area.max.y - area.min.y + 1;
		int sizeZ = area.max.z - area.min.z + 1;
		templateMetaData.dimensions = new int[] {sizeX, sizeY, sizeZ};
		int totalSize = sizeX * sizeY * sizeZ;
		templateMetaData.blockTypes = new short[totalSize];
		templateMetaData.blockOrientations = new byte[totalSize];
		// Pieces are sparse — only non-empty blocks are stored, each with its own voidPos
		ObjectArrayList<VoidSegmentPiece> pieces = area.getPieces();
		for(int i = 0; i < pieces.size(); i++) {
			VoidSegmentPiece piece = pieces.get(i);
			int rx = piece.voidPos.x - area.min.x;
			int ry = piece.voidPos.y - area.min.y;
			int rz = piece.voidPos.z - area.min.z;
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
