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

	private IonColors() {}

	/** The ion's ARGB colour, or opaque white (no tint) for colourless ions. */
	public static int of(String ionId) {
		return 0xFFFFFFFF; // colourless by default — see class doc
	}
}
