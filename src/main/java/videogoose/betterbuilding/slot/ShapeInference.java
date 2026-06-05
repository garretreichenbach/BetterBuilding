package videogoose.betterbuilding.slot;

import org.schema.game.common.data.element.ElementInformation;
import org.schema.game.common.data.element.ElementKeyMap;

/**
 * Lifts a concrete StarMade block back into slot space: derives its {@link Shape}
 * and its base/cube block (so a hand-built selection can be matched against a
 * palette).
 */
public final class ShapeInference {

	private ShapeInference() {}

	public static Shape shapeOf(short type) {
		return Shape.of(type);
	}

	/**
	 * The base ("cube") block of any shape/slab variant. The base carries the
	 * {@code styleIds}/{@code slabIds} arrays used for shape-aware remapping.
	 */
	public static short baseOf(short type) {
		ElementInformation info = ElementKeyMap.getInfoFast(type);
		if (info == null) return type;
		int src = info.getSourceReference();
		return (src != 0 && ElementKeyMap.isValidType(src)) ? (short) src : type;
	}
}
