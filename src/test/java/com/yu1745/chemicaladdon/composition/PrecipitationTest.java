package com.yu1745.chemicaladdon.composition;

import static com.yu1745.chemicaladdon.composition.EngineHarness.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The classic aqueous double-displacement / precipitation matrix, checked at
 * engine level. All expectations were derived analytically from the authored
 * log_k values and {@link Solution#MINERAL_LOG_OFFSET} — these tests pin the
 * calibration, so changing either the data or the offset fails loudly here.
 */
class PrecipitationTest {

	@BeforeAll
	static void load() {
		EngineHarness.load();
	}

	@Test
	void silverChloridePrecipitatesCompletely() {
		// AgNO3 + NaCl -> AgCl(s) + NaNO3 (the textbook qualitative-analysis precipitate)
		Solution s = solve(1000, ions("Ag+1", 100L, "NO3-1", 100L, "Na+1", 100L, "Cl-1", 100L), 20);
		assertSuspended(s, "silver_chloride", 100);
		assertIon(s, "Ag+1", 0);
		assertIon(s, "Cl-1", 0);
		assertIon(s, "Na+1", 100);
		assertIon(s, "NO3-1", 100);
		assertNeutral(s);
	}

	@Test
	void bariumSulfatePrecipitatesCompletely() {
		// BaCl2 + Na2SO4 -> BaSO4(s) + 2 NaCl
		Solution s = solve(1000, ions("Ba+2", 100L, "Cl-1", 200L, "Na+1", 200L, "SO4-2", 100L), 20);
		assertSuspended(s, "barium_sulfate", 100);
		assertIon(s, "Ba+2", 0);
		assertIon(s, "SO4-2", 0);
		assertIon(s, "Na+1", 200);
		assertNeutral(s);
	}

	@Test
	void silverCarbonateHandlesTwoToOneStoichiometry() {
		// 2 Ag+ + CO3-- -> Ag2CO3(s): the 2:1 count path of the solver
		Solution s = solve(1000, ions("Ag+1", 200L, "NO3-1", 200L, "Na+1", 200L, "CO3-2", 100L), 20);
		assertSuspended(s, "silver_carbonate", 100);
		assertIon(s, "Ag+1", 0);
		assertIon(s, "CO3-2", 0);
		assertIon(s, "NO3-1", 200);
		assertNeutral(s);
	}

	@Test
	void copperHydroxideHandlesOneToTwoStoichiometry() {
		// CuSO4 + 2 NaOH -> Cu(OH)2(s) + Na2SO4
		Solution s = solve(1000, ions("Cu+2", 100L, "SO4-2", 100L, "Na+1", 200L, "OH-1", 200L), 20);
		assertSuspended(s, "copper_hydroxide", 100);
		assertIon(s, "Cu+2", 0);
		assertIon(s, "OH-1", 0);
		assertIon(s, "Na+1", 200);
		assertNeutral(s);
	}

	@Test
	void ferricHydroxideHandlesOneToThreeStoichiometry() {
		// Fe3+ + 3 OH- -> Fe(OH)3(s)
		Solution s = solve(1000, ions("Fe+3", 100L, "Cl-1", 300L, "Na+1", 300L, "OH-1", 300L), 20);
		assertSuspended(s, "iron_hydroxide", 100);
		assertIon(s, "Fe+3", 0);
		assertIon(s, "OH-1", 0);
		assertNeutral(s);
	}

	@Test
	void magnesiumHydroxidePrecipitatesFromBrineWithLye() {
		// MgCl2 + 2 NaOH -> Mg(OH)2(s)
		Solution s = solve(1000, ions("Mg+2", 100L, "Cl-1", 200L, "Na+1", 200L, "OH-1", 200L), 20);
		assertSuspended(s, "magnesium_hydroxide", 100);
		assertIon(s, "Mg+2", 0);
	}

	@Test
	void doubleDisplacementNa2CO3PlusBaCl2() {
		// Na2CO3 + BaCl2 -> BaCO3(s) + 2 NaCl
		Solution s = solve(1000, ions("Na+1", 200L, "CO3-2", 100L, "Ba+2", 100L, "Cl-1", 200L), 20);
		assertSuspended(s, "barium_carbonate", 100);
		assertIon(s, "Ba+2", 0);
		assertIon(s, "CO3-2", 0);
		assertIon(s, "Cl-1", 200);
	}

	@Test
	void gypsumLeavesASaturatedMotherLiquor() {
		// CaSO4 (log_k -4.6, offset -2 -> -6.6): at 100/100 in 1000 water the
		// equilibrium keeps exactly 1 unit back — the calibration's visible
		// "saturated mother liquor" case (vs the insoluble salts above)
		Solution s = solve(1000, ions("Ca+2", 100L, "SO4-2", 100L, "Na+1", 100L, "Cl-1", 100L), 20);
		assertSuspended(s, "gypsum", 99);
		assertIon(s, "Ca+2", 1);
		assertIon(s, "SO4-2", 1);
	}

	@Test
	void commonIonExcessSulfateDrivesCalciumToExhaustion() {
		// same Ca total, but sulfate in excess: the shared-ion equilibrium sits
		// far past the 1:1 residual -> Ca fully consumed (common-ion effect)
		Solution s = solve(1000, ions("Ca+2", 100L, "SO4-2", 300L, "Na+1", 500L, "Cl-1", 500L), 20);
		assertSuspended(s, "gypsum", 100);
		assertIon(s, "Ca+2", 0);
	}

	@Test
	void ferricHydroxideOutcompetesMagnesiumForLimitedHydroxide() {
		// 100 Fe3+ need exactly 300 OH; Mg2+ gets none — least-soluble first
		Solution s = solve(1000, ions("Fe+3", 100L, "Mg+2", 100L, "Cl-1", 500L, "OH-1", 300L), 20);
		assertSuspended(s, "iron_hydroxide", 100);
		assertSuspended(s, "magnesium_hydroxide", 0);
		assertIon(s, "Mg+2", 100);
		assertIon(s, "OH-1", 0);
	}

	@Test
	void limestoneStillPrecipitatesToExhaustion() {
		// regression pin for the flagship entry (log_k -8.3)
		Solution s = solve(1000, ions("Ca+2", 300L, "Cl-1", 300L, "Na+1", 300L, "CO3-2", 300L), 20);
		assertSuspended(s, "limestone", 300);
		assertIon(s, "Ca+2", 0);
	}

	@Test
	void basicCopperCarbonateLeavesColourlessMotherLiquor() {
		// soda ash + copper sulfate flagship: malachite-green slurry + spectators
		Solution s = solve(1000, ions("Cu+2", 100L, "SO4-2", 100L, "Na+1", 400L, "CO3-2", 200L), 20);
		assertSuspended(s, "copper_carbonate", 100);
		assertIon(s, "Cu+2", 0);
		assertIon(s, "CO3-2", 100);
		assertEquals(com.yu1745.chemicaladdon.fluid.SolidColors.of(id("copper_carbonate")),
			tintOf(s), "the slurry should render the malachite-green solid colour");
	}
}
