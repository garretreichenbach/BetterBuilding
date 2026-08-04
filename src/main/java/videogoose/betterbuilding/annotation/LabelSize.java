package videogoose.betterbuilding.annotation;

import org.newdawn.slick.UnicodeFont;
import org.schema.schine.graphicsengine.forms.font.FontLibrary;

/**
 * Text size for an annotation label.
 * <p>
 * Backed by distinct fonts rather than a scale factor: {@code GUITextOverlay} can be
 * scaled, but these are bitmap fonts, so scaling up gives blurry text. Picking a font that
 * was rasterised at the target size keeps it crisp.
 * <p>
 * The outlined Arial variants are used deliberately - annotation text is drawn over
 * arbitrary ship geometry, and the outline is what keeps it legible against a light hull.
 */
public enum LabelSize {

	SMALL("Small"),
	MEDIUM("Medium"),
	LARGE("Large"),
	HUGE("Huge");

	private final String displayName;

	LabelSize(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}

	/**
	 * Resolved lazily rather than cached in a field: FontLibrary builds fonts against the
	 * GL context, so touching it during class init (which can happen off the render thread)
	 * is not safe.
	 */
	public UnicodeFont getFont() {
		switch(this) {
			case SMALL:
				return FontLibrary.getBoldArial12White();
			case LARGE:
				return FontLibrary.getBoldArial20White();
			case HUGE:
				return FontLibrary.getBoldArial32();
			case MEDIUM:
			default:
				return FontLibrary.getBoldArial16White();
		}
	}

	/** @return the size one step larger, or this if already the largest. */
	public LabelSize larger() {
		return ordinal() < values().length - 1 ? values()[ordinal() + 1] : this;
	}

	/** @return the size one step smaller, or this if already the smallest. */
	public LabelSize smaller() {
		return ordinal() > 0 ? values()[ordinal() - 1] : this;
	}

	/** Case-insensitive lookup by name, returning null when there is no match. */
	public static LabelSize byName(String name) {
		if(name == null) {
			return null;
		}
		for(LabelSize s : values()) {
			if(s.name().equalsIgnoreCase(name)) {
				return s;
			}
		}
		return null;
	}
}
