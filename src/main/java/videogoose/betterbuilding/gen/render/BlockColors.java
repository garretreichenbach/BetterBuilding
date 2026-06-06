package videogoose.betterbuilding.gen.render;

import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.element.ElementKeyMap;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps a StarMade block type id to a representative RGB colour for the software
 * {@link Renderer}, by averaging the block's actual texture — the same approach
 * the engine's {@code AdvancedBuildModeColorFinder} uses: take the front-face
 * {@code textureId}, locate its tile in the 16×16 texture sheet, average it.
 *
 * <p>Computed at runtime from the live block config + on-disk texture sheets and
 * cached per type, so it automatically reflects whatever blocks the game/mods
 * currently define — nothing to re-bake when blocks are added or removed.
 *
 * <p>Atlas layout (from the engine): sheets {@code t###.png} of 16×16 tiles at
 * resolution {@code RES}; for a {@code textureId}: sheet = id/256, tile = id%256,
 * tileX = tile%16, tileY = tile/16.
 */
public final class BlockColors {

	private static final int DEFAULT_RGB = 0x8b9298;
	private static final int RES = 64;            // smallest sheet set; fine for averaging
	private static final int TILES = 16;          // 16×16 tiles per sheet
	private static final int TILES_PER_SHEET = TILES * TILES;

	/** Active texture pack dir, relative to the game working directory. */
	private static final File SHEET_DIR = new File("./data/textures/block/Default/" + RES);

	private final Map<Short, Integer> colorCache = new HashMap<>();
	private final Map<Integer, BufferedImage> sheetCache = new HashMap<>();
	private final Map<Integer, Boolean> sheetMissing = new HashMap<>();

	public int colorFor(short type) {
		Integer cached = colorCache.get(type);
		if (cached != null) return cached;
		int rgb = compute(type);
		colorCache.put(type, rgb);
		return rgb;
	}

	private int compute(short type) {
		try {
			ElementInformation info = ElementKeyMap.getInfo(type);
			if (info == null) return DEFAULT_RGB;
			int textureId = info.getTextureId(0); // front face, as the engine does
			int sheetIndex = textureId / TILES_PER_SHEET;
			BufferedImage sheet = loadSheet(sheetIndex);
			if (sheet == null) return DEFAULT_RGB;

			int tile = textureId % TILES_PER_SHEET;
			int x = (tile % TILES) * RES;
			int y = (tile / TILES) * RES;
			if (x < 0 || y < 0 || x + RES > sheet.getWidth() || y + RES > sheet.getHeight()) {
				return DEFAULT_RGB;
			}
			return averageRgb(sheet.getSubimage(x, y, RES, RES));
		} catch (Exception e) {
			return DEFAULT_RGB;
		}
	}

	private BufferedImage loadSheet(int sheetIndex) {
		if (Boolean.TRUE.equals(sheetMissing.get(sheetIndex))) return null;
		BufferedImage cached = sheetCache.get(sheetIndex);
		if (cached != null) return cached;
		File f = new File(SHEET_DIR, String.format("t%03d.png", sheetIndex));
		if (!f.isFile()) {
			sheetMissing.put(sheetIndex, true);
			return null;
		}
		try {
			BufferedImage sheet = ImageIO.read(f);
			sheetCache.put(sheetIndex, sheet);
			return sheet;
		} catch (Exception e) {
			sheetMissing.put(sheetIndex, true);
			return null;
		}
	}

	/** Alpha-weighted average so transparent edges (glass, decals) don't wash out the colour. */
	private static int averageRgb(BufferedImage tile) {
		long r = 0, g = 0, b = 0, aSum = 0;
		int w = tile.getWidth(), h = tile.getHeight();
		for (int j = 0; j < h; j++) {
			for (int i = 0; i < w; i++) {
				int argb = tile.getRGB(i, j);
				int a = (argb >>> 24) & 0xFF;
				r += ((argb >> 16) & 0xFF) * a;
				g += ((argb >> 8) & 0xFF) * a;
				b += (argb & 0xFF) * a;
				aSum += a;
			}
		}
		if (aSum == 0) return DEFAULT_RGB;
		int rr = (int) (r / aSum), gg = (int) (g / aSum), bb = (int) (b / aSum);
		return (rr << 16) | (gg << 8) | bb;
	}
}
