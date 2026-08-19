package com.yu1745.chemengine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/**
 * Track B1: the data layer must at least parse PHREEQC-style redox half-reactions
 * containing the electron pseudo-species e-.
 */
class RedoxParsingTest {

    @Test void electronChargeParsesAsMinusOne() {
        assertEquals(-1, Ion.chargeOf("e-"));
    }

    @Test void redoxHalfReactionParses() {
        Equilibrium eq = Equilibrium.parse("Fe+3 + e- = Fe+2", 13.0);
        assertNotNull(eq);
        assertEquals(13.0, eq.logK(), 1e-12);
    }
}
