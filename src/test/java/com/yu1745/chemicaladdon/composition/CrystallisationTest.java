package com.yu1745.chemicaladdon.composition;

import static com.yu1745.chemicaladdon.composition.EngineHarness.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Tabulated-solubility (curve) behaviour. Since U14's kinetics rework,
 * crystallisation is KINETIC (affinity-law growth × nucleation penalty) —
 * equilibrium targets are unchanged, but they are reached by iterating the
 * solve, exactly like a vessel ticks. Dissolution and hot-unsaturated holds
 * stay instantaneous.
 */
class CrystallisationTest {

	@BeforeAll
	static void load() {
		EngineHarness.load();
	}

	@Test
	void coolingCrystallisesOnlyTheExcess() {
		// 500 f.u. KNO3 / 1000 water (0.5): fine at 100°C (threshold 2.45),
		// supersaturated at 20°C (threshold 0.316) -> only the excess leaves,
		// floor(0.316 x 1000) = 316 stays as the saturated mother liquor
		Solution s = solveToFixpoint(1000, ions("K+1", 500L, "NO3-1", 500L), 20);
		assertSediment(s, "potassium_nitrate", 184);
		assertIon(s, "K+1", 316);
		assertIon(s, "NO3-1", 316);
	}

	@Test
	void hotSolutionStaysDissolved() {
		Solution s = solve(1000, ions("K+1", 500L, "NO3-1", 500L), 100);
		assertSediment(s, "potassium_nitrate", 0);
		assertIon(s, "K+1", 500);
	}

	@Test
	void fractionalCrystallisationSeparatesKNO3FromNaCl() {
		// THE classic separation: KNO3 + NaCl at 100°C both dissolved (NaCl kept
		// under its curve at 300), cool to 20°C -> only KNO3 drops out
		Solution hot = solve(1000, ions("K+1", 500L, "NO3-1", 500L, "Na+1", 300L, "Cl-1", 300L), 100);
		assertSediment(hot, "potassium_nitrate", 0);
		assertSediment(hot, "rock_salt", 0);

		Solution cold = solveToFixpoint(hot.molecular(), hot.ions(), hot.suspended(), hot.sediment(), 20);
		assertSediment(cold, "potassium_nitrate", 184);
		assertSediment(cold, "rock_salt", 0); // NaCl at 0.3 stays well under 0.36
		assertIon(cold, "Na+1", 300);
		assertIon(cold, "Cl-1", 300);
	}

	@Test
	void sedimentRedissolvesInstantlyOnHeating() {
		// dissolution is fast (fine-crystal surface kinetics): one solve at the
		// hotter temperature takes the curve's whole headroom at once
		Solution cold = solveToFixpoint(1000, ions("K+1", 500L, "NO3-1", 500L), 20);
		assertEquals(184, cold.sediment().getOrDefault(id("potassium_nitrate"), 0L));
		Solution hot = solve(cold.molecular(), cold.ions(), cold.suspended(), cold.sediment(), 100);
		assertSediment(hot, "potassium_nitrate", 0);
		assertIon(hot, "K+1", 500);
	}

	@Test
	void doubleSaltAlumCrystallisesAsAUnit() {
		// K+ + Al3+ + 2 SO4-- crystallise together as alum: 100 f.u. at 20°C
		// exceeds the curve (0.06 x 1000 = 60) -> 40 crystallise as ONE sediment
		// species, consuming the 1:1:2 set (double-salt semantics, not two salts)
		Solution s = solveToFixpoint(1000, ions("K+1", 100L, "Al+3", 100L, "SO4-2", 200L), 20);
		assertSediment(s, "potassium_alum", 40);
		assertIon(s, "K+1", 60);
		assertIon(s, "Al+3", 60);
		assertIon(s, "SO4-2", 120);
	}

	@Test
	void ferrousSulfateHasRetrogradeSolubilityAbove60C() {
		// FeSO4's curve DROPS above 60°C (43.6 @ 80, 37.3 @ 100): a hot
		// saturated solution crystallises MORE as it heats past 60 — the data
		// table's own stress test for interpolation direction (800 f.u. keeps
		// both states past the nucleation gate)
		Solution s60 = solveToFixpoint(1000, ions("Fe+2", 800L, "SO4-2", 800L), 60);
		assertSediment(s60, "ferrous_sulfate", 800 - 486);
		Solution s100 = solveToFixpoint(1000, ions("Fe+2", 800L, "SO4-2", 800L), 100);
		assertSediment(s100, "ferrous_sulfate", 800 - 373);
	}
}
