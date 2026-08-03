package videogoose.betterbuilding.render;

import java.util.ArrayList;
import java.util.List;

import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;

import org.schema.game.client.data.GameClientState;
import org.schema.game.common.controller.SegmentController;
import org.schema.schine.graphicsengine.camera.Camera;
import org.schema.schine.graphicsengine.core.Controller;
import org.schema.schine.graphicsengine.core.Timer;
import org.schema.schine.graphicsengine.forms.gui.GUIElement;
import org.schema.schine.graphicsengine.forms.gui.GUITextOverlay;
import org.schema.schine.graphicsengine.util.WorldToScreenConverter;

import videogoose.betterbuilding.annotation.Annotation;
import videogoose.betterbuilding.annotation.AnnotationStore;

/**
 * Draws annotation text during the GUI pass, where orthogonal projection is already set up.
 * <p>
 * Added to the HUD via {@code HudCreateEvent.addElement}, which gets {@code onInit},
 * {@code update} and {@code draw} called for us. Projection is done here rather than by
 * reusing {@code HudIndicatorOverlay.drawString}, for two reasons: that method mutates a
 * text overlay shared with the game's own indications, and it is only reachable through
 * {@code getHud()}, which exists on the new HUD but not the basic one.
 */
public class AnnotationLabelOverlay extends GUIElement {

	private final AnnotationStore store;
	private final EntityResolver entities;

	private GUITextOverlay text;

	private final Vector3f anchorWorld = new Vector3f();
	private final Vector3f labelWorld = new Vector3f();
	private final Vector3f onScreen = new Vector3f();
	private final Vector3f camToLabel = new Vector3f();
	private final Vector4f color = new Vector4f();

	public AnnotationLabelOverlay(GameClientState state, AnnotationStore store, EntityResolver entities) {
		super(state);
		this.store = store;
		this.entities = entities;
	}

	@Override
	public void onInit() {
		text = new GUITextOverlay(32, 32, getState());
		text.setTextSimple("");
		text.onInit();
	}

	@Override
	public void update(Timer timer) {
	}

	@Override
	public void draw() {
		if(text == null || store.size() == 0) {
			return;
		}
		WorldToScreenConverter converter = converter();
		if(converter == null) {
			return;
		}
		Camera camera = Controller.getCamera();
		if(camera == null) {
			return;
		}
		List<SegmentController> visible = entities.visibleEntities();
		for(int e = 0; e < visible.size(); e++) {
			SegmentController c = visible.get(e);
			List<Annotation> annotations = store.forEntity(c.getUniqueIdentifier());
			for(int i = 0; i < annotations.size(); i++) {
				drawLabel(c, annotations.get(i), converter, camera);
			}
		}
	}

	private void drawLabel(SegmentController c, Annotation a, WorldToScreenConverter converter, Camera camera) {
		if(a.visibility == Annotation.Visibility.HIDDEN || a.anchor == null) {
			return;
		}
		String caption = caption(a);
		if(caption.isEmpty()) {
			return;
		}
		if(a.anchor.toWorld(c, anchorWorld) == null) {
			return;
		}
		AnnotationGeometry.labelPosition(c, a, anchorWorld, labelWorld);

		camToLabel.sub(labelWorld, camera.getPos());
		float distance = camToLabel.length();
		if(distance > a.maxDistance) {
			return;
		}
		//Behind the camera the converter deliberately throws the point millions of pixels
		//off screen rather than reporting failure, so cull on the dot product instead.
		if(distance > 0 && camera.getForward().dot(camToLabel) < 0) {
			return;
		}
		converter.convert(labelWorld, onScreen, true, camera);

		a.getColor(color);
		text.setTextSimple(caption);
		text.getPos().set((int) onScreen.x, (int) onScreen.y, 0);
		text.setColor(color.x, color.y, color.z, color.w);
		text.draw();
	}

	private String caption(Annotation a) {
		switch(a.type) {
			case DIMENSION:
				float blocks = AnnotationGeometry.measureBlocks(a);
				if(blocks < 0) {
					return a.text == null ? "" : a.text;
				}
				String measured = formatBlocks(blocks);
				return a.text == null || a.text.isEmpty() ? measured : a.text + ": " + measured;
			default:
				return a.text == null ? "" : a.text;
		}
	}

	private String formatBlocks(float blocks) {
		//whole numbers are the common case (axis-aligned measurements); avoid "5.0m"
		if(Math.abs(blocks - Math.round(blocks)) < 0.01f) {
			return Math.round(blocks) + "m";
		}
		return String.format("%.1fm", blocks);
	}

	private WorldToScreenConverter converter() {
		GameClientState state = (GameClientState) getState();
		if(state == null || state.getScene() == null) {
			return null;
		}
		return state.getScene().getWorldToScreenConverter();
	}

	@Override
	public void cleanUp() {
		if(text != null) {
			text.cleanUp();
			text = null;
		}
	}

	@Override
	public float getWidth() {
		return 0;
	}

	@Override
	public float getHeight() {
		return 0;
	}
}
