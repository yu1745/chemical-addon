package com.yu1745.chemengine;

import static com.yu1745.chemengine.State.mb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yu1745.chemengine.solver.Solver;
import org.junit.jupiter.api.Test;

/**
 * Real-data kinetics: limestone (limescale) carries rate 0.0001 (per unit water per
 * tick), so big precipitations are gradual while small cleanups stay effectively
 * instant (budget = rate * water * drive, drive clamped at 1000 -> ~100 MB/tick when
 * far from equilibrium).
 */
class RealDataRateTest {

    private final Engine e = Harness.engine();

    @Test void limescalePrecipitatesGraduallyAtLargeScale() {
        State s = new State(25).ions("Ca+2", mb(300)).ions("Cl-1", mb(300))
            .ions("Na+1", mb(300)).ions("CO3-2", mb(300)).water(mb(1000));
        Solver.Result first = e.solveClosed(s);
        long sed = first.state.suspended().getOrDefault("chemicaladdon:limestone", 0L);
        assertTrue(sed < mb(300), "first tick only partial scale: " + sed);
        assertTrue(first.rateLimited.contains("chemicaladdon:limestone"), "flagged rate-limited");

        for (int i = 0; i < 20; i++) s = e.solveClosed(s).state;
        assertTrue(s.suspended().getOrDefault("chemicaladdon:limestone", 0L) >= mb(299), "converges: " + s);
    }

    @Test void smallCleanupIsStillInstant() {
        // 20 CaCO3 worth of scale: budget ~100 > extent 20 -> completes in one tick
        // (fixture neutralised with spectator Na+; was net -40 mB)
        State s = new State(25).ions("Ca+2", mb(20)).ions("Na+1", mb(40))
            .ions("Cl-1", mb(40)).ions("CO3-2", mb(20)).water(mb(1000));
        Solver.Result r = e.solveClosed(s);
        assertTrue(r.state.suspended().getOrDefault("chemicaladdon:limestone", 0L) >= mb(19), "small cleanup instant");
        assertTrue(r.state.ionAmount("Ca+2") < mb(1));
    }
}
