package com.yu1745.chemicaladdon.composition;

import static com.yu1745.chemicaladdon.composition.EngineHarness.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Complexation equilibria: masking (ligand ties up a metal before any mineral
 * can nucleate), the amphoteric aluminium pair, and the coloured thiocyanate
 * complex. Complexation entries solve BEFORE minerals (SpeciesManager's
 * ordering), which is what makes masking an emergent property.
 */
class ComplexationTest {

	@BeforeAll
	static void load() {
		EngineHarness.load();
	}

	@Test
	void silverAmmoniaComplexMasksSilverChloride() {
		// AgNO3 + NaCl with ammonia present: [Ag(NH3)2]+ ties up the silver
		// first, so no AgCl precipitates — the qualitative-analysis trick
		Solution s = solve(
			mol(Solution.WATER, 1000L, "ammonia", 2000L),
			ions("Ag+1", 100L, "NO3-1", 100L, "Na+1", 100L, "Cl-1", 100L), 20);
		assertSuspended(s, "silver_chloride", 0);
		assertIon(s, "[Ag(NH3)2]+1", 100);
		assertIon(s, "Ag+1", 0);
		assertIon(s, "Cl-1", 100);
	}

	@Test
	void copperAmmoniaComplexMasksCarbonate() {
		// mirror of the GameTest, engine-level: no carbonate falls out
		Solution s = solve(
			mol(Solution.WATER, 1000L, "ammonia", 2000L),
			ions("Cu+2", 20L, "SO4-2", 20L, "Na+1", 80L, "CO3-2", 40L), 20);
		assertSuspended(s, "copper_carbonate", 0);
		assertIon(s, "[Cu(NH3)4]+2", 20);
		assertIon(s, "Cu+2", 0);
	}

	@Test
	void zincAmmoniaComplexForms() {
		Solution s = solve(
			mol(Solution.WATER, 1000L, "ammonia", 2000L),
			ions("Zn+2", 100L, "SO4-2", 100L), 20);
		assertIon(s, "[Zn(NH3)4]+2", 100);
		assertIon(s, "Zn+2", 0);
	}

	@Test
	void ferricThiocyanateSitsAtItsEquilibrium() {
		// Fe3+ + SCN- <-> [FeSCN]+2 (beta 2.1, no offset for aqueous entries):
		// 100/100 in 1000 water -> exactly 75 units complex, 25 free — the
		// bisection's analytic answer (log10(1000m/(100-m)^2) <= 2.1)
		Solution s = solve(1000, ions("Fe+3", 100L, "Cl-1", 300L, "K+1", 100L, "SCN-1", 100L), 20);
		assertIon(s, "[FeSCN]+2", 75);
		assertIon(s, "Fe+3", 25);
		assertIon(s, "SCN-1", 25);
	}

	@Test
	void amphotericAluminiumDissolvesInExcessLye() {
		// Al3+ + lye: Al(OH)3 precipitates first, then the excess OH- carries it
		// back into solution as [Al(OH)4]- — amphoterism from two entries
		Solution s = solveToFixpoint(
			mol(Solution.WATER, 1000L),
			ions("Al+3", 100L, "Cl-1", 300L, "Na+1", 700L, "OH-1", 1000L), 20);
		assertSuspended(s, "aluminium_hydroxide", 0);
		assertIon(s, "[Al(OH)4]-1", 100);
		assertIon(s, "Al+3", 0);
		// 300 OH consumed by hydroxide + 100 by the aluminate = 600 left
		assertIonNear(s, "OH-1", 600, 5);
	}

	@Test
	void amphotericAluminiumReprecipitatesOnAcidification() {
		// acidifying the aluminate solution walks it back: H+ eats OH-, the
		// complex releases OH-, Al(OH)3 falls out again
		Solution alkaline = solveToFixpoint(
			mol(Solution.WATER, 1000L),
			ions("Al+3", 100L, "Cl-1", 300L, "Na+1", 700L, "OH-1", 1000L), 20);
		Solution acidified = solveToFixpoint(
			alkaline.molecular(),
			withAdded(alkaline.ions(), "H+1", 800L),
			alkaline.suspended(), alkaline.sediment(), 20);
		long reprecipitated = acidified.suspended().getOrDefault(id("aluminium_hydroxide"), 0L);
		assertTrue(reprecipitated >= 90,
			"acidification should reprecipitate most of the aluminium (got " + reprecipitated
				+ " suspended, ions " + acidified.ions() + ")");
		assertIonNear(acidified, "[Al(OH)4]-1", 100 - reprecipitated, 5);
	}

	@Test
	void thiocyanateComplexReadsBloodRed() {
		// the colour story: the blend is dominated by red channels
		Solution s = solve(1000, ions("Fe+3", 100L, "Cl-1", 300L, "K+1", 100L, "SCN-1", 100L), 20);
		int tint = tintOf(s);
		int r = (tint >> 16) & 0xFF, g = (tint >> 8) & 0xFF, b = tint & 0xFF;
		assertTrue(r > b + 20, "ferric thiocyanate should read red-dominant (r=" + r + " b=" + b + ")");
	}

	@Test
	void ferricChlorideAloneReadsYellowBrown() {
		Solution s = solve(1000, ions("Fe+3", 100L, "Cl-1", 300L), 20);
		assertEquals(com.yu1745.chemicaladdon.fluid.IonColors.of("Fe+3"), tintOf(s));
	}

	private static Map<String, Long> withAdded(Map<String, Long> ions, String key, long delta) {
		Map<String, Long> out = new java.util.LinkedHashMap<>(ions);
		out.merge(key, delta, Long::sum);
		return out;
	}
}
