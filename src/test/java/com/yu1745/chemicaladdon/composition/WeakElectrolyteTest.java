package com.yu1745.chemicaladdon.composition;

import static com.yu1745.chemicaladdon.composition.EngineHarness.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The first weak-electrolyte entry (NH3 + H2O <-> NH4+ + OH-, Kb 10^-4.75,
 * raw log_k — aqueous entries get no {@link Solution#MINERAL_LOG_OFFSET}):
 * ammonia water ionises only slightly, and titrating it with a strong acid
 * converts it wholesale to ammonium salt through the equilibrate<->neutralise
 * interleaving.
 */
class WeakElectrolyteTest {

	@BeforeAll
	static void load() {
		EngineHarness.load();
	}

	@Test
	void ammoniaIonisesOnlySlightly() {
		// 200 units NH3 / 1000 water (c = 0.2): x = sqrt(1.8e-5 x 0.2) ≈ 1.9e-3
		// -> a couple of units of NH4+/OH- and ~198 stay molecular
		Solution s = solve(mol(Solution.WATER, 1000L, "ammonia", 200L), Map.of(), 20);
		assertIonNear(s, "NH4+1", 2, 2);
		assertIonNear(s, "OH-1", 2, 2);
		assertEquals(200L, s.molecular().get(id("ammonia")) + ion(s, "NH4+1"), "nitrogen conservation");
	}

	@Test
	void strongAcidTitrationConvertsAmmoniaToAmmonium() {
		// HCl + NH3·H2O -> NH4Cl to completion: each OH- the weak base sheds is
		// snatched by H+, pulling the ionisation forward until the acid runs out
		Solution s = solveToFixpoint(
			mol(Solution.WATER, 1000L, "ammonia", 200L),
			ions("H+1", 100L, "Cl-1", 100L), 20);
		assertIonNear(s, "NH4+1", 100, 3);
		assertIon(s, "H+1", 0);
		// the leftover ammonia stays molecular (weak base, not consumed)
		long nh3 = s.molecular().getOrDefault(id("ammonia"), 0L);
		assertEquals(200L, nh3 + ion(s, "NH4+1"), "nitrogen conservation (NH3=" + nh3 + ")");
	}

	@Test
	void ammoniaWaterBucketsRelaxToWeakBase() {
		// a packed "fully ionised" NH4+OH- state (what the creative bucket used
		// to mean) relaxes backward to mostly-molecular ammonia: weak bases do
		// not stay dissociated
		Solution s = solve(mol(Solution.WATER, 1000L), ions("NH4+1", 100L, "OH-1", 100L), 20);
		assertTrue(ion(s, "NH4+1") < 20,
			"most of the packed ammonium should relax back to molecular ammonia (got NH4=" + ion(s, "NH4+1") + ")");
		long nh3 = s.molecular().getOrDefault(id("ammonia"), 0L);
		assertEquals(100L, nh3 + ion(s, "NH4+1"), "nitrogen conservation");
	}

	@Test
	void weakBaseSparinglyNeutralisesStrongAcidInExcess() {
		// excess ammonia vs a little acid: all acid is consumed, no OH-
		// accumulation beyond the weak ionisation level
		Solution s = solveToFixpoint(
			mol(Solution.WATER, 1000L, "ammonia", 500L),
			ions("H+1", 50L, "Cl-1", 50L), 20);
		assertIon(s, "H+1", 0);
		assertTrue(ion(s, "OH-1") <= 5, "a weak base cannot accumulate hydroxide (got " + ion(s, "OH-1") + ")");
	}
}
