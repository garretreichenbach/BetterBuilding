package thederpgamer.betterbuilding.manager;

import api.mod.ModSkeleton;
import api.mod.config.PersistentObjectUtil;
import thederpgamer.betterbuilding.BetterBuilding;
import thederpgamer.betterbuilding.data.HotbarData;

import java.io.*;

/**
 * Manages saving and loading of hotbar layouts.
 *
 * @version 1.0 - [01/27/2021]
 * @author TheDerpGamer
 */
public class HotbarManager {

	private static final ModSkeleton instance = BetterBuilding.getInstance().getSkeleton();
	private static File hotbarsFile;

	public static void initialize() {
		if(ConfigManager.getMainConfig().getBoolean("global-hotbars")) {
			hotbarsFile = new File(BetterBuilding.getInstance().getSkeleton().getResourcesFolder().getPath() + "/hotbars.smdat");
			if(!hotbarsFile.exists()) {
				try {
					hotbarsFile.createNewFile();
				} catch(IOException exception) {
					BetterBuilding.getInstance().logException("Failed to create hotbars file", exception);
				}
			}
		}
	}

	public static void saveHotbars(HotbarData[][] hotbars) {
		if(ConfigManager.getMainConfig().getBoolean("global-hotbars")) {
			try {
				FileOutputStream fileStream = new FileOutputStream(hotbarsFile);
				ObjectOutputStream objectStream = new ObjectOutputStream(fileStream);
				for(int h = 0; h < 10; h++) {
					for(int i = 0; i < 10; i++) {
						objectStream.writeObject(hotbars[h][i]);
					}
				}
				objectStream.close();
				fileStream.close();
				BetterBuilding.getInstance().logInfo("Successfully saved hotbars");
			} catch(IOException exception) {
				BetterBuilding.getInstance().logException("Failed to save hotbars", exception);
			}
		} else {
			PersistentObjectUtil.removeAllObjects(instance, hotbars.getClass());
			PersistentObjectUtil.addObject(instance, hotbars);
			PersistentObjectUtil.save(instance);
		}
	}

	public static HotbarData[][] loadHotbars() {
		if(ConfigManager.getMainConfig().getBoolean("global-hotbars")) {
			HotbarData[][] hotbars = new HotbarData[10][10];
			try {
				FileInputStream fileStream = new FileInputStream(hotbarsFile);
				ObjectInputStream objectStream = new ObjectInputStream(fileStream);
				for(int h = 0; h < 10; h++) {
					for(int i = 0; i < 10; i++) {
						hotbars[h][i] = (HotbarData) objectStream.readObject();
					}
				}
				objectStream.close();
				fileStream.close();
				BetterBuilding.getInstance().logInfo("Successfully loaded hotbars");
			} catch(IOException | ClassNotFoundException exception) {
				BetterBuilding.getInstance().logException("Failed to load hotbars", exception);
			}
			return hotbars;
		} else {
			if(PersistentObjectUtil.getObjects(instance, HotbarData[][].class).isEmpty()) {
				HotbarData[][] hotbars = new HotbarData[10][10];
				PersistentObjectUtil.addObject(instance, hotbars);
				PersistentObjectUtil.save(instance);
			}
			try {
				return (HotbarData[][]) PersistentObjectUtil.getObjects(instance, HotbarData[][].class).get(0);
			} catch(Exception exception) {
				BetterBuilding.getInstance().logException("Failed to load hotbars", exception);
				return new HotbarData[10][10];
			}
		}
	}
}