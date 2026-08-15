package com.yu1745.chemicaladdon.composition;

import static com.yu1745.chemicaladdon.composition.EngineHarness.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The speciation report (per-solid saturation indices) — the goggles'
 * "why is it not reacting" data source.
 */
class SpeciationReportTest {

	@BeforeAll
	static void load() {
		EngineHarness.load();
	}

	private static Solution.Speciation forTarget(Solution s, String path) {
		return s.report().stream()
			.filter(p -> p.target().equals(id(path)))
			.findFirst()
			.orElseThrow(() -> new AssertionError("no report entry for " + path + " in " + s.report()));
	}

	@Test
	void precipitatingMineralReportsSupersaturatedAndMoved() {
		Solution s = solve(1000, ions("Ca+2", 300L, "Cl-1", 300L, "Na+1", 300L, "CO3-2", 300L), 20);
		Solution.Speciation limestone = forTarget(s, "limestone");
		assertEquals(300, limestone.moved());
		assertTrue(limestone.si() >= 0, "post-solve SI sits at/over saturation (got " + limestone.si() + ")");
	}

	@Test
	void absentConstituentsReportMinusInfinity() {
		// no calcium anywhere: limestone is utterly undersaturated
		Solution s = solve(1000, ions("Na+1", 100L, "Cl-1", 100L), 20);
		Solution.Speciation limestone = forTarget(s, "limestone");
		assertEquals(Double.NEGATIVE_INFINITY, limestone.si());
		assertEquals(0, limestone.moved());
	}

	@Test
	void supersaturatedCurveSpeciesReportsPositiveSIAndKinetics() {
		// crystallisation is kinetic: one solve moves a sliver, but the report
		// already shows the true thermodynamic state (SI > 0, rate-limited)
		Solution s = solve(1000, ions("K+1", 500L, "NO3-1", 500L), 20);
		Solution.Speciation kno3 = forTarget(s, "potassium_nitrate");
		assertTrue(kno3.moved() > 0 && kno3.moved() < 184, "a kinetic sliver, not the whole excess (got " + kno3.moved() + ")");
		assertTrue(kno3.si() > 0, "still supersaturated after one solve (got " + kno3.si() + ")");
		assertTrue(kno3.rateLimited(), "kinetics is holding it back (got " + kno3 + ")");
	}

	@Test
	void unsaturatedCurveSpeciesReportsNegativeSI() {
		Solution s = solve(1000, ions("K+1", 100L, "NO3-1", 100L), 20);
		Solution.Speciation kno3 = forTarget(s, "potassium_nitrate");
		assertEquals(0, kno3.moved());
		assertTrue(kno3.si() < -0.3, "0.1 vs threshold 0.316 -> SI = -0.5 (got " + kno3.si() + ")");
	}

	@Test
	void everyMineralAndCurveSpeciesHasAnEntry() {
		Solution s = solve(1000, Map.of(), 20);
		long minerals = SpeciesManager.allEquilibria().stream().filter(e -> e.solid() != null).count();
		long curves = SpeciesManager.all().stream().filter(Species::isCrystallisable).count();
		assertEquals(minerals + curves, s.report().size(),
			"one report entry per mineral entry + curve species");
		assertFalse(s.report().isEmpty());
	}
}
