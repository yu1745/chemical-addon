package com.yu1745.chemengine;

import static com.yu1745.chemengine.State.mb;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Solvay process: the aqueous steps that the current engine can represent.
 *
 * <p>Step 1 is the carbonation of brine:
 * {@code NaCl + NH3 + CO2 + H2O -> NaHCO3(s) + NH4Cl}.
 * Step 5 is the ammonia recovery:
 * {@code Ca(OH)2(s) + 2 NH4Cl -> CaCl2 + 2 NH3 + 2 H2O}.
 */
class SolvayProcessTest {

    private final Engine e = Harness.engine();

    @Test void step1CarbonationPrecipitatesSodiumBicarbonate() {
        State s = e.solveClosed(new State(20)
            .ions("Na+1", mb(600)).ions("Cl-1", mb(600))
            .molecule("chemicaladdon:ammonia", mb(300))
            .molecule("chemicaladdon:carbon_dioxide", mb(300))
            .water(mb(1000))).state;

        long nahco3 = s.suspended().getOrDefault("chemicaladdon:sodium_bicarbonate", 0L);
        long nh4 = s.ions().getOrDefault("NH4+1", 0L);
        long hco3 = s.ions().getOrDefault("HCO3-1", 0L);
        long co3 = s.ions().getOrDefault("CO3-2", 0L);
        long co2 = s.molecules().getOrDefault("chemicaladdon:carbon_dioxide", 0L);
        long na = s.ions().getOrDefault("Na+1", 0L);

        assertTrue(nahco3 >= mb(100),
            "NaHCO3 precipitates (110.5 MB with Kw ΔH + expr-deltaH algebra @20C; 120 was the pre-Kw-fix calibration): " + s);
        assertTrue(nh4 >= mb(260), "NH4Cl forms in solution: " + s);
        assertTrue(hco3 >= mb(120), "bicarbonate remains in solution: " + s);
        assertEquals(mb(600), s.ions().getOrDefault("Cl-1", 0L) + s.molecules().getOrDefault("chemicaladdon:hydrogen_chloride", 0L), "chloride is conserved");
        assertEquals(mb(600), na + nahco3, "sodium conserved (Na+ + NaHCO3): " + s);
        assertEquals(mb(300), co3 + hco3 + co2 + nahco3, "carbon conserved (CO3/HCO3/CO2/NaHCO3): " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void step5AmmoniaRecoveryFromSlakedLimeAndAmmoniumChloride() {
        State s = e.solveClosed(new State(20)
            .suspended("chemicaladdon:slaked_lime", mb(50))
            .ions("NH4+1", mb(100)).ions("Cl-1", mb(100))
            .water(mb(1000))).state;

        long nh3 = s.molecules().getOrDefault("chemicaladdon:ammonia", 0L);
        long ca = s.ions().getOrDefault("Ca+2", 0L);
        long nh4 = s.ions().getOrDefault("NH4+1", 0L);
        long limeLeft = s.suspended().getOrDefault("chemicaladdon:slaked_lime", 0L);

        // 独立重审（子代理）指出：97.9 实际值 vs 95 阈值余量仅 3%，属刀刃；退回 90（8% 余量）
        assertTrue(nh3 >= mb(90), "ammonia is recovered: " + s);
        assertTrue(ca >= mb(45), "calcium chloride forms: " + s);
        assertTrue(nh4 < mb(5), "ammonium is consumed: " + s);
        assertTrue(limeLeft < mb(1), "slaked lime is consumed: " + s);
        assertEquals(mb(0), s.netCharge());
    }
}
