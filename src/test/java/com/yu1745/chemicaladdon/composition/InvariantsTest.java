package com.yu1745.chemicaladdon.composition;

import static com.yu1745.chemicaladdon.composition.EngineHarness.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Structural invariants over randomised compositions: whatever the engine
 * does, it must never violate charge neutrality, never emit a negative
 * amount, and always be idempotent (a solved state re-solves to itself —
 * the vessel runs the solver every tick).
 */
class InvariantsTest {

	@BeforeAll
	static void load() {
		EngineHarness.load();
	}

	/** Random but charge-neutral salt cocktails built from real solution species. */
	private static List<Map<String, Long>> randomCocktails(int count, long seed) {
		Random rng = new Random(seed);
		List<Species> electrolytes = SpeciesManager.all().stream()
			.filter(Species::isElectrolyte)
			.filter(s -> s.phase() == Species.Phase.LIQUID)
			.toList();
		assertTrue(electrolytes.size() >= 10, "need a decent electrolyte palette (got " + electrolytes.size() + ")");
		List<Map<String, Long>> out = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			Map<String, Long> ions = new java.util.LinkedHashMap<>();
			int picks = 1 + rng.nextInt(4);
			for (int p = 0; p < picks; p++) {
				Species s = electrolytes.get(rng.nextInt(electrolytes.size()));
				long units = 50 * (1 + rng.nextInt(10));
				for (Species.IonComponent c : s.ions()) {
					ions.merge(c.ion().id(), units * c.count(), Long::sum);
				}
			}
			out.add(ions);
		}
		return out;
	}

	@Test
	void neutralityHoldsAndNothingGoesNegative() {
		for (Map<String, Long> ions : randomCocktails(200, 42L)) {
			assertEquals(0L, netCharge(ions), "input must start neutral: " + ions);
			Solution s = solve(1000, ions, 20 + new Random().nextInt(80));
			assertNeutral(s);
			for (long v : s.ions().values()) {
				assertTrue(v > 0, "negative ion amount: " + s.ions());
			}
			for (long v : s.suspended().values()) {
				assertTrue(v > 0, "negative suspended amount: " + s.suspended());
			}
			for (long v : s.sediment().values()) {
				assertTrue(v > 0, "negative sediment amount: " + s.sediment());
			}
		}
	}

	@Test
	void fixpointsAreStable() {
		// kinetic states evolve over solves by design (crystallisation), so
		// plain idempotency is off the table — instead: whatever the cocktail,
		// iterating reaches a state that re-solves to itself, staying neutral
		for (Map<String, Long> ions : randomCocktails(60, 11L)) {
			Solution s = solve(1000, ions, 60);
			for (int i = 0; i < 3000; i++) {
				Solution next = solve(s.molecular(), s.ions(), s.suspended(), s.sediment(), 60);
				if (next.ions().equals(s.ions()) && next.sediment().equals(s.sediment())
					&& next.suspended().equals(s.suspended())) {
					s = next;
					break;
				}
				s = next;
			}
			Solution again = solve(s.molecular(), s.ions(), s.suspended(), s.sediment(), 60);
			assertEquals(s.ions(), again.ions(), "the fixpoint must re-solve to itself: " + ions);
			assertEquals(s.sediment(), again.sediment(), "stable sediment: " + ions);
			assertNeutral(s);
		}
	}

	@Test
	void kineticCocktailsStayNeutralAndBounded() {
		// full palette (curves included): solving may evolve over ticks, but
		// never breaks neutrality and never invents negative amounts
		for (Map<String, Long> ions : randomCocktails(100, 13L)) {
			Solution s = solve(1000, ions, 60);
			for (int tick = 0; tick < 5; tick++) {
				s = solve(s.molecular(), s.ions(), s.suspended(), s.sediment(), 60);
				assertNeutral(s);
				for (long v : s.ions().values()) {
					assertTrue(v > 0, "negative ion amount: " + s.ions());
				}
				for (long v : s.sediment().values()) {
					assertTrue(v > 0, "negative sediment amount: " + s.sediment());
				}
			}
		}
	}

	@Test
	void solveWithAmmoniaAndAcidConvergesToFixpoint() {
		// the fixpoint helper itself must terminate on a messy state
		Solution s = solveToFixpoint(
			mol(Solution.WATER, 1000L, "ammonia", 500L),
			ions("H+1", 100L, "Cl-1", 100L, "Na+1", 50L, "SO4-2", 25L),
			Map.of(), Map.of(), 20);
		assertNeutral(s);
	}
}
