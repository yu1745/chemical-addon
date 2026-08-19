package com.yu1745.chemengine;

import static com.yu1745.chemengine.State.mb;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * Track B2/B3: closed-system two-couple redox.
 *
 * Fe3+/Fe2+ and Cu2+/Cu+ share the e- component. Electrons are conserved because
 * the initial Cu+ carries an electron balance; no fixed external pe is required.
 */
class RedoxSolverTest {

    private static Engine redoxEngine() {
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

    @Test void electronTransferBetweenIronAndCopper() {
        Engine e = redoxEngine();
        State in = new State(25)
            .ions("Fe+3", mb(100))
            .ions("Cu+1", mb(100))
            .ions("Cl-1", mb(400))
            .water(mb(1000));
        State s = e.solveClosed(in).state;

        long fe2 = s.ions().getOrDefault("Fe+2", 0L);
        long fe3 = s.ions().getOrDefault("Fe+3", 0L);
        long cu1 = s.ions().getOrDefault("Cu+1", 0L);
        long cu2 = s.ions().getOrDefault("Cu+2", 0L);

        assertTrue(fe2 > mb(1), "Fe3+ should accept electrons from Cu+: " + s);
        assertTrue(cu2 > mb(1), "Cu+ should be oxidised to Cu2+: " + s);
        assertTrue(fe3 > 0, "not all Fe3+ is reduced: " + s);
        assertTrue(cu1 > 0, "not all Cu+ is oxidised: " + s);
        // conservation: electron total (Fe2+ and Cu+ each carry one electron), metals, chloride
        assertEquals(mb(100), fe2 + cu1 + s.ions().getOrDefault("e-", 0L), "electrons conserved: " + s);
        assertEquals(mb(100), fe2 + fe3, "iron conserved: " + s);
        assertEquals(mb(100), cu2 + cu1, "copper conserved: " + s);
        assertEquals(mb(400), s.ions().getOrDefault("Cl-1", 0L), "chloride conserved: " + s);
        assertTrue(Math.abs(s.netCharge()) < mb(1), "charge neutral: " + s);
    }

    /** A Fe2+/Fe3+ couple paired with a Cl2/Cl- couple: 2FeCl2 + Cl2 -> 2FeCl3. */
    private static Engine chlorineOxidationEngine() {
        JsonObject fe = JsonParser.parseString(
            "{\"formula\":\"FeCl2\",\"phase\":\"LIQUID\",\"ions\":[{\"ion\":\"Fe\",\"charge\":2,\"count\":1},{\"ion\":\"Cl\",\"charge\":-1,\"count\":2}],\"equilibria\":[{\"reaction\":\"Fe+3 + e- = Fe+2\",\"log_k\":13.0}]}"
        ).getAsJsonObject();
        JsonObject cl2 = JsonParser.parseString(
            "{\"formula\":\"Cl2\",\"phase\":\"GAS\",\"gasSolubility\":0.001,\"equilibria\":[{\"reaction\":\"chemicaladdon:chlorine + 2 e- = 2 Cl-1\",\"log_k\":46.0}]}"
        ).getAsJsonObject();
        SpeciesDatabase db = new SpeciesDatabase()
            .register(Species.parse("ferrous_chloride", fe))
            .register(Species.parse("chlorine", cl2));
        return Engine.from(db);
    }

    @Test void ferrousChlorideOxidisedByChlorine() {
        // 2FeCl2 + Cl2 -> 2FeCl3, logK = 46 - 2*13 = 20 (strongly product-favoured).
        // Historically this threw "negative component remainder" in projectExact: the e-
        // mass balance left a one-quantum shortfall that the generic feasibility repair
        // cannot fix (the e--coupled secondaries carry NEGATIVE coeff for e-). The
        // projection must tolerate the e- pseudo-species' sub-quantum remainder.
        Engine e = chlorineOxidationEngine();
        State in = new State(25)
            .ions("Fe+2", mb(100))
            .ions("Cl-1", mb(200))
            .molecule("chemicaladdon:chlorine", mb(50))
            .water(mb(1000));
        State s = e.solveClosed(in).state;

        long fe3 = s.ions().getOrDefault("Fe+3", 0L);
        long fe2 = s.ions().getOrDefault("Fe+2", 0L);
        long cl = s.ions().getOrDefault("Cl-1", 0L);
        long cl2 = s.molecules().getOrDefault("chemicaladdon:chlorine", 0L);

        assertTrue(fe3 >= mb(99), "Fe2+ should be fully oxidised to Fe3+ by Cl2: " + s);
        assertTrue(fe2 <= mb(1), "only a trace of Fe2+ may remain: " + s);
        assertTrue(cl2 <= mb(1), "Cl2 should be fully consumed: " + s);
        assertTrue(Math.abs((fe3 + fe2) - mb(100)) <= 1, "iron conserved: " + s);
        assertTrue(Math.abs((cl + 2 * cl2) - mb(300)) <= 1, "chloride conserved: " + s);
        assertTrue(Math.abs(s.netCharge()) < mb(1), "charge neutral: " + s);
    }

    /** FeCl2 alone has no electron acceptor and must NOT spontaneously oxidise to Fe3+.
     *  Behavioral lock for the always-active e- handling: with only a ferrous input the
     *  electron mass balance has no acceptor to dump into, so the ferrous salt must stay
     *  reduced rather than leaking electrons into a free e- pool. */
    @Test void ferrousChlorideAlone_isNotSpontaneouslyOxidised() {
        Engine e = chlorineOxidationEngine();
        State in = new State(25)
            .ions("Fe+2", mb(100))
            .ions("Cl-1", mb(200))
            .water(mb(1000));
        State s = e.solveClosed(in).state;

        long fe3 = s.ions().getOrDefault("Fe+3", 0L);
        long fe2 = s.ions().getOrDefault("Fe+2", 0L);
        assertTrue(fe2 >= mb(99), "Fe2+ with no oxidant must remain ferrous: " + s);
        assertTrue(fe3 <= mb(1), "Fe3+ must not form without an electron acceptor: " + s);
        assertTrue(Math.abs(s.netCharge()) < mb(1), "charge neutral: " + s);
    }

    @Test void reverseElectronTransferFromIronToCopper() {
        Engine e = redoxEngine();
        State in = new State(25)
            .ions("Fe+2", mb(100))
            .ions("Cu+2", mb(100))
            .ions("Cl-1", mb(400))
            .water(mb(1000));
        State s = e.solveClosed(in).state;

        long fe3 = s.ions().getOrDefault("Fe+3", 0L);
        long cu1 = s.ions().getOrDefault("Cu+1", 0L);
        long fe2 = s.ions().getOrDefault("Fe+2", 0L);
        long cu2 = s.ions().getOrDefault("Cu+2", 0L);

        assertTrue(fe3 > 0, "Fe2+ should donate electrons: " + s);
        assertTrue(cu1 > 0, "Cu2+ should accept electrons: " + s);
        assertTrue(fe2 > 0, "not all Fe2+ is oxidised: " + s);
        assertTrue(cu2 > 0, "not all Cu2+ is reduced: " + s);
        // conservation: electron total (initial Fe2+ carries 100 electrons), metals, chloride
        assertEquals(mb(100), fe2 + cu1 + s.ions().getOrDefault("e-", 0L), "electrons conserved: " + s);
        assertEquals(mb(100), fe3 + fe2, "iron conserved: " + s);
        assertEquals(mb(100), cu2 + cu1, "copper conserved: " + s);
        assertEquals(mb(400), s.ions().getOrDefault("Cl-1", 0L), "chloride conserved: " + s);
        assertTrue(Math.abs(s.netCharge()) < mb(1), "charge neutral: " + s);
    }

    private static Engine threeCoupleEngine() {
        JsonObject fe = JsonParser.parseString(
            "{\"formula\":\"FeCl3\",\"phase\":\"LIQUID\",\"ions\":[{\"ion\":\"Fe\",\"charge\":3,\"count\":1},{\"ion\":\"Cl\",\"charge\":-1,\"count\":3}],\"equilibria\":[{\"reaction\":\"Fe+3 + e- = Fe+2\",\"log_k\":13.0}]}"
        ).getAsJsonObject();
        JsonObject cu = JsonParser.parseString(
            "{\"formula\":\"CuCl2\",\"phase\":\"LIQUID\",\"ions\":[{\"ion\":\"Cu\",\"charge\":2,\"count\":1},{\"ion\":\"Cl\",\"charge\":-1,\"count\":2}],\"equilibria\":[{\"reaction\":\"Cu+2 + e- = Cu+1\",\"log_k\":6.0}]}"
        ).getAsJsonObject();
        JsonObject ce = JsonParser.parseString(
            "{\"formula\":\"CeCl4\",\"phase\":\"LIQUID\",\"ions\":[{\"ion\":\"Ce\",\"charge\":4,\"count\":1},{\"ion\":\"Cl\",\"charge\":-1,\"count\":4}],\"equilibria\":[{\"reaction\":\"Ce+4 + e- = Ce+3\",\"log_k\":15.0}]}"
        ).getAsJsonObject();
        SpeciesDatabase db = new SpeciesDatabase()
            .register(Species.parse("ferric_chloride", fe))
            .register(Species.parse("cupric_chloride", cu))
            .register(Species.parse("ceric_chloride", ce));
        return Engine.from(db);
    }

    @Test void threeCoupleElectronTransfer() {
        Engine e = threeCoupleEngine();
        State in = new State(25)
            .ions("Fe+2", mb(100))
            .ions("Cu+2", mb(100))
            .ions("Ce+4", mb(100))
            .ions("Cl-1", mb(800))
            .water(mb(1000));
        State s = e.solveClosed(in).state;

        long fe3 = s.ions().getOrDefault("Fe+3", 0L);
        long ce3 = s.ions().getOrDefault("Ce+3", 0L);
        long ce4 = s.ions().getOrDefault("Ce+4", 0L);
        long fe2 = s.ions().getOrDefault("Fe+2", 0L);
        long cu1 = s.ions().getOrDefault("Cu+1", 0L);
        long cu2 = s.ions().getOrDefault("Cu+2", 0L);

        assertTrue(fe3 > 0, "Fe2+ should be oxidised by Ce4+: " + s);
        assertTrue(ce3 > 0, "Ce4+ should be reduced to Ce3+: " + s);
        assertTrue(ce4 > 0, "not all Ce4+ is consumed: " + s);
        assertTrue(fe2 > 0, "not all Fe2+ is consumed: " + s);
        // conservation: electron total (initial Fe2+ carries 100 electrons; Fe2+/Cu+/Ce3+
        // each carry one), metals, chloride
        assertEquals(mb(100), fe2 + cu1 + ce3 + s.ions().getOrDefault("e-", 0L), "electrons conserved: " + s);
        assertEquals(mb(100), fe3 + fe2, "iron conserved: " + s);
        assertEquals(mb(100), cu2 + cu1, "copper conserved: " + s);
        assertEquals(mb(100), ce4 + ce3, "cerium conserved: " + s);
        assertEquals(mb(800), s.ions().getOrDefault("Cl-1", 0L), "chloride conserved: " + s);
        assertTrue(Math.abs(s.netCharge()) < mb(1), "charge neutral: " + s);
    }
}
