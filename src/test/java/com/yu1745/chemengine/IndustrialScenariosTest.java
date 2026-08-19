package com.yu1745.chemengine;

import static com.yu1745.chemengine.State.mb;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Track C 场景补全：把工业蓝图（com.yu1745.chemengine.industrial）中标为"引擎可表达"
 * 但此前没有场景测试的原料生产流程逐个落成测试。电解/火法/煅烧/有机特种仍留空待实现。
 *
 * <p>新增数据（本轮）：gypsum Ksp（phreeqc -4.58 直用）、SO2/HSO3-/SO3-2 体系与
 * CaSO3（NIST/文献，estimated）、Cl2 碱性歧化（文献，estimated）、锌酸盐两性
 * （phreeqc Zn(OH)4-2 组合）。
 */
class IndustrialScenariosTest {

    private final Engine e = Harness.engine();

    @Test void ammoniumSulfateAbsorbsAmmonia() {
        // 2NH3 + H2SO4 -> (NH4)2SO4: ammonia neutralises the acid completely
        State s = e.solveClosed(new State(20)
            .molecule("chemicaladdon:ammonia", mb(200))
            .ions("H+1", mb(200)).ions("SO4-2", mb(100)).water(mb(1000))).state;
        assertTrue(s.ionAmount("NH4+1") >= mb(190), "NH4+ forms: " + s);
        assertEquals(mb(100), s.ionAmount("SO4-2"), "sulfate conserved: " + s);
        assertTrue(s.moleculeAmount("chemicaladdon:ammonia") < mb(1), "ammonia consumed: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void ammoniumNitrateFromAmmoniaAndAcid() {
        // NH3 + HNO3 -> NH4NO3
        State s = e.solveClosed(new State(20)
            .molecule("chemicaladdon:ammonia", mb(100))
            .ions("H+1", mb(100)).ions("NO3-1", mb(100)).water(mb(1000))).state;
        assertTrue(s.ionAmount("NH4+1") >= mb(95), "NH4+ forms: " + s);
        assertEquals(mb(100), s.ionAmount("NO3-1"), "nitrate conserved: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void magnesiumChlorideFromBruciteAndAcid() {
        // seawater-Mg route: Mg(OH)2 + 2HCl -> MgCl2 + 2H2O (instant dissolution)
        State s = e.solveClosed(new State(20)
            .suspended("chemicaladdon:magnesium_hydroxide", mb(100))
            .ions("H+1", mb(200)).ions("Cl-1", mb(200)).water(mb(1000))).state;
        assertTrue(s.ionAmount("Mg+2") >= mb(95), "MgCl2 forms: " + s);
        assertTrue(s.suspendedAmount("chemicaladdon:magnesium_hydroxide") < mb(1), "brucite consumed: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void copperSulfateFromMalachiteAcidDissolution() {
        // Cu2(OH)2CO3 + 2H2SO4 -> 2CuSO4 + CO2 + 3H2O
        State s = e.solveClosed(new State(20)
            .suspended("chemicaladdon:copper_carbonate", mb(50))
            .ions("H+1", mb(200)).ions("SO4-2", mb(100)).water(mb(1000))).state;
        assertTrue(s.ionAmount("Cu+2") >= mb(95), "CuSO4 forms: " + s);
        assertTrue(s.moleculeAmount("chemicaladdon:carbon_dioxide") >= mb(45), "CO2 evolved: " + s);
        assertTrue(s.suspendedAmount("chemicaladdon:copper_carbonate") < mb(5), "malachite dissolved: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void potassiumAlumFromAluminiumHydroxideAcidDissolution() {
        // 2Al(OH)3 + 3H2SO4 -> Al2(SO4)3 + 6H2O (with K2SO4 for the alum stoichiometry)
        State s = e.solveClosed(new State(20)
            .suspended("chemicaladdon:aluminium_hydroxide", mb(50))
            .ions("H+1", mb(150)).ions("SO4-2", mb(100)).ions("K+1", mb(50)).water(mb(1000))).state;
        assertTrue(s.ionAmount("Al+3") >= mb(45), "Al3+ dissolves: " + s);
        assertTrue(s.suspendedAmount("chemicaladdon:aluminium_hydroxide") < mb(5), "Al(OH)3 consumed: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void potassiumNitrateByMetathesisAndCoolingCrystallisation() {
        // NaNO3 + KCl ⇌ KNO3 + NaCl: all four ions dissolve hot (80C), cooling
        // crystallises KNO3 (0C cap 13.3 g/100g -> 133 mB) while NaCl stays below its
        // 0C cap (35.7 g/100g -> 357 mB).
        State hot = e.solveClosed(new State(80)
            .ions("Na+1", mb(200)).ions("NO3-1", mb(200))
            .ions("K+1", mb(200)).ions("Cl-1", mb(200)).water(mb(1000))).state;
        assertEquals(mb(0), hot.sedimentAmount("chemicaladdon:potassium_nitrate"), "hot: all dissolved");
        State cool = new State(0)
            .ions("Na+1", mb(200)).ions("NO3-1", mb(200))
            .ions("K+1", mb(200)).ions("Cl-1", mb(200)).water(mb(1000));
        for (int i = 0; i < 30; i++) cool = e.solveClosed(cool).state;
        long sed = cool.sedimentAmount("chemicaladdon:potassium_nitrate");
        assertTrue(sed >= mb(55) && sed <= mb(80), "KNO3 crystallises on cooling (~67): " + cool);
        assertTrue(cool.ionAmount("K+1") >= mb(125) && cool.ionAmount("K+1") <= mb(140),
            "K+ left at the 0C curve (~133): " + cool);
        assertEquals(mb(0), cool.sedimentAmount("chemicaladdon:rock_salt"), "NaCl stays dissolved: " + cool);
        assertEquals(mb(0), cool.netCharge());
    }

    @Test void aluminiumHydroxideByCarbonationOfAluminate() {
        // Bayer separation step: 2[Al(OH)4]- + CO2 -> 2Al(OH)3 + CO3-2 + H2O
        State s = e.solveClosed(new State(20)
            .ions("Na+1", mb(100)).ions("[Al(OH)4]-1", mb(100))
            .molecule("chemicaladdon:carbon_dioxide", mb(60)).water(mb(1000))).state;
        assertTrue(s.suspendedAmount("chemicaladdon:aluminium_hydroxide") >= mb(90),
            "carbonation precipitates Al(OH)3: " + s);
        assertTrue(s.ionAmount("[Al(OH)4]-1") < mb(10), "aluminate consumed: " + s);
        assertEquals(mb(60),
            s.ionAmount("CO3-2") + s.ionAmount("HCO3-1")
                + s.moleculeAmount("chemicaladdon:carbon_dioxide"),
            "carbon conserved (CO3/HCO3/CO2): " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void gypsumPrecipitatesAtItsKsp() {
        // CaSO4·2H2O Ksp = 10^-4.58 (phreeqc): 100 mB each -> ~99.5 mB precipitate,
        // residual [Ca]=[SO4]=0.51 mB (exactly (Ksp)^(1/2) in the 1000 mB volume)
        State s = e.solveClosed(new State(20)
            .ions("Ca+2", mb(100)).ions("SO4-2", mb(100)).water(mb(1000))).state;
        assertTrue(s.suspendedAmount("chemicaladdon:gypsum") >= mb(90), "gypsum precipitates: " + s);
        assertTrue(s.ionAmount("Ca+2") < mb(5), "calcium fixed as gypsum: " + s);
        assertEquals(s.ionAmount("Ca+2"), s.ionAmount("SO4-2"), "stoichiometric residual: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void calciumSulfiteByFlueGasDesulphurisation() {
        // FGD: Ca(OH)2 + SO2 -> CaSO3 + H2O (SO2 dissolves, HSO3-/SO3-2 feed the solid)
        State s = e.solveClosed(new State(20)
            .ions("Ca+2", mb(100)).ions("OH-1", mb(200))
            .molecule("chemicaladdon:sulfur_dioxide", mb(100)).water(mb(1000))).state;
        assertTrue(s.suspendedAmount("chemicaladdon:calcium_sulfite") >= mb(90),
            "CaSO3 precipitates: " + s);
        assertTrue(s.moleculeAmount("chemicaladdon:sulfur_dioxide") < mb(1), "SO2 absorbed: " + s);
        assertTrue(s.ionAmount("Ca+2") < mb(5), "calcium fixed: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void zincHydroxideAmphotericDissolutionScalesWithBase() {
        // Zn(OH)2 + 2OH- ⇌ [Zn(OH)4]-2 (logK -1.6, phreeqc-derived): weak amphoteric
        // dissolution that must strengthen as [OH-] grows (1 M vs 2 M NaOH)
        State weak = e.solveClosed(new State(20)
            .suspended("chemicaladdon:zinc_hydroxide", mb(50))
            .ions("Na+1", mb(1000)).ions("OH-1", mb(1000)).water(mb(1000))).state;
        State strong = e.solveClosed(new State(20)
            .suspended("chemicaladdon:zinc_hydroxide", mb(50))
            .ions("Na+1", mb(2000)).ions("OH-1", mb(2000)).water(mb(1000))).state;
        long z1 = weak.ionAmount("[Zn(OH)4]-2");
        long z2 = strong.ionAmount("[Zn(OH)4]-2");
        assertTrue(z1 > mb(1) / 10, "zincate forms in strong base: " + weak);
        assertTrue(z2 > z1, "more base dissolves more Zn(OH)2: " + z1 + " vs " + z2);
        assertTrue(weak.suspendedAmount("chemicaladdon:zinc_hydroxide") > mb(40),
            "Zn(OH)2 not fully dissolved (weak amphoterism): " + weak);
        assertEquals(mb(0), weak.netCharge());
        assertEquals(mb(0), strong.netCharge());
    }

    @Test void sodiumHypochloriteFromChlorineAndCaustic() {
        // Cl2 + 2NaOH -> NaCl + NaClO + H2O (alkaline disproportionation, logK 15.3)
        State s = e.solveClosed(new State(20)
            .molecule("chemicaladdon:chlorine", mb(100))
            .ions("Na+1", mb(200)).ions("OH-1", mb(200)).water(mb(1000))).state;
        assertTrue(s.ionAmount("ClO-1") >= mb(90), "hypochlorite forms: " + s);
        assertTrue(s.ionAmount("Cl-1") >= mb(90), "chloride coproduct: " + s);
        assertTrue(s.moleculeAmount("chemicaladdon:chlorine") < mb(1), "chlorine consumed: " + s);
        assertTrue(s.ionAmount("OH-1") < mb(20), "caustic consumed: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void potassiumThiocyanateByAmmoniumExchange() {
        // NH4SCN + KOH -> KSCN + NH3 + H2O: the NH4+/OH- neutralisation drives the
        // exchange (NH3 leaves), KSCN stays in solution
        State s = e.solveClosed(new State(20)
            .ions("NH4+1", mb(100)).ions("SCN-1", mb(100))
            .ions("K+1", mb(100)).ions("OH-1", mb(100)).water(mb(1000))).state;
        assertTrue(s.moleculeAmount("chemicaladdon:ammonia") >= mb(90), "NH3 released: " + s);
        assertEquals(mb(100), s.ionAmount("SCN-1"), "thiocyanate conserved (KSCN in solution): " + s);
        assertEquals(mb(100), s.ionAmount("K+1"), "potassium conserved: " + s);
        assertTrue(s.ionAmount("NH4+1") < mb(10), "ammonium consumed: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void oxygenFromHydrogenPeroxideDecomposition() {
        // 2H2O2 -> 2H2O + O2 (logK 20.9, complete): 100 mB H2O2 yields 50 mB O2
        State s = e.solveClosed(new State(20)
            .molecule("chemicaladdon:hydrogen_peroxide", mb(100)).water(mb(1000))).state;
        assertTrue(s.moleculeAmount("chemicaladdon:oxygen") >= mb(45),
            "O2 evolved (stoichiometric 50): " + s);
        assertTrue(s.moleculeAmount("chemicaladdon:hydrogen_peroxide") < mb(1),
            "H2O2 decomposed: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void sulfurDioxideFromSulfiteAndAcid() {
        // lab prep: Na2SO3 + 2HCl -> 2NaCl + SO2 + H2O; SO2 + H2O ⇌ H+ + HSO3-
        // (pKa1 1.81) leaves a measurable HSO3- fraction at this pH
        State s = e.solveClosed(new State(20)
            .ions("Na+1", mb(200)).ions("SO3-2", mb(100))
            .ions("H+1", mb(200)).ions("Cl-1", mb(200)).water(mb(1000))).state;
        assertTrue(s.moleculeAmount("chemicaladdon:sulfur_dioxide") >= mb(55),
            "SO2 evolved: " + s);
        assertEquals(mb(100),
            s.moleculeAmount("chemicaladdon:sulfur_dioxide")
                + s.ionAmount("HSO3-1") + s.ionAmount("SO3-2"),
            "sulfur conserved (SO2/HSO3-/SO3-2): " + s);
        assertTrue(s.ionAmount("SO3-2") < mb(5), "sulfite protonated: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void superphosphateFromPhosphateRockAndSulfuricAcid() {
        // Ca3(PO4)2 + 2H2SO4 -> Ca(H2PO4)2 + 2CaSO4: the apatite dissolves in acid,
        // the phosphate fully protonates to water-soluble H2PO4-, and the calcium is
        // fixed as gypsum — exactly the industrial superphosphate (single superphosphate)
        State s = e.solveClosed(new State(20)
            .suspended("chemicaladdon:calcium_phosphate", mb(50))
            .ions("H+1", mb(300)).ions("SO4-2", mb(150)).water(mb(1000))).state;
        assertTrue(s.ionAmount("H2PO4-1") >= mb(90), "phosphate becomes soluble H2PO4-: " + s);
        assertTrue(s.suspendedAmount("chemicaladdon:gypsum") >= mb(140),
            "calcium fixed as gypsum: " + s);
        assertEquals(mb(100),
            s.ionAmount("H2PO4-1") + s.ionAmount("HPO4-2") + s.ionAmount("PO4-3"),
            "phosphorus conserved: " + s);
        assertTrue(s.ionAmount("PO4-3") < mb(1), "phosphate protonated: " + s);
        assertEquals(mb(150), s.ionAmount("Ca+2") + s.suspendedAmount("chemicaladdon:gypsum"),
            "calcium conserved: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void bleachingPowderMotherLiquor() {
        // 2Cl2 + 2Ca(OH)2 -> Ca(ClO)2 + CaCl2 + 2H2O: the alkaline disproportionation
        // yields hypochlorite + chloride; both calcium salts are soluble so Ca2+ stays
        // in solution (the "mother liquor" of bleaching-powder manufacture)
        State s = e.solveClosed(new State(20)
            .ions("Ca+2", mb(100)).ions("OH-1", mb(200))
            .molecule("chemicaladdon:chlorine", mb(100)).water(mb(1000))).state;
        assertTrue(s.ionAmount("ClO-1") >= mb(90), "hypochlorite forms: " + s);
        assertTrue(s.ionAmount("Cl-1") >= mb(90), "chloride coproduct: " + s);
        assertEquals(mb(100), s.ionAmount("Ca+2"), "calcium conserved (soluble salts): " + s);
        assertTrue(s.moleculeAmount("chemicaladdon:chlorine") < mb(1), "chlorine consumed: " + s);
        assertTrue(s.ionAmount("OH-1") < mb(20), "lime consumed: " + s);
        assertEquals(mb(0), s.netCharge());
    }
}
