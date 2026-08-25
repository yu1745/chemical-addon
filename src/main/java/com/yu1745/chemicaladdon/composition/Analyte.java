package com.yu1745.chemicaladdon.composition;

/**
 * Player-facing instrument readings as pure functions over a vessel's ion /
 * molecular / suspended domains (U17, plans/04 §9.1 measurement honesty): the
 * gauges and test papers answer "how much / how far", never "what". Everything
 * here is a <b>declared</b> physical scale — the same philosophy as the
 * solubility curves and the U16 heat ledger — and is deliberately
 * species-blind: a hydrometer reports how much is dissolved, not what.
 *
 * <p>All inputs are solver units (mB × {@link Chemistry#UNIT_PER_MB}).
 */
public final class Analyte {

	private Analyte() {}

	// ------------------------------------------------------------------- pH

	/**
	 * pH 0–14 of an aqueous phase from its H⁺ / OH⁻ ion units and water units
	 * ({@code pH = −log₁₀[H⁺]}; alkaline side via {@code [H⁺] = Kw/[OH⁻]},
	 * {@link Chemistry#KW}). After the solver's neutralisation runs, at most one
	 * of the two ions survives in bulk, so the three cases are exhaustive:
	 * acid present (direct), hydroxide present (Kw), neither (a neutral salt
	 * solution or pure water — pH 7, the free lunch plans/04 §9.5 promised).
	 */
	public static int ph(long hUnits, long ohUnits, long waterUnits) {
		if (waterUnits <= 0) {
			return 7; // nothing to read: a dry gauge shows neutral, not nonsense
		}
		if (hUnits >= 1) {
			return clamp(Math.round(-Math.log10((double) hUnits / waterUnits)), 0, 14);
		}
		if (ohUnits >= 1) {
			// pH = −log10(Kw/[OH⁻]) = 14 + log10([OH⁻])
			return clamp(Math.round(14 + Math.log10((double) ohUnits / waterUnits)), 0, 14);
		}
		return 7;
	}

	// ------------------------------------------------------------------ °Bé

	/**
	 * Declared Baumé scale anchor: a <b>curve-saturated NaCl brine</b> reads
	 * 30 °Bé — the hydrometer float the salt works have always aimed at. In
	 * engine units saturated brine carries 0.36 formula units of NaCl per
	 * water unit, each splitting into one Na⁺ + one Cl⁻ unit, so the dissolved
	 * (Σ ion + solute) / water ratio is 0.72 — that is the anchor. Linear in
	 * total dissolved concentration above it: same concentration of a
	 * different salt reads (almost) the same, which is the instrument's
	 * honesty, not a defect.
	 */
	public static final double BAUME_AT_SATURATED_BRINE = 30.0;
	public static final double SATURATED_BRINE_CONCENTRATION = 0.72;

	/** °Bé 0–30 of an aqueous phase (dissolved = all ion units + non-water molecular solute units). */
	public static int baume(long dissolvedUnits, long waterUnits) {
		if (waterUnits <= 0) {
			return 0;
		}
		double concentration = (double) dissolvedUnits / waterUnits;
		double be = concentration * BAUME_AT_SATURATED_BRINE / SATURATED_BRINE_CONCENTRATION;
		return clamp(Math.round(be), 0, 30);
	}

	// -------------------------------------------------------------- turbidity

	/** Turbidity bin edges: suspended units / water units at 微浑 / 浑 / 浆. */
	public static final double TURBIDITY_SLIGHT = 0.01;
	public static final double TURBIDITY_TURBID = 0.05;
	public static final double TURBIDITY_SLURRY = 0.2;

	/**
	 * Turbidity 0–3 (清 / 微浑 / 浑 / 浆): the suspended-solid volume fraction of
	 * the aqueous phase. The settled bed ({@code Sediment}) is excluded — a
	 * crystal bed under clear liquor reads clear, which is exactly the
	 * "is the precipitate done settling" distinction a settling-basin operator
	 * makes by eye.
	 */
	public static int turbidityBin(long suspendedUnits, long waterUnits) {
		if (waterUnits <= 0) {
			return 0;
		}
		double fraction = (double) suspendedUnits / waterUnits;
		if (fraction < TURBIDITY_SLIGHT) {
			return 0;
		}
		if (fraction < TURBIDITY_TURBID) {
			return 1;
		}
		if (fraction < TURBIDITY_SLURRY) {
			return 2;
		}
		return 3;
	}

	private static int clamp(long v, int lo, int hi) {
		return (int) Math.max(lo, Math.min(hi, v));
	}
}
