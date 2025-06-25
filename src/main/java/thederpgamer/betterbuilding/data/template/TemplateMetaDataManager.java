package thederpgamer.betterbuilding.data.template;

import org.schema.game.client.controller.manager.ingame.CopyArea;

import java.util.HashMap;

/**
 * Manages saving and loading of template metadata.
 */
public class TemplateMetaDataManager {

	private static final HashMap<String, TemplateMetaData> metaDataMap = new HashMap<>();

	public static void createMetaDataFor(String name, CopyArea area) {
		metaDataMap.remove(name);
		TemplateMetaData metaData = TemplateMetaData.fromRawTemplate(name, area);
		metaDataMap.put(name, metaData);
	}

	public static TemplateMetaData getMetaData(String name) {
		return metaDataMap.get(name);
	}
}