package com.yu1745.chemengine;

import static com.yu1745.chemengine.State.mb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yu1745.chemengine.solver.Solver;
import org.junit.jupiter.api.Test;

/**
 * The killer case: basic copper carbonate precipitates while the carbonate/bicarbonate
 * equilibria feed it, and the produced CO2 is either retained (closed) or vented
 * (open). In the old integer solver the sub-unit OH- of the hydrolysis floor deadlocked
 * this and needed the coupleDeficits hack; the continuous solve has no such floor.
 *
 * <p>Note: with the shipped constants, malachite wins the competition with copper
 * hydroxide at this pH — the thermodynamic assemblage contains malachite only
 * (copper_hydroxide < 1 mB is asserted below). The old engine's "both solids"
 * behaviour was an ordering artifact; the invariants below are what the engine
 * must honour.
 */
class MalachiteTest {

    @Test
    void closedVesselConservesCopperCarbonateAndCharge() {
        State in = new State(20)
            .ions("Cu+2", mb(100)).ions("SO4-2", mb(100)).ions("Na+1", mb(400)).ions("CO3-2", mb(200))
            .water(mb(1000));
        State s = Harness.engine().solveClosed(in).state;

        long cuResidual = s.ions().getOrDefault("Cu+2", 0L);
        long malachite = s.suspended().getOrDefault("chemicaladdon:copper_carbonate", 0L);
        long hydroxide = s.suspended().getOrDefault("chemicaladdon:copper_hydroxide", 0L);

        // copper is exhausted by the two solids
        assertTrue(cuResidual <= mb(5), "copper exhausted: " + s);
        assertTrue(malachite > mb(0), "malachite precipitates: " + s);
        assertTrue(hydroxide < mb(1), "malachite wins over copper hydroxide: " + s);
        assertEquals(mb(100), cuResidual + 2 * malachite + hydroxide, "copper conserved");

        // carbon conserved across carbonate/bicarbonate/dissolved CO2/malachite
        long c = s.ions().getOrDefault("CO3-2", 0L)
            + s.ions().getOrDefault("HCO3-1", 0L)
            + s.molecules().getOrDefault("chemicaladdon:carbon_dioxide", 0L)
            + malachite;
        assertEquals(mb(200), c, "carbonate conserved");

        assertEquals(mb(0), s.netCharge(), "charge neutrality");
    }

    @Test
    void limestonePrecipitatesToExhaustion() {
        // limestone Ksp now carries rate 0.0001: scale forms gradually (budget ~100 MB/tick),
        // so exhaust the 300 units over several reaction ticks.
        State s = new State(20)
            .ions("Ca+2", mb(300)).ions("Cl-1", mb(300)).ions("Na+1", mb(300)).ions("CO3-2", mb(300))
            .water(mb(1000));
        for (int i = 0; i < 20; i++) s = Harness.engine().solveClosed(s).state;
        assertTrue(s.suspended().getOrDefault("chemicaladdon:limestone", 0L) >= mb(299), "scale exhausts: " + s);
        assertTrue(s.ions().getOrDefault("Ca+2", 0L) < mb(1));
        assertEquals(mb(0), s.netCharge());
    }

    @Test
    void ironHydroxideOutcompetesMagnesiumForHydroxide() {
        // Fixture neutralised (was net -300 mB: OH- 300 lacked a cation).
        State in = new State(20)
            .ions("Fe+3", mb(100)).ions("Mg+2", mb(100)).ions("Na+1", mb(300))
            .ions("Cl-1", mb(500)).ions("OH-1", mb(300))
            .water(mb(1000));
        State s = Harness.engine().solveClosed(in).state;
        assertEquals(mb(100), s.suspended().getOrDefault("chemicaladdon:iron_hydroxide", 0L));
        assertEquals(mb(0), s.suspended().getOrDefault("chemicaladdon:magnesium_hydroxide", 0L));
        assertEquals(mb(100), s.ions().getOrDefault("Mg+2", 0L));
        assertTrue(s.ions().getOrDefault("OH-1", 0L) < mb(1));
    }
}
