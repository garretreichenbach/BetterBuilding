package thederpgamer.betterbuilding.data.tools;

/**
 * Interface for marking a method as a tool that can be called by the LLM.
 *
 * @author Garret Reichenbach
 */
public @interface Tool {

	/**
	 * The name of the tool.
	 *
	 * @return the name of the tool
	 */
	String name();

	/**
	 * A description of what the tool does.
	 *
	 * @return a description of the tool
	 */
	String description();

	/**
	 * An array of parameters that the tool accepts.
	 * @return an array of parameter names
	 */
	String[] parameters() default {};

	/**
	 * The return type of the tool method.
	 * Default is "void", indicating no return value.
	 *
	 * @return the return type of the tool method
	 */
	String returnType() default "void";
}
