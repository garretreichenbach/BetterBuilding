package videogoose.betterbuilding.data.template;


import api.utils.element.Blocks;

import java.util.*;

/**
 * Utility for converting between Blocks enum names and IDs for AI context.
 * Filters to building-relevant blocks to keep the AI prompt manageable.
 */
public class BlockPalette {

	private static final Set<String> PALETTE_CATEGORIES = new HashSet<>(Arrays.asList(
			"HULL", "ARMOR", "WEDGE", "CORNER", "HEPTA", "TETRA", "SLAB",
			"GLASS", "LIGHT", "DOOR"
	));

	/**
	 * Build a filtered block palette for the AI prompt.
	 * Only includes building-relevant blocks to avoid overwhelming the model.
	 */
	public static String toJsonPaletteString() {
		StringBuilder sb = new StringBuilder("{");
		boolean first = true;
		for(Blocks block : Blocks.values()) {
			if(block == Blocks.EMPTY_SPACE) continue;
			if(!isRelevant(block.name())) continue;
			if(!first) sb.append(",");
			sb.append("\"").append(block.name()).append("\":").append(block.getId());
			first = false;
		}
		sb.append("}");
		return sb.toString();
	}

	private static boolean isRelevant(String name) {
		for(String category : PALETTE_CATEGORIES) {
			if(name.contains(category)) return true;
		}
		return false;
	}

	/**
	 * Get human-readable name for a block type ID using Blocks enum.
	 */
	public static String getName(short typeId) {
		Blocks block = Blocks.fromId(typeId);
		if(block != Blocks.EMPTY_SPACE || typeId == 0) return block.name();
		return "UNKNOWN_" + typeId;
	}

	/**
	 * Build a block composition summary showing counts per block type.
	 */
	public static String summarizeTemplate(TemplateMetaData template) {
		Map<Short, Integer> counts = new LinkedHashMap<>();
		short[] types = template.getBlockTypes();
		for(short type : types) {
			if(type != 0) {
				counts.merge(type, 1, Integer::sum);
			}
		}

		StringBuilder sb = new StringBuilder("{");
		boolean first = true;
		for(Map.Entry<Short, Integer> entry : counts.entrySet()) {
			if(!first) sb.append(",");
			sb.append("\"").append(getName(entry.getKey())).append("\":").append(entry.getValue());
			first = false;
		}
		sb.append("}");
		return sb.toString();
	}

	/**
	 * Build a structural summary of a template showing cross-section slices
	 * along the Z axis so the AI can understand the shape.
	 * Uses single characters per block type for compactness.
	 */
	public static String structuralSummary(TemplateMetaData template) {
		int[] dims = template.getDimensions();
		StringBuilder sb = new StringBuilder();

		// Map block types to short labels
		Map<Short, Character> labelMap = new LinkedHashMap<>();
		char nextLabel = 'A';
		short[] types = template.getBlockTypes();
		for(short type : types) {
			if(type != 0 && !labelMap.containsKey(type)) {
				labelMap.put(type, nextLabel++);
				if(nextLabel > 'Z') nextLabel = 'a';
			}
		}

		// Legend
		sb.append("Legend: ");
		boolean first = true;
		for(Map.Entry<Short, Character> entry : labelMap.entrySet()) {
			if(!first) sb.append(", ");
			sb.append(entry.getValue()).append("=").append(getName(entry.getKey()));
			first = false;
		}
		sb.append("\n");

		// Show slices along Z (front-to-back), sampling if too many
		int step = Math.max(1, dims[2] / 8);
		for(int z = 0; z < dims[2]; z += step) {
			sb.append("Z=").append(z).append(":\n");
			for(int y = dims[1] - 1; y >= 0; y--) {
				for(int x = 0; x < dims[0]; x++) {
					short type = template.getTypeAt(x, y, z);
					if(type == 0) {
						sb.append('.');
					} else {
						Character label = labelMap.get(type);
						sb.append(label != null ? label : '?');
					}
				}
				sb.append('\n');
			}
		}
		return sb.toString();
	}
}
