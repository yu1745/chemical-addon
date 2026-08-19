package com.yu1745.chemengine;

import static com.yu1745.chemengine.State.mb;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * NH4Cl is the by-product / mother-liquor salt in Hou's combined soda process.
 * Covers both cooling crystallisation and NaCl common-ion salting-out.
 */
class AmmoniumChlorideTest {

    private final Engine e = Harness.engine();

    @Test void ammoniumChlorideCrystallisesWhenCold() {
        State s = new State(0)
            .ions("NH4+1", mb(500)).ions("Cl-1", mb(500))
            .water(mb(1000));
        for (int i = 0; i < 60; i++) s = e.solveClosed(s).state;

        long sediment = s.sedimentAmount("chemicaladdon:ammonium_chloride");
        // 0 C solubility 29.4 g/100g -> cap 294 mB: 500 input converges to ~206 mB
        // sediment with ~294 mB NH4+ in solution (exact solubility-curve equilibrium).
        assertTrue(sediment >= mb(190), "NH4Cl crystallises on cooling: " + s);
        assertTrue(s.ions().getOrDefault("NH4+1", 0L) < mb(310),
            "NH4+ drops as NH4Cl precipitates: " + s);
        // all NH4 carriers conserved (NH4+, NH3, NH4Cl solid)
        assertEquals(mb(500),
            s.ions().getOrDefault("NH4+1", 0L)
                + s.molecules().getOrDefault("chemicaladdon:ammonia", 0L) + sediment,
            "ammonium conserved: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void ammoniumChlorideSaltingOutWithSodiumChloride() {
        // NH4Cl is below its own cooling curve (200 < 294 at 0C), but the extra Cl-
        // from NaCl pushes the ion product past saturation. A seed allows the seeded
        // common-ion crystallisation path to act.
        State s = new State(0)
            .ions("NH4+1", mb(200))
            .ions("Na+1", mb(300))
            .ions("Cl-1", mb(500))
            .sediment("chemicaladdon:ammonium_chloride", mb(1))
            .water(mb(1000));
        for (int i = 0; i < 60; i++) s = e.solveClosed(s).state;

        long sediment = s.sedimentAmount("chemicaladdon:ammonium_chloride");
        // common-ion equilibrium [NH4][Cl] = 294^2 (the 0 C saturation product):
        // (200-x)(500-x) = 86436 -> x ~ 19.9 mB; the solver converges to ~20.9.
        assertTrue(sediment >= mb(18),
            "NaCl common-ion effect salts out NH4Cl: " + s);
        // threshold 190 keeps >=5% margin over the converged 180.05 mB (independent
        // review flagged <187 at 3.9% as knife-edge); the conservation assertion below
        // is the real guard, the threshold is secondary
        assertTrue(s.ions().getOrDefault("NH4+1", 0L) < mb(190),
            "NH4+ decreases as NH4Cl is salted out: " + s);
        // all NH4 carriers conserved: input = 200 mB NH4+ ions + 1 mB seed crystal
        // (sediment is part of the input state, carried through the solve)
        assertEquals(mb(201),
            s.ions().getOrDefault("NH4+1", 0L)
                + s.molecules().getOrDefault("chemicaladdon:ammonia", 0L) + sediment,
            "ammonium conserved (incl. 1 mB seed): " + s);
        assertEquals(mb(0), s.netCharge());
    }
}
