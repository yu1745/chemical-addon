package com.yu1745.chemengine;

import static com.yu1745.chemengine.State.mb;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yu1745.chemengine.solver.Solver;
import org.junit.jupiter.api.Test;

/**
 * Industrial acid/base synthesis sub-reactions that can be represented with the
 * existing aqueous/gas engine and data-only additions.
 */
class IndustrialSynthesisTest {

    private final Engine e = Harness.engine();

    @Test void sulfurTrioxideAbsorbsToSulfuricAcid() {
        Solver.Result r = e.solveClosed(new State(20)
            .molecule("chemicaladdon:sulfur_trioxide", mb(100)).water(mb(1000)));
        State s = r.state;
        assertTrue(s.ions().getOrDefault("H+1", 0L) >= mb(190),
            "SO3 absorption produces H+: " + s);
        assertTrue(s.ions().getOrDefault("SO4-2", 0L) >= mb(99),
            "SO3 absorption produces sulfate: " + s);
        assertTrue(s.molecules().getOrDefault("chemicaladdon:sulfur_trioxide", 0L) < mb(1),
            "SO3 is consumed: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void hydrogenChlorideEvolvesFromSaltAndSulfuricAcidOpen() {
        Solver.Result r = e.solveOpen(new State(20)
            .ions("Na+1", mb(200)).ions("Cl-1", mb(200))
            .ions("H+1", mb(200)).ions("SO4-2", mb(100)).water(mb(1000)));
        State s = r.state;
        assertTrue(r.gasVented.getOrDefault("chemicaladdon:hydrogen_chloride", 0L) >= mb(100),
            "HCl gas evolves: " + r.gasVented);
        assertTrue(s.ions().getOrDefault("Cl-1", 0L) < mb(100),
            "chloride leaves the solution as HCl: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void nitrogenDioxideAbsorbsToNitricAcid() {
        // Runs at 20 C (below the 25 C K reference) with the authoritative NO2
        // absorption delta_h (-138.18 kJ/mol): this exercises the Van't Hoff
        // shift AND the stoichiometric pre-seed fix for the strongly non-linear
        // (Q^5) reaction, which used to stall at 20 C with large inputs.
        Solver.Result r = e.solveClosed(new State(20)
            .molecule("chemicaladdon:nitrogen_dioxide", mb(100)).water(mb(1000)));
        State s = r.state;
        assertTrue(s.ions().getOrDefault("H+1", 0L) >= mb(60),
            "NO2 absorption produces acid: " + s);
        assertTrue(s.ions().getOrDefault("NO3-1", 0L) >= mb(60),
            "NO2 absorption produces nitrate: " + s);
        assertTrue(s.molecules().getOrDefault("chemicaladdon:nitric_oxide", 0L) >= mb(30),
            "NO is produced by NO2 disproportionation: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void sulfurTrioxideAbsorptionReleasesHeat() {
        Solver.Result r = e.solveClosed(new State(20)
            .molecule("chemicaladdon:sulfur_trioxide", mb(100)).water(mb(1000)));
        assertTrue(r.energyJ > 0, "SO3 hydration is exothermic: " + r.energyJ);
        assertTrue(r.heatRiseC > 0, "released heat raises temperature: " + r.heatRiseC);
        // magnitude check: 100 mB SO3 (molarMass 80) * 227.72 kJ/mol = 284.6 kJ
        assertTrue(r.energyJ > 250_000 && r.energyJ < 320_000,
            "SO3 hydration heat matches delta_h (-227.72 kJ/mol): " + r.energyJ);
    }

    @Test void causticisationProducesSodiumHydroxideAndCalciumCarbonate() {
        State s = new State(20)
            .ions("Na+1", mb(200)).ions("CO3-2", mb(100))
            .suspended("chemicaladdon:slaked_lime", mb(100)).water(mb(1000));
        for (int i = 0; i < 40; i++) s = e.solveClosed(s).state;
        assertTrue(s.suspended().getOrDefault("chemicaladdon:limestone", 0L) >= mb(99),
            "CaCO3 precipitates: " + s);
        assertTrue(s.suspended().getOrDefault("chemicaladdon:slaked_lime", 0L) < mb(1),
            "slaked lime consumed: " + s);
        assertTrue(s.ions().getOrDefault("OH-1", 0L) >= mb(199),
            "NaOH/OH- produced: " + s);
        assertTrue(s.ions().getOrDefault("Ca+2", 0L) < mb(1),
            "calcium is fixed as carbonate: " + s);
        assertEquals(mb(0), s.netCharge());
    }
}
