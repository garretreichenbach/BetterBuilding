package thederpgamer.betterbuilding.data.ai;

import py4j.GatewayServer;
import thederpgamer.betterbuilding.BetterBuilding;

/**
 * [Description]
 *
 * @author Garret Reichenbach
 */
public class LLMManager {

	public static void prompt(String prompt, LLMResponseCallback callback) {
		GatewayServer gateway = new GatewayServer(null, 25333);
		gateway.start();
		try {
			Object response = gateway.getPythonServerEntryPoint(new Class[] {LLM.class});
			callback.onResponse(response);
		} catch(Exception exception) {
			BetterBuilding.getInstance().logException("Failed to prompt LLM", exception);
			callback.onError(exception);
		} finally {
			gateway.shutdown();
		}
	}
}
