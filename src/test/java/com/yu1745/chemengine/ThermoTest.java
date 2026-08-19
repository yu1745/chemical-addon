package com.yu1745.chemengine;

import static com.yu1745.chemengine.State.mb;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Track A5: verify that merged delta_h actually drives Van't Hoff temperature
 * dependence in the solver, not just energy accounting.
 *
 * <p>This tests the NH4+ dissociation equilibrium
 * {@code NH4+ + H2O = NH3 + H+} (delta_h +52.22 kJ/mol, endothermic as written,
 * ammonium_chloride_solution.json): heating shifts it toward NH3, which is both the
 * authored data and real chemistry (heating ammonium salts drives off ammonia).
 *
 * <p>Kw TEMPERATURE FIX (Track F1, landed): the implicit autoionisation entry
 * {@code H+ + OH- = water} now carries its formation enthalpy -55.91 kJ/mol
 * (SpeciesDatabase.allEquilibria; +55.9066 from phreeqc.dat). The leaf-elimination
 * algebra carries delta_h through to each secondary's component-space direction
 * (SystemModel's exprDeltaH), so:
 * <ul>
 *   <li>the OH- = -H+ secondary scales as +55.91 (dissociation endothermic) —
 *       Kw_diss rises with temperature (pKw ~14.9 @0C, ~13.3 @50C), as in reality;</li>
 *   <li>the net NH3 hydrolysis K_hyd = Kw/K_diss scales with +55.91 - 52.22 =
 *       +3.69 kJ/mol (endothermic), matching the authored +3.69 and the NBS +3.3 —
 *       previously the frozen Kw made it -52.2 kJ/mol, the OPPOSITE of reality;</li>
 *   <li>chained entries (HCO3- via the hydrolysis form, hydroxide Ksp) get the
 *       correct effective direction too (e.g. HCO3- = CO3-2 + H+ carries -14.9,
 *       Mg(OH)2 dissolution over Mg+2 - 2H+ carries -111.3).</li>
 * </ul>
 * The second test below pins the now-correct direction: in a closed ammonia water,
 * heating favours NH4+ (endothermic hydrolysis).
 */
class ThermoTest {

    private final Engine e = Harness.engine();

    @Test void nh4DissociationIsEndothermicSoHotFavoursAmmonia() {
        State cold = e.solveClosed(new State(0)
            .ions("NH4+1", mb(100)).ions("Cl-1", mb(100)).water(mb(1000))).state;
        State hot = e.solveClosed(new State(50)
            .ions("NH4+1", mb(100)).ions("Cl-1", mb(100)).water(mb(1000))).state;

        long nh3Cold = cold.molecules().getOrDefault("chemicaladdon:ammonia", 0L);
        long nh3Hot = hot.molecules().getOrDefault("chemicaladdon:ammonia", 0L);

        assertTrue(nh3Hot > nh3Cold,
            "endothermic NH4+ dissociation (delta_h 52.22 kJ/mol) should produce more NH3 at high temperature: "
                + nh3Hot + " vs " + nh3Cold);
    }

    /**
     * F1 RESOLVED (Kw delta_h fix landed): the net NH3 hydrolysis is endothermic
     * (+3.69 kJ/mol effective, matching reality +3.3), so in a closed ammonia water
     * heating favours NH4+ — the OPPOSITE of the pre-fix behaviour (frozen Kw made
     * it -52.2 kJ/mol exothermic; the old sentinel test asserted cold > hot and is
     * now inverted into a positive assertion of the correct physics).
     */
    @Test void ammoniaHydrolysisIsEndothermicSoHotFavoursAmmonium() {
        State cold = e.solveClosed(new State(0)
            .molecule("chemicaladdon:ammonia", mb(100)).water(mb(1000))).state;
        State hot = e.solveClosed(new State(50)
            .molecule("chemicaladdon:ammonia", mb(100)).water(mb(1000))).state;
        long nh4Cold = cold.ions().getOrDefault("NH4+1", 0L);
        long nh4Hot = hot.ions().getOrDefault("NH4+1", 0L);
        assertTrue(nh4Hot > nh4Cold,
            "endothermic NH3 hydrolysis (Kw ΔH -55.91 + NH4+ +52.22 = +3.69 kJ/mol net) "
                + "should favour NH4+ when hot: " + nh4Cold + " vs " + nh4Hot);
    }
}