package videogoose.betterbuilding.annotation;

import java.util.HashSet;

/**
 * Which layers are currently hidden.
 * <p>
 * Layers themselves are not a registry: a layer exists exactly when some annotation names
 * it, so creating one is just typing a new name. Only the hidden set needs storing, and
 * only the hidden set, because an empty set is the correct default for a layer nobody has
 * touched.
 * <p>
 * Hidden state is global rather than per-entity. Layers are conceptual groupings ("power",
 * "structural", "todo") that a builder wants consistently on or off while working, not a
 * per-ship setting to re-toggle on every entity.
 */
public class LayerSettings {

	public HashSet<String> hidden = new HashSet<String>();

	public boolean isHidden(String layer) {
		return layer != null && hidden.contains(layer);
	}

	public void setHidden(String layer, boolean hide) {
		if(layer == null) {
			return;
		}
		if(hide) {
			hidden.add(layer);
		} else {
			hidden.remove(layer);
		}
	}
}
