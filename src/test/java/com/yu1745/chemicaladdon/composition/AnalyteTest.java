package com.yu1745.chemicaladdon.composition;

import static com.yu1745.chemicaladdon.composition.EngineHarness.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * U17 instrument readings as pure functions (plans/04 §9.1): pH / Baumé /
 * turbidity over solver-unit states, plus the solver interplay the pH gauge
 * depends on — the neutralisation fixed point leaves at most one bulk ion,
 * so the three pH cases (acid / Kw-alkaline / neither) are exhaustive.
 */
class AnalyteTest {

	@BeforeAll
	static void load() {
		EngineHarness.load();
	}

	@Test
	void pureWaterAndNeutralSaltsReadPh7() {
		assertEquals(7, Analyte.ph(0, 0, 10_000_000), "pure water is pH 7 by definition");
		// a fully neutralised salt solution: the solver's fixed point has neither
		// bulk ion — the free pH-7 the Kw reading layer promised (plans/04 §9.5)
		Solution s = solve(mol(Solution.WATER, 10_000_000L),
			ions("H+1", 1000L, "Cl-1", 1000L, "Na+1", 1000L, "OH-1", 1000L), 20);
		assertEquals(0L, s.ions().getOrDefault("H+1", 0L));
		assertEquals(0L, s.ions().getOrDefault("OH-1", 0L));
		assertEquals(7, Analyte.ph(s.ions().getOrDefault("H+1", 0L),
			s.ions().getOrDefault("OH-1", 0L), s.molecular().get(Solution.WATER)));
	}

	@Test
	void bothSidesOfThePhScaleAreLogarithmic() {
		// 1000 mB of water = 1e7 units: [H⁺] = 1 is the 10⁻⁷ resolution gate
		assertEquals(0, Analyte.ph(10_000_000, 0, 10_000_000));
		assertEquals(2, Analyte.ph(100_000, 0, 10_000_000));
		assertEquals(4, Analyte.ph(1_000, 0, 10_000_000));
		assertEquals(7, Analyte.ph(1, 0, 10_000_000), "the single autoionised unit reads pH 7");
		// alkaline side via Kw: pH = 14 + log10([OH⁻])
		assertEquals(14, Analyte.ph(0, 10_000_000, 10_000_000));
		assertEquals(12, Analyte.ph(0, 100_000, 10_000_000));
		assertEquals(7, Analyte.ph(0, 1, 10_000_000));
	}

	@Test
	void titrationJumpsAcrossEquivalence() {
		// 1000 mB water; 1 mB of base beyond equivalence: [OH⁻]=1e-3 → pH ≈ 11
		Solution alkaline = solve(mol(Solution.WATER, 10_000_000L),
			ions("Na+1", 30_000L, "Cl-1", 30_000L, "H+1", 10_000L, "OH-1", 20_000L), 20);
		long oh = alkaline.ions().getOrDefault("OH-1", 0L);
		assertEquals(10_000L, oh, "excess base survives neutralisation");
		assertEquals(11, Analyte.ph(0, oh, 10_000_000));
		// 1 mB of acid beyond equivalence: [H⁺]=1e-3 → pH ≈ 3 — the endpoint jump
		Solution acidic = solve(mol(Solution.WATER, 10_000_000L),
			ions("Na+1", 20_000L, "Cl-1", 20_000L, "H+1", 30_000L, "OH-1", 10_000L), 20);
		long h = acidic.ions().getOrDefault("H+1", 0L);
		assertEquals(20_000L, h);
		assertEquals(3, Analyte.ph(h, 0, 10_000_000));
	}

	@Test
	void weakBaseReadsMildlyAlkaline() {
		// ammonia at c=2e-3 ionises x=sqrt(Kb·c)≈1.9e-4 → pH ≈ 10.3 — the
		// weak-electrolyte channel feeds the electrode the same OH⁻ it always did
		Solution s = solve(mol(Solution.WATER, 10_000_000L, "ammonia", 20_000L), ions(), 20);
		long oh = s.ions().getOrDefault("OH-1", 0L);
		assertTrue(oh > 100, "ammonia sheds measurable hydroxide (got " + oh + ")");
		int ph = Analyte.ph(0, oh, 10_000_000);
		assertTrue(ph >= 9 && ph <= 11, "dilute ammonia reads mildly alkaline, got " + ph);
	}

	@Test
	void baumeAnchorAndLinearity() {
		// the anchor: a curve-saturated NaCl brine (2 × 0.36 formula units per
		// water unit, both ions counted) reads exactly 30 °Bé
		assertEquals(30, Analyte.baume(7_200_000, 10_000_000));
		assertEquals(15, Analyte.baume(3_600_000, 10_000_000), "half concentration, half scale");
		assertEquals(3, Analyte.baume(720_000, 10_000_000), "a molecular solute raises density too");
		assertEquals(0, Analyte.baume(0, 10_000_000));
		assertEquals(30, Analyte.baume(100_000_000, 10_000_000), "clamped at full scale");
	}

	@Test
	void turbidityBins() {
		assertEquals(0, Analyte.turbidityBin(0, 10_000_000));
		assertEquals(0, Analyte.turbidityBin(99_999, 10_000_000), "below 1% reads clear");
		assertEquals(1, Analyte.turbidityBin(100_000, 10_000_000), "1% = 微浑");
		assertEquals(2, Analyte.turbidityBin(500_000, 10_000_000), "5% = 浑");
		assertEquals(3, Analyte.turbidityBin(2_000_000, 10_000_000), "20% = 浆");
	}
}
