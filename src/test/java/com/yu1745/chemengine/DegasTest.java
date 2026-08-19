package com.yu1745.chemengine;

import static com.yu1745.chemengine.State.mb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yu1745.chemengine.solver.Solver;
import org.junit.jupiter.api.Test;

/** Open-vessel gas escape vs sealed-vessel retention. */
class DegasTest {

    @Test
    void openVesselVentsDissolvedGasBeyondHenryRetention() {
        // 1000 water retains 1 unit of a default-retention gas; put 50 units in.
        // Note: the vented amount is floored to whole mB, so with retention = 1 mB the
        // result sits exactly at 49 — this assertion pins that floor boundary.
        State in = new State(20)
            .molecule("chemicaladdon:carbon_dioxide", mb(50))
            .ions("Na+1", mb(100)).ions("Cl-1", mb(100))
            .water(mb(1000));
        Solver.Result r = Harness.engine().solveOpen(in);
        assertTrue(r.gasVented.getOrDefault("chemicaladdon:carbon_dioxide", 0L) >= mb(49),
            "excess gas vents: " + r.gasVented);
        assertEquals(mb(0), r.state.netCharge(), "charge neutral");
    }

    @Test
    void closedVesselKeepsGasDissolved() {
        State in = new State(20)
            .molecule("chemicaladdon:carbon_dioxide", mb(50))
            .ions("Na+1", mb(100)).ions("Cl-1", mb(100))
            .water(mb(1000));
        Solver.Result r = Harness.engine().solveClosed(in);
        assertEquals(0, r.gasVented.size(), "sealed vessel keeps gas: " + r.gasVented);
    }

    @Test
    void malachitePrecipitatesAtProductionScale() {
        long scale = 10_000;
        State in = new State(20)
            .ions("Cu+2", mb(100 * scale)).ions("SO4-2", mb(100 * scale))
            .ions("Na+1", mb(400 * scale)).ions("CO3-2", mb(200 * scale))
            .water(mb(1000 * scale));
        State s = Harness.engine().solveClosed(in).state;
        long malachite = s.suspended().getOrDefault("chemicaladdon:copper_carbonate", 0L);
        long hydroxide = s.suspended().getOrDefault("chemicaladdon:copper_hydroxide", 0L);
        long cuResidual = s.ions().getOrDefault("Cu+2", 0L);
        assertTrue(malachite > mb(0), "malachite precipitates at scale: " + s);
        assertTrue(hydroxide < mb(1), "malachite wins over copper hydroxide at scale: " + s);
        assertEquals(mb(100 * scale), cuResidual + 2 * malachite + hydroxide, "copper conserved at scale");
        assertEquals(mb(200 * scale),
            s.ions().getOrDefault("CO3-2", 0L) + s.ions().getOrDefault("HCO3-1", 0L)
                + s.molecules().getOrDefault("chemicaladdon:carbon_dioxide", 0L) + malachite,
            "carbon conserved at scale");
        assertEquals(mb(0), s.netCharge(), "charge neutral at scale");
    }
}
