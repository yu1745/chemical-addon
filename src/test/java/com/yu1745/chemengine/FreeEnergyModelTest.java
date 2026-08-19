package com.yu1745.chemengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yu1745.chemengine.solver.FreeEnergyDatabase;
import com.yu1745.chemengine.solver.Solver;
import com.yu1745.chemengine.solver.SystemModel;
import org.junit.jupiter.api.Test;

/**
 * Track B: the model is built purely from formation free energies ΔG_f° (no balanced reaction
 * strings) and precipitation EMERGES from the ΔG_f°-derived solubility products. Verifies that
 * the derivation reproduces literature Ksp, that the least-soluble product forms, and that
 * charge neutrality + component conservation hold exactly.
 */
class FreeEnergyModelTest {

    private static FreeEnergyDatabase alkalineEarthDb() {
        return new FreeEnergyDatabase()
            .basis("Ca+2", +2, -553.58, "Ca", 1)
            .basis("Ba+2", +2, -560.77, "Ba", 1)
            .basis("Sr+2", +2, -559.48, "Sr", 1)
            .basis("CO3-2", -2, -527.81, "C", 1, "O", 3)
            .basis("SO4-2", -2, -744.53, "S", 1, "O", 4)
            .basis("Cl-1", -1, -131.23, "Cl", 1)
            .species("HCO3-1", -1, -586.77, "H", 1, "C", 1, "O", 3)
            .solid("barite",       -1362.2, "Ba", 1, "S", 1, "O", 4)
            .solid("strontianite", -1140.1, "Sr", 1, "C", 1, "O", 3)
            .solid("witherite",    -1137.5, "Ba", 1, "C", 1, "O", 3)
            .solid("calcite",      -1129.0, "Ca", 1, "C", 1, "O", 3)
            .solid("celestite",    -1341.0, "Sr", 1, "S", 1, "O", 4)
            .solid("anhydrite",    -1321.8, "Ca", 1, "S", 1, "O", 4);
    }

    private static SystemModel.Mineral mineral(SystemModel model, String key) {
        for (SystemModel.Mineral m : model.minerals()) if (m.solidKey.equals(key)) return m;
        return null;
    }

    @Test void derivedKspMatchesLiteratureAndPrecipitationEmerges() {
        SystemModel model = SystemModel.fromFreeEnergy(alkalineEarthDb());
        assertTrue(model.componentCount() >= 5, "master-ion basis not built");
        assertTrue(model.droppedEquilibria().isEmpty(),
            "carbonate/sulfate system must be expressible over the master-ion basis: " + model.droppedEquilibria());

        // Ksp is DERIVED from ΔG_f°, not authored — and should land on literature values.
        assertEquals(-9.97, mineral(model, "barite").authoredLogK, 0.15);
        assertEquals(-8.34, mineral(model, "calcite").authoredLogK, 0.2);
        assertEquals(-9.25, mineral(model, "strontianite").authoredLogK, 0.15);
        assertEquals(-4.15, mineral(model, "anhydrite").authoredLogK, 0.25);
        assertTrue(model.secondaries().size() >= 2, "OH-1 Kw + derived HCO3-1 should be secondaries");

        // Input: Ba2+ 50, Sr2+ 50, Ca2+ 100, SO4-2 100, CO3-2 100 mB, water. Charge-balanced.
        State in = new State(25).water(State.mb(1000))
            .ions("Ba+2", State.mb(50))
            .ions("Sr+2", State.mb(50))
            .ions("Ca+2", State.mb(100))
            .ions("SO4-2", State.mb(100))
            .ions("CO3-2", State.mb(100));
        Solver.Result r = Solver.solve(model, null, in, Solver.Vessel.CLOSED);
        State o = r.state;

        // The most-insoluble product (barite, BaSO4) must emerge.
        assertTrue(o.suspended().getOrDefault("barite", 0L) > State.mb(1),
            "barite should precipitate: " + o.suspended());

        // Charge neutrality.
        assertTrue(Math.abs(o.netCharge()) < State.QUANTA_PER_MB, "net charge: " + o.netCharge());

        // Exact component conservation (aqueous + suspended + secondary).
        long barite = o.suspended().getOrDefault("barite", 0L);
        long baOut = o.ions().getOrDefault("Ba+2", 0L) + barite;
        assertEquals(State.mb(50), baOut, "barium conserved");
        long caOut = o.ions().getOrDefault("Ca+2", 0L) + o.suspended().getOrDefault("calcite", 0L);
        assertEquals(State.mb(100), caOut, "calcium conserved");
        long srOut = o.ions().getOrDefault("Sr+2", 0L)
            + o.suspended().getOrDefault("strontianite", 0L)
            + o.suspended().getOrDefault("celestite", 0L);
        assertEquals(State.mb(50), srOut, "strontium conserved");
        long so4Out = o.ions().getOrDefault("SO4-2", 0L) + barite
            + o.suspended().getOrDefault("celestite", 0L);
        assertEquals(State.mb(100), so4Out, "sulfate conserved");
        long co3Out = o.ions().getOrDefault("CO3-2", 0L)
            + o.ions().getOrDefault("HCO3-1", 0L)
            + o.suspended().getOrDefault("calcite", 0L)
            + o.suspended().getOrDefault("strontianite", 0L)
            + o.suspended().getOrDefault("witherite", 0L);
        assertEquals(State.mb(100), co3Out, "carbonate conserved");

        // GLOBAL equilibrium: the phase assemblage must be KKT-consistent — every present
        // solid at saturation (SI~0) and every absent solid undersaturated (SI<0). The
        // global Gibbs search guarantees this by construction; this asserts it end-to-end.
        assertKktConsistent(model, o);

        // The global search ranks phase sets by total Gibbs free energy; validate that
        // the equilibrium (with precipitation) has LOWER G than the same composition with
        // no precipitate — i.e. the ranking formula captures "precipitation is favoured".
        State allDissolved = new State(25).water(State.mb(1000))
            .ions("Ba+2", State.mb(50)).ions("Sr+2", State.mb(50))
            .ions("Ca+2", State.mb(100)).ions("SO4-2", State.mb(100)).ions("CO3-2", State.mb(100));
        assertTrue(gibbsFromState(model, o) < gibbsFromState(model, allDissolved),
            "precipitation must lower total Gibbs energy");
    }

    /** Re-implementation of the solver's Gibbs measure (relative to the pure-component
     *  reference, in RT units) for a projected state — used to validate the global ranking. */
    private static double gibbsFromState(SystemModel model, State o) {
        double water = o.waterAmount();
        double G = 0;
        for (int i = 0; i < model.speciesCount(); i++) {
            String key = model.speciesKey(i);
            long amt = o.ions().getOrDefault(key, 0L) + o.molecules().getOrDefault(key, 0L);
            if (amt <= 0) continue;
            G += amt * (Math.log(amt) - Math.log(water));
            if (i >= model.componentCount())
                G -= amt * model.speciesLogKEffAt(i, o.temperatureC()) * Math.log(10.0);
        }
        for (SystemModel.Mineral m : model.minerals()) {
            long amt = o.suspended().getOrDefault(m.solidKey, 0L);
            if (amt <= 0) continue;
            G += amt * m.logKEffAt(o.temperatureC()) * Math.log(10.0);
        }
        return G;
    }

    /** Present => |SI| small; absent => SI < small positive tolerance (KKT of the equilibrium). */
    private static void assertKktConsistent(SystemModel model, State o) {
        double water = o.waterAmount();
        for (SystemModel.Mineral m : model.minerals()) {
            double si = 0;
            boolean allIonsPresent = true;
            for (int c = 0; c < model.componentCount(); c++) {
                if (m.coeff[c] == 0) continue;
                long amt = o.ions().getOrDefault(model.components().get(c), 0L)
                    + o.molecules().getOrDefault(model.components().get(c), 0L);
                if (amt <= 0) { allIonsPresent = false; break; }
                si += m.coeff[c] * (Math.log(amt) - Math.log(water));
            }
            si -= m.logKEffAt(o.temperatureC()) * Math.log(10.0);
            boolean present = o.suspended().containsKey(m.solidKey);
            if (allIonsPresent) {
                assertTrue(!present || Math.abs(si) < 0.1,
                    "present " + m.solidKey + " must be saturated, SI=" + si);
                assertTrue(present || si < 0.1,
                    "absent " + m.solidKey + " must be undersaturated, SI=" + si);
            }
        }
    }
}
