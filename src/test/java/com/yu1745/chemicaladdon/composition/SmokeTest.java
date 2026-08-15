package com.yu1745.chemicaladdon.composition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Headless smoke: proves the composition engine runs under plain JUnit with no
 * Minecraft/Forge bootstrap — the species data loads from the classpath and
 * the solver produces sane domains. If this file cannot run, the whole
 * src/test layer's premise (engine strip-mined from MC) is broken.
 */
class SmokeTest {

	@BeforeAll
	static void loadSpecies() {
		SpeciesManager.loadBuiltin();
	}

	@Test
	void speciesLoadFromClasspath() {
		assertTrue(SpeciesManager.all().size() >= 20, "builtin species should load (got " + SpeciesManager.all().size() + ")");
	}

	@Test
	void neutraliseRunsHeadless() {
		Solution s = new Solution(
			Map.of(Solution.WATER, 1000L),
			Map.of("H+1", 100L, "Cl-1", 100L, "Na+1", 100L, "OH-1", 100L),
			20);
		s.solve();
		assertEquals(0, s.ions().getOrDefault("H+1", 0L));
		assertEquals(100, s.ions().getOrDefault("Na+1", 0L));
		assertEquals(1100, s.molecular().getOrDefault(Solution.WATER, 0L));
	}
}
