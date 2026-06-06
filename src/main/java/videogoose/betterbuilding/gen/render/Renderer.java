package videogoose.betterbuilding.gen.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Pure-Java isometric voxel renderer. Projects a {@link VoxelView} to a 2:1
 * isometric image by painting each surface voxel's three camera-facing cube
 * faces (top / +X / +Z) back-to-front, with per-face shading for depth cues.
 *
 * <p>No OpenGL and no game textures: each block contributes a single colour
 * (see {@code rgb}). This is enough for a VLM to read silhouette, proportions
 * and major features for captioning and the critique loop. Runs headless.
 */
public final class Renderer {

	/** Shading multipliers per face so the form reads in 3D. */
	private static final float SHADE_TOP = 1.00f;
	private static final float SHADE_X = 0.82f;   // +X faces
	private static final float SHADE_Z = 0.64f;   // +Z faces

	private static final int BACKGROUND = 0x101418; // dark slate

	private Renderer() {}

	/**
	 * Render an isometric view, fitting the model within roughly {@code targetSize}
	 * pixels on the larger axis (plus a small margin).
	 */
	public static BufferedImage renderIso(VoxelView v, int targetSize) {
		final int dx = v.dimX(), dy = v.dimY(), dz = v.dimZ();

		// Isometric unit projection (tile t = 1): a +1 step in each axis maps to:
		//   +X -> (+t, +t/2)   +Z -> (-t, +t/2)   +Y(up) -> (0, -t)
		// Screen extent in unit space (over the full [0..dx]x[0..dy]x[0..dz] box):
		double spanX = (dx + dz);                 // width  in units of t
		double spanY = (dx + dz) / 2.0 + dy;      // height in units of t
		double t = Math.max(1.0, (targetSize - 16) / Math.max(spanX, spanY));

		// Compute pixel bounds by projecting the 8 box corners.
		double minSx = Double.MAX_VALUE, maxSx = -Double.MAX_VALUE;
		double minSy = Double.MAX_VALUE, maxSy = -Double.MAX_VALUE;
		for (int cx = 0; cx <= dx; cx += dx == 0 ? 1 : dx) {
			for (int cy = 0; cy <= dy; cy += dy == 0 ? 1 : dy) {
				for (int cz = 0; cz <= dz; cz += dz == 0 ? 1 : dz) {
					double sx = (cx - cz) * t;
					double sy = (cx + cz) * (t / 2.0) - cy * t;
					minSx = Math.min(minSx, sx);
					maxSx = Math.max(maxSx, sx);
					minSy = Math.min(minSy, sy);
					maxSy = Math.max(maxSy, sy);
				}
			}
		}

		int margin = 8;
		int w = (int) Math.ceil(maxSx - minSx) + margin * 2;
		int h = (int) Math.ceil(maxSy - minSy) + margin * 2;
		w = Math.max(1, w);
		h = Math.max(1, h);
		final double offX = margin - minSx;
		final double offY = margin - minSy;

		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		g.setColor(new Color(BACKGROUND));
		g.fillRect(0, 0, w, h);

		// Painter's algorithm: for an orthographic isometric camera along (1,1,1),
		// depth = x + y + z. Draw far (small sum) first so nearer voxels overwrite.
		int maxSum = (dx - 1) + (dy - 1) + (dz - 1);
		for (int sum = 0; sum <= maxSum; sum++) {
			for (int x = 0; x < dx; x++) {
				for (int y = 0; y < dy; y++) {
					int z = sum - x - y;
					if (z < 0 || z >= dz) continue;
					if (!v.isSolid(x, y, z)) continue;
					if (isInterior(v, x, y, z)) continue;
					drawVoxel(g, v.rgb(x, y, z), x, y, z, t, offX, offY);
				}
			}
		}

		g.dispose();
		return img;
	}

	/** A voxel fully surrounded by solid neighbours contributes nothing visible. */
	private static boolean isInterior(VoxelView v, int x, int y, int z) {
		return v.isSolid(x - 1, y, z) && v.isSolid(x + 1, y, z)
				&& v.isSolid(x, y - 1, z) && v.isSolid(x, y + 1, z)
				&& v.isSolid(x, y, z - 1) && v.isSolid(x, y, z + 1);
	}

	private static void drawVoxel(Graphics2D g, int rgb, int x, int y, int z,
								  double t, double offX, double offY) {
		// Top face (+Y): draw only if the cell above is air-exposed in the view —
		// but painter's order makes overdraw harmless, so we draw all three
		// camera-facing faces of every surface voxel.
		// Corner projector (block-space corner -> screen point).
		// Faces (CCW corner lists):
		fillFace(g, shade(rgb, SHADE_TOP), t, offX, offY,
				x, y + 1, z, x + 1, y + 1, z, x + 1, y + 1, z + 1, x, y + 1, z + 1);     // top +Y
		fillFace(g, shade(rgb, SHADE_X), t, offX, offY,
				x + 1, y, z, x + 1, y + 1, z, x + 1, y + 1, z + 1, x + 1, y, z + 1);     // right +X
		fillFace(g, shade(rgb, SHADE_Z), t, offX, offY,
				x, y, z + 1, x, y + 1, z + 1, x + 1, y + 1, z + 1, x + 1, y, z + 1);     // left +Z
	}

	private static void fillFace(Graphics2D g, int color, double t, double offX, double offY,
								 int x0, int y0, int z0, int x1, int y1, int z1,
								 int x2, int y2, int z2, int x3, int y3, int z3) {
		Polygon p = new Polygon();
		addPoint(p, x0, y0, z0, t, offX, offY);
		addPoint(p, x1, y1, z1, t, offX, offY);
		addPoint(p, x2, y2, z2, t, offX, offY);
		addPoint(p, x3, y3, z3, t, offX, offY);
		g.setColor(new Color(color));
		g.fillPolygon(p);
	}

	private static void addPoint(Polygon p, int x, int y, int z, double t, double offX, double offY) {
		int sx = (int) Math.round((x - z) * t + offX);
		int sy = (int) Math.round((x + z) * (t / 2.0) - y * t + offY);
		p.addPoint(sx, sy);
	}

	private static int shade(int rgb, float f) {
		int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * f));
		int gg = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * f));
		int b = Math.min(255, Math.round((rgb & 0xFF) * f));
		return (r << 16) | (gg << 8) | b;
	}
}
