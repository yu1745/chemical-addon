package com.yu1745.chemicaladdon.composition;

import static com.yu1745.chemicaladdon.composition.EngineHarness.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The U16 energy ledger (plans/03 §12): reaction heat is accounted in joules
 * against the declared {@code 1 unit ≡ 1 g} body with water's specific heat,
 * so {@code ΔT = Q/(feedUnits × 4.18)} is mass-coupled — a fixed amount of
 * neutralisation boils a concentrated feed and barely warms a dilute one.
 * Evaporation carries its latent heat away, the self-limiting feedback that
 * keeps a boiling pot near 100 °C.
 */
class EnergyLedgerTest {

	@BeforeAll
	static void load() {
		EngineHarness.load();
	}

	@Test
	void concentratedNeutralisationSelfBoils() {
		// 1:1:1 water:H+:OH- — the plan's acceptance number: every third unit
		// is a neutralising pair, so ΔT = 3172/(3 × 4.18) ≈ 253 °C regardless
		// of the pot's size: a concentrated neutralisation crosses the boiling
		// point from ambient (self-boil) with no heater involved
		Solution s = solve(mol(Solution.WATER, 1000L), ions("H+1", 1000L, "OH-1", 1000L), 20);
		assertEquals(0L, s.ions().getOrDefault("H+1", 0L), "all acid consumed");
		assertTrue(s.energyJ() > 0, "neutralisation must release energy");
		assertEquals(253.0, s.heatRiseC(), 2.0, "1:1:1 ΔT ≈ 253 °C (got " + s.heatRiseC() + ")");
	}

	@Test
	void dilutionMassCouplesTheTemperatureRise() {
		// same 1000 pairs, 10× the water: the feed mass triples→12× ... the
		// rise scales exactly with the inverse feed mass (3000 → 12000 units:
		// 63.25 = 253/4). This is the coupling the old lumped "+50 °C per
		// solve" constant could not express: big dilute vessels barely warm.
		Solution s = solve(mol(Solution.WATER, 10_000L), ions("H+1", 1000L, "OH-1", 1000L), 20);
		assertEquals(63.25, s.heatRiseC(), 2.0, "dilute ΔT = 253 × (3000/12000) (got " + s.heatRiseC() + ")");
	}

	@Test
	void evaporationCarriesLatentHeatAway() {
		// venting 50 units of steam from a 2000-unit body costs 50 × 2260 J:
		// ΔT = -113000/(2000 × 4.18) ≈ -13.5 °C. Without a heat source the
		// body drops below its boiling point and stops venting — boiling needs
		// energy, which is what keeps a pot at ~100 °C and quenches exotherm
		// flashes once the reaction heat is spent.
		Solution s = new Solution(water(2000), Map.of(), Map.of(), Map.of(), 100);
		s.solve();
		assertEquals(0.0, s.energyJ(), 1e-9, "inert water releases nothing");
		s.evaporateWater(50);
		assertEquals(1950L, s.molecular().get(Solution.WATER), "50 units vented");
		assertEquals(-13.5, s.heatRiseC(), 0.5, "latent-heat cooling (got " + s.heatRiseC() + ")");
	}

	@Test
	void weakBaseTitrationPaysFullNeutralisationHeat() {
		// HCl + NH3·H2O → NH4Cl: the weak-electrolyte pathway completes the
		// neutralisation stoichiometrically, and every driven pair pays the
		// same 3172 J as a direct one. The ledger is per-solve (the reactor
		// applies ΔT every tick, exactly like this loop), so the total rise
		// accumulates across the fixpoint iterations: 100 pairs over a
		// 1400-unit feed → ΔT = 317200/(1400 × 4.18) ≈ 54.2
		double rise = 0;
		Solution s = solve(
			mol(Solution.WATER, 1000L, "ammonia", 200L),
			ions("H+1", 100L, "Cl-1", 100L), 20);
		rise += s.heatRiseC();
		while (s.ions().getOrDefault("H+1", 0L) > 0) {
			s = solve(Map.copyOf(s.molecular()), Map.copyOf(s.ions()),
				Map.copyOf(s.suspended()), Map.copyOf(s.sediment()), 20);
			rise += s.heatRiseC();
		}
		assertEquals(0L, s.ions().getOrDefault("H+1", 0L), "titration complete");
		assertEquals(54.2, rise, 3.0, "driven pairs are heat-accounted too (got " + rise + ")");
	}
}
