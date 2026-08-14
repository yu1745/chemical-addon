package com.yu1745.chemicaladdon.fluid;

/**
 * Colour table for ions, keyed by canonical ion id (e.g. "H+1", "SO4-2").
 * The main-line inorganic ions are all colourless, so any mixture of them reads
 * as clear water — a deliberate property: "you cannot tell what is in it" is the
 * point, and pushes the player toward in-world assay tools.
 *
 * <p>Future coloured ions (Cu+2 blue, Fe+3 yellow-brown …) get their ARGB here;
 * {@link Mixture#blendColor} then tints the mixture by its actual ion content.
 */
public final class IonColors {

	/**
	 * The faint tint of colourless contents (clear water / colourless ions): a
	 * low-alpha white. Low enough that a white precipitate (CaCO₃, rendered
	 * opaque) reads clearly against it, but non-zero so the liquid surface stays
	 * visible. Tune this one alpha to shift "clear" vs "turbid" contrast.
	 */
	public static final int CLEAR_TINT = 0x28FFFFFF; // ~16% opacity faint white

	private IonColors() {}

	/**
	 * The ion's ARGB colour. Main-line inorganic ions are colourless → {@link
	 * #CLEAR_TINT} (faint white), so a clear solution of them reads as clear water
	 * rather than opaque white — which lets a white precipitate stand out. Coloured
	 * ions (Cu+2 blue, Fe+3 yellow-brown …) later get an opaque ARGB here and
	 * {@link Mixture#blendColor} tints by the actual ion content.
	 */
	public static int of(String ionId) {
		return CLEAR_TINT; // colourless by default — see class doc
	}
}
