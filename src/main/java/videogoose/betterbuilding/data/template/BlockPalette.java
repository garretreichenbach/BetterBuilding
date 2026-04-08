package videogoose.betterbuilding.data.template;


import api.utils.element.Blocks;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility for converting between Blocks enum names and IDs for AI context.
 * Builds human-readable block palette strings using StarMade's Blocks enum directly.
 */
public class BlockPalette {

	/**
	 * Build a JSON-friendly map of block name -> ID for the AI prompt.
	 * Filters to only building-relevant blocks (armor, hull, systems, etc).
	 */
	public static String toJsonPaletteString() {
		StringBuilder sb = new StringBuilder("{");
		boolean first = true;
		for(Blocks block : Blocks.values()) {
			if(block == Blocks.EMPTY_SPACE) continue;
			if(!first) sb.append(",");
			sb.append("\"").append(block.name()).append("\":").append(block.getId());
			first = false;
		}
		sb.append("}");
		return sb.toString();
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
	 * Build a block summary from a TemplateMetaData showing counts per block type.
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
}
