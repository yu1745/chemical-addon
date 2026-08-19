package com.yu1745.chemicaladdon.parity;

import static com.yu1745.chemicaladdon.composition.EngineHarness.id;
import static com.yu1745.chemicaladdon.composition.EngineHarness.ions;
import static com.yu1745.chemicaladdon.composition.EngineHarness.mol;
import static com.yu1745.chemicaladdon.composition.EngineHarness.water;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.yu1745.chemicaladdon.composition.Analyte;
import com.yu1745.chemicaladdon.composition.Chemistry;
import com.yu1745.chemicaladdon.composition.EngineHarness;
import com.yu1745.chemicaladdon.composition.Solution;
import com.yu1745.chemengine.kernel.IPhreeqc;

/**
 * P1 parity baseline: run the mod's own solver and the vendored IPhreeqc
 * kernel on the same five representative compositions and print a side-by-side
 * numeric table. This test pins <b>qualitative</b> agreement only — genuine
 * semantic differences between the engines are recorded, not failed.
 *
 * <p>Unit conventions used to line the two engines up:
 * <ul>
 * <li>Mod engine: amounts are integer solver units; 1 mB of water =
 *     {@link Chemistry#UNIT_PER_MB} = 10 000 units, and the pH gauge reads
 *     {@code -log10(HUnits/waterUnits)} — i.e. the engine's own
 *     "concentration" is the unit ratio. To express an intended molality
 *     {@code c} we feed {@code c × waterUnits} units of solute (this is the
 *     convention the engine's own AnalyteTest calibrates: 1e5 H in 1e7 water
 *     reads pH 2).</li>
 * <li>IPhreeqc: plain molality totals (mol/kgw) with {@code pH charge}
 *     electroneutrality balancing, sit.dat + addenda.</li>
 * </ul>
 */
class ParityBaselineTest {

	/**
	 * Test-scale basis: 10 000 units of water; an intended concentration c is
	 * fed as {@code c × 10 000} units (the harness convention of
	 * PrecipitationTest/WeakElectrolyteTest — e.g. c=0.01 → 100 units). The
	 * mod's pH gauge reads -log10(HUnits/waterUnits), so this encoding makes
	 * "0.01 molal strong acid" read pH 2 exactly (AnalyteTest's calibration).
	 */
	private static final long WATER = 10_000;

	/** Debug dump of the mod solver's four domains (kept small). */
	private static String dump(Solution s) {
		return "mol=" + s.molecular() + " ions=" + s.ions() + " susp=" + s.suspended() + " sed=" + s.sediment();
	}

	private static final List<String> TABLE = new ArrayList<>();

	@BeforeAll
	static void load() {
		EngineHarness.load();
	}

	// ------------------------------------------------------------- helpers

	private static int modPh(Solution s) {
		return Analyte.ph(s.ions().getOrDefault("H+1", 0L), s.ions().getOrDefault("OH-1", 0L),
				s.molecular().getOrDefault(Solution.WATER, 0L));
	}

	/** Engine "concentration" of an ion = ion units / water units (its own molality analogue). */
	private static double modConc(Solution s, String ion) {
		long water = s.molecular().getOrDefault(Solution.WATER, 0L);
		return water <= 0 ? 0 : s.ions().getOrDefault(ion, 0L) / (double) water;
	}

	private static double modPrecipUnits(Solution s, String species) {
		return s.suspended().getOrDefault(id(species), 0L) + s.sediment().getOrDefault(id(species), 0L);
	}

	private static void row(String scenario, String quantity, double modVal, double phreeqcVal, String unit) {
		double diff = phreeqcVal == 0 && modVal == 0 ? 0
				: phreeqcVal == 0 ? Double.POSITIVE_INFINITY : Math.abs(modVal - phreeqcVal) / Math.abs(phreeqcVal);
		String flag = unit.equals("pH") ? (Math.abs(modVal - phreeqcVal) > 1.0 ? "  <== ΔpH>1" : "")
				: (diff > 0.30 ? String.format("  <== Δ%.0f%%", diff * 100) : "");
		TABLE.add(String.format("%-28s %-22s %12.4g  %12.4g  %-6s%s", scenario, quantity, modVal, phreeqcVal, unit, flag));
	}

	private static void assertPhNear(String scenario, int modPh, double phreeqcPh, double tol) {
		assertTrue(Math.abs(modPh - phreeqcPh) <= tol,
				scenario + ": mod pH " + modPh + " vs IPhreeqc pH " + String.format("%.2f", phreeqcPh)
						+ " (tolerance " + tol + ")");
	}

	// ------------------------------------------------------------ scenarios

	@Test
	void hclSolution() {
		// NB: the mod solver never auto-dissociates molecular feeds — strong
		// electrolyte must be fed as ions (RulesEngine does this upstream).
		Solution s = EngineHarness.solve(WATER, ions("H+1", 100L, "Cl-1", 100L), 20);
		System.out.println("[mod HCl] " + dump(s));
		int ph = modPh(s);
		try (IPhreeqc q = IPhreeqc.create()) {
			IPhreeqc.RunResult r = q.run("""
					SOLUTION 1 dilute HCl
					    temp      20
					    pH        7  charge
					    Cl        0.01 mol/kgw
					SELECTED_OUTPUT 1
					    -molalities H+  Cl-
					    -pH       true
					END
					""");
			double pPh = r.row(0).d("pH");
			row("1 HCl 0.01", "pH", ph, pPh, "pH");
			row("1 HCl 0.01", "m_H+", modConc(s, "H+1"), r.row(0).d("m_H+"), "molal");
			row("1 HCl 0.01", "m_Cl-", modConc(s, "Cl-1"), r.row(0).d("m_Cl-"), "molal");
			assertPhNear("HCl", ph, pPh, 0.6);
		}
	}

	@Test
	void naohSolution() {
		Solution s = EngineHarness.solve(WATER, ions("Na+1", 100L, "OH-1", 100L), 20);
		System.out.println("[mod NaOH] " + dump(s));
		int ph = modPh(s);
		try (IPhreeqc q = IPhreeqc.create()) {
			IPhreeqc.RunResult r = q.run("""
					SOLUTION 1 dilute NaOH
					    temp      20
					    pH        7  charge
					    Na        0.01 mol/kgw
					SELECTED_OUTPUT 1
					    -molalities Na+  OH-
					    -pH       true
					END
					""");
			double pPh = r.row(0).d("pH");
			row("2 NaOH 0.01", "pH", ph, pPh, "pH");
			row("2 NaOH 0.01", "m_OH-", modConc(s, "OH-1"), r.row(0).d("m_OH-"), "molal");
			assertPhNear("NaOH", ph, pPh, 0.6);
		}
	}

	@Test
	void agclPrecipitation() {
		// 0.01 "molal" each of Ag+, NO3-, Na+, Cl- → AgCl(s)
		Solution s = EngineHarness.solveToFixpoint(WATER,
				ions("Ag+1", 100L, "NO3-1", 100L, "Na+1", 100L, "Cl-1", 100L), 20);
		System.out.println("[mod AgCl] " + dump(s));
		double modAgCl = modPrecipUnits(s, "silver_chloride") / (double) WATER; // in "molal" units
		double modAgAq = modConc(s, "Ag+1");
		try (IPhreeqc q = IPhreeqc.create()) {
			IPhreeqc.RunResult r = q.run("""
					SOLUTION 1 AgNO3 + NaCl
					    temp      20
					    pH        7  charge
					    pe        4
					    Ag        0.01 mol/kgw
					    N(5)      0.01 mol/kgw
					    Na        0.01 mol/kgw
					    Cl        0.01 mol/kgw
					EQUILIBRIUM_PHASES 1
					    AgCl(cr)  0     1e-6
					SELECTED_OUTPUT 1
					    -molalities Ag+  Cl-
					    -pH       true
					USER_PUNCH 1
					    -headings pAgCl
					    -start
					    10 PUNCH EQUI("AgCl(cr)")
					    -end
					END
					""");
			double precip = r.row(r.rowCount() - 1).d("pAgCl") - 1e-6;
			double pAgAq = r.row(r.rowCount() - 1).d("m_Ag+");
			row("3 AgCl ppt", "AgCl precipitated", modAgCl, precip, "mol/kgw");
			row("3 AgCl ppt", "m_Ag+ residual", modAgAq, pAgAq, "molal");
			row("3 AgCl ppt", "pH", modPh(s), r.row(r.rowCount() - 1).d("pH"), "pH");
			assertTrue(modAgCl > 0.008, "mod side should precipitate most AgCl, got " + modAgCl);
			assertTrue(precip > 0.008, "phreeqc side should precipitate most AgCl, got " + precip);
		}
	}

	@Test
	void calciteSaturation() {
		// Ca + carbonate at equal 0.01: limestone Ksp precipitation vs Calcite equilibrium.
		// (The mod species set has no CO2/carbonic-acid equilibrium, so CO2 blowing
		// cannot be modelled — this scenario degrades to a plain Ksp comparison.)
		Solution s = EngineHarness.solveToFixpoint(WATER, ions("Ca+2", 100L, "CO3-2", 100L), 20);
		System.out.println("[mod CaCO3] " + dump(s));
		double modCaCO3 = modPrecipUnits(s, "limestone") / (double) WATER;
		double modCaAq = modConc(s, "Ca+2");
		try (IPhreeqc q = IPhreeqc.create()) {
			IPhreeqc.RunResult r = q.run("""
					SOLUTION 1 CaCO3 slug
					    temp      20
					    pH        7  charge
					    pe        4
					    Ca        0.01 mol/kgw
					    C(4)      0.01 mol/kgw
					EQUILIBRIUM_PHASES 1
					    Calcite   0     1e-6
					SELECTED_OUTPUT 1
					    -molalities Ca+2  CO3-2
					    -pH       true
					USER_PUNCH 1
					    -headings pCalcite
					    -start
					    10 PUNCH EQUI("Calcite")
					    -end
					END
					""");
			double precip = r.row(r.rowCount() - 1).d("pCalcite") - 1e-6;
			double pCa = r.row(r.rowCount() - 1).d("m_Ca+2");
			row("4 CaCO3 sat", "Calcite precipitated", modCaCO3, precip, "mol/kgw");
			row("4 CaCO3 sat", "m_Ca+2 aq", modCaAq, pCa, "molal");
			row("4 CaCO3 sat", "pH", modPh(s), r.row(r.rowCount() - 1).d("pH"), "pH");
			assertTrue(modCaCO3 > 0.005, "mod side should precipitate bulk CaCO3, got " + modCaCO3);
			assertTrue(precip > 0.005, "phreeqc side should precipitate bulk Calcite, got " + precip);
		}
	}

	@Test
	void ammoniaWeakBase() {
		// NH3 0.01 in water; authored Kb log_k = -4.75, water activity 1.
		Solution s = EngineHarness.solve(mol(Solution.WATER, WATER, id("ammonia"), 100L), Map.of(), 20);
		System.out.println("[mod NH3] " + dump(s));
		int ph = modPh(s);
		double modOh = modConc(s, "OH-1");
		double modNh4 = modConc(s, "NH4+1");
		try (IPhreeqc q = IPhreeqc.create()) {
			IPhreeqc.RunResult r = q.run("""
					SOLUTION 1 ammonia water
					    temp      20
					    pH        7  charge
					    pe        4
					    N(-3)     0.01 mol/kgw
					SELECTED_OUTPUT 1
					    -molalities NH4+  OH-
					    -pH       true
					END
					""");
			double pPh = r.row(0).d("pH");
			row("5 NH3 0.01", "pH", ph, pPh, "pH");
			row("5 NH3 0.01", "m_OH-", modOh, r.row(0).d("m_OH-"), "molal");
			row("5 NH3 0.01", "m_NH4+", modNh4, r.row(0).d("m_NH4+"), "molal");
			// both engines are Kb-driven on the same -4.75: agreement within 1 pH unit
			assertPhNear("NH3", ph, pPh, 1.0);
			assertTrue(ph >= 9 && ph <= 12, "mod NH3 pH should be ~10-11, got " + ph);
		}
	}

	@org.junit.jupiter.api.AfterAll
	static void report() {
		System.out.println();
		System.out.println("================ ParityBaselineTest: mod solver vs IPhreeqc (sit.dat) ================");
		System.out.printf("%-28s %-22s %14s  %14s  %-6s%n", "scenario", "quantity", "mod", "IPhreeqc", "unit");
		System.out.println("-".repeat(110));
		TABLE.forEach(System.out::println);
		System.out.println("(rows flagged <== are differences beyond the 30% / 1-pH threshold)");
		System.out.println("=====================================================================================");
	}
}
