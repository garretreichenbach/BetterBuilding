package thederpgamer.betterbuilding.ai;

/**
 * [Description]
 *
 * @author Garret Reichenbach
 */
public interface LLM {

	String prompt(String prompt) throws Exception;
}
