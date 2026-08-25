package com.yu1745.chemicaladdon.composition;

import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

/**
 * Composition-layer constants + logger, deliberately independent of the mod
 * root class ({@code ChemicalAddon}) — whose static initialiser touches
 * registries and therefore explodes outside a bootstrapped Minecraft. The
 * engine (Solution / Equilibrium / Species / SpeciesManager) must stay
 * runnable under plain JUnit with no Forge bootstrap (see src/test/java).
 */
public final class Chemistry {

	/** The mod's namespace (mirrors ChemicalAddon.MODID; kept a literal so no class-init chain). */
	public static final String MOD_ID = "chemicaladdon";

	/**
	 * Internal solver units per mB (plans/03 §5 unit convention): the solver,
	 * the mixture's ratio parts and every rules-engine amount work in
	 * 1/10000 mB "units", so equilibrium residuals and hydrolysis products
	 * survive at 10⁻⁷ concentration resolution. The anchor is the weakest
	 * hydrolysis worth modelling in inorganic chemistry — Mg²⁺ (Ka ≈ 10⁻¹¹·⁴,
	 * [H⁺] ≈ 6e-7 in a 0.1 salt); anything weaker (Ca²⁺/Na⁺, 10⁻¹³) is
	 * unobservable in reality and deliberately unmodelled. 10⁻⁷ also leaves
	 * the door open for a future Kw entry (pure-water pH 7).
	 *
	 * <p>Headroom, pinned by the largest vessel (7×7×7 = 125,000 mB → 1.25e9
	 * units): {@code distribute()}'s total×part product ≤ 1.56e18 (6× inside
	 * long); per-component unit amounts fit int (2.1e9) — the unit-view
	 * derives soft-cap the total so a pathologically large stack (a third-party
	 * mega-tank) can never overflow. Exact integers throughout preserve charge
	 * neutrality, the GCD ratio identity and deterministic solves. The mB view
	 * (renderer / pipes / buckets) stays the transport granularity.
	 */
	public static final int UNIT_PER_MB = 10_000;

	/** The fixed-point fraction grid (U18): the solver's round trip runs at
	 * QUANTA_PER_UNIT × the legacy unit grid — 1/10,000,000 mB — so equilibrium
	 * residuals three orders below any display/transport resolution persist in
	 * the ratio tag instead of being truncated at every write-back. Long math
	 * throughout; parts are ratios so legacy int-tagged saves load unchanged. */
	public static final long QUANTA_PER_UNIT = 1_000;
	public static final long QUANTA_PER_MB = (long) UNIT_PER_MB * QUANTA_PER_UNIT;

	/** Slf4j logger under the same name the mod uses; safe headless. */
	public static final Logger LOGGER = LogUtils.getLogger();

	// ------------------------------------------------------------- U16 energy
	// ledger (plans/03 §12). Declared dimension convention: 1 unit ≡ 1 g (the
	// same declared-convention philosophy as the solubility curves), so the
	// whole vessel is one lumped body with water's specific heat.

	/** Specific heat of the vessel contents: J per unit per °C (water, 4.18 J/g·°C). */
	public static final double HEAT_CAPACITY_PER_UNIT = 4.18;

	/**
	 * Strong-acid/strong-base neutralisation enthalpy per H/OH pair:
	 * 57.1 kJ/mol ÷ 18 g/mol (per unit ≡ g of water formed). A 1:1:1
	 * water:H⁺:OH⁻ feed releases Q = N/3 × 3172 J over a body of N units —
	 * ΔT = 3172/(3 × 4.18) ≈ 253 °C: concentrated neutralisation self-boils.
	 */
	public static final double NEUTRALISATION_J_PER_PAIR = 3172.0;

	/**
	 * Water's latent heat of vaporisation per unit (2260 J/g). Every unit of
	 * steam an open boiling vessel vents takes this much energy with it —
	 * evaporation cools the remaining body, the self-limiting negative
	 * feedback that keeps a boiling pot at ~100 °C and quenches exotherm-driven
	 * flashes once the reaction heat is spent.
	 */
	public static final double VAPORISATION_J_PER_UNIT = 2260.0;

	/**
	 * The ionic product of water Kw = [H⁺]·[OH⁻] = 1e-14, in the engine's own
	 * concentration units (units / water units — the same scale every log_k is
	 * authored on). U17 (plans/04 §9.5): the pH gauge's alkaline side reads
	 * {@code [H⁺] = Kw / [OH⁻]} and pure water (neither ion present) is pH 7 by
	 * definition. Deliberately <b>not</b> a solver entry: a real autoionisation
	 * pair injected into the ion domain would change the mixture's GCD ratio
	 * tag (a solved vessel's contents would no longer stack with a freshly
	 * packed bucket of the same composition), and at the 10⁻⁷ resolution gate
	 * the pair is exactly 1 unit in 1000 mB — invisible to every mechanism
	 * except the reading, which computes it analytically instead.
	 */
	public static final double KW = 1e-14;

	/**
	 * Dev assay mode (U17 measurement-honesty knob): when true, engine-internal
	 * knowledge (speciation lines, mixed-residue percentages) is revealed —
	 * the developer's god-view. Player-facing instruments must never read this
	 * state (plans/03 §6); this flag only unlocks diagnostic display.
	 */
	public static volatile boolean ASSAY = false;

	private Chemistry() {}
}
