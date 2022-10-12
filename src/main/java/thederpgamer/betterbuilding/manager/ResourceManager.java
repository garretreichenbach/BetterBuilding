package thederpgamer.betterbuilding.manager;

import api.utils.textures.StarLoaderTexture;
import org.schema.schine.graphicsengine.core.ResourceException;
import org.schema.schine.graphicsengine.forms.Mesh;
import org.schema.schine.resource.ResourceLoader;
import thederpgamer.amogus.Amogus;

import java.io.IOException;
import java.util.HashMap;

/**
 * <Description>
 *
 * @author TheDerpGamer
 * @since 06/15/2021
 */
public class ResourceManager {

	private static final String[] modelNames = {
			"amogus"
	};

	private static final HashMap<String, Mesh> meshMap = new HashMap<>();

	public static void loadResources(final Amogus instance, final ResourceLoader loader) {
		StarLoaderTexture.runOnGraphicsThread(
				new Runnable() {
					@Override
					public void run() {
						// Load models
						for(String modelName : modelNames) {
							try {
								loader.getMeshLoader().loadModMesh(instance, modelName, instance.getClass().getResourceAsStream("models/" + modelName + ".zip"), null);
								Mesh mesh = loader.getMeshLoader().getModMesh(Amogus.getInstance(), modelName);
								mesh.setFirstDraw(true);
								meshMap.put(modelName, mesh);
							} catch(ResourceException | IOException exception) {
								exception.printStackTrace();
							}
						}
					}
				});
	}

	public static Mesh getMesh(String name) {
		if(meshMap.containsKey(name)) return (Mesh) meshMap.get(name).getChilds().get(0);
		else return null;
	}
}
