package videogoose.betterbuilding;


import api.listener.Listener;
import api.listener.events.draw.RegisterWorldDrawersEvent;
import api.listener.events.gui.HudCreateEvent;
import api.mod.StarLoader;
import api.mod.StarMod;
import videogoose.betterbuilding.annotation.AnnotationStore;
import videogoose.betterbuilding.input.AnnotationControls;
import videogoose.betterbuilding.render.AnnotationGeometryDrawer;
import videogoose.betterbuilding.render.AnnotationLabelOverlay;
import videogoose.betterbuilding.render.EntityResolver;

/**
 * Building utilities for StarMade, centred on in-world annotations: labels, leader lines
 * and measurements that make a ship document itself.
 * <p>
 * Client-only. Annotations we author live in local storage; annotations already baked into
 * an entity's per-block JSON metadata are read-only from here, since there is no
 * client-to-server API for writing it.
 */
public class BetterBuilding extends StarMod {

	private static BetterBuilding instance;

	private AnnotationStore store;
	private EntityResolver entities;
	private AnnotationControls controls;

	public BetterBuilding() {
	}

	public static BetterBuilding getInstance() {
		return instance;
	}

	public AnnotationStore getStore() {
		return store;
	}

	public EntityResolver getEntities() {
		return entities;
	}

	public static void main(String[] args) {
	}

	@Override
	public void onEnable() {
		instance = this;

		store = new AnnotationStore(this);
		store.load();
		entities = new EntityResolver();

		registerWorldDrawer();
		registerHudOverlay();

		controls = new AnnotationControls(this, store);
		controls.register();
	}

	/** Geometry (leader lines, dimension lines) is drawn in the world pass. */
	private void registerWorldDrawer() {
		StarLoader.registerListener(RegisterWorldDrawersEvent.class, new Listener<RegisterWorldDrawersEvent>() {
			@Override
			public void onEvent(RegisterWorldDrawersEvent event) {
				event.getModDrawables().add(new AnnotationGeometryDrawer(store, entities));
			}
		}, this);
	}

	/** Text is a 2D overlay, so it is drawn in the GUI pass instead. */
	private void registerHudOverlay() {
		StarLoader.registerListener(HudCreateEvent.class, new Listener<HudCreateEvent>() {
			@Override
			public void onEvent(HudCreateEvent event) {
				event.addElement(new AnnotationLabelOverlay(event.getInputState(), store, entities));
			}
		}, this);
	}

}
