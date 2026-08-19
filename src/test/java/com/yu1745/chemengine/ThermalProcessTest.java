package com.yu1745.chemengine;

import static com.yu1745.chemengine.State.mb;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * D3: high-temperature / solid-phase net steps. These are non-aqueous and driven
 * externally (combustion / calcination), so they are modelled as PURE stoichiometric
 * net reactions ({@link Electrolysis#advance}) — no aqueous equilibrium re-solve, which
 * would be the wrong tool for a dry kiln/furnace (the aqueous solver would re-dissolve
 * or reclassify the non-aqueous solids). The step transforms reactants to products
 * (gas + solid) exactly; downstream aqueous capture (SO2->acid, HCl->acid, CO2
 * absorption) is a separate aqueous step handled by the equilibrium engine.
 */
class ThermalProcessTest {

    private static final Electrolysis LIME_KILN = Electrolysis.parse(
        "chemicaladdon:limestone(s) = chemicaladdon:quicklime(s) + chemicaladdon:carbon_dioxide");
    private static final Electrolysis SODA_CALCINE = Electrolysis.parse(
        "2 chemicaladdon:sodium_bicarbonate(s) = chemicaladdon:sodium_carbonate(s)"
            + " + chemicaladdon:carbon_dioxide + water");
    private static final Electrolysis ALUMINA_CALCINE = Electrolysis.parse(
        "2 chemicaladdon:aluminium_hydroxide(s) = chemicaladdon:aluminium_oxide(s) + 3 water");
    private static final Electrolysis SULFUR_BURN = Electrolysis.parse(
        "chemicaladdon:sulfur(s) + chemicaladdon:oxygen = chemicaladdon:sulfur_dioxide");
    private static final Electrolysis CARBON_BURN = Electrolysis.parse(
        "chemicaladdon:carbon(s) + chemicaladdon:oxygen = chemicaladdon:carbon_dioxide");
    private static final Electrolysis HCL_SYNTHESIS = Electrolysis.parse(
        "chemicaladdon:hydrogen + chemicaladdon:chlorine = 2 chemicaladdon:hydrogen_chloride");

    @Test void limeKiln_convertsLimestoneToQuicklimeAndCO2() {
        // CaCO3 --900-1200C--> CaO + CO2. mb(40) progress consumes 40 CaCO3, makes 40 CaO + 40 CO2.
        State s = LIME_KILN.advance(new State(20)
            .suspended("chemicaladdon:limestone", mb(100)), mb(40));
        assertEquals(mb(60), s.suspendedAmount("chemicaladdon:limestone"), "limestone consumed: " + s);
        assertEquals(mb(40), s.suspendedAmount("chemicaladdon:quicklime"), "quicklime produced: " + s);
        assertEquals(mb(40), s.moleculeAmount("chemicaladdon:carbon_dioxide"), "CO2 produced: " + s);
    }

    @Test void sodaCalcination_makesSodaAshAndVentsCO2() {
        // 2 NaHCO3 --150-200C--> Na2CO3 + CO2 + H2O. mb(40) progress consumes 80 bicarb.
        State s = SODA_CALCINE.advance(new State(20)
            .suspended("chemicaladdon:sodium_bicarbonate", mb(100)), mb(40));
        assertEquals(mb(20), s.suspendedAmount("chemicaladdon:sodium_bicarbonate"), "bicarb consumed: " + s);
        assertEquals(mb(40), s.suspendedAmount("chemicaladdon:sodium_carbonate"), "soda ash produced: " + s);
        assertEquals(mb(40), s.moleculeAmount("chemicaladdon:carbon_dioxide"), "CO2 produced: " + s);
        assertEquals(mb(40), s.moleculeAmount(State.WATER), "water produced: " + s);
    }

    @Test void aluminaCalcination_makesAlumina() {
        // 2 Al(OH)3 --calcine--> Al2O3 + 3 H2O. mb(40) progress consumes 80 hydroxide.
        State s = ALUMINA_CALCINE.advance(new State(20)
            .suspended("chemicaladdon:aluminium_hydroxide", mb(100)), mb(40));
        assertEquals(mb(20), s.suspendedAmount("chemicaladdon:aluminium_hydroxide"), "hydroxide consumed: " + s);
        assertEquals(mb(40), s.suspendedAmount("chemicaladdon:aluminium_oxide"), "alumina produced: " + s);
        assertEquals(mb(120), s.moleculeAmount(State.WATER), "water produced: " + s);
    }

    @Test void sulfurBurn_makesSO2() {
        // S + O2 --ignite--> SO2. mb(40) progress consumes 40 S + 40 O2.
        State s = SULFUR_BURN.advance(new State(20)
            .suspended("chemicaladdon:sulfur", mb(100)).molecule("chemicaladdon:oxygen", mb(100)), mb(40));
        assertEquals(mb(60), s.suspendedAmount("chemicaladdon:sulfur"), "sulfur consumed: " + s);
        assertEquals(mb(60), s.moleculeAmount("chemicaladdon:oxygen"), "oxygen consumed: " + s);
        assertEquals(mb(40), s.moleculeAmount("chemicaladdon:sulfur_dioxide"), "SO2 produced: " + s);
    }

    @Test void carbonBurn_makesCO2() {
        // C + O2 --ignite--> CO2. mb(40) progress consumes 40 C + 40 O2.
        State s = CARBON_BURN.advance(new State(20)
            .suspended("chemicaladdon:carbon", mb(100)).molecule("chemicaladdon:oxygen", mb(100)), mb(40));
        assertEquals(mb(60), s.suspendedAmount("chemicaladdon:carbon"), "carbon consumed: " + s);
        assertEquals(mb(40), s.moleculeAmount("chemicaladdon:carbon_dioxide"), "CO2 produced: " + s);
    }

    @Test void hclSynthesis_makesHCl() {
        // H2 + Cl2 --ignite--> 2 HCl(g). mb(40) progress consumes 40 H2 + 40 Cl2, makes 80 HCl.
        State s = HCL_SYNTHESIS.advance(new State(20)
            .molecule("chemicaladdon:hydrogen", mb(100)).molecule("chemicaladdon:chlorine", mb(100)), mb(40));
        assertEquals(mb(60), s.moleculeAmount("chemicaladdon:hydrogen"), "H2 consumed: " + s);
        assertEquals(mb(60), s.moleculeAmount("chemicaladdon:chlorine"), "Cl2 consumed: " + s);
        assertEquals(mb(80), s.moleculeAmount("chemicaladdon:hydrogen_chloride"), "HCl produced: " + s);
    }

    private static final Electrolysis AMMONIA_OXIDATION = Electrolysis.parse(
        "4 chemicaladdon:ammonia + 5 chemicaladdon:oxygen = 4 chemicaladdon:nitric_oxide + 6 water");
    private static final Electrolysis NO_OXIDATION = Electrolysis.parse(
        "2 chemicaladdon:nitric_oxide + chemicaladdon:oxygen = 2 chemicaladdon:nitrogen_dioxide");
    private static final Electrolysis SO2_OXIDATION = Electrolysis.parse(
        "2 chemicaladdon:sulfur_dioxide + chemicaladdon:oxygen = 2 chemicaladdon:sulfur_trioxide");
    private static final Electrolysis NITROGEN_FIXATION = Electrolysis.parse(
        "chemicaladdon:nitrogen + chemicaladdon:oxygen = 2 chemicaladdon:nitric_oxide");
    private static final Electrolysis CO_REGENERATION = Electrolysis.parse(
        "chemicaladdon:carbon_dioxide + chemicaladdon:carbon(s) = 2 chemicaladdon:carbon_monoxide");
    private static final Electrolysis BLAST_FURNACE = Electrolysis.parse(
        "chemicaladdon:ferric_oxide(s) + 3 chemicaladdon:carbon_monoxide"
            + " = 2 chemicaladdon:iron_metal(s) + 3 chemicaladdon:carbon_dioxide");
    private static final Electrolysis WATER_GAS = Electrolysis.parse(
        "chemicaladdon:carbon(s) + water = chemicaladdon:carbon_monoxide + chemicaladdon:hydrogen");
    private static final Electrolysis PYRITE_ROAST = Electrolysis.parse(
        "4 chemicaladdon:pyrite(s) + 11 chemicaladdon:oxygen"
            + " = 2 chemicaladdon:ferric_oxide(s) + 8 chemicaladdon:sulfur_dioxide");

    @Test void ammoniaOxidation_makesNO() {
        // 4 NH3 + 5 O2 --Pt/800C--> 4 NO + 6 H2O. mb(10) progress.
        State s = AMMONIA_OXIDATION.advance(new State(20)
            .molecule("chemicaladdon:ammonia", mb(100)).molecule("chemicaladdon:oxygen", mb(100)), mb(10));
        assertEquals(mb(60), s.moleculeAmount("chemicaladdon:ammonia"), "NH3 consumed: " + s);
        assertEquals(mb(50), s.moleculeAmount("chemicaladdon:oxygen"), "O2 consumed: " + s);
        assertEquals(mb(40), s.moleculeAmount("chemicaladdon:nitric_oxide"), "NO produced: " + s);
        assertEquals(mb(60), s.moleculeAmount(State.WATER), "water produced: " + s);
    }

    @Test void nitricOxideOxidation_makesNO2() {
        // 2 NO + O2 -> 2 NO2. mb(40) progress.
        State s = NO_OXIDATION.advance(new State(20)
            .molecule("chemicaladdon:nitric_oxide", mb(100)).molecule("chemicaladdon:oxygen", mb(100)), mb(40));
        assertEquals(mb(20), s.moleculeAmount("chemicaladdon:nitric_oxide"), "NO consumed: " + s);
        assertEquals(mb(80), s.moleculeAmount("chemicaladdon:nitrogen_dioxide"), "NO2 produced: " + s);
    }

    @Test void sulfurDioxideOxidation_makesSO3() {
        // 2 SO2 + O2 -> 2 SO3 (contact process). mb(40) progress.
        State s = SO2_OXIDATION.advance(new State(20)
            .molecule("chemicaladdon:sulfur_dioxide", mb(100)).molecule("chemicaladdon:oxygen", mb(100)), mb(40));
        assertEquals(mb(20), s.moleculeAmount("chemicaladdon:sulfur_dioxide"), "SO2 consumed: " + s);
        assertEquals(mb(80), s.moleculeAmount("chemicaladdon:sulfur_trioxide"), "SO3 produced: " + s);
    }

    @Test void nitrogenFixation_makesNO() {
        // N2 + O2 --discharge--> 2 NO. mb(40) progress.
        State s = NITROGEN_FIXATION.advance(new State(20)
            .molecule("chemicaladdon:nitrogen", mb(100)).molecule("chemicaladdon:oxygen", mb(100)), mb(40));
        assertEquals(mb(60), s.moleculeAmount("chemicaladdon:nitrogen"), "N2 consumed: " + s);
        assertEquals(mb(80), s.moleculeAmount("chemicaladdon:nitric_oxide"), "NO produced: " + s);
    }

    @Test void carbonMonoxideRegeneration_makesCO() {
        // CO2 + C --highT--> 2 CO. mb(40) progress.
        State s = CO_REGENERATION.advance(new State(20)
            .molecule("chemicaladdon:carbon_dioxide", mb(100)).suspended("chemicaladdon:carbon", mb(100)), mb(40));
        assertEquals(mb(60), s.moleculeAmount("chemicaladdon:carbon_dioxide"), "CO2 consumed: " + s);
        assertEquals(mb(60), s.suspendedAmount("chemicaladdon:carbon"), "carbon consumed: " + s);
        assertEquals(mb(80), s.moleculeAmount("chemicaladdon:carbon_monoxide"), "CO produced: " + s);
    }

    @Test void blastFurnaceReduction_makesIron() {
        // Fe2O3 + 3 CO --highT--> 2 Fe + 3 CO2. mb(20) progress.
        State s = BLAST_FURNACE.advance(new State(20)
            .suspended("chemicaladdon:ferric_oxide", mb(100)).molecule("chemicaladdon:carbon_monoxide", mb(100)), mb(20));
        assertEquals(mb(80), s.suspendedAmount("chemicaladdon:ferric_oxide"), "Fe2O3 consumed: " + s);
        assertEquals(mb(40), s.suspendedAmount("chemicaladdon:iron_metal"), "iron produced: " + s);
        assertEquals(mb(60), s.moleculeAmount("chemicaladdon:carbon_dioxide"), "CO2 produced: " + s);
    }

    @Test void waterGas_makesCOAndH2() {
        // C + H2O --highT--> CO + H2. mb(40) progress.
        State s = WATER_GAS.advance(new State(20)
            .suspended("chemicaladdon:carbon", mb(100)).water(mb(100)), mb(40));
        assertEquals(mb(60), s.suspendedAmount("chemicaladdon:carbon"), "carbon consumed: " + s);
        assertEquals(mb(40), s.moleculeAmount("chemicaladdon:carbon_monoxide"), "CO produced: " + s);
        assertEquals(mb(40), s.moleculeAmount("chemicaladdon:hydrogen"), "H2 produced: " + s);
    }

    @Test void pyriteRoasting_makesSO2() {
        // 4 FeS2 + 11 O2 --roast--> 2 Fe2O3 + 8 SO2. mb(10) progress.
        State s = PYRITE_ROAST.advance(new State(20)
            .suspended("chemicaladdon:pyrite", mb(100)).molecule("chemicaladdon:oxygen", mb(200)), mb(10));
        assertEquals(mb(60), s.suspendedAmount("chemicaladdon:pyrite"), "pyrite consumed: " + s);
        assertEquals(mb(20), s.suspendedAmount("chemicaladdon:ferric_oxide"), "Fe2O3 produced: " + s);
        assertEquals(mb(80), s.moleculeAmount("chemicaladdon:sulfur_dioxide"), "SO2 produced: " + s);
    }

    private static final Electrolysis COPPER_PATINA = Electrolysis.parse(
        "2 chemicaladdon:copper_metal(s) + chemicaladdon:oxygen + chemicaladdon:carbon_dioxide"
            + " + water = chemicaladdon:copper_carbonate(s)");

    @Test void copperPatina_makesMalachite() {
        // 2 Cu + O2 + CO2 + H2O --natural--> Cu2(OH)2CO3 (verdigris / malachite). mb(20) progress.
        State s = COPPER_PATINA.advance(new State(20)
            .suspended("chemicaladdon:copper_metal", mb(100))
            .molecule("chemicaladdon:oxygen", mb(100)).molecule("chemicaladdon:carbon_dioxide", mb(100))
            .water(mb(100)), mb(20));
        assertEquals(mb(60), s.suspendedAmount("chemicaladdon:copper_metal"), "copper consumed: " + s);
        assertEquals(mb(20), s.suspendedAmount("chemicaladdon:copper_carbonate"), "malachite produced: " + s);
    }

    private static final Electrolysis CARBIDE_FURNACE = Electrolysis.parse(
        "chemicaladdon:quicklime(s) + 3 chemicaladdon:carbon(s)"
            + " = chemicaladdon:calcium_carbide(s) + chemicaladdon:carbon_monoxide");
    private static final Electrolysis ACETYLENE_FROM_CARBIDE = Electrolysis.parse(
        "chemicaladdon:calcium_carbide(s) + 2 water = chemicaladdon:slaked_lime(s) + chemicaladdon:acetylene");
    private static final Electrolysis UREA_SYNTHESIS = Electrolysis.parse(
        "2 chemicaladdon:ammonia + chemicaladdon:carbon_dioxide = chemicaladdon:urea + water");

    @Test void calciumCarbideFromLimeAndCarbon() {
        // CaO + 3 C --2200C furnace--> CaC2 + CO. mb(20) progress.
        State s = CARBIDE_FURNACE.advance(new State(20)
            .suspended("chemicaladdon:quicklime", mb(100)).suspended("chemicaladdon:carbon", mb(100)), mb(20));
        assertEquals(mb(80), s.suspendedAmount("chemicaladdon:quicklime"), "lime consumed: " + s);
        assertEquals(mb(40), s.suspendedAmount("chemicaladdon:carbon"), "carbon consumed: " + s);
        assertEquals(mb(20), s.suspendedAmount("chemicaladdon:calcium_carbide"), "carbide produced: " + s);
        assertEquals(mb(20), s.moleculeAmount("chemicaladdon:carbon_monoxide"), "CO produced: " + s);
    }

    @Test void acetyleneFromCarbide() {
        // CaC2 + 2 H2O -> Ca(OH)2 + C2H2. mb(20) progress.
        State s = ACETYLENE_FROM_CARBIDE.advance(new State(20)
            .suspended("chemicaladdon:calcium_carbide", mb(100)).water(mb(100)), mb(20));
        assertEquals(mb(80), s.suspendedAmount("chemicaladdon:calcium_carbide"), "carbide consumed: " + s);
        assertEquals(mb(20), s.suspendedAmount("chemicaladdon:slaked_lime"), "slaked lime produced: " + s);
        assertEquals(mb(20), s.moleculeAmount("chemicaladdon:acetylene"), "acetylene produced: " + s);
    }

    @Test void ureaSynthesis() {
        // 2 NH3 + CO2 --20MPa/180C--> CO(NH2)2 + H2O. mb(20) progress.
        State s = UREA_SYNTHESIS.advance(new State(20)
            .molecule("chemicaladdon:ammonia", mb(100)).molecule("chemicaladdon:carbon_dioxide", mb(100)), mb(20));
        assertEquals(mb(60), s.moleculeAmount("chemicaladdon:ammonia"), "NH3 consumed: " + s);
        assertEquals(mb(80), s.moleculeAmount("chemicaladdon:carbon_dioxide"), "CO2 consumed: " + s);
        assertEquals(mb(20), s.moleculeAmount("chemicaladdon:urea"), "urea produced: " + s);
    }

    private static final Electrolysis COPPER_SMELT_ROAST = Electrolysis.parse(
        "2 chemicaladdon:cuprous_sulfide(s) + 3 chemicaladdon:oxygen"
            + " = 2 chemicaladdon:cuprous_oxide(s) + 2 chemicaladdon:sulfur_dioxide");
    private static final Electrolysis COPPER_SMELT_REDUCE = Electrolysis.parse(
        "chemicaladdon:cuprous_sulfide(s) + 2 chemicaladdon:cuprous_oxide(s)"
            + " = 6 chemicaladdon:copper_metal(s) + chemicaladdon:sulfur_dioxide");
    private static final Electrolysis ALUMINIUM_ELECTROLYSIS = Electrolysis.parse(
        "2 chemicaladdon:aluminium_oxide(s) = 4 chemicaladdon:aluminium_metal(s) + 3 chemicaladdon:oxygen");

    @Test void copperSmeltRoast_makesCuprousOxide() {
        // 2 Cu2S + 3 O2 --smelt--> 2 Cu2O + 2 SO2. mb(10) progress.
        State s = COPPER_SMELT_ROAST.advance(new State(20)
            .suspended("chemicaladdon:cuprous_sulfide", mb(100)).molecule("chemicaladdon:oxygen", mb(100)), mb(10));
        assertEquals(mb(80), s.suspendedAmount("chemicaladdon:cuprous_sulfide"), "Cu2S consumed: " + s);
        assertEquals(mb(20), s.suspendedAmount("chemicaladdon:cuprous_oxide"), "Cu2O produced: " + s);
        assertEquals(mb(20), s.moleculeAmount("chemicaladdon:sulfur_dioxide"), "SO2 produced: " + s);
    }

    @Test void copperSmeltReduce_makesCopper() {
        // Cu2S + 2 Cu2O --smelt--> 6 Cu + SO2. mb(10) progress.
        State s = COPPER_SMELT_REDUCE.advance(new State(20)
            .suspended("chemicaladdon:cuprous_sulfide", mb(100)).suspended("chemicaladdon:cuprous_oxide", mb(100)), mb(10));
        assertEquals(mb(90), s.suspendedAmount("chemicaladdon:cuprous_sulfide"), "Cu2S consumed: " + s);
        assertEquals(mb(80), s.suspendedAmount("chemicaladdon:cuprous_oxide"), "Cu2O consumed: " + s);
        assertEquals(mb(60), s.suspendedAmount("chemicaladdon:copper_metal"), "copper produced: " + s);
        assertEquals(mb(10), s.moleculeAmount("chemicaladdon:sulfur_dioxide"), "SO2 produced: " + s);
    }

    @Test void moltenSaltAluminiumElectrolysis() {
        // 2 Al2O3 --cryolite/electrolysis--> 4 Al + 3 O2. mb(10) progress.
        State s = ALUMINIUM_ELECTROLYSIS.advance(new State(20)
            .suspended("chemicaladdon:aluminium_oxide", mb(100)), mb(10));
        assertEquals(mb(80), s.suspendedAmount("chemicaladdon:aluminium_oxide"), "Al2O3 consumed: " + s);
        assertEquals(mb(40), s.suspendedAmount("chemicaladdon:aluminium_metal"), "aluminium produced: " + s);
        assertEquals(mb(30), s.moleculeAmount("chemicaladdon:oxygen"), "O2 produced: " + s);
    }

    private static final Electrolysis MANNHEIM_HCL = Electrolysis.parse(
        "chemicaladdon:rock_salt(s) + chemicaladdon:sulfuric_acid"
            + " = chemicaladdon:sodium_bisulfate(s) + chemicaladdon:hydrogen_chloride");
    private static final Electrolysis MANNHEIM_HNO3 = Electrolysis.parse(
        "chemicaladdon:sodium_nitrate(s) + chemicaladdon:sulfuric_acid"
            + " = chemicaladdon:sodium_bisulfate(s) + chemicaladdon:nitric_acid");

    @Test void mannheimMakesHydrogenChloride() {
        // NaCl + H2SO4(conc) --heat--> NaHSO4 + HCl(g). mb(40) progress.
        State s = MANNHEIM_HCL.advance(new State(20)
            .suspended("chemicaladdon:rock_salt", mb(100)).molecule("chemicaladdon:sulfuric_acid", mb(100)), mb(40));
        assertEquals(mb(60), s.suspendedAmount("chemicaladdon:rock_salt"), "NaCl consumed: " + s);
        assertEquals(mb(60), s.moleculeAmount("chemicaladdon:sulfuric_acid"), "H2SO4 consumed: " + s);
        assertEquals(mb(40), s.suspendedAmount("chemicaladdon:sodium_bisulfate"), "NaHSO4 produced: " + s);
        assertEquals(mb(40), s.moleculeAmount("chemicaladdon:hydrogen_chloride"), "HCl produced: " + s);
    }

    @Test void mannheimMakesNitricAcid() {
        // NaNO3 + H2SO4(conc) --warm--> NaHSO4 + HNO3. mb(40) progress.
        State s = MANNHEIM_HNO3.advance(new State(20)
            .suspended("chemicaladdon:sodium_nitrate", mb(100)).molecule("chemicaladdon:sulfuric_acid", mb(100)), mb(40));
        assertEquals(mb(60), s.suspendedAmount("chemicaladdon:sodium_nitrate"), "NaNO3 consumed: " + s);
        assertEquals(mb(40), s.suspendedAmount("chemicaladdon:sodium_bisulfate"), "NaHSO4 produced: " + s);
        assertEquals(mb(40), s.moleculeAmount("chemicaladdon:nitric_acid"), "HNO3 produced: " + s);
    }

    private static final Electrolysis KCN_FUSION = Electrolysis.parse(
        "chemicaladdon:potassium_cyanide(s) + chemicaladdon:sulfur(s) = chemicaladdon:potassium_thiocyanate(s)");

    @Test void potassiumCyanideFusion_makesThiocyanate() {
        // KCN + S --molten--> KSCN. mb(40) progress.
        State s = KCN_FUSION.advance(new State(20)
            .suspended("chemicaladdon:potassium_cyanide", mb(100)).suspended("chemicaladdon:sulfur", mb(100)), mb(40));
        assertEquals(mb(60), s.suspendedAmount("chemicaladdon:potassium_cyanide"), "KCN consumed: " + s);
        assertEquals(mb(60), s.suspendedAmount("chemicaladdon:sulfur"), "sulfur consumed: " + s);
        assertEquals(mb(40), s.suspendedAmount("chemicaladdon:potassium_thiocyanate"), "KSCN produced: " + s);
    }

    private static final Electrolysis FERRIC_OXIDE_ACID = Electrolysis.parse(
        "chemicaladdon:ferric_oxide(s) + 6 H+1 = 2 Fe+3 + 3 water");
    private static final Electrolysis ZINC_OXIDE_ACID = Electrolysis.parse(
        "chemicaladdon:zinc_oxide(s) + 2 H+1 = Zn+2 + water");
    private static final Electrolysis CUPROUS_OXIDE_ACID = Electrolysis.parse(
        "chemicaladdon:cupric_oxide(s) + 2 H+1 = Cu+2 + water");
    private static final Electrolysis POTASSIUM_CHLORATE_DECOMP = Electrolysis.parse(
        "2 chemicaladdon:potassium_chlorate(s) = 2 chemicaladdon:potassium_chloride(s) + 3 chemicaladdon:oxygen");
    private static final Electrolysis MANGANESE_DIOXIDE_ACID = Electrolysis.parse(
        "chemicaladdon:manganese_dioxide(s) + 4 H+1 + 2 Cl-1 = Mn+2 + chemicaladdon:chlorine + 2 water");
    private static final Electrolysis SILVER_NITRIC_ACID = Electrolysis.parse(
        "chemicaladdon:silver_metal(s) + 2 H+1 + NO3-1 = Ag+1 + chemicaladdon:nitrogen_dioxide + water");
    private static final Electrolysis BAYER_DISSOLVE = Electrolysis.parse(
        "chemicaladdon:aluminium_oxide(s) + 2 OH-1 + 3 water = 2 [Al(OH)4]-1");

    @Test void ferricOxideAcidDissolution() {
        // Fe2O3 + 6 HCl -> 2 FeCl3 + 3 H2O (oxide acid dissolution, Cl- spectator). mb(10).
        State s = FERRIC_OXIDE_ACID.advance(new State(20)
            .suspended("chemicaladdon:ferric_oxide", mb(100)).ions("H+1", mb(100)), mb(10));
        assertEquals(mb(90), s.suspendedAmount("chemicaladdon:ferric_oxide"), "Fe2O3 consumed: " + s);
        assertEquals(mb(20), s.ionAmount("Fe+3"), "Fe3+ produced: " + s);
        assertEquals(mb(30), s.moleculeAmount(State.WATER), "water produced: " + s);
    }

    @Test void zincOxideAcidDissolution() {
        // ZnO + H2SO4 -> ZnSO4 + H2O. mb(40) progress.
        State s = ZINC_OXIDE_ACID.advance(new State(20)
            .suspended("chemicaladdon:zinc_oxide", mb(100)).ions("H+1", mb(100)), mb(40));
        assertEquals(mb(60), s.suspendedAmount("chemicaladdon:zinc_oxide"), "ZnO consumed: " + s);
        assertEquals(mb(40), s.ionAmount("Zn+2"), "Zn2+ produced: " + s);
    }

    @Test void cupricOxideAcidDissolution() {
        // CuO + H2SO4 -> CuSO4 + H2O. mb(40) progress.
        State s = CUPROUS_OXIDE_ACID.advance(new State(20)
            .suspended("chemicaladdon:cupric_oxide", mb(100)).ions("H+1", mb(100)), mb(40));
        assertEquals(mb(60), s.suspendedAmount("chemicaladdon:cupric_oxide"), "CuO consumed: " + s);
        assertEquals(mb(40), s.ionAmount("Cu+2"), "Cu2+ produced: " + s);
    }

    @Test void potassiumChlorateDecomposesToOxygen() {
        // 2 KClO3 --MnO2/delta--> 2 KCl + 3 O2. mb(10) progress.
        State s = POTASSIUM_CHLORATE_DECOMP.advance(new State(20)
            .suspended("chemicaladdon:potassium_chlorate", mb(100)), mb(10));
        assertEquals(mb(80), s.suspendedAmount("chemicaladdon:potassium_chlorate"), "KClO3 consumed: " + s);
        assertEquals(mb(20), s.suspendedAmount("chemicaladdon:potassium_chloride"), "KCl produced: " + s);
        assertEquals(mb(30), s.moleculeAmount("chemicaladdon:oxygen"), "O2 produced: " + s);
    }

    @Test void manganeseDioxideAcidDissolution() {
        // MnO2 + 4 HCl(conc) -> MnCl2 + Cl2 + 2 H2O. mb(10) progress.
        State s = MANGANESE_DIOXIDE_ACID.advance(new State(20)
            .suspended("chemicaladdon:manganese_dioxide", mb(100)).ions("H+1", mb(100)).ions("Cl-1", mb(100)), mb(10));
        assertEquals(mb(90), s.suspendedAmount("chemicaladdon:manganese_dioxide"), "MnO2 consumed: " + s);
        assertEquals(mb(10), s.ionAmount("Mn+2"), "Mn2+ produced: " + s);
        assertEquals(mb(10), s.moleculeAmount("chemicaladdon:chlorine"), "Cl2 produced: " + s);
    }

    @Test void silverDissolvesInNitricAcid() {
        // Ag + 2 HNO3(conc) -> AgNO3 + NO2 + H2O. mb(10) progress.
        State s = SILVER_NITRIC_ACID.advance(new State(20)
            .suspended("chemicaladdon:silver_metal", mb(100)).ions("H+1", mb(100)).ions("NO3-1", mb(100)), mb(10));
        assertEquals(mb(90), s.suspendedAmount("chemicaladdon:silver_metal"), "silver consumed: " + s);
        assertEquals(mb(10), s.ionAmount("Ag+1"), "Ag+ produced: " + s);
        assertEquals(mb(10), s.moleculeAmount("chemicaladdon:nitrogen_dioxide"), "NO2 produced: " + s);
    }

    @Test void bayerDissolution_formsAluminate() {
        // Al2O3 + 2 NaOH + 3 H2O -> 2 NaAlO2 + ... (aluminate). mb(10) progress.
        State s = BAYER_DISSOLVE.advance(new State(20)
            .suspended("chemicaladdon:aluminium_oxide", mb(100)).ions("OH-1", mb(100)).water(mb(100)), mb(10));
        assertEquals(mb(90), s.suspendedAmount("chemicaladdon:aluminium_oxide"), "Al2O3 consumed: " + s);
        assertEquals(mb(20), s.ionAmount("[Al(OH)4]-1"), "aluminate produced: " + s);
    }

    private static final Electrolysis COPPER_CONC_SULFURIC = Electrolysis.parse(
        "chemicaladdon:copper_metal(s) + 4 H+1 + SO4-2 = Cu+2 + chemicaladdon:sulfur_dioxide + 2 water");
    private static final Electrolysis COPPER_DILUTE_NITRIC = Electrolysis.parse(
        "3 chemicaladdon:copper_metal(s) + 8 H+1 + 2 NO3-1 = 3 Cu+2 + 2 chemicaladdon:nitric_oxide + 4 water");
    private static final Electrolysis COPPER_CONC_NITRIC = Electrolysis.parse(
        "chemicaladdon:copper_metal(s) + 4 H+1 + 2 NO3-1 = Cu+2 + 2 chemicaladdon:nitrogen_dioxide + 2 water");
    private static final Electrolysis LIME_SLAKING = Electrolysis.parse(
        "chemicaladdon:quicklime(s) + water = chemicaladdon:slaked_lime(s)");
    private static final Electrolysis FGD_OXIDATION = Electrolysis.parse(
        "2 chemicaladdon:calcium_sulfite(s) + chemicaladdon:oxygen = 2 chemicaladdon:gypsum(s)");

    @Test void copperDissolvesInConcentratedSulfuric() {
        // Cu + 2 H2SO4(conc) -> CuSO4 + SO2 + 2 H2O. mb(10) progress.
        State s = COPPER_CONC_SULFURIC.advance(new State(20)
            .suspended("chemicaladdon:copper_metal", mb(100)).ions("H+1", mb(100)).ions("SO4-2", mb(100)), mb(10));
        assertEquals(mb(90), s.suspendedAmount("chemicaladdon:copper_metal"), "copper consumed: " + s);
        assertEquals(mb(10), s.ionAmount("Cu+2"), "Cu2+ produced: " + s);
        assertEquals(mb(10), s.moleculeAmount("chemicaladdon:sulfur_dioxide"), "SO2 produced: " + s);
    }

    @Test void copperDissolvesInDiluteNitric_makesNO() {
        // 3 Cu + 8 HNO3(dil) -> 3 Cu(NO3)2 + 2 NO + 4 H2O. mb(10) progress.
        State s = COPPER_DILUTE_NITRIC.advance(new State(20)
            .suspended("chemicaladdon:copper_metal", mb(100)).ions("H+1", mb(100)).ions("NO3-1", mb(100)), mb(10));
        assertEquals(mb(70), s.suspendedAmount("chemicaladdon:copper_metal"), "copper consumed: " + s);
        assertEquals(mb(30), s.ionAmount("Cu+2"), "Cu2+ produced: " + s);
        assertEquals(mb(20), s.moleculeAmount("chemicaladdon:nitric_oxide"), "NO produced: " + s);
    }

    @Test void copperDissolvesInConcentratedNitric_makesNO2() {
        // Cu + 4 HNO3(conc) -> Cu(NO3)2 + 2 NO2 + 2 H2O. mb(10) progress.
        State s = COPPER_CONC_NITRIC.advance(new State(20)
            .suspended("chemicaladdon:copper_metal", mb(100)).ions("H+1", mb(100)).ions("NO3-1", mb(100)), mb(10));
        assertEquals(mb(90), s.suspendedAmount("chemicaladdon:copper_metal"), "copper consumed: " + s);
        assertEquals(mb(10), s.ionAmount("Cu+2"), "Cu2+ produced: " + s);
        assertEquals(mb(20), s.moleculeAmount("chemicaladdon:nitrogen_dioxide"), "NO2 produced: " + s);
    }

    @Test void quicklimeSlaking_makesSlakedLime() {
        // CaO + H2O -> Ca(OH)2. mb(40) progress.
        State s = LIME_SLAKING.advance(new State(20)
            .suspended("chemicaladdon:quicklime", mb(100)).water(mb(100)), mb(40));
        assertEquals(mb(60), s.suspendedAmount("chemicaladdon:quicklime"), "quicklime consumed: " + s);
        assertEquals(mb(40), s.suspendedAmount("chemicaladdon:slaked_lime"), "slaked lime produced: " + s);
    }

    @Test void fgdOxidation_makesGypsum() {
        // 2 CaSO3 + O2 -> 2 CaSO4 (flue-gas desulfurisation oxidation). mb(10) progress.
        State s = FGD_OXIDATION.advance(new State(20)
            .suspended("chemicaladdon:calcium_sulfite", mb(100)).molecule("chemicaladdon:oxygen", mb(100)), mb(10));
        assertEquals(mb(80), s.suspendedAmount("chemicaladdon:calcium_sulfite"), "CaSO3 consumed: " + s);
        assertEquals(mb(20), s.suspendedAmount("chemicaladdon:gypsum"), "gypsum produced: " + s);
    }

    private static final Electrolysis LIMESTONE_SULFURIC = Electrolysis.parse(
        "chemicaladdon:limestone(s) + 2 H+1 = Ca+2 + chemicaladdon:carbon_dioxide + water");

    @Test void limestoneSulfuricAcid_makesGypsum() {
        // CaCO3 + H2SO4 -> CaSO4 + CO2 + H2O (acid-carbonate). mb(10) progress.
        State s = LIMESTONE_SULFURIC.advance(new State(20)
            .suspended("chemicaladdon:limestone", mb(100)).ions("H+1", mb(100)), mb(10));
        assertEquals(mb(90), s.suspendedAmount("chemicaladdon:limestone"), "limestone consumed: " + s);
        assertEquals(mb(10), s.ionAmount("Ca+2"), "Ca2+ produced: " + s);
        assertEquals(mb(10), s.moleculeAmount("chemicaladdon:carbon_dioxide"), "CO2 produced: " + s);
    }

    private static final Electrolysis HABER_SYNTHESIS = Electrolysis.parse(
        "chemicaladdon:nitrogen + 3 chemicaladdon:hydrogen = 2 chemicaladdon:ammonia");

    @Test void haberSynthesis_makesAmmonia() {
        // N2 + 3 H2 -> 2 NH3 (Haber, net reaction). mb(20) progress.
        // NOTE: real Haber is a partial-conversion high-pressure equilibrium (~15-25% per
        // pass, 400-500C / 10-30 MPa); the net reaction expresses the stoichiometric
        // synthesis (conversion efficiency is a plant/process detail outside this model).
        State s = HABER_SYNTHESIS.advance(new State(20)
            .molecule("chemicaladdon:nitrogen", mb(100)).molecule("chemicaladdon:hydrogen", mb(100)), mb(20));
        assertEquals(mb(80), s.moleculeAmount("chemicaladdon:nitrogen"), "N2 consumed: " + s);
        assertEquals(mb(40), s.moleculeAmount("chemicaladdon:hydrogen"), "H2 consumed: " + s);
        assertEquals(mb(40), s.moleculeAmount("chemicaladdon:ammonia"), "NH3 produced: " + s);
    }
}
