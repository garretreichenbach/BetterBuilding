package videogoose.betterbuilding.render;

import java.util.ArrayList;
import java.util.List;

import org.schema.game.client.data.GameClientState;
import org.schema.game.common.controller.SegmentController;
import org.schema.game.common.data.world.SimpleTransformableSendableObject;

import api.common.GameClient;

/**
 * Supplies the entities worth considering for annotation drawing this frame.
 * <p>
 * Only entities in the player's current sector are returned:
 * {@code getWorldTransformOnClient()} falls back to a stale cached transform for entities
 * elsewhere, which would place annotations at wrong positions.
 */
public class EntityResolver {

	private final List<SegmentController> scratch = new ArrayList<SegmentController>();

	/**
	 * @return segment controllers in the current sector. The returned list is reused
	 * between calls, so callers must not retain it.
	 */
	public List<SegmentController> visibleEntities() {
		scratch.clear();
		GameClientState state = GameClient.getClientState();
		if(state == null || state.getCurrentSectorEntities() == null) {
			return scratch;
		}
		int sector = state.getCurrentSectorId();
		for(SimpleTransformableSendableObject<?> o : state.getCurrentSectorEntities().values()) {
			if(!(o instanceof SegmentController)) {
				continue;
			}
			SegmentController c = (SegmentController) o;
			if(c.getSectorId() != sector || c.getUniqueIdentifier() == null) {
				continue;
			}
			scratch.add(c);
		}
		return scratch;
	}

	/** @return the segment controller with this UID in the current sector, or null. */
	public SegmentController byKey(String entityKey) {
		if(entityKey == null) {
			return null;
		}
		List<SegmentController> all = visibleEntities();
		for(int i = 0; i < all.size(); i++) {
			if(entityKey.equals(all.get(i).getUniqueIdentifier())) {
				return all.get(i);
			}
		}
		return null;
	}
}
