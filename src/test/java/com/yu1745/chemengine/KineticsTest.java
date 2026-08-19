package com.yu1745.chemengine;

import static com.yu1745.chemengine.State.mb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yu1745.chemengine.solver.Solver;
import com.yu1745.chemengine.solver.SystemModel;
import org.junit.jupiter.api.Test;

/**
 * Temperature (solubility curves + van't Hoff) and the two-speed kinetics:
 * crystallisation grows by an affinity law per solve tick, unseeded solutions sit
 * metastable below the nucleation gate, one seed crystal collapses them, dissolution
 * is instant, and boiling a pot dry crashes everything out.
 */
class KineticsTest {

    private final Engine e = Harness.engine();

    private static State kno3(long k, long no3, int tempC) {
        return new State(tempC).ions("K+1", k).ions("NO3-1", no3).water(mb(1000));
    }

    @Test void coolingKno3CrystallisesOverTicks() {
        // 60 C: 110 g/100g -> cap 1100, 500 KNO3 all dissolved
        State hot = e.solveClosed(kno3(mb(500), mb(500), 60)).state;
        assertEquals(mb(0), hot.sedimentAmount("chemicaladdon:potassium_nitrate"), "hot: all dissolved");

        // 20 C: 31.6 g/100g -> cap 316. First tick: unseeded nucleation, slow.
        State cold = new State(20).ions("K+1", mb(500)).ions("NO3-1", mb(500)).water(mb(1000));
        State s = cold;
        for (int i = 0; i < 60; i++) {
            s = e.solveClosed(s).state;
        }
        long sed = s.sedimentAmount("chemicaladdon:potassium_nitrate");
        // converges exactly to 184.000 mB (= 500 - 316 cap); band [176, 190] keeps
        // >=3% margin on both sides (upper edge was 187 = 1.6%, flagged knife-edge by
        // the independent re-review)
        assertTrue(sed >= mb(176) && sed <= mb(190), "cooling precipitates ~184 KNO3: sed=" + sed + " " + s);
        assertEquals(mb(500) - sed, s.ionAmount("K+1"), "K leaves solution");
        assertEquals(mb(0), s.netCharge());
    }

    @Test void metastableWithoutSeedThenSeedingCollapses() {
        // 20 C: 400 KNO3 vs cap 316 -> supersaturation 0.266 < 0.5 nucleation gate
        State s = kno3(mb(400), mb(400), 20);
        s = e.solveClosed(s).state;
        assertEquals(mb(0), s.sedimentAmount("chemicaladdon:potassium_nitrate"), "metastable: no crystal without seed");

        // one seed crystal collapses the metastable zone
        s.adjustSediment("chemicaladdon:potassium_nitrate", mb(1));
        s = e.solveClosed(s).state;
        assertTrue(s.sedimentAmount("chemicaladdon:potassium_nitrate") > mb(1), "seed grows: " + s);
    }

    @Test void heatingRedissolvesCrystals() {
        // crystallise at 20 C, then heat back to 60 C -> all redissolve (instant)
        State s = kno3(mb(500), mb(500), 20);
        for (int i = 0; i < 60; i++) s = e.solveClosed(s).state;
        assertTrue(s.sedimentAmount("chemicaladdon:potassium_nitrate") > 0);
        State hot = e.solveClosed(new State(60)
            .ions(s.ions()).molecules(s.molecules()).sediment(s.sediment())).state;
        assertEquals(mb(0), hot.sedimentAmount("chemicaladdon:potassium_nitrate"), "hot: redissolved");
    }

    @Test void brineEvaporatesToDrySalt() {
        // NaCl brine: 400 NaCl in 1000 water, boil dry -> all 400 crash out as rock salt
        State brine = new State(100).ions("Na+1", mb(400)).ions("Cl-1", mb(400)).water(mb(1000));
        brine.evaporateWater(mb(1000));
        State dry = e.solveClosed(brine).state;
        assertEquals(mb(400), dry.sedimentAmount("chemicaladdon:rock_salt"), "dry-out yields salt: " + dry);
        assertEquals(mb(0), dry.ionAmount("Na+1"), "no dissolved Na left");
    }

    @Test void neutralisationReleasesHeat() {
        State in = new State(20).ions("H+1", mb(100)).ions("Cl-1", mb(100))
            .ions("Na+1", mb(100)).ions("OH-1", mb(100)).water(mb(1000));
        Solver.Result r = e.solveClosed(in);
        // Physical anchor: 100 mB H+/OH- pairs = 100 g / 18 g/mol = 5.56 mol;
        // 5.56 mol * 57.1 kJ/mol = 317.2 kJ. The assertion uses the same constant the
        // solver does (near-tautological by design — it pins the exact accounting
        // path), so the independent anchors are heatRiseC (uses feedUnits and the
        // specific-heat constant) and the energy magnitude itself.
        assertEquals(mb(100) * Solver.NEUTRALISATION_J_PER_PAIR, r.energyJ, 1.0, "100 pairs of neutralisation heat");
        assertTrue(r.energyJ > 300_000 && r.energyJ < 335_000, "magnitude = 5.56 mol * 57.1 kJ/mol: " + r.energyJ);
        assertTrue(r.heatRiseC > 40.0, "concentrated neutralisation self-heats: " + r.heatRiseC);
    }

    @Test void vanthoffMakesLimestoneLessSolubleHot() {
        // Function test with the shipped limestone constants (log_k -8.3,
        // delta_h -9.61 kJ/mol): CaCO3 dissolution is exothermic, so K falls with T
        assertTrue(SystemModel.vanthoff(-8.3, -9.610648, 100) < SystemModel.vanthoff(-8.3, -9.610648, 20),
            "hot limestone is less soluble");
    }

    @Test void stirringSlowsCrystallisation() {
        State a = kno3(mb(500), mb(500), 20).stirring(1.0);
        State b = kno3(mb(500), mb(500), 20).stirring(0.3);
        long sa = 0, sb = 0;
        for (int i = 0; i < 5; i++) {
            a = e.solveClosed(a).state;
            b = e.solveClosed(b).state;
        }
        sa = a.sedimentAmount("chemicaladdon:potassium_nitrate");
        sb = b.sedimentAmount("chemicaladdon:potassium_nitrate");
        assertTrue(sb < sa, "stirring 0.3 grows slower than 1.0: " + sb + " vs " + sa);
    }
}
