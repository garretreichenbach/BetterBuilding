package videogoose.betterbuilding.command;

import api.utils.game.PlayerUtils;
import org.schema.game.common.data.player.PlayerState;

/**
 * {@code /bb compose} — opens the structure composer. Placeholder until the
 * composer vertical slice lands in Milestone 3.
 */
public class ComposeSub implements SubCommand {

	@Override
	public String name() {
		return "compose";
	}

	@Override
	public String usage() {
		return "compose";
	}

	@Override
	public String description() {
		return "Open the structure composer (coming in Milestone 3).";
	}

	@Override
	public void run(PlayerState sender, String[] args) {
		PlayerUtils.sendMessage(sender, "[bb] The composer is not implemented yet (Milestone 3). "
				+ "Available now: /bb capture, /bb reskin, /bb reload.");
	}
}
