package videogoose.betterbuilding.command;

import org.schema.game.common.data.player.PlayerState;

/**
 * One subcommand of {@code /bb}.
 */
public interface SubCommand {

	/** Lower-case keyword that selects this subcommand (e.g. "capture"). */
	String name();

	/** Usage string shown in help, without the leading {@code /bb}. */
	String usage();

	String description();

	/** Execute. {@code args} excludes the subcommand keyword itself. */
	void run(PlayerState sender, String[] args) throws Exception;
}
