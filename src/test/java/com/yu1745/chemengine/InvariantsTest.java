package com.yu1745.chemengine;

import static com.yu1745.chemengine.State.mb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yu1745.chemengine.solver.SystemModel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * Adversarial invariants: random neutral ion soups must come out charge-neutral,
 * mass-conserving (every component total preserved, see the conservation assertion)
 * and the solve must be deterministic.
 */
class InvariantsTest {

    private static final String[][] CATIONS = {
        {"Na+1", "1"}, {"Ca+2", "2"}, {"Cu+2", "2"}, {"Mg+2", "2"}
    };
    private static final String[][] ANIONS = {
        {"Cl-1", "1"}, {"SO4-2", "2"}, {"CO3-2", "2"}, {"OH-1", "1"}, {"NO3-1", "1"}
    };

    @Test
    void randomNeutralSoupsStayNeutralAndDeterministic() {
        Random rnd = new Random(12345);
        Engine engine = Harness.engine();
        for (int trial = 0; trial < 200; trial++) {
            State in = randomNeutralState(rnd);
            State a;
            try {
                a = engine.solveClosed(in).state;
            } catch (RuntimeException e) {
                throw new AssertionError("trial " + trial + " input " + in, e);
            }
            State b = engine.solveClosed(in).state;
            assertEquals(mb(0), a.netCharge(), "charge: " + in + " -> " + a);
            assertEquals(a.ions(), b.ions(), "determinism ions: " + in);
            assertEquals(a.suspended(), b.suspended(), "determinism solids: " + in);
            assertComponentConservation(engine, in, a, trial);
        }
    }

    /** Every non-H component total must survive the solve exactly (the integer
     *  projection preserves each component total by construction; this assertion locks
     *  that invariant so a mass-losing bug cannot hide behind charge neutrality alone).
     *  H+ is excluded: its balance is the charge-balance equation (see SystemModel). */
    private static void assertComponentConservation(Engine engine, State in, State out, int trial) {
        var model = engine.model();
        int C = model.componentCount();
        for (int c = 0; c < C; c++) {
            String key = model.components().get(c);
            if (key.equals(SystemModel.H_PLUS) || key.equals(SystemModel.ELECTRON)) continue;
            double tin = 0, tout = 0;
            tin += componentTotal(model, in.ions(), c);
            tin += componentTotal(model, in.molecules(), c);
            tin += mineralTotal(model, in.suspended(), c);
            tout += componentTotal(model, out.ions(), c);
            tout += componentTotal(model, out.molecules(), c);
            tout += mineralTotal(model, out.suspended(), c);
            assertEquals(tin, tout, 1e-6,
                "component " + key + " conserved (trial " + trial + "): " + in + " -> " + out);
        }
        // no curve-salt sediment can form from these soups (amounts far below the
        // solubility-curve caps); if one ever appears the conservation check above
        // must be extended to cover it.
        assertEquals(java.util.Map.of(), out.sediment(), "no sediment from soup (trial " + trial + ")");
    }

    private static double componentTotal(SystemModel model, Map<String, Long> species, int c) {
        double total = 0;
        for (Map.Entry<String, Long> e : species.entrySet()) {
            if (e.getKey().equals(State.WATER)) continue;
            Integer idx = model.speciesIndexOf(e.getKey());
            if (idx != null) total += model.speciesCoeff(idx)[c] * e.getValue();
        }
        return total;
    }

    private static double mineralTotal(SystemModel model, Map<String, Long> suspended, int c) {
        double total = 0;
        for (Map.Entry<String, Long> e : suspended.entrySet()) {
            for (SystemModel.Mineral m : model.minerals()) {
                if (m.solidKey.equals(e.getKey())) {
                    total += m.coeff[c] * e.getValue();
                    break;
                }
            }
        }
        return total;
    }

    private static State randomNeutralState(Random rnd) {
        // build a neutral soup from whole salts: pick cations and balance with anions
        Map<String, Long> ions = new LinkedHashMap<>();
        long water = 1000;
        int n = 1 + rnd.nextInt(4);
        for (int i = 0; i < n; i++) {
            String[] cat = CATIONS[rnd.nextInt(CATIONS.length)];
            String[] an = ANIONS[rnd.nextInt(ANIONS.length)];
            int zc = Integer.parseInt(cat[1]);
            int za = Integer.parseInt(an[1]);
            // smallest multiple that neutralises: lcm of |zc| and |za|
            int lcm = lcm(zc, za);
            long units = lcm * (1 + rnd.nextInt(20));
            ions.merge(cat[0], units * (lcm / zc), Long::sum);
            ions.merge(an[0], units * (lcm / za), Long::sum);
        }
        // NOTE: water = 1000 quanta (1e-4 mB) puts these soups deep in the quantum-limited
        // regime: sub-quantum equilibria round to 0/1 quanta, so mass-action laws are not
        // checkable here. This test locks charge neutrality + determinism + exact component
        // conservation only (thermodynamic consistency is covered by PhysicsAuditTest).
        State in = new State(20).water(water);
        ions.forEach(in::ions);
        assertTrue(in.netCharge() == 0, "fixture must be neutral: " + in);
        return in;
    }

    private static int lcm(int a, int b) {
        return a / gcd(a, b) * b;
    }

    private static int gcd(int a, int b) {
        while (b != 0) { int t = a % b; a = b; b = t; }
        return a;
    }
}
