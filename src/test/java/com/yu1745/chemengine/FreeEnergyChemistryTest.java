package com.yu1745.chemengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yu1745.chemengine.solver.FreeEnergyDatabase;
import com.yu1745.chemengine.solver.InorganicIonCatalog;
import com.yu1745.chemengine.solver.Solver;
import com.yu1745.chemengine.solver.SystemModel;
import org.junit.jupiter.api.Test;

/**
 * Track E acceptance: with the SMALL master-ion basis (one dominant ion per element + H+ +
 * the electron pool), every other catalog ion is a DERIVED secondary species, so acid-base,
 * redox and complexation equilibria ACTUALLY happen in the solve — the "conserved but no
 * chemistry" failure of the full-96-basis model is gone. All constants below come out of
 * ΔG_f° via the free-energy machinery; the assertions check the behaviour they predict.
 */
class FreeEnergyChemistryTest {

    private static SystemModel fullModel() {
        SystemModel m = SystemModel.fromFreeEnergy(InorganicIonCatalog.database());
        assertTrue(m.droppedEquilibria().isEmpty(),
            "all 49 catalog secondaries must be expressible over the small basis: " + m.droppedEquilibria());
        return m;
    }

    @Test void bisulfatePartiallyDissociates() {
        // HSO4- is a secondary (SO4-2 + H+), so it must partially dissociate.
        SystemModel model = fullModel();
        State s = Solver.solve(model, null, new State(25).water(State.mb(1000))
            .ions("Na+1", State.mb(100)).ions("HSO4-1", State.mb(100)), Solver.Vessel.CLOSED).state;
        long hso4 = s.ions().getOrDefault("HSO4-1", 0L);
        long so4 = s.ions().getOrDefault("SO4-2", 0L);
        long h = s.ions().getOrDefault("H+1", 0L);
        // Ka2 = [SO4][H]/[HSO4] = 10^-1.997; input 100 mB: ~27 mB dissociated.
        assertTrue(so4 > State.mb(20) && so4 < State.mb(35), "bisulfate partially dissociates: " + so4);
        assertEquals(hso4 + so4, State.mb(100), "sulfur conserved (SO4 + HSO4)");
        assertEquals(so4, h, "each dissociation produces one H+");
        assertEquals(0, s.netCharge(), "charge neutral");
        // and the derived Ka2 is reproduced exactly in the solve:
        // n(H+)*n(SO4)/[n(HSO4)*Vwater] = 10^-1.997  (Vwater = 1000 mB = 1e10 quanta)
        double ka2 = (double) h * so4 / (hso4 * (double) s.waterAmount());
        assertEquals(-1.997, Math.log10(ka2), 0.05, "derived Ka2 reproduced in the solve");
    }

    @Test void carbonateHydrolysesAndSpeciates() {
        SystemModel model = fullModel();
        State s = Solver.solve(model, null, new State(25).water(State.mb(1000))
            .ions("Na+1", State.mb(200)).ions("CO3-2", State.mb(100)), Solver.Vessel.CLOSED).state;
        long co3 = s.ions().getOrDefault("CO3-2", 0L);
        long hco3 = s.ions().getOrDefault("HCO3-1", 0L);
        long oh = s.ions().getOrDefault("OH-1", 0L);
        assertTrue(hco3 > State.mb(1), "carbonate hydrolyses to bicarbonate: " + hco3);
        assertEquals(hco3, oh, "stoichiometric hydrolysis (1 OH- per HCO3-)");
        assertEquals(co3 + hco3, State.mb(100), "carbon conserved");
        assertEquals(0, s.netCharge());
    }

    @Test void netRedoxFe3Cu1RunsToCompletion() {
        // Fe3+ + Cu+ = Fe2+ + Cu2+: electrons transfer between the couples (shared e- pool),
        // K = 10^(13.018 - 2.593) = 10^10.4 — essentially complete.
        SystemModel model = fullModel();
        State s = Solver.solve(model, null, new State(25).water(State.mb(1000))
            .ions("Fe+3", State.mb(50)).ions("Cl-1", State.mb(150))
            .ions("Cu+1", State.mb(50)).ions("NO3-1", State.mb(50)), Solver.Vessel.CLOSED).state;
        long fe3 = s.ions().getOrDefault("Fe+3", 0L);
        long fe2 = s.ions().getOrDefault("Fe+2", 0L);
        long cu1 = s.ions().getOrDefault("Cu+1", 0L);
        long cu2 = s.ions().getOrDefault("Cu+2", 0L);
        assertTrue(fe2 > State.mb(49), "Fe3+ reduced to Fe2+: " + fe2);
        assertTrue(cu2 > State.mb(49), "Cu+ oxidised to Cu2+: " + cu2);
        assertTrue(fe3 < State.mb(1), "Fe3+ nearly exhausted: " + fe3);
        assertTrue(cu1 < State.mb(1), "Cu+ nearly exhausted: " + cu1);
        assertEquals(fe3 + fe2, State.mb(50), "iron conserved");
        assertEquals(cu1 + cu2, State.mb(50), "copper conserved");
        assertEquals(0, s.netCharge());
    }

    @Test void ferricHydroxidePrecipitates() {
        FreeEnergyDatabase fdb = InorganicIonCatalog.database()
            .solid("Fe(OH)3", -705.0, "Fe", 1, "O", 3, "H", 3);
        SystemModel model = SystemModel.fromFreeEnergy(fdb);
        assertTrue(model.droppedEquilibria().isEmpty(), "hydroxide expressible: " + model.droppedEquilibria());
        State s = Solver.solve(model, null, new State(25).water(State.mb(1000))
            .ions("Fe+3", State.mb(30)).ions("OH-1", State.mb(90)), Solver.Vessel.CLOSED).state;
        assertEquals(State.mb(30), s.suspended().getOrDefault("Fe(OH)3", 0L),
            "Fe(OH)3 precipitates stoichiometrically: " + s.suspended());
        assertEquals(0, s.netCharge(), "charge neutral");
    }

    @Test void amphotericAluminiumHydroxideDissolvesInExcessBase() {
        // Real amphoteric complexation without redox: Al3+ + 3OH- -> Al(OH)3(s) at
        // stoichiometric base, but [Al(OH)4]- wins once the base is in excess — the
        // sharp switch is real aluminium chemistry (hydroxide dissolves above pH ~10.5).
        FreeEnergyDatabase fdb = InorganicIonCatalog.database()
            .solid("Al(OH)3", -1120.0, "Al", 1, "O", 3, "H", 3);
        SystemModel model = SystemModel.fromFreeEnergy(fdb);

        State s = Solver.solve(model, null, new State(25).water(State.mb(1000))
            .ions("Al+3", State.mb(100)).ions("Cl-1", State.mb(300))
            .ions("Na+1", State.mb(300)).ions("OH-1", State.mb(300)), Solver.Vessel.CLOSED).state;
        long alOh3 = s.suspended().getOrDefault("Al(OH)3", 0L);
        long alum = s.ions().getOrDefault("Al(OH)4-1", 0L);
        assertTrue(alOh3 >= State.mb(95), "stoichiometric base precipitates Al(OH)3: " + alOh3);
        assertTrue(alum < State.mb(5), "little aluminate at stoichiometric base: " + alum);
        assertEquals(0, s.netCharge());

        State s2 = Solver.solve(model, null, new State(25).water(State.mb(1000))
            .ions("Al+3", State.mb(100)).ions("Cl-1", State.mb(300))
            .ions("Na+1", State.mb(400)).ions("OH-1", State.mb(400)), Solver.Vessel.CLOSED).state;
        long alum2 = s2.ions().getOrDefault("Al(OH)4-1", 0L);
        long alOh32 = s2.suspended().getOrDefault("Al(OH)3", 0L);
        assertTrue(alum2 >= State.mb(95), "excess base dissolves the hydroxide to aluminate: " + alum2);
        assertTrue(alOh32 == 0, "no hydroxide left in excess base: " + alOh32);
        assertEquals(0, s2.netCharge());
    }
}