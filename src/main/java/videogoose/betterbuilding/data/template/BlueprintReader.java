package videogoose.betterbuilding.data.template;

import org.schema.game.common.data.world.SegmentData;
import videogoose.betterbuilding.BetterBuilding;

import java.io.File;
import java.io.FileInputStream;
import java.io.DataInputStream;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Reads StarMade blueprint directories and extracts block data into TemplateMetaData.
 * Blueprints are stored as directories containing DATA/*.smd3 segment files.
 * Each segment is a 16x16x16 chunk of blocks packed as 32-bit integers.
 */
public class BlueprintReader {

	private static final int SEG = 16;
	private static final int BLOCKS_PER_SEGMENT = SEG * SEG * SEG; // 4096

	// Bit masks for unpacking block data (from SegmentData)
	private static final int TYPE_MASK = 0x7FF;          // bits 0-10 (11 bits)
	private static final int HP_MASK = 0x0007F800;        // bits 11-17 (7 bits)
	private static final int HP_SHIFT = 11;
	private static final int ACTIVE_MASK = 0x00040000;    // bit 18
	private static final int ORIENT_MASK = 0x00F80000;    // bits 19-23 (5 bits)
	private static final int ORIENT_SHIFT = 19;

	/**
	 * List all blueprint directories in the given base path.
	 * Returns names that can be passed to readBlueprint().
	 */
	public static List<String> listBlueprints(File blueprintDir) {
		List<String> names = new ArrayList<>();
		if(!blueprintDir.isDirectory()) return names;

		File[] dirs = blueprintDir.listFiles(File::isDirectory);
		if(dirs == null) return names;

		for(File dir : dirs) {
			// A valid blueprint directory has a DATA subfolder with .smd3 files
			File dataDir = new File(dir, "DATA");
			if(dataDir.isDirectory()) {
				File[] smd3Files = dataDir.listFiles((d, name) -> name.endsWith(".smd3"));
				if(smd3Files != null && smd3Files.length > 0) {
					names.add(dir.getName());
				}
			}
		}
		Collections.sort(names);
		return names;
	}

	/**
	 * Resolve a wildcard pattern against blueprint directory names.
	 */
	public static List<String> resolveWildcard(File blueprintDir, String pattern) {
		List<String> all = listBlueprints(blueprintDir);
		if("*".equals(pattern)) return all;

		String regex = pattern
				.replace("\\", "\\\\")
				.replace(".", "\\.")
				.replace("(", "\\(").replace(")", "\\)")
				.replace("[", "\\[").replace("]", "\\]")
				.replace("{", "\\{").replace("}", "\\}")
				.replace("+", "\\+").replace("^", "\\^").replace("$", "\\$")
				.replace("|", "\\|")
				.replace("*", ".*")
				.replace("?", ".");
		regex = "(?i)^" + regex + "$";

		List<String> matches = new ArrayList<>();
		for(String name : all) {
			if(name.matches(regex)) {
				matches.add(name);
			}
		}
		return matches;
	}

	/**
	 * Read a blueprint directory and convert it to a TemplateMetaData.
	 * Scans all .smd3 segment files, determines the bounding box of non-air blocks,
	 * and creates a tightly-fitted TemplateMetaData.
	 *
	 * @param blueprintDir The base blueprints directory
	 * @param name The blueprint name (subdirectory name)
	 * @return TemplateMetaData containing all blocks, or null if empty
	 */
	public static TemplateMetaData readBlueprint(File blueprintDir, String name) throws Exception {
		File dataDir = new File(new File(blueprintDir, name), "DATA");
		if(!dataDir.isDirectory()) {
			throw new Exception("No DATA directory found for blueprint: " + name);
		}

		File[] smd3Files = dataDir.listFiles((d, n) -> n.endsWith(".smd3"));
		if(smd3Files == null || smd3Files.length == 0) {
			throw new Exception("No .smd3 files found for blueprint: " + name);
		}

		// First pass: collect all non-air blocks with absolute positions
		List<BlockEntry> blocks = new ArrayList<>();

		for(File smd3File : smd3Files) {
			try {
				readSegmentFile(smd3File, blocks);
			} catch(Exception e) {
				BetterBuilding.getInstance().logWarning("Failed to read segment file " +
						smd3File.getName() + ": " + e.getMessage());
			}
		}

		if(blocks.isEmpty()) return null;

		// Find bounding box
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
		for(BlockEntry b : blocks) {
			if(b.x < minX) minX = b.x;
			if(b.y < minY) minY = b.y;
			if(b.z < minZ) minZ = b.z;
			if(b.x > maxX) maxX = b.x;
			if(b.y > maxY) maxY = b.y;
			if(b.z > maxZ) maxZ = b.z;
		}

		int dimX = maxX - minX + 1;
		int dimY = maxY - minY + 1;
		int dimZ = maxZ - minZ + 1;

		TemplateMetaData template = new TemplateMetaData(name, new int[]{dimX, dimY, dimZ});

		// Place all blocks, shifted to 0-origin
		for(BlockEntry b : blocks) {
			int rx = b.x - minX;
			int ry = b.y - minY;
			int rz = b.z - minZ;
			template.setTypeAt(rx, ry, rz, b.type);
			template.setOrientationAt(rx, ry, rz, b.orient);
		}

		return template;
	}

	/**
	 * Read a single .smd3 segment region file and extract non-air blocks.
	 *
	 * smd3 file format:
	 * - Filename encodes region coordinates: <uid>.<rx>.<ry>.<rz>.smd3
	 * - Each region contains up to 8x8x8 = 512 segments
	 * - File starts with a segment index, followed by compressed segment data
	 */
	private static void readSegmentFile(File file, List<BlockEntry> blocks) throws Exception {
		// Parse region coordinates from filename: <uid>.<rx>.<ry>.<rz>.smd3
		String[] parts = file.getName().replace(".smd3", "").split("\\.");
		if(parts.length < 4) return;

		int regionX, regionY, regionZ;
		try {
			regionX = Integer.parseInt(parts[parts.length - 3]);
			regionY = Integer.parseInt(parts[parts.length - 2]);
			regionZ = Integer.parseInt(parts[parts.length - 1]);
		} catch(NumberFormatException e) {
			return; // Skip files with unparseable coordinates
		}

		// Region base position in block coordinates (each region = 8 segments * 16 blocks = 128 blocks)
		int regionBaseX = regionX * 128;
		int regionBaseY = regionY * 128;
		int regionBaseZ = regionZ * 128;

		try(DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
			// Read segment index header
			// The index is 512 entries (8x8x8), each with offset (int) and length (int)
			int indexEntries = 512;
			int[] offsets = new int[indexEntries];
			int[] lengths = new int[indexEntries];

			for(int i = 0; i < indexEntries; i++) {
				offsets[i] = dis.readInt();
				lengths[i] = dis.readInt();
			}

			// Read the rest of the file into a byte array for segment access
			byte[] fileData = new byte[dis.available()];
			dis.readFully(fileData);

			int headerSize = indexEntries * 8; // 512 * (4 + 4)

			// Process each segment that has data
			for(int i = 0; i < indexEntries; i++) {
				if(lengths[i] <= 0) continue;

				// Segment position within the region (8x8x8 grid)
				int segLocalX = (i % 8);
				int segLocalY = ((i / 8) % 8);
				int segLocalZ = (i / 64);

				// Absolute segment base position in blocks
				int segBaseX = regionBaseX + segLocalX * SEG;
				int segBaseY = regionBaseY + segLocalY * SEG;
				int segBaseZ = regionBaseZ + segLocalZ * SEG;

				try {
					int dataOffset = offsets[i] - headerSize;
					if(dataOffset < 0 || dataOffset + lengths[i] > fileData.length) continue;

					byte[] segmentBytes = Arrays.copyOfRange(fileData, dataOffset, dataOffset + lengths[i]);

					// Decompress segment data
					int[] blockData = decompressSegment(segmentBytes);
					if(blockData == null) continue;

					// Extract non-air blocks
					for(int idx = 0; idx < Math.min(blockData.length, BLOCKS_PER_SEGMENT); idx++) {
						int packed = blockData[idx];
						short type = (short)(packed & TYPE_MASK);
						if(type == 0) continue; // Air

						byte orient = (byte)((packed & ORIENT_MASK) >>> ORIENT_SHIFT);
						// StarMade uses 5-bit orientation (0-31) but we only use 0-5
						// Map extended orientations to basic 6 directions
						byte simpleOrient = mapOrientation(orient);

						int bx = segBaseX + (idx % SEG);
						int by = segBaseY + ((idx / SEG) % SEG);
						int bz = segBaseZ + (idx / (SEG * SEG));

						blocks.add(new BlockEntry(bx, by, bz, type, simpleOrient));
					}
				} catch(Exception e) {
					// Skip corrupt segments
				}
			}
		}
	}

	/**
	 * Decompress a segment's block data.
	 * Segments are typically GZIP compressed arrays of 4096 ints.
	 */
	private static int[] decompressSegment(byte[] compressed) {
		try {
			GZIPInputStream gzip = new GZIPInputStream(new java.io.ByteArrayInputStream(compressed));
			DataInputStream dis = new DataInputStream(gzip);
			int[] data = new int[BLOCKS_PER_SEGMENT];
			for(int i = 0; i < BLOCKS_PER_SEGMENT; i++) {
				data[i] = dis.readInt();
			}
			dis.close();
			return data;
		} catch(Exception e) {
			// Might not be gzip - try reading as raw ints
			try {
				DataInputStream dis = new DataInputStream(new java.io.ByteArrayInputStream(compressed));
				int[] data = new int[BLOCKS_PER_SEGMENT];
				for(int i = 0; i < BLOCKS_PER_SEGMENT; i++) {
					data[i] = dis.readInt();
				}
				dis.close();
				return data;
			} catch(Exception e2) {
				return null;
			}
		}
	}

	/**
	 * Map StarMade's 5-bit orientation (0-31, includes rotations) to simple 6-direction (0-5).
	 * 0=front(+Z), 1=back(-Z), 2=top(+Y), 3=bottom(-Y), 4=right(+X), 5=left(-X)
	 */
	private static byte mapOrientation(byte orient) {
		// StarMade orientations 0-5 are the 6 basic directions
		// 6-31 are rotated variants; map them back to the primary direction
		if(orient <= 5) return orient;
		// Orientations 6-31 are 4 rotations each of the 6 base directions
		// Pattern: base direction = (orient - 6) / 4, but shifted
		// Simplified mapping: keep the primary axis
		return (byte)((orient % 6));
	}

	static class BlockEntry {
		final int x, y, z;
		final short type;
		final byte orient;

		BlockEntry(int x, int y, int z, short type, byte orient) {
			this.x = x; this.y = y; this.z = z;
			this.type = type; this.orient = orient;
		}
	}
}
