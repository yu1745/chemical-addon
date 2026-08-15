package com.yu1745.chemicaladdon.composition;

import static com.yu1745.chemicaladdon.composition.EngineHarness.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import net.minecraft.resources.ResourceLocation;

/**
 * The kinetic layer: crystallisation is time-ordered by default (affinity-law
 * growth × nucleation penalty), equilibrium entries can opt into per-entry
 * rates, and evaporation to dryness crashes everything out (evaporite).
 */
class KineticsTest {

	@BeforeAll
	static void load() {
		EngineHarness.load();
		// a synthetic slow mineral for per-entry-rate tests (harmless elsewhere:
		// its ions never appear in other cocktails)
		SpeciesManager.registerForTest(Species.parse(id("slow_salt"), JsonParser.parseString("""
			{
			  "formula": "SlowSalt",
			  "phase": "SOLID",
			  "equilibria": [
			    { "reaction": "slow_salt(s) = X+1 + Y-1", "log_k": -8, "rate": 0.001 }
			  ]
			}
			""")));
	}

	@Test
	void crystallisationIsTimeOrderedAndNeverOvershoots() {
		// one solve moves a sliver (unseeded nucleation), not the whole excess;
		// iterating converges to the same equilibrium the instant model had
		Solution first = solve(1000, ions("K+1", 500L, "NO3-1", 500L), 20);
		long firstMove = first.sediment().getOrDefault(id("potassium_nitrate"), 0L);
		assertTrue(firstMove > 0 && firstMove < 184,
			"first solve should crystallise a sliver, not all 184 (got " + firstMove + ")");
		// monotone: sediment only grows, solute only shrinks, across solves
		long prevSed = firstMove;
		long prevK = ion(first, "K+1");
		Solution s = first;
		for (int i = 0; i < 500 && ion(s, "K+1") > 316; i++) {
			s = solve(s.molecular(), s.ions(), s.suspended(), s.sediment(), 20);
			long sed = s.sediment().getOrDefault(id("potassium_nitrate"), 0L);
			assertTrue(sed >= prevSed, "sediment must be nondecreasing");
			assertTrue(ion(s, "K+1") <= prevK, "dissolved K must be nonincreasing");
			prevSed = sed;
			prevK = ion(s, "K+1");
		}
		assertEquals(316, ion(s, "K+1"), "converged to the saturated mother liquor");
		assertEquals(184, prevSed);
	}

	@Test
	void seedingCollapsesMetastableSolutions() {
		// 400 f.u. KNO3 / 1000 water is 27% supersaturated — BELOW the
		// nucleation gate, so unseeded it sits metastable forever (the
		// quench-cooled state); one grain of sediment collapses it
		int unseeded = ticksToSettle(Map.of(), 400);
		int seeded = ticksToSettle(Map.of(id("potassium_nitrate"), 1L), 400);
		assertEquals(5000, unseeded, "unseeded must stay metastable (loop cap hit)");
		assertTrue(seeded < 30,
			"a seed must collapse the metastable solution quickly (seeded=" + seeded + ")");
	}

	private static int ticksToSettle(Map<ResourceLocation, Long> sediment, long units) {
		Solution s = solve(water(1000), ions("K+1", units, "NO3-1", units), Map.of(), sediment, 20);
		int ticks = 0;
		while (ion(s, "K+1") > 316 && ticks < 5000) {
			s = solve(s.molecular(), s.ions(), s.suspended(), s.sediment(), 20);
			ticks++;
		}
		return ticks;
	}

	@Test
	void unseededNucleationRequiresDeepSupersaturation() {
		// the metastable gate in one shot: the same 27% supersaturation that
		// sits frozen unseeded crystallises (slowly, penalised) once past 50%
		Solution metastable = solve(1000, ions("K+1", 400L, "NO3-1", 400L), 20);
		assertSediment(metastable, "potassium_nitrate", 0);
		Solution deep = solve(1000, ions("K+1", 500L, "NO3-1", 500L), 20);
		assertTrue(deep.sediment().getOrDefault(id("potassium_nitrate"), 0L) > 0,
			"past the gate homogeneous nucleation starts (got " + deep.sediment() + ")");
	}

	@Test
	void evaporiteDryOutCrashesEverythingOut() {
		// no solvent left: dissolved curve species go wholly to sediment
		// (boiling a pot dry yields salt, not a dry "ion soup")
		Solution s = solve(Map.of(), ions("K+1", 500L, "NO3-1", 500L), Map.of(), Map.of(), 20);
		assertSediment(s, "potassium_nitrate", 500);
		assertIon(s, "K+1", 0);
	}

	@Test
	void slowRateEntryAdvancesOneStepPerSolve() {
		// rate 0.001 with a huge drive clamps to the 1-unit floor: exactly one
		// formula unit per solve
		Solution s1 = solve(10000, ions("X+1", 1000L, "Y-1", 1000L), 25);
		assertSuspended(s1, "slow_salt", 1);
		Solution s2 = solve(s1.molecular(), s1.ions(), s1.suspended(), s1.sediment(), 25);
		assertSuspended(s2, "slow_salt", 2);
		assertTrue(s1.report().stream().anyMatch(p -> p.target().equals(id("slow_salt")) && p.rateLimited()),
			"the report should flag the kinetics-held mineral");
	}

	@Test
	void arrheniusDoublesTheRatePer25C() {
		// at 75 °C the coarse Arrhenius factor is 2^2 = 4 -> four units per solve
		Solution hot = solve(10000, ions("X+1", 1000L, "Y-1", 1000L), 75);
		assertSuspended(hot, "slow_salt", 4);
	}

	@Test
	void instantEquilibriaAreUnaffected() {
		// entries without a rate still snap to equilibrium in a single solve —
		// the fast lane (limestone, all current authored minerals)
		Solution s = solve(1000, ions("Ca+2", 300L, "Cl-1", 300L, "Na+1", 300L, "CO3-2", 300L), 20);
		assertSuspended(s, "limestone", 300);
	}
}
