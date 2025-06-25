package thederpgamer.betterbuilding.data.template;

import org.schema.game.client.controller.manager.ingame.CopyArea;
import org.schema.game.common.data.VoidSegmentPiece;
import org.schema.schine.resource.tag.Tag;
import org.schema.schine.resource.tag.TagSerializable;
import thederpgamer.betterbuilding.data.ai.IGeneratableData;

import java.io.File;
import java.io.FileInputStream;

/**
 * Represents metadata for a saved template, including its name, dimensions, block types, and orientations.
 * <br/>This data is sent to the LLM in an understandable format for generating new templates.
 */
public class TemplateMetaData implements TagSerializable, IGeneratableData {

	private static final byte VERSION = 0;
	private String name;
	private int[] dimensions;
	private int[] blockTypes;
	private byte[] blockOrientations;

	public static TemplateMetaData fromRawTemplate(String name, CopyArea area) {
		TemplateMetaData templateMetaData = new TemplateMetaData();
		templateMetaData.name = name;
		templateMetaData.dimensions = new int[] {area.getSize().x, area.getSize().y, area.getSize().z};
		templateMetaData.blockTypes = new int[area.getSize().x * area.getSize().y * area.getSize().z];
		templateMetaData.blockOrientations = new byte[area.getSize().x * area.getSize().y * area.getSize().z];
		for(int x = 0; x < area.getSize().x; x++) {
			for(int y = 0; y < area.getSize().y; y++) {
				for(int z = 0; z < area.getSize().z; z++) {
					VoidSegmentPiece piece = area.getPieces().get(x + y * area.getSize().x + z * area.getSize().x * area.getSize().y);
					if(piece != null && piece.isValid()) {
						int index = x + y * area.getSize().x + z * area.getSize().x * area.getSize().y;
						templateMetaData.blockTypes[index] = piece.getType();
						templateMetaData.blockOrientations[index] = piece.getOrientation();
					} else {
						int index = x + y * area.getSize().x + z * area.getSize().x * area.getSize().y;
						templateMetaData.blockTypes[index] = 0; // Default block type for empty space
						templateMetaData.blockOrientations[index] = 0; // Default orientation for empty space
					}
				}
			}
		}
		return templateMetaData;
	}

	private TemplateMetaData() {
		// Default constructor for internal use
	}

	public TemplateMetaData(File file) throws Exception {
		FileInputStream fileInputStream = new FileInputStream(file);
		Tag tag = Tag.readFrom(fileInputStream, true, false);
		fromTagStructure(tag);
	}

	@Override
	public void fromTagStructure(Tag tag) {

	}

	@Override
	public Tag toTagStructure() {
		return null;
	}
}
