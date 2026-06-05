package videogoose.betterbuilding.gui;

import videogoose.betterbuilding.slot.Slot;

import java.util.LinkedHashMap;

/**
 * Shared state for the palette editor dialog. {@link GUIInputDialog}'s no-arg
 * super constructor builds the panel before subclass fields can be set, so the
 * editing target is passed through this static holder instead.
 *
 * <p>Single-player clientside, single dialog at a time — a static current state
 * is sufficient.
 */
public final class PaletteEditorState {

	public static PaletteEditorState current;

	/** Palette file name (without extension). */
	public final String name;
	/** slot -> assigned base block id (0 = unassigned). Insertion-ordered. */
	public final LinkedHashMap<Slot, Short> draft;

	public PaletteEditorState(String name, LinkedHashMap<Slot, Short> draft) {
		this.name = name;
		this.draft = draft;
	}
}
