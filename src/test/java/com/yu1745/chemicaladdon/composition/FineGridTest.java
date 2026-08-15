package com.yu1745.chemicaladdon.composition;

import static com.yu1745.chemicaladdon.composition.EngineHarness.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The fine-grid payoff (UNIT_PER_MB = 10⁴): sub-mB equilibrium states that the
 * old 1-unit grid quantised away now land on their analytic answers. Amounts
 * here are solver units — 1000 mB of water is 10,000,000 units, so the
 * concentration resolution is 10⁻⁷ (the Mg²⁺-hydrolysis anchor).
 */
class FineGridTest {

	/** 1000 mB of water in solver units. */
	private static final long BUCKET_WATER = 1000L * Chemistry.UNIT_PER_MB;

	@BeforeAll
	static void load() {
		EngineHarness.load();
	}

	@Test
	void gypsumMotherLiquorLandsOnItsAnalyticResidual() {
		// CaSO4 0.1 + NaCl spectators: equilibrium c(Ca)·c(SO4) = 10^(-4.6-2)
		// -> both residuals 10^-3.3 = 5e-4 -> exactly 5000 units each
		// (the old grid snapped this to 0 or 1 whole units)
		Solution s = solve(BUCKET_WATER,
			ions("Ca+2", 100L * Chemistry.UNIT_PER_MB, "SO4-2", 100L * Chemistry.UNIT_PER_MB,
				"Na+1", 100L * Chemistry.UNIT_PER_MB, "Cl-1", 100L * Chemistry.UNIT_PER_MB), 20);
		assertIonNear(s, "Ca+2", 5000, 100);
		assertIonNear(s, "SO4-2", 5000, 100);
		assertEquals(100L * Chemistry.UNIT_PER_MB - ion(s, "Ca+2"), susp(s, "gypsum"), "gypsum mass balance");
	}

	@Test
	void ammoniaIonisationLandsOnItsAnalyticDegree() {
		// NH3 0.2 in 1000 mB: x = sqrt(1.8e-5 × 0.2) = 1.9e-3 -> 19,000 units
		// of NH4+/OH- (the old grid saw 1-2 whole units only)
		Solution s = solve(mol(Solution.WATER, BUCKET_WATER, "ammonia", 200L * Chemistry.UNIT_PER_MB),
			ions(), 20);
		assertIonNear(s, "NH4+1", 19000, 2000);
		assertIonNear(s, "OH-1", 19000, 2000);
	}

	@Test
	void diluteAmmoniaStillShowsMeasurableIonisation() {
		// 10 mB of ammonia in a bucket (c = 0.01): x = 4.2e-4 -> ~4200 units —
		// an order of magnitude below the old grid's floor
		Solution s = solve(mol(Solution.WATER, BUCKET_WATER, "ammonia", 10L * Chemistry.UNIT_PER_MB),
			ions(), 20);
		assertIonNear(s, "NH4+1", 4200, 800);
	}

	@Test
	void chargeNeutralityHoldsOnTheFineGrid() {
		Solution s = solve(BUCKET_WATER,
			ions("Ca+2", 100L * Chemistry.UNIT_PER_MB, "SO4-2", 100L * Chemistry.UNIT_PER_MB,
				"Na+1", 100L * Chemistry.UNIT_PER_MB, "Cl-1", 100L * Chemistry.UNIT_PER_MB), 20);
		assertNeutral(s);
	}
}
