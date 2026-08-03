package videogoose.betterbuilding.render;

import java.util.List;

import javax.vecmath.Vector3f;
import javax.vecmath.Vector4f;

import org.lwjgl.opengl.GL11;
import org.schema.game.common.controller.SegmentController;
import org.schema.schine.graphicsengine.core.Controller;
import org.schema.schine.graphicsengine.core.GlUtil;
import org.schema.schine.graphicsengine.core.Timer;

import api.utils.draw.ModWorldDrawer;
import videogoose.betterbuilding.annotation.Annotation;
import videogoose.betterbuilding.annotation.AnnotationStore;

/**
 * Draws annotation geometry (leader lines, dimension lines and their end ticks) during the
 * world pass, in immediate mode, following the pattern in
 * {@code org.schema.schine.graphicsengine.forms.debug.DebugLine}.
 * <p>
 * Text is deliberately not drawn here. Labels are a 2D overlay and belong in the GUI pass,
 * where orthogonal projection is set up - see {@link AnnotationLabelOverlay}. This split
 * mirrors how the game draws its own indications.
 * <p>
 * We do not push into {@code DebugDrawer}'s static vectors, which are partly gated behind
 * {@code EngineSettings.P_PHYSICS_DEBUG_ACTIVE}.
 */
public class AnnotationGeometryDrawer extends ModWorldDrawer {

	private final AnnotationStore store;
	private final EntityResolver entities;

	private final Vector3f anchorWorld = new Vector3f();
	private final Vector3f labelWorld = new Vector3f();
	private final Vector3f anchorBWorld = new Vector3f();
	private final Vector4f color = new Vector4f();
	private final Vector3f camDelta = new Vector3f();

	/** Half-length of the tick marks drawn at each end of a dimension line, in metres. */
	private static final float TICK_HALF_LENGTH = 0.5f;

	public AnnotationGeometryDrawer(AnnotationStore store, EntityResolver entities) {
		this.store = store;
		this.entities = entities;
	}

	@Override
	public void onInit() {
	}

	@Override
	public void cleanUp() {
	}

	@Override
	public boolean isInvisible() {
		return false;
	}

	@Override
	public void update(Timer timer) {
	}

	@Override
	public void postWorldDraw() {
		if(store.size() == 0) {
			return;
		}
		beginLines();
		try {
			for(SegmentController c : entities.visibleEntities()) {
				List<Annotation> annotations = store.forEntity(c.getUniqueIdentifier());
				for(int i = 0; i < annotations.size(); i++) {
					drawAnnotation(c, annotations.get(i));
				}
			}
		} finally {
			endLines();
		}
	}

	private void drawAnnotation(SegmentController c, Annotation a) {
		if(a.visibility == Annotation.Visibility.HIDDEN || a.anchor == null) {
			return;
		}
		if(a.anchor.toWorld(c, anchorWorld) == null) {
			return;
		}
		if(!withinRange(a, anchorWorld)) {
			return;
		}
		a.getColor(color);

		switch(a.type) {
			case LEADER_LABEL:
				AnnotationGeometry.labelPosition(c, a, anchorWorld, labelWorld);
				line(anchorWorld, labelWorld, color);
				break;
			case DIMENSION:
				if(a.anchorB == null || a.anchorB.toWorld(c, anchorBWorld) == null) {
					return;
				}
				line(anchorWorld, anchorBWorld, color);
				endTicks(anchorWorld, anchorBWorld, color);
				break;
			case LABEL:
			default:
				//nothing to draw in 3D; the label itself is the whole annotation
				break;
		}
	}

	private boolean withinRange(Annotation a, Vector3f worldPos) {
		camDelta.set(worldPos);
		camDelta.sub(Controller.getCamera().getPos());
		return camDelta.lengthSquared() <= a.maxDistance * a.maxDistance;
	}

	/**
	 * Short perpendicular ticks at each end of a dimension line, so the measured extent
	 * reads unambiguously against the geometry behind it.
	 */
	private void endTicks(Vector3f from, Vector3f to, Vector4f c) {
		Vector3f axis = new Vector3f(to);
		axis.sub(from);
		if(axis.lengthSquared() < 1.0e-6f) {
			return;
		}
		axis.normalize();

		//any vector not parallel to the axis works as a seed for the perpendicular
		Vector3f seed = Math.abs(axis.y) > 0.9f ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0);
		Vector3f perp = new Vector3f();
		perp.cross(axis, seed);
		if(perp.lengthSquared() < 1.0e-6f) {
			return;
		}
		perp.normalize();
		perp.scale(TICK_HALF_LENGTH);

		tick(from, perp, c);
		tick(to, perp, c);
	}

	private void tick(Vector3f at, Vector3f perp, Vector4f c) {
		Vector3f a = new Vector3f(at);
		Vector3f b = new Vector3f(at);
		a.sub(perp);
		b.add(perp);
		line(a, b, c);
	}

	private void line(Vector3f from, Vector3f to, Vector4f c) {
		GlUtil.glColor4f(c.x, c.y, c.z, c.w);
		GL11.glVertex3f(from.x, from.y, from.z);
		GL11.glVertex3f(to.x, to.y, to.z);
	}

	private void beginLines() {
		GlUtil.glDisable(GL11.GL_TEXTURE_2D);
		GlUtil.glEnable(GL11.GL_COLOR_MATERIAL);
		GlUtil.glDisable(GL11.GL_LIGHTING);
		GlUtil.glEnable(GL11.GL_BLEND);
		GlUtil.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		GL11.glBegin(GL11.GL_LINES);
	}

	private void endLines() {
		GL11.glEnd();
		GlUtil.glDisable(GL11.GL_BLEND);
		GlUtil.glDisable(GL11.GL_COLOR_MATERIAL);
		GlUtil.glEnable(GL11.GL_LIGHTING);
		GlUtil.glEnable(GL11.GL_TEXTURE_2D);
	}
}
