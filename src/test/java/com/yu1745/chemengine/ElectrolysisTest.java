package com.yu1745.chemengine;

import static com.yu1745.chemengine.State.mb;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yu1745.chemengine.solver.Solver;
import org.junit.jupiter.api.Test;

/** D1b: forced-progress electrolysis coupled to the equilibrium re-solve. */
class ElectrolysisTest {

    private final Engine e = Harness.engine();

    private static final Electrolysis WATER = Electrolysis.parse(
        "2 water = 2 chemicaladdon:hydrogen + chemicaladdon:oxygen");
    private static final Electrolysis CHLORALKALI = Electrolysis.parse(
        "2 Cl-1 + 2 water = 2 OH-1 + chemicaladdon:hydrogen + chemicaladdon:chlorine");

    @Test void waterElectrolysis_producesAndVentsHydrogenAndOxygen() {
        // 2 H2O -> 2 H2 + O2; gases evolve in an open cell. mb(10) progress produces
        // mb(20) H2 + mb(10) O2; the low-solubility gases vent almost completely.
        Solver.Result r = e.electrolyze(new State(20).water(mb(1000)), WATER, mb(10));
        State s = r.state;
        assertTrue(r.gasVented.getOrDefault("chemicaladdon:hydrogen", 0L) >= mb(18),
            "hydrogen vents ~= produced: " + r.gasVented);
        assertTrue(r.gasVented.getOrDefault("chemicaladdon:oxygen", 0L) >= mb(8),
            "oxygen vents ~= produced: " + r.gasVented);
        assertTrue(s.waterAmount() <= mb(980), "water consumed: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void chloralkali_consumesChlorideMakesHypochloriteAndVentsHydrogen() {
        // 2 NaCl + 2 H2O -> 2 NaOH + H2 + Cl2 (Na+ spectator). mb(50) progress consumes
        // mb(100) chloride and makes mb(100) OH- + mb(50) Cl2. In a batch the Cl2 meets
        // the OH- and disproportionates (Cl2+2OH-=Cl-+ClO-+H2O, logK 15.3), giving the
        // classic mixed-product result: hypochlorite bleach + residual NaCl + H2.
        Solver.Result r = e.electrolyze(new State(20)
            .ions("Na+1", mb(100)).ions("Cl-1", mb(100)).water(mb(1000)),
            CHLORALKALI, mb(50));
        State s = r.state;
        assertEquals(mb(100), s.ionAmount("Na+1"), "sodium conserved: " + s);
        assertTrue(s.ionAmount("ClO-1") >= mb(40), "hypochlorite (bleach) formed: " + s);
        assertTrue(s.ionAmount("Cl-1") >= mb(40) && s.ionAmount("Cl-1") <= mb(60),
            "residual chloride (NaCl): " + s);
        assertTrue(r.gasVented.getOrDefault("chemicaladdon:hydrogen", 0L) >= mb(45),
            "hydrogen vents: " + r.gasVented);
        // total chlorine conserved across solution (Cl-, ClO-, dissolved Cl2) + vented Cl2;
        // each Cl2 molecule carries 2 Cl atoms
        long clTotal = s.ionAmount("Cl-1") + s.ionAmount("ClO-1")
            + 2 * s.moleculeAmount("chemicaladdon:chlorine")
            + 2 * r.gasVented.getOrDefault("chemicaladdon:chlorine", 0L);
        assertEquals(mb(100), clTotal, "chlorine conserved: " + s + " vented=" + r.gasVented);
        assertEquals(mb(0), s.netCharge());
    }
}
