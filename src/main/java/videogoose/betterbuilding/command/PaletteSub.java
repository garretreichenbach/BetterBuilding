package videogoose.betterbuilding.command;

import api.utils.game.PlayerUtils;
import api.utils.textures.StarLoaderTexture;
import org.schema.game.common.data.player.PlayerState;
import videogoose.betterbuilding.content.Palette;
import videogoose.betterbuilding.content.PaletteLibrary;
import videogoose.betterbuilding.gui.PaletteEditorDialog;
import videogoose.betterbuilding.gui.PaletteEditorState;
import videogoose.betterbuilding.slot.Slot;
import videogoose.betterbuilding.slot.SlotRegistry;

import java.io.File;
import java.util.LinkedHashMap;

/**
 * {@code /bb palette <name>} — open the click-to-assign palette editor for the
 * named palette (loading it if it already exists). Pick a block in your hotbar,
 * click a slot to assign it, then Save.
 */
public class PaletteSub implements SubCommand {

	private static final Slot[] BUILTINS = {
			SlotRegistry.PRIMARY_HULL, SlotRegistry.SECONDARY_HULL, SlotRegistry.ACCENT,
			SlotRegistry.GLASS, SlotRegistry.LIGHT, SlotRegistry.SYSTEM
	};

	@Override
	public String name() {
		return "palette";
	}

	@Override
	public String usage() {
		return "palette <name>";
	}

	@Override
	public String description() {
		return "Open the palette editor (assign blocks to slots) for <name>.";
	}

	@Override
	public void run(PlayerState sender, String[] args) {
		if (args.length < 1) {
			PlayerUtils.sendMessage(sender, "Usage: /bb " + usage());
			java.util.List<String> names = PaletteLibrary.names();
			if (!names.isEmpty()) {
				PlayerUtils.sendMessage(sender, "[bb] Existing palettes: " + String.join(", ", names));
			}
			return;
		}
		String pname = args[0].endsWith(".json") ? args[0].substring(0, args[0].length() - 5) : args[0];

		LinkedHashMap<Slot, Short> draft = new LinkedHashMap<>();
		for (Slot s : BUILTINS) draft.put(s, (short) 0);

		File f = PaletteLibrary.file(pname);
		if (f.exists()) {
			try {
				Palette existing = Palette.load(f);
				for (Slot s : existing.slots()) draft.put(s, existing.base(s));
			} catch (Exception e) {
				PlayerUtils.sendMessage(sender, "[bb] (couldn't load existing '" + pname + "': " + e.getMessage() + ") — starting fresh.");
			}
		}

		PaletteEditorState.current = new PaletteEditorState(pname, draft);
		// The command runs on the server thread; GUI creation makes OpenGL calls
		// that must happen on the client graphics thread.
		StarLoaderTexture.runOnGraphicsThread(new Runnable() {
			@Override
			public void run() {
				new PaletteEditorDialog().activate();
			}
		});
		PlayerUtils.sendMessage(sender, "[bb] Opened palette editor for '" + pname + "'.");
	}
}
