package com.yu1745.chemengine;

import static com.yu1745.chemengine.State.mb;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Additional coverage for chemistry that was defined in the species data but not
 * locked by the earlier core tests:
 * silver solids/ammine, zinc hydroxide, copper/iron complexes, aluminium amphoterism,
 * multi-cation carbonate/hydroxide competition, and pure-water autoionisation cleanup.
 */
class CoverageTest {

    private final Engine e = Harness.engine();

    @Test void silverChloridePrecipitates() {
        State s = e.solveClosed(new State(20)
            .ions("Ag+1", mb(100)).ions("NO3-1", mb(100))
            .ions("Na+1", mb(100)).ions("Cl-1", mb(100)).water(mb(1000))).state;
        assertTrue(s.suspended().getOrDefault("chemicaladdon:silver_chloride", 0L) >= mb(99),
            "AgCl precipitates: " + s);
        assertTrue(s.ions().getOrDefault("Ag+1", 0L) < mb(1), "Ag exhausted: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void silverCarbonatePrecipitates() {
        State s = e.solveClosed(new State(20)
            .ions("Ag+1", mb(200)).ions("NO3-1", mb(200))
            .ions("Na+1", mb(200)).ions("CO3-2", mb(100)).water(mb(1000))).state;
        assertTrue(s.suspended().getOrDefault("chemicaladdon:silver_carbonate", 0L) >= mb(99),
            "Ag2CO3 precipitates: " + s);
        assertTrue(s.ions().getOrDefault("Ag+1", 0L) < mb(1), "Ag exhausted: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void silverAmmineComplexForms() {
        State s = e.solveClosed(new State(20)
            .ions("Ag+1", mb(100)).ions("NO3-1", mb(100))
            .molecule("chemicaladdon:ammonia", mb(200)).water(mb(1000))).state;
        assertTrue(s.ions().getOrDefault("[Ag(NH3)2]+1", 0L) >= mb(90),
            "silver ammine complex forms: " + s);
        assertTrue(s.ions().getOrDefault("Ag+1", 0L) < mb(2), "free Ag largely consumed: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void zincHydroxidePrecipitates() {
        State s = e.solveClosed(new State(20)
            .ions("Zn+2", mb(100)).ions("SO4-2", mb(100))
            .ions("Na+1", mb(200)).ions("OH-1", mb(200)).water(mb(1000))).state;
        assertTrue(s.suspended().getOrDefault("chemicaladdon:zinc_hydroxide", 0L) >= mb(99),
            "Zn(OH)2 precipitates: " + s);
        assertTrue(s.ions().getOrDefault("Zn+2", 0L) < mb(1), "Zn exhausted: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void copperAmmineAndHydroxideCompetition() {
        // Ammonia both complexes Cu and hydrolyses to OH-, so copper hydroxide also appears.
        State s = e.solveClosed(new State(20)
            .ions("Cu+2", mb(100)).ions("SO4-2", mb(100))
            .molecule("chemicaladdon:ammonia", mb(400)).water(mb(1000))).state;
        assertTrue(s.ions().getOrDefault("[Cu(NH3)4]+2", 0L) > mb(1),
            "copper ammine complex forms: " + s);
        assertTrue(s.suspended().getOrDefault("chemicaladdon:copper_hydroxide", 0L) > mb(50),
            "copper hydroxide competes: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void ferricThiocyanateComplexes() {
        // Fe3+ hydrolyses as well as forming FeSCN2+, so both are expected.
        State s = e.solveClosed(new State(20)
            .ions("Fe+3", mb(100)).ions("Cl-1", mb(300))
            .ions("K+1", mb(100)).ions("SCN-1", mb(100)).water(mb(1000))).state;
        assertTrue(s.ions().getOrDefault("[FeSCN]+2", 0L) >= mb(50),
            "FeSCN2+ forms: " + s);
        assertTrue(s.suspended().getOrDefault("chemicaladdon:iron_hydroxide", 0L) >= mb(20),
            "iron hydrolysis/precipitation competes: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void aluminiumHydroxidePrecipitatesAtStoichiometricBase() {
        State s = e.solveClosed(new State(20)
            .ions("Al+3", mb(100)).ions("Cl-1", mb(300))
            .ions("Na+1", mb(300)).ions("OH-1", mb(300)).water(mb(1000))).state;
        assertTrue(s.suspended().getOrDefault("chemicaladdon:aluminium_hydroxide", 0L) >= mb(99),
            "Al(OH)3 precipitates: " + s);
        assertTrue(s.ions().getOrDefault("Al+3", 0L) < mb(1), "Al exhausted: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void aluminiumHydroxidePartiallyDissolvesInExcessBase() {
        // Threshold recalibrated with the authoritative delta_h values
        // (Gibbsite dissolution +64.9 kJ/mol, aluminate formation +20.6 kJ/mol,
        // both endothermic): at 20 C ~40 MB of the 100 MB Al stays as aluminate.
        State s = e.solveClosed(new State(20)
            .ions("Al+3", mb(100)).ions("Cl-1", mb(300))
            .ions("Na+1", mb(700)).ions("OH-1", mb(700)).water(mb(1000))).state;
        assertTrue(s.ions().getOrDefault("[Al(OH)4]-1", 0L) >= mb(30),
            "aluminate forms in excess base: " + s);
        assertTrue(s.suspended().getOrDefault("chemicaladdon:aluminium_hydroxide", 0L) > mb(20),
            "some Al(OH)3 remains: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void carbonateCompetitionFavoursBariumAndCalciumOverMagnesium() {
        State s = e.solveClosed(new State(20)
            .ions("Ba+2", mb(50)).ions("Ca+2", mb(100)).ions("Mg+2", mb(100))
            .ions("Cl-1", mb(500)).ions("Na+1", mb(200)).ions("CO3-2", mb(100))
            .water(mb(1000))).state;
        assertTrue(s.suspended().getOrDefault("chemicaladdon:barium_carbonate", 0L) >= mb(20),
            "BaCO3 precipitates: " + s);
        assertTrue(s.suspended().getOrDefault("chemicaladdon:limestone", 0L) >= mb(50),
            "CaCO3 precipitates: " + s);
        assertEquals(0L, s.suspended().getOrDefault("chemicaladdon:magnesium_carbonate", 0L),
            "MgCO3 should not win with limited carbonate: " + s);
        assertTrue(s.ions().getOrDefault("Mg+2", 0L) >= mb(99), "Mg stays dissolved: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void hydroxideCompetitionFavoursIronOverCopperZincMagnesium() {
        State s = e.solveClosed(new State(20)
            .ions("Fe+3", mb(100)).ions("Cu+2", mb(100)).ions("Zn+2", mb(100)).ions("Mg+2", mb(100))
            .ions("Cl-1", mb(900)).ions("Na+1", mb(300)).ions("OH-1", mb(300))
            .water(mb(1000))).state;
        assertTrue(s.suspended().getOrDefault("chemicaladdon:iron_hydroxide", 0L) >= mb(99),
            "Fe(OH)3 dominates: " + s);
        assertTrue(s.suspended().getOrDefault("chemicaladdon:copper_hydroxide", 0L) < mb(1),
            "Cu(OH)2 does not consume the limited OH: " + s);
        assertTrue(s.ions().getOrDefault("Zn+2", 0L) >= mb(99), "Zn stays dissolved: " + s);
        assertTrue(s.ions().getOrDefault("Mg+2", 0L) >= mb(99), "Mg stays dissolved: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void pureWaterHasNoAutoionisationIons() {
        State s = e.solveClosed(new State(20).water(mb(1000))).state;
        assertTrue(s.ions().isEmpty(), "autoionisation pair should be suppressed: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    /**
     * Ksp numerical anchor (independent review finding): the "fully precipitated"
     * assertions (solid >= 99, limiting ion < 1 mB) are immune to the Ksp MAGNITUDE —
     * changing AgCl logK by 4 orders of magnitude still passes them, because sub-mB
     * residuals are below the quantum grid. Slaked lime is the one mineral whose
     * equilibrium residual is measurable (>1 mB): [Ca][OH]^2 = Ksp = 10^-7.2
     * (log_k -5.2 + MINERAL_LOG_OFFSET -2) pins the residual at ~2.5 mB Ca at 25 C.
     * This anchors the Ksp value itself, not just "precipitation happens".
     */
    @Test void slakedLimeSaturationAnchorsKsp() {
        State s = e.solveClosed(new State(25)
            .suspended("chemicaladdon:slaked_lime", mb(50)).water(mb(1000))).state;
        long ca = s.ions().getOrDefault("Ca+2", 0L);
        // Ksp = 10^-7.2 -> [Ca] = (Ksp/4)^(1/3) = 2.5e-3 -> 2.5 mB in 1000 mB water;
        // the [2, 3] mB window spans logK -7.5 .. -6.97, i.e. -5.2 +- 0.24
        // ([Ca] ∝ Ksp^(1/3), so +-20% in [Ca] is +-0.24 in logK)
        assertTrue(ca >= mb(2) && ca <= mb(3), "Ca(OH)2 saturation [Ca] ~2.5 mB: " + s);
        assertTrue(s.suspendedAmount("chemicaladdon:slaked_lime") > 0, "solid remains: " + s);
        assertEquals(mb(0), s.netCharge());
    }
}
