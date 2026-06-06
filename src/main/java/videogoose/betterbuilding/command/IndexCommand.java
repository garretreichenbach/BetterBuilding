package videogoose.betterbuilding.command;

import api.mod.StarMod;
import api.utils.game.PlayerUtils;
import api.utils.game.chat.CommandInterface;
import org.schema.game.common.data.player.PlayerState;

import javax.annotation.Nullable;
import java.io.File;

import videogoose.betterbuilding.BetterBuilding;
import videogoose.betterbuilding.ai.AIClient;
import videogoose.betterbuilding.ai.AIConfig;
import videogoose.betterbuilding.ai.RetrievalIndex;

/**
 * Phase-1: (re)build the retrieval index by embedding every caption in
 * {@code BetterBuilding/captions/} → {@code BetterBuilding/index.json}.
 * Requires LM Studio to have an embedding model loaded (see {@code embed_model}
 * in {@code ai.properties}). Runs on a background thread.
 */
public class IndexCommand implements CommandInterface {

	private static final File CAPTION_DIR = new File("./BetterBuilding/captions");
	private static final File INDEX_FILE = new File("./BetterBuilding/index.json");

	@Override
	public String getCommand() {
		return "bb_index";
	}

	@Override
	public String[] getAliases() {
		return new String[] {"/bb_index"};
	}

	@Override
	public String getDescription() {
		return "Rebuild the retrieval index by embedding all captions.\n" +
				"Requires an embedding model loaded in LM Studio (embed_model in ai.properties).\n" +
				"Usage: /bb_index";
	}

	@Override
	public boolean isAdminOnly() {
		return false;
	}

	@Override
	public boolean onCommand(PlayerState sender, String[] args) {
		File[] caps = CAPTION_DIR.listFiles((d, n) -> n.endsWith(".json"));
		if (caps == null || caps.length == 0) {
			PlayerUtils.sendMessage(sender, "[BB] No captions in " + CAPTION_DIR.getPath() + " — run /bb_caption first.");
			return true;
		}

		PlayerUtils.sendMessage(sender, "[BB] Indexing " + caps.length + " caption(s)...");

		new Thread(() -> {
			try {
				AIClient client = new AIClient(AIConfig.load());
				RetrievalIndex index = new RetrievalIndex();
				int n = index.buildFromCaptions(CAPTION_DIR, client, (done, total, name) ->
						BetterBuilding.getInstance().logInfo("[BB] indexed " + done + "/" + total + ": " + name));
				index.save(INDEX_FILE);
				PlayerUtils.sendMessage(sender, "[BB] Index built: " + n + " ship(s) -> " + INDEX_FILE.getName());
			} catch (Exception e) {
				PlayerUtils.sendMessage(sender, "[BB] index failed: " + e.getMessage());
				BetterBuilding.getInstance().logException("index failed", e);
			}
		}, "BetterBuilding-Index").start();

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
