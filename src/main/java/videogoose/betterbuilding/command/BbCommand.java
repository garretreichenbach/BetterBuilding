package videogoose.betterbuilding.command;

import api.mod.StarMod;
import api.utils.game.PlayerUtils;
import api.utils.game.chat.CommandInterface;
import org.schema.game.common.data.player.PlayerState;
import videogoose.betterbuilding.BetterBuilding;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The {@code /bb} command group. Dispatches to {@link SubCommand}s by first arg:
 * {@code /bb capture}, {@code /bb reskin}, {@code /bb reload}, {@code /bb compose}.
 */
public class BbCommand implements CommandInterface {

	private final StarMod mod;
	private final Map<String, SubCommand> subs = new LinkedHashMap<>();

	public BbCommand(StarMod mod) {
		this.mod = mod;
		register(new CaptureSub());
		register(new ReskinSub());
		register(new InspectSub());
		register(new PaletteSub());
		register(new ReloadSub());
		register(new ComposeSub());
	}

	private void register(SubCommand s) {
		subs.put(s.name(), s);
	}

	@Override
	public String getCommand() {
		return "bb";
	}

	@Override
	public String[] getAliases() {
		return new String[]{"betterbuilding"};
	}

	@Override
	public String getDescription() {
		StringBuilder sb = new StringBuilder("BetterBuilding composer. Subcommands:\n");
		for (SubCommand s : subs.values()) {
			sb.append("  /bb ").append(s.usage()).append(" - ").append(s.description()).append('\n');
		}
		return sb.toString();
	}

	@Override
	public boolean isAdminOnly() {
		return false;
	}

	@Override
	public boolean onCommand(PlayerState sender, String[] args) {
		if (args.length == 0) {
			PlayerUtils.sendMessage(sender, getDescription());
			return true;
		}
		SubCommand sub = subs.get(args[0].toLowerCase(Locale.ROOT));
		if (sub == null) {
			PlayerUtils.sendMessage(sender, "Unknown subcommand '" + args[0] + "'. Type /bb for help.");
			return true;
		}
		String[] rest = Arrays.copyOfRange(args, 1, args.length);
		try {
			sub.run(sender, rest);
		} catch (Exception e) {
			PlayerUtils.sendMessage(sender, "[bb] Error: " + e.getMessage());
			BetterBuilding.getInstance().logException("/bb " + sub.name() + " failed", e);
		}
		return true;
	}

	@Override
	public void serverAction(@Nullable PlayerState sender, String[] args) {
	}

	@Override
	public StarMod getMod() {
		return mod;
	}
}
