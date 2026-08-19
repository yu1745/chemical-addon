package com.yu1745.chemengine;

import static com.yu1745.chemengine.State.mb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yu1745.chemengine.solver.Solver;
import org.junit.jupiter.api.Test;

/**
 * Rate-limited equilibria: a slow precipitation advances at most
 * rate * 2^((T-25)/25) * stirring * |Q/K - 1| reaction units per tick, while fast
 * equilibria stay instant. Uses a synthetic slow salt to isolate the mechanism
 * (shipped limestone carries rate 0.0001, but mixing real data in here would couple
 * the rate check to the carbonate system).
 */
class RateLimitTest {

    private static Engine slowEngine() {
        JsonObject o = JsonParser.parseString(
            "{\"formula\":\"XY\",\"phase\":\"SOLID\",\"equilibria\":[{\"reaction\":\"slow_salt(s) = X+1 + Y-1\",\"log_k\":-2,\"rate\":0.001}]}"
        ).getAsJsonObject();
        Species slow = Species.parse("slow_salt", o);
        SpeciesDatabase db = new SpeciesDatabase().register(slow);
        return Engine.from(db);
    }

    @Test void firstTickAdvancesByAffinityBudget() {
        Engine e = slowEngine();
        // 30 each in 1000 water: Q = 9e-4 vs K = 1e-4 (with -2 mineral offset), drive = 8,
        // budget = 1 * 2^0 * 1 * 8 = 8 reaction units; equilibrium extent is 20.
        State in = new State(25).ions("X+1", mb(30)).ions("Y-1", mb(30)).water(mb(1000));
        Solver.Result r = e.solveClosed(in);
        assertEquals(mb(8), r.state.suspended().getOrDefault("chemicaladdon:slow_salt", 0L),
            "first tick precipitates only the budget: " + r.state);
        assertTrue(r.rateLimited.contains("chemicaladdon:slow_salt"), "flagged rate-limited");
        assertEquals(mb(0), r.state.netCharge());
    }

    @Test void repeatedTicksConvergeToEquilibrium() {
        Engine e = slowEngine();
        State s = new State(25).ions("X+1", mb(30)).ions("Y-1", mb(30)).water(mb(1000));
        for (int i = 0; i < 50; i++) {
            s = e.solveClosed(s).state;
        }
        // equilibrium: [X][Y] = 1e-4 -> 10 each dissolved -> 20 precipitated
        long sed = s.suspended().getOrDefault("chemicaladdon:slow_salt", 0L);
        assertTrue(sed >= mb(19), "slowly reaches full precipitation: " + s);
        assertTrue(s.ionAmount("X+1") < mb(11));
        assertEquals(mb(0), s.netCharge());
    }

    @Test void rateIsScaleRelative() {
        // same concentrations at 1000 and 100,000 water must move the same fraction
        Engine e = slowEngine();
        long k = 100;
        State small = new State(25).ions("X+1", mb(30)).ions("Y-1", mb(30)).water(mb(1000));
        State big = new State(25).ions("X+1", mb(30 * k)).ions("Y-1", mb(30 * k)).water(mb(1000 * k));
        long smallSed = e.solveClosed(small).state.suspended().getOrDefault("chemicaladdon:slow_salt", 0L);
        long bigSed = e.solveClosed(big).state.suspended().getOrDefault("chemicaladdon:slow_salt", 0L);
        assertEquals(smallSed * k, bigSed, 2 * k, "same clock for 1000 vs 100,000 water (+-1 tick rounding)");
    }

    @Test void hotTemperatureSpeedsUpAndStirringSlowsDown() {
        Engine e = slowEngine();
        State hot = new State(50).ions("X+1", mb(30)).ions("Y-1", mb(30)).water(mb(1000));
        State cold = new State(25).ions("X+1", mb(30)).ions("Y-1", mb(30)).water(mb(1000));
        State stirred = new State(25).stirring(0.3).ions("X+1", mb(30)).ions("Y-1", mb(30)).water(mb(1000));
        long h = e.solveClosed(hot).state.suspended().getOrDefault("chemicaladdon:slow_salt", 0L);
        long c = e.solveClosed(cold).state.suspended().getOrDefault("chemicaladdon:slow_salt", 0L);
        long st = e.solveClosed(stirred).state.suspended().getOrDefault("chemicaladdon:slow_salt", 0L);
        // 50 C: 2^((50-25)/25) = 2 -> budget 30 ; 25 C: budget 15 ; stirred 0.3: budget ~4.5->5
        assertTrue(h > c, "hot faster: " + h + " vs " + c);
        assertTrue(st < c, "stirring 0.3 slower: " + st + " vs " + c);
    }
}
