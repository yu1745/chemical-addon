package com.yu1745.chemengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yu1745.chemengine.solver.FreeEnergyDatabase;
import com.yu1745.chemengine.solver.InorganicIonCatalog;
import com.yu1745.chemengine.solver.Solver;
import com.yu1745.chemengine.solver.SystemModel;
import org.junit.jupiter.api.Test;

/**
 * Regression: hydroxide/hydrate solids must be expressible over the master-ion basis even
 * though OH-1 is NOT a component (it is the Kw autoionisation secondary). They are expressed
 * in acid-dissolution form (H2O + H+ instead of OH-), and the water ΔG° term enters the Ksp.
 * The old balance() dropped them ("not expressible over master-ion basis"); the combination
 * search (with candidate ions restricted to a subset of the target's elements + H+ charge
 * balancer) resolves the Fe2+/Fe3+, Al3+/Al(OH)4- ambiguity.
 */
class HydroxideExpressibilityTest {

    @Test void hydroxideSolidsAreExpressible() {
        FreeEnergyDatabase fdb = InorganicIonCatalog.database()
            .solid("Al(OH)3", -1120.0, "Al", 1, "O", 3, "H", 3)
            .solid("Mg(OH)2", -834.0, "Mg", 1, "O", 2, "H", 2)
            .solid("Zn(OH)2", -849.0, "Zn", 1, "O", 2, "H", 2)
            .solid("Fe(OH)3", -705.0, "Fe", 1, "O", 3, "H", 3);
        SystemModel model = SystemModel.fromFreeEnergy(fdb);
        assertTrue(model.droppedEquilibria().isEmpty(),
            "hydroxides must be expressible: " + model.droppedEquilibria());

        SystemModel.Mineral al = mineral(model, "Al(OH)3");
        assertEquals(1.0, al.coeff[model.indexOf("Al+3")], 1e-9, "Al(OH)3 dissociates to Al3+");
        assertEquals(-3.0, al.coeff[model.indexOf("H+1")], 1e-9, "Al(OH)3 acid-dissolution consumes 3 H+");
        // Fe(OH)3 over the small basis also needs the electron pool (Fe3+ = Fe2+ - e-).
        SystemModel.Mineral fe = mineral(model, "Fe(OH)3");
        assertEquals(1.0, fe.coeff[model.indexOf("Fe+2")], 1e-9, "Fe(OH)3 dissociates to Fe2+ - e- (Fe3+ form)");
        assertEquals(-1.0, fe.coeff[model.indexOf("e-")], 1e-9, "Fe(OH)3 carries one electron from the pool");
    }

    @Test void ferricHydroxidePrecipitatesFromFeAndHydroxide() {
        FreeEnergyDatabase fdb = InorganicIonCatalog.database()
            .solid("Fe(OH)3", -705.0, "Fe", 1, "O", 3, "H", 3);
        SystemModel model = SystemModel.fromFreeEnergy(fdb);
        // Fe3+ 30 mB + OH- 90 mB (charge-balanced) -> Fe(OH)3 should precipitate.
        State in = new State(25).water(State.mb(1000))
            .ions("Fe+3", State.mb(30))
            .ions("OH-1", State.mb(90));
        Solver.Result r = Solver.solve(model, null, in, Solver.Vessel.CLOSED);
        State o = r.state;
        assertTrue(o.suspended().getOrDefault("Fe(OH)3", 0L) > State.mb(1),
            "Fe(OH)3 should precipitate: " + o.suspended());
        assertTrue(Math.abs(o.netCharge()) < State.QUANTA_PER_MB, "net charge: " + o.netCharge());
    }

    private static SystemModel.Mineral mineral(SystemModel model, String key) {
        for (SystemModel.Mineral m : model.minerals()) if (m.solidKey.equals(key)) return m;
        return null;
    }
}
