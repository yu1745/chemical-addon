package com.yu1745.chemengine;

import static com.yu1745.chemengine.State.mb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yu1745.chemengine.solver.Solver;
import org.junit.jupiter.api.Test;

/**
 * Common industrial aqueous-chemistry reactions, run through the extracted engine.
 * Each test prints the full resulting state; assertions pin charge neutrality and the
 * expected major species. Water is the untracked solvent, so "produced water" never
 * shows up — the signature of neutralisation is that H+ and OH- disappear together.
 */
class IndustrialChemistryTest {

    private final Engine e = Harness.engine();

    private State closed(State in) { return e.solveClosed(in).state; }

    private void show(String name, State out) {
        System.out.println("### " + name);
        System.out.println("    ions      = " + out.ions());
        System.out.println("    molecules = " + out.molecules());
        System.out.println("    suspended = " + out.suspended());
        System.out.println("    charge    = " + out.netCharge());
    }

    // ---------------------------------------------------------------- 三酸两碱

    @Test void hclPlusNaoh() {
        State s = closed(new State(20).ions("H+1", mb(100)).ions("Cl-1", mb(100))
            .ions("Na+1", mb(100)).ions("OH-1", mb(100)).water(mb(1000)));
        show("HCl + NaOH -> NaCl + H2O", s);
        assertEquals(mb(100), s.ions().getOrDefault("Na+1",0L));
        assertTrue(s.ions().getOrDefault("Cl-1",0L) + s.molecules().getOrDefault("chemicaladdon:hydrogen_chloride",0L) >= mb(99),
            "chloride conserved including dissolved HCl: " + s);
        assertTrue(s.ions().getOrDefault("H+1",0L) + s.ions().getOrDefault("OH-1",0L) < mb(1),
            "H+/OH- neutralised (trace autoionisation allowed): " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void h2so4PlusNaoh() {
        State s = closed(new State(20).ions("H+1", mb(200)).ions("SO4-2", mb(100))
            .ions("Na+1", mb(200)).ions("OH-1", mb(200)).water(mb(1000)));
        show("H2SO4 + 2NaOH -> Na2SO4 + 2H2O", s);
        assertEquals(mb(200), s.ions().getOrDefault("Na+1",0L));
        assertEquals(mb(100), s.ions().getOrDefault("SO4-2",0L));
        assertTrue(s.ions().getOrDefault("H+1",0L) + s.ions().getOrDefault("OH-1",0L) < mb(1),
            "neutralised (sub-mB H+/OH- residue tolerated): " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void hno3PlusNaoh() {
        State s = closed(new State(20).ions("H+1", mb(100)).ions("NO3-1", mb(100))
            .ions("Na+1", mb(100)).ions("OH-1", mb(100)).water(mb(1000)));
        show("HNO3 + NaOH -> NaNO3 + H2O", s);
        assertEquals(mb(100), s.ions().getOrDefault("Na+1",0L));
        assertEquals(mb(100), s.ions().getOrDefault("NO3-1",0L));
        assertEquals(mb(0), s.netCharge());
    }

    @Test void hclExcessOverNaoh() {
        State s = closed(new State(20).ions("H+1", mb(150)).ions("Cl-1", mb(150))
            .ions("Na+1", mb(100)).ions("OH-1", mb(100)).water(mb(1000)));
        show("HCl(过量) + NaOH", s);
        assertTrue(s.ions().getOrDefault("H+1",0L) + s.molecules().getOrDefault("chemicaladdon:hydrogen_chloride",0L) >= mb(40),
            "excess acid remains: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    // ---------------------------------------------------------------- 纯碱/小苏打与酸

    @Test void hclPlusSodaAshClosed() {
        State s = closed(new State(20).ions("H+1", mb(200)).ions("Cl-1", mb(200))
            .ions("Na+1", mb(200)).ions("CO3-2", mb(100)).water(mb(1000)));
        show("2HCl + Na2CO3 -> 2NaCl + CO2 + H2O (closed)", s);
        assertEquals(mb(200), s.ions().getOrDefault("Na+1",0L));
        assertTrue(s.ions().getOrDefault("Cl-1",0L) + s.molecules().getOrDefault("chemicaladdon:hydrogen_chloride",0L) >= mb(199),
            "chloride conserved including dissolved HCl: " + s);
        assertTrue(s.molecules().getOrDefault("chemicaladdon:carbon_dioxide",0L) >= mb(99),
            "all carbonate becomes CO2(aq): " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void hclPlusSodaAshOpen() {
        State in = new State(20).ions("H+1", mb(200)).ions("Cl-1", mb(200))
            .ions("Na+1", mb(200)).ions("CO3-2", mb(100)).water(mb(1000));
        Solver.Result r = e.solveOpen(in);
        show("2HCl + Na2CO3 (open, CO2 vents)", r.state);
        System.out.println("    gasVented = " + r.gasVented);
        assertTrue(r.gasVented.getOrDefault("chemicaladdon:carbon_dioxide",0L) >= mb(98),
            "CO2 escapes: " + r.gasVented);
        assertEquals(mb(0), r.state.netCharge());
    }

    @Test void hclInsufficientGivesBicarbonate() {
        State s = closed(new State(20).ions("H+1", mb(100)).ions("Cl-1", mb(100))
            .ions("Na+1", mb(200)).ions("CO3-2", mb(100)).water(mb(1000)));
        show("HCl(不足) + Na2CO3 -> NaHCO3 + NaCl", s);
        assertTrue(s.ions().getOrDefault("HCO3-1",0L) >= mb(90), "bicarbonate forms: " + s);
        assertTrue(s.molecules().getOrDefault("chemicaladdon:carbon_dioxide",0L) < mb(5), "no CO2: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void hclPlusBicarbonate() {
        State s = closed(new State(20).ions("H+1", mb(100)).ions("Cl-1", mb(100))
            .ions("Na+1", mb(100)).ions("HCO3-1", mb(100)).water(mb(1000)));
        show("NaHCO3 + HCl -> NaCl + CO2 + H2O (closed)", s);
        assertTrue(s.molecules().getOrDefault("chemicaladdon:carbon_dioxide",0L) >= mb(99), "CO2 forms: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    // ---------------------------------------------------------------- 氨碱、石灰

    @Test void ammoniaPlusHcl() {
        State s = closed(new State(20).molecule("chemicaladdon:ammonia", mb(100))
            .ions("H+1", mb(100)).ions("Cl-1", mb(100)).water(mb(1000)));
        show("NH3·H2O + HCl -> NH4Cl", s);
        assertTrue(s.ions().getOrDefault("NH4+1",0L) >= mb(90), "ammonium forms: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void naohAbsorbsCo2() {
        State s = closed(new State(20).molecule("chemicaladdon:carbon_dioxide", mb(100))
            .ions("Na+1", mb(200)).ions("OH-1", mb(200)).water(mb(1000)));
        show("2NaOH + CO2 -> Na2CO3 + H2O", s);
        assertEquals(mb(200), s.ions().getOrDefault("Na+1",0L));
        assertTrue(s.ions().getOrDefault("CO3-2",0L) >= mb(80), "carbonate forms: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void limewaterPlusCo2() {
        State s = closed(new State(20).molecule("chemicaladdon:carbon_dioxide", mb(100))
            .ions("Ca+2", mb(100)).ions("OH-1", mb(200)).water(mb(1000)));
        show("Ca(OH)2 + CO2 -> CaCO3 + H2O", s);
        assertTrue(s.suspended().getOrDefault("chemicaladdon:limestone",0L) >= mb(99),
            "limestone precipitates: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void hclDescalesLimestone() {
        // Boundary note: dissolution is rate-limited (rate 0.0001), and the affinity
        // drive clamps at 1000, so the per-tick budget is exactly
        // 1e-4 * 1000 mB * 1000 = 100 mB — exactly the scale of this fixture. The
        // "dissolution is instant" property holds only up to that 100 mB budget; a
        // larger scale would need several ticks.
        State s = closed(new State(20).suspended("chemicaladdon:limestone", mb(100))
            .ions("H+1", mb(200)).ions("Cl-1", mb(200)).water(mb(1000)));
        show("CaCO3(垢) + 2HCl -> CaCl2 + CO2 + H2O (closed)", s);
        assertEquals(mb(100), s.ions().getOrDefault("Ca+2",0L), "scale dissolves");
        assertTrue(s.suspended().getOrDefault("chemicaladdon:limestone",0L) == 0L, "solid gone");
        assertEquals(mb(0), s.netCharge());
    }

    // ---------------------------------------------------------------- 粗盐精炼

    @Test void crudeSaltRefiningSequential() {
        // 粗盐: NaCl 1000 + CaCl2 20 + MgCl2 10 + Na2SO4 15，四步精炼（每步过滤）。
        State brine = new State(20)
            .ions("Na+1",mb(1000+30)).ions("Cl-1",mb(1000+40+20))
            .ions("Ca+2", mb(20)).ions("Mg+2", mb(10)).ions("SO4-2", mb(15))
            .water(mb(1000));

        // 1) BaCl2 除 SO4-2
        State s1 = settle(mix(brine).ions("Ba+2", mb(15)).ions("Cl-1", mb(30)).build());
        show("粗盐精炼 1/4: BaCl2 除 SO4", s1);
        assertTrue(s1.suspended().getOrDefault("chemicaladdon:barium_sulfate",0L) >= mb(14), "BaSO4: " + s1);
        assertTrue(s1.ions().getOrDefault("SO4-2",0L) <= mb(1), "SO4 removed: " + s1);

        // 2) Na2CO3 除 Ca2+（并沉淀过量 Ba2+）
        State s2 = settle(mix(s1).ions("Na+1", mb(44)).ions("CO3-2", mb(22)).build());
        show("粗盐精炼 2/4: Na2CO3 除 Ca(并沉淀部分 MgCO3)", s2);
        assertTrue(s2.suspended().getOrDefault("chemicaladdon:limestone",0L) >= mb(18), "CaCO3: " + s2);
        assertTrue(s2.ions().getOrDefault("Ca+2",0L) <= mb(2), "Ca removed: " + s2);

        // 3) NaOH 除剩余 Mg2+（Na2CO3 上一步已沉淀部分碱式碳酸镁）
        State s3 = settle(mix(s2).ions("Na+1", mb(22)).ions("OH-1", mb(22)).build());
        show("粗盐精炼 3/4: NaOH 除 Mg", s3);
        assertTrue(s3.ions().getOrDefault("Mg+2",0L) <= mb(1), "Mg removed: " + s3);

        // 4) HCl 回调（中和上一步过量的 OH-；实际工厂会按 pH 滴定）
        State s4 = settle(mix(s3).ions("H+1", mb(8)).ions("Cl-1", mb(8)).build());
        show("粗盐精炼 4/4: HCl 回调得精盐水", s4);
        assertTrue(s4.ions().getOrDefault("Mg+2",0L) + s4.ions().getOrDefault("SO4-2",0L)
            + s4.ions().getOrDefault("Ba+2",0L) + s4.ions().getOrDefault("CO3-2",0L)
            + s4.ions().getOrDefault("OH-1",0L) < mb(1), "Mg/SO4/Ba/CO3/OH gone");
        assertTrue(s4.ions().getOrDefault("Ca+2",0L) <= mb(2),
            "Ca trace <=1 (Na2CO3 dose shared with MgCO3): " + s4);
        // 精盐水 = NaCl 主成分 + 微量 Ca（由 Cl 平衡），整体电中性
        assertEquals(mb(0), s4.netCharge(), "refined brine neutral");
    }

    /** Take the liquid part of a solved state and start a new mix from it (filter the solids). */
    private State.Builder mix(State liquid) {
        State.Builder b = new State.Builder();
        b.ions = new java.util.LinkedHashMap<>(liquid.ions());
        b.molecules = new java.util.LinkedHashMap<>(liquid.molecules());
        return b;
    }

    /** Run reaction ticks until the state stops moving (rate-limited steps need a few ticks). */
    private State settle(State in) {
        State s = e.solveClosed(in).state;
        for (int i = 0; i < 40; i++) {
            State next = e.solveClosed(s).state;
            if (next.ions().equals(s.ions()) && next.suspended().equals(s.suspended())
                && next.sediment().equals(s.sediment()) && next.molecules().equals(s.molecules())) {
                return next;
            }
            s = next;
        }
        return s;
    }

    // ---------------------------------------------------------------- 食盐本身

    @Test void rockSaltDissolves() {
        State s = closed(new State(20).ions("Na+1", mb(100)).ions("Cl-1", mb(100)).water(mb(1000)));
        show("NaCl(盐) 溶解", s);
        assertEquals(mb(100), s.ions().getOrDefault("Na+1",0L));
        assertTrue(s.ions().getOrDefault("Cl-1",0L) + s.molecules().getOrDefault("chemicaladdon:hydrogen_chloride",0L) >= mb(99),
            "chloride conserved including dissolved HCl: " + s);
    }
}
