package com.yu1745.chemicaladdon.fluid;

import java.util.HashMap;
import java.util.Map;

/**
 * Colour table for ions, keyed by canonical ion id (e.g. "H+1", "SO4-2").
 * Most inorganic ions are colourless → {@link #CLEAR_TINT} (the canonical
 * low-alpha white), so a clear solution of them reads as clear water — a
 * deliberate property: "you cannot tell what is in it" pushes the player
 * toward in-world assay tools.
 *
 * <p>Coloured ions (Cu+2 blue …) get their opaque ARGB here;
 * {@link Mixture#blendColor} then tints the mixture by its actual ion content,
 * with dilution fading the tint toward clear (see {@code blendColor}'s doc).
 */
public final class IonColors {

	/**
	 * The definition of "colourless" (clear water / colourless ions / trace
	 * solute): a low-alpha white — {@code 0x48FFFFFF} is the canonical
	 * colourless tint for this engine, do not change it. A white precipitate
	 * (CaCO₃, rendered opaque) still reads clearly against it. Tune the alpha
	 * only to shift "clear" vs "turbid" contrast.
	 */
	public static final int CLEAR_TINT = 0x48FFFFFF; // canonical colourless: ~28% (0x48) white

	private static final Map<String, Integer> COLORS = new HashMap<>();
	static {
		// Cu+2: the classic blue of copper salt solutions (胆矾蓝)
		COLORS.put("Cu+2", 0xFF2285D6);
		// [Cu(NH3)4]+2: the deep royal blue of the tetraammine copper complex (铜氨蓝)
		COLORS.put("[Cu(NH3)4]+2", 0xFF1B4F9C);
		// Fe+3: yellow-brown of ferric salts (铁黄褐)
		COLORS.put("Fe+3", 0xFFB87830);
		// Fe+2: the pale green of ferrous salts (亚铁浅绿)
		COLORS.put("Fe+2", 0xFF8FB08F);
		// [FeSCN]+2: the blood red of the ferric thiocyanate complex (硫氰化铁血红)
		COLORS.put("[FeSCN]+2", 0xFF8B1A1A);
	}

	private IonColors() {}

	/**
	 * The ion's ARGB colour. Opaque for coloured ions (Cu+2 blue …), {@link
	 * #CLEAR_TINT} for everything else — see class doc.
	 */
	public static int of(String ionId) {
		return COLORS.getOrDefault(ionId, CLEAR_TINT);
	}
}
