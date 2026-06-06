package videogoose.betterbuilding.command;

import api.mod.StarMod;
import api.utils.game.PlayerUtils;
import api.utils.game.chat.CommandInterface;
import org.schema.game.common.data.player.PlayerState;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;

import videogoose.betterbuilding.BetterBuilding;
import videogoose.betterbuilding.ai.AIClient;
import videogoose.betterbuilding.ai.AIConfig;
import videogoose.betterbuilding.ai.RetrievalIndex;

/**
 * Phase-1: query the retrieval index — embed a free-text description and print
 * the closest captioned ships. Validates that retrieval will surface sensible
 * example ships for generation priming.
 *
 * <p>Usage: {@code /bb_search <description>} (optionally end with a number for k).
 */
public class SearchCommand implements CommandInterface {

	private static final File INDEX_FILE = new File("./BetterBuilding/index.json");
	private static final int DEFAULT_K = 5;

	@Override
	public String getCommand() {
		return "bb_search";
	}

	@Override
	public String[] getAliases() {
		return new String[] {"/bb_search"};
	}

	@Override
	public String getDescription() {
		return "Search the retrieval index for ships matching a description.\n" +
				"Usage: /bb_search <description> [k]";
	}

	@Override
	public boolean isAdminOnly() {
		return false;
	}

	@Override
	public boolean onCommand(PlayerState sender, String[] args) {
		if (args.length == 0) {
			PlayerUtils.sendMessage(sender, "[BB] Usage: /bb_search <description> [k]");
			return true;
		}

		// Optional trailing integer = k.
		int k = DEFAULT_K;
		int end = args.length;
		try {
			int maybe = Integer.parseInt(args[args.length - 1]);
			if (maybe > 0 && args.length > 1) {
				k = maybe;
				end = args.length - 1;
			}
		} catch (NumberFormatException ignored) {
			// no trailing k
		}

		StringBuilder q = new StringBuilder();
		for (int i = 0; i < end; i++) {
			if (q.length() > 0) q.append(' ');
			q.append(args[i]);
		}
		final String query = q.toString().replaceAll("^\"|\"$", "").trim();
		final int topK = k;

		new Thread(() -> {
			try {
				RetrievalIndex index = new RetrievalIndex();
				index.load(INDEX_FILE);
				if (index.size() == 0) {
					PlayerUtils.sendMessage(sender, "[BB] Index is empty — run /bb_index first.");
					return;
				}

				AIClient client = new AIClient(AIConfig.load());
				float[] qv = client.embed(query);
				List<RetrievalIndex.Hit> hits = index.query(qv, topK);

				PlayerUtils.sendMessage(sender, "[BB] Top " + hits.size() + " for \"" + query + "\":");
				int rank = 1;
				for (RetrievalIndex.Hit h : hits) {
					String role = h.entry.role.isEmpty() ? "?" : h.entry.role;
					PlayerUtils.sendMessage(sender, String.format("  %d. %.3f  [%s] %s",
							rank++, h.score, role, h.entry.name));
				}
			} catch (Exception e) {
				PlayerUtils.sendMessage(sender, "[BB] search failed: " + e.getMessage());
				BetterBuilding.getInstance().logException("search failed", e);
			}
		}, "BetterBuilding-Search").start();

		return true;
	}

	@Override
	public void serverAction(@Nullable PlayerState sender, String[] args) {
	}

	@Override
	public StarMod getMod() {
		return BetterBuilding.getInstance();
	}
}
