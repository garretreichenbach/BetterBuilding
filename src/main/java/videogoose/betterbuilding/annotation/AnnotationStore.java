package videogoose.betterbuilding.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import api.mod.StarMod;
import api.mod.config.PersistentObjectUtil;

/**
 * In-memory store of annotations, grouped by entity so the draw loop is a single map hit
 * rather than a scan over everything the player has ever annotated.
 * <p>
 * Backed by {@link PersistentObjectUtil} for durability. Annotations we author are local
 * data: there is no client-to-server API for writing the game's own per-block JSON
 * metadata, so that remains a read-only import source until this mod grows a server
 * component.
 */
public class AnnotationStore {

	private final StarMod mod;

	/** entityKey to annotations. Rebuilt from the flat persistent list on load. */
	private final Map<String, List<Annotation>> byEntity = new HashMap<String, List<Annotation>>();

	private static final List<Annotation> NONE = Collections.emptyList();

	public AnnotationStore(StarMod mod) {
		this.mod = mod;
	}

	/**
	 * Loads persisted annotations and rebuilds the per-entity index.
	 * Entries with no anchor are dropped: they cannot be drawn or repaired.
	 */
	public void load() {
		byEntity.clear();
		int dropped = 0;
		for(Object o : PersistentObjectUtil.getObjects(mod.getSkeleton(), Annotation.class)) {
			Annotation a = (Annotation) o;
			if(a.getEntityKey() == null) {
				dropped++;
				continue;
			}
			index(a);
		}
		if(dropped > 0) {
			System.err.println("[BetterBuilding] dropped " + dropped + " annotation(s) with no anchor");
		}
	}

	public void save() {
		PersistentObjectUtil.save(mod.getSkeleton());
	}

	public void add(Annotation a) {
		if(a.getEntityKey() == null) {
			throw new IllegalArgumentException("annotation has no anchor: " + a);
		}
		index(a);
		PersistentObjectUtil.addObject(mod.getSkeleton(), a);
		save();
	}

	public boolean remove(Annotation a) {
		List<Annotation> list = byEntity.get(a.getEntityKey());
		if(list != null) {
			list.remove(a);
			if(list.isEmpty()) {
				byEntity.remove(a.getEntityKey());
			}
		}
		boolean removed = PersistentObjectUtil.removeObject(mod.getSkeleton(), a);
		if(removed) {
			save();
		}
		return removed;
	}

	/**
	 * @return annotations on the given entity. Never null; the returned list is the live
	 * one, so callers must not mutate it structurally while drawing.
	 */
	public List<Annotation> forEntity(String entityKey) {
		if(entityKey == null) {
			return NONE;
		}
		List<Annotation> list = byEntity.get(entityKey);
		return list == null ? NONE : list;
	}

	public boolean hasAnnotations(String entityKey) {
		return !forEntity(entityKey).isEmpty();
	}

	public int size() {
		int n = 0;
		for(List<Annotation> list : byEntity.values()) {
			n += list.size();
		}
		return n;
	}

	private void index(Annotation a) {
		List<Annotation> list = byEntity.get(a.getEntityKey());
		if(list == null) {
			list = new ArrayList<Annotation>();
			byEntity.put(a.getEntityKey(), list);
		}
		list.add(a);
	}
}
