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

	/** Slf4j logger under the same name the mod uses; safe headless. */
	public static final Logger LOGGER = LogUtils.getLogger();

	/**
	 * Dev assay mode (U17 measurement-honesty knob): when true, engine-internal
	 * knowledge (speciation lines, mixed-residue percentages) is revealed —
	 * the developer's god-view. Player-facing instruments must never read this
	 * state (plans/03 §6); this flag only unlocks diagnostic display.
	 */
	public static volatile boolean ASSAY = false;

	private Chemistry() {}
}
