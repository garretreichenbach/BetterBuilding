package thederpgamer.betterbuilding.data.ai;

/**
 * [Description]
 *
 * @author Garret Reichenbach
 */
public interface LLMResponseCallback {
	void onResponse(Object response);

	void onError(Exception exception);
}
