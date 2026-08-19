package com.yu1745.chemengine;

import static com.yu1745.chemengine.State.mb;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * Track B5: industrial hydrometallurgy-style redox.
 *
 * <p>FeCl3 leaches CuCl: Fe3+ accepts an electron from Cu+ and both change oxidation
 * state, simulating a simple ferric-chloride leaching step.
 *
 * <p>Electrode conventions used here (logK = n*E0/0.05916 at 25 C):
 * <pre>
 *   Fe3+ + e- = Fe2+                 E0 = +0.771 V  -> logK 13.0
 *   NO3- + 2H+ + 2e- = NO2- + H2O    E0 = +0.83 V   -> logK 28.0
 *   O2 + 4H+ + 4e- = 2H2O            E0 = +1.229 V  -> logK 83.1
 * </pre>
 * Note the acid O2 couple: the ALKALINE couple (O2 + 4e- = 4OH-, E0 = +0.40 V) cannot
 * oxidise Fe2+ (E0 = +0.77 V) without hydroxide precipitation coupling; a fixture using
 * it made the electron mass balance infeasible (the old test asserted a ~0.03 mB trace).
 */
class IndustrialRedoxTest {

    private static Engine leachingEngine() {
        JsonObject fe = JsonParser.parseString(
            "{\"formula\":\"FeCl3\",\"phase\":\"LIQUID\",\"ions\":[{\"ion\":\"Fe\",\"charge\":3,\"count\":1},{\"ion\":\"Cl\",\"charge\":-1,\"count\":3}],\"equilibria\":[{\"reaction\":\"Fe+3 + e- = Fe+2\",\"log_k\":13.0}]}"
        ).getAsJsonObject();
        JsonObject cu = JsonParser.parseString(
            "{\"formula\":\"CuCl\",\"phase\":\"LIQUID\",\"ions\":[{\"ion\":\"Cu\",\"charge\":2,\"count\":1},{\"ion\":\"Cl\",\"charge\":-1,\"count\":2}],\"equilibria\":[{\"reaction\":\"Cu+2 + e- = Cu+1\",\"log_k\":6.0}]}"
        ).getAsJsonObject();
        SpeciesDatabase db = new SpeciesDatabase()
            .register(Species.parse("ferric_chloride", fe))
            .register(Species.parse("cupric_chloride", cu));
        return Engine.from(db);
    }

    @Test void ferricChlorideLeachesCuprousChloride() {
        Engine e = leachingEngine();
        State in = new State(25)
            .ions("Fe+3", mb(100))
            .ions("Cu+1", mb(100))
            .ions("Cl-1", mb(400))
            .water(mb(1000));
        State s = e.solveClosed(in).state;

        long fe2 = s.ions().getOrDefault("Fe+2", 0L);
        long cu2 = s.ions().getOrDefault("Cu+2", 0L);
        long fe3 = s.ions().getOrDefault("Fe+3", 0L);
        long cu1 = s.ions().getOrDefault("Cu+1", 0L);

        // K = 1e13/1e6 = 1e7 for Fe3+ + Cu+ -> Fe2+ + Cu2+: essentially complete
        assertTrue(fe2 > mb(90), "Fe3+ should be reduced to Fe2+: " + s);
        assertTrue(cu2 > mb(90), "Cu+ should be oxidised to Cu2+: " + s);
        // conservation: per-metal and electron totals (electron content: Fe2+, Cu+)
        assertEquals(mb(100), fe2 + fe3, "iron conserved: " + s);
        assertEquals(mb(100), cu2 + cu1, "copper conserved: " + s);
        assertEquals(mb(100), fe2 + cu1 + s.ions().getOrDefault("e-", 0L), "electrons conserved (incl. e- remainder): " + s);
        assertEquals(mb(400), s.ions().getOrDefault("Cl-1", 0L), "chloride conserved: " + s);
        assertTrue(Math.abs(s.netCharge()) < mb(1), "charge neutral: " + s);
    }

    private static Engine nitrateReductionEngine() {
        JsonObject fe = JsonParser.parseString(
            "{\"formula\":\"FeCl3\",\"phase\":\"LIQUID\",\"ions\":[{\"ion\":\"Fe\",\"charge\":3,\"count\":1},{\"ion\":\"Cl\",\"charge\":-1,\"count\":3}],\"equilibria\":[{\"reaction\":\"Fe+3 + e- = Fe+2\",\"log_k\":13.0}]}"
        ).getAsJsonObject();
        JsonObject na = JsonParser.parseString(
            "{\"formula\":\"NaNO3\",\"phase\":\"LIQUID\",\"ions\":[{\"ion\":\"Na\",\"charge\":1,\"count\":1},{\"ion\":\"NO3\",\"charge\":-1,\"count\":1}],\"equilibria\":[{\"reaction\":\"NO3-1 + 2 H+1 + 2 e- = NO2-1\",\"log_k\":28.0}]}"
        ).getAsJsonObject();
        SpeciesDatabase db = new SpeciesDatabase()
            .register(Species.parse("ferric_chloride", fe))
            .register(Species.parse("sodium_nitrate", na));
        return Engine.from(db);
    }

    @Test void ferrousIronReducesNitrateToNitrite() {
        // logK 28 (real E0 = +0.83 V for the NO2- product form): the combined reaction
        // NO3- + 2Fe2+ + 2H+ = NO2- + 2Fe3+ + H2O has K = 1e28/1e26 = 100, so a
        // meaningful fraction reacts. (The old logK 15 made it K = 1e-11 — the test then
        // asserted "fe3 > 0" on a 0.009 mB trace residual, i.e. nothing happened.)
        Engine e = nitrateReductionEngine();
        State in = new State(25)
            .ions("Fe+2", mb(100))
            .ions("Na+1", mb(100))
            .ions("NO3-1", mb(100))
            .ions("H+1", mb(200))
            .ions("Cl-1", mb(400))
            .water(mb(1000));
        State s = e.solveClosed(in).state;

        long fe3 = s.ions().getOrDefault("Fe+3", 0L);
        long no2 = s.ions().getOrDefault("NO2-1", 0L);
        long fe2 = s.ions().getOrDefault("Fe+2", 0L);
        long no3 = s.ions().getOrDefault("NO3-1", 0L);

        assertTrue(fe3 > mb(50), "Fe2+ should reduce nitrate on a meaningful scale: " + s);
        assertTrue(no2 > mb(25), "nitrate should be reduced to nitrite: " + s);
        assertEquals(mb(100), fe2 + fe3, "iron conserved: " + s);
        assertEquals(mb(100), no2 + no3, "nitrogen conserved: " + s);
        // electron conservation: initial total = Fe2+ (100) - 2*NO3- (200) = -100 mB;
        // output total = e- + Fe2+ - 2*NO3 (the NO3- secondary carries -2 electrons)
        assertEquals(mb(-100),
            s.ions().getOrDefault("e-", 0L) + fe2 - 2 * no3,
            "electrons conserved: " + s);
        assertTrue(Math.abs(s.netCharge()) < mb(1), "charge neutral: " + s);
    }

    private static Engine oxygenOxidationEngine() {
        JsonObject fe = JsonParser.parseString(
            "{\"formula\":\"FeCl3\",\"phase\":\"LIQUID\",\"ions\":[{\"ion\":\"Fe\",\"charge\":3,\"count\":1},{\"ion\":\"Cl\",\"charge\":-1,\"count\":3}],\"equilibria\":[{\"reaction\":\"Fe+3 + e- = Fe+2\",\"log_k\":13.0}]}"
        ).getAsJsonObject();
        JsonObject o2 = JsonParser.parseString(
            "{\"formula\":\"O2\",\"phase\":\"GAS\",\"gasSolubility\":0.001,\"equilibria\":[{\"reaction\":\"chemicaladdon:oxygen + 4 H+1 + 4 e- = 2 water\",\"log_k\":83.1}]}"
        ).getAsJsonObject();
        SpeciesDatabase db = new SpeciesDatabase()
            .register(Species.parse("ferric_chloride", fe))
            .register(Species.parse("oxygen", o2));
        return Engine.from(db);
    }

    @Test void ferrousIronIsOxidisedByOxygen() {
        // 4Fe2+ + O2 + 4H+ -> 4Fe3+ + 2H2O: 100 Fe2+ needs 25 O2 and 100 H+ (both
        // limiting), so ~all Fe2+ is oxidised and the acid is consumed — real chemistry
        // for acidic ferrous oxidation (rusting's acid couple). The alkaline couple
        // (O2 + 4e- = 4OH-, E0 +0.40 V) is thermodynamically unable to oxidise Fe2+.
        Engine e = oxygenOxidationEngine();
        State in = new State(25)
            .ions("Fe+2", mb(100))
            .ions("Na+1", mb(100))
            .ions("Cl-1", mb(400))
            .ions("H+1", mb(100))
            .molecule("chemicaladdon:oxygen", mb(100))
            .water(mb(1000));
        State s = e.solveClosed(in).state;

        long fe3 = s.ions().getOrDefault("Fe+3", 0L);
        long fe2 = s.ions().getOrDefault("Fe+2", 0L);
        long h = s.ions().getOrDefault("H+1", 0L);
        long o2 = s.molecules().getOrDefault("chemicaladdon:oxygen", 0L);

        assertTrue(fe3 > mb(90), "Fe2+ should be oxidised by O2: " + s);
        assertTrue(h < mb(50), "acid is consumed by the oxidation: " + s);
        assertTrue(o2 < mb(90), "O2 is consumed: " + s);
        // +-1 quantum: the integer projection's rint half-rounding can move one
        // quantum between the Fe+3 remainder and Fe+2 (measured: 999,999,999/1e9)
        assertTrue(Math.abs((fe3 + fe2) - mb(100)) <= 1, "iron conserved: " + s);
        // electron conservation: initial total = Fe2+ (100) - 4*O2 (400) = -300 mB;
        // output total = e- + Fe2+ - 4*O2 (the O2 secondary carries -4 electrons)
        assertEquals(mb(-300),
            s.ions().getOrDefault("e-", 0L) + fe2 - 4 * o2,
            "electrons conserved: " + s);
        assertTrue(Math.abs(s.netCharge()) < mb(1), "charge neutral: " + s);
    }
}
