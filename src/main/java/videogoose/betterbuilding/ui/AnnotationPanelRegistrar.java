package videogoose.betterbuilding.ui;

import org.schema.game.client.view.gui.advanced.AdvancedGUIElement;
import org.schema.game.client.view.gui.advanced.AdvancedGUIGroup;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIDockableList;
import org.schema.schine.graphicsengine.forms.gui.newgui.GUIDockableList.DockerElementExpandable;

import api.listener.Listener;
import api.listener.events.gui.AdvancedBuildModeGUICreateEvent;
import api.mod.StarLoader;
import api.mod.StarMod;
import videogoose.betterbuilding.annotation.AnnotationStore;

/**
 * Adds the annotation section to the advanced build mode panel.
 * <p>
 * {@code AdvancedBuildModeGUICreateEvent} fires <i>after</i> {@code AdvancedGUIElement}
 * has already walked its group list and built each one into the dockable list, so adding
 * to {@code event.getGroups()} alone would register the group without ever building it.
 * We therefore also do the build step the event missed: add the expandable element and
 * call {@code build} on it, mirroring what the loop in {@code AdvancedGUIElement} does.
 */
public class AnnotationPanelRegistrar {

	private final StarMod mod;
	private final AnnotationStore store;

	public AnnotationPanelRegistrar(StarMod mod, AnnotationStore store) {
		this.mod = mod;
		this.store = store;
	}

	public void register() {
		StarLoader.registerListener(AdvancedBuildModeGUICreateEvent.class, new Listener<AdvancedBuildModeGUICreateEvent>() {
			@Override
			public void onEvent(AdvancedBuildModeGUICreateEvent event) {
				addGroup(event);
			}
		}, mod);
	}

	private void addGroup(AdvancedBuildModeGUICreateEvent event) {
		GUIDockableList list = event.getDockableList();
		if(list == null || event.getGroups() == null || event.getGroups().isEmpty()) {
			return;
		}
		//the event carries no direct handle on the owning AdvancedGUIElement, but every
		//group holds one, and the build mode panel always has at least one group by now
		AdvancedGUIElement owner = event.getGroups().get(0).getMainElement();
		if(owner == null) {
			System.err.println("[BetterBuilding] could not resolve advanced GUI owner; annotation panel not added");
			return;
		}
		for(AdvancedGUIGroup existing : event.getGroups()) {
			if(existing instanceof AnnotationBuildModeGroup) {
				return; //already added; the panel can be rebuilt
			}
		}
		AnnotationBuildModeGroup group = new AnnotationBuildModeGroup(owner, store);
		event.getGroups().add(group);
		if(group.isHidden()) {
			return;
		}
		DockerElementExpandable el = list.addElementExpanded(group.getWindowId(), group.getTitle(),
				group.isClosable(), group.getCloseCallback(), group.getSubListIndex(),
				group.getBackgroundColor(), group.isDefaultExpanded());
		el.build(group);
	}
}
