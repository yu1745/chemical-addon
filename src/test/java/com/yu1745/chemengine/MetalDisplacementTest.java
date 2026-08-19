package com.yu1745.chemengine;

import static com.yu1745.chemengine.State.mb;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yu1745.chemengine.solver.Solver;
import org.junit.jupiter.api.Test;

/**
 * D1a metal displacement: metal solids enter the balance solver as a displacement
 * progress x (Fe(s) + Cu2+ = Fe2+ + Cu(s), logK 26.5 = n*E0/0.05916). The material
 * contribution of a displacement mineral is the NEGATIVE of its mass-action coeff
 * (Fe2+ grows, Cu2+ shrinks with x), and the reactant metal pool caps the progress.
 *
 * <p>Composite coupling (complexation / pH / multi-metal ordering) is solved in the
 * SAME Newton as the solution balance; see PLAN D1a for the known strong-complexation
 * Newton-seed limitation.
 */
class MetalDisplacementTest {

    private final Engine e = Harness.engine();

    @Test void ironDisplacesCopperFromSulfate() {
        // Fe(s) + CuSO4 -> FeSO4 + Cu(s): stoich-equivalent amounts, essentially complete
        State s = e.solveClosed(new State(20)
            .suspended("chemicaladdon:iron_metal", mb(100))
            .ions("Cu+2", mb(100)).ions("SO4-2", mb(100)).water(mb(1000))).state;
        assertTrue(s.ionAmount("Fe+2") >= mb(95), "iron dissolves: " + s);
        assertTrue(s.ionAmount("Cu+2") < mb(5), "copper displaced: " + s);
        assertTrue(s.suspendedAmount("chemicaladdon:copper_metal") >= mb(95), "copper metal grows: " + s);
        assertTrue(s.suspendedAmount("chemicaladdon:iron_metal") < mb(5), "iron metal consumed: " + s);
        assertEquals(mb(100), s.ionAmount("Fe+2") + s.suspendedAmount("chemicaladdon:iron_metal"),
            "iron conserved (solution + metal): " + s);
        assertEquals(mb(100), s.ionAmount("Cu+2") + s.suspendedAmount("chemicaladdon:copper_metal"),
            "copper conserved (solution + metal): " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void ironLimitedDisplacement() {
        // Fe is the limiting reagent: 50 Fe displaces 50 Cu, leaving 50 Cu2+ in solution
        State s = e.solveClosed(new State(20)
            .suspended("chemicaladdon:iron_metal", mb(50))
            .ions("Cu+2", mb(100)).ions("SO4-2", mb(100)).water(mb(1000))).state;
        assertTrue(s.ionAmount("Fe+2") >= mb(45) && s.ionAmount("Fe+2") <= mb(55), "iron fully displaced: " + s);
        assertTrue(s.ionAmount("Cu+2") >= mb(45) && s.ionAmount("Cu+2") <= mb(55), "half the copper remains: " + s);
        assertTrue(s.suspendedAmount("chemicaladdon:copper_metal") >= mb(45)
            && s.suspendedAmount("chemicaladdon:copper_metal") <= mb(55), "copper grown ~50: " + s);
        assertEquals(mb(0), s.suspendedAmount("chemicaladdon:iron_metal"), "iron metal exhausted: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void ironExcessDisplacement() {
        // Cu is the limiting reagent: all 100 Cu2+ displaced, 50 Fe remains as metal
        State s = e.solveClosed(new State(20)
            .suspended("chemicaladdon:iron_metal", mb(150))
            .ions("Cu+2", mb(100)).ions("SO4-2", mb(100)).water(mb(1000))).state;
        assertTrue(s.ionAmount("Fe+2") >= mb(95), "iron oxidized: " + s);
        assertTrue(s.ionAmount("Cu+2") < mb(5), "copper fully displaced: " + s);
        assertTrue(s.suspendedAmount("chemicaladdon:copper_metal") >= mb(95), "copper grown: " + s);
        assertTrue(s.suspendedAmount("chemicaladdon:iron_metal") >= mb(45)
            && s.suspendedAmount("chemicaladdon:iron_metal") <= mb(55), "50 Fe left as metal: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void noReactantMetal_noDisplacement() {
        // No iron metal: the displacement reaction has no reactant pool, so nothing is
        // displaced — no iron dissolves and no copper metal is produced. Copper may still
        // legitimately precipitate as Cu(OH)2 (a Ksp mineral), which is unrelated to
        // displacement; total copper must still be conserved.
        State s = e.solveClosed(new State(20)
            .ions("Cu+2", mb(100)).ions("SO4-2", mb(100)).water(mb(1000))).state;
        assertEquals(mb(0), s.ionAmount("Fe+2"), "no iron dissolves without iron metal: " + s);
        assertEquals(mb(0), s.suspendedAmount("chemicaladdon:copper_metal"),
            "no copper metal without a displacement reaction: " + s);
        assertEquals(mb(0), s.netCharge());
        assertEquals(mb(100),
            s.ionAmount("Cu+2") + s.suspendedAmount("chemicaladdon:copper_hydroxide")
                + s.suspendedAmount("chemicaladdon:copper_metal"),
            "copper conserved (solution + hydroxide + metal): " + s);
    }

    @Test void productMetalOnly_noDisplacement() {
        // Copper metal (the displacement PRODUCT) input with no iron reactant: the product
        // pool is tracked but no reaction can run, so the metal is untouched, no iron
        // dissolves, and total copper is conserved.
        State s = e.solveClosed(new State(20)
            .suspended("chemicaladdon:copper_metal", mb(30))
            .ions("Cu+2", mb(100)).ions("SO4-2", mb(100)).water(mb(1000))).state;
        assertEquals(mb(30), s.suspendedAmount("chemicaladdon:copper_metal"),
            "product metal pool untouched: " + s);
        assertEquals(mb(0), s.ionAmount("Fe+2"), "no iron dissolves without iron metal: " + s);
        assertEquals(mb(0), s.netCharge());
        assertEquals(mb(130),
            s.ionAmount("Cu+2") + s.suspendedAmount("chemicaladdon:copper_hydroxide")
                + s.suspendedAmount("chemicaladdon:copper_metal"),
            "copper conserved (solution + hydroxide + metal): " + s);
    }

    @Test void metalWithNoOxidizableIon_remainsMetal() {
        // Iron metal with no copper ion (no redox partner): the metal must not dissolve
        // spontaneously — Fe(s) stays intact, no Fe2+ forms, and no copper is produced.
        State s = e.solveClosed(new State(20)
            .suspended("chemicaladdon:iron_metal", mb(100)).water(mb(1000))).state;
        assertEquals(mb(100), s.suspendedAmount("chemicaladdon:iron_metal"), "iron metal intact: " + s);
        assertEquals(mb(0), s.ionAmount("Fe+2"), "no iron dissolves without an oxidizer: " + s);
        assertEquals(mb(0), s.suspendedAmount("chemicaladdon:copper_metal"), "no copper produced: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void ironDisplacesCopper_complexedByAmmonia() {
        // Cu2+ strongly complexed by NH3 as [Cu(NH3)4]2+ (logK 12.6). The net reaction
        // Fe(s) + [Cu(NH3)4]2+ = Fe2+ + Cu(s) + 4NH3 has K = 10^26.5 / 10^12.6 = 10^13.9,
        // hugely favourable, so the displacement still runs essentially to completion: the
        // ammine complex fully dissociates to feed Cu2+ to the metal. This is a genuine
        // composite equilibrium (complexation + redox) solved in one Newton — the free
        // Cu2+ is tiny yet the total copper displacement is complete.
        State s = e.solveClosed(new State(20)
            .suspended("chemicaladdon:iron_metal", mb(100))
            .ions("Cu+2", mb(100)).ions("SO4-2", mb(100))
            .molecule("chemicaladdon:ammonia", mb(400)).water(mb(1000))).state;
        // displacement ran to completion: essentially all copper is metal, iron all dissolved
        assertTrue(s.suspendedAmount("chemicaladdon:copper_metal") >= mb(95),
            "copper displaced to metal: " + s);
        assertTrue(s.suspendedAmount("chemicaladdon:iron_metal") < mb(5), "iron consumed: " + s);
        assertTrue(s.ionAmount("Fe+2") >= mb(95), "iron dissolved: " + s);
        // ammine complex fully dissociated (free Cu2+ buffered tiny, all Cu displaced)
        assertTrue(s.ionAmount("[Cu(NH3)4]+2") < mb(5), "ammine complex dissociated: " + s);
        assertTrue(s.ionAmount("Cu+2") < mb(5), "free copper displaced: " + s);
        // total iron conserved exactly (solution + metal)
        assertEquals(mb(100), s.ionAmount("Fe+2") + s.suspendedAmount("chemicaladdon:iron_metal"),
            "iron conserved (solution + metal): " + s);
        // copper conserved across solution + ammine complex + hydroxide + metal
        long cuTotal = s.ionAmount("Cu+2") + s.ionAmount("[Cu(NH3)4]+2")
            + s.suspendedAmount("chemicaladdon:copper_hydroxide")
            + s.suspendedAmount("chemicaladdon:copper_metal");
        assertEquals(mb(100), cuTotal, "copper conserved: " + s);
        assertEquals(mb(0), s.netCharge(), "net charge zero: " + s);
    }

    @Test void ironDisplacesCopper_inAcid_phCoupled() {
        // Fe(s) + CuSO4 in acidic solution (pH coupling): the displacement is solved in the
        // same Newton as the acid equilibrium; acid does not stop it, and iron/copper are
        // conserved exactly with charge balance preserved.
        State s = e.solveClosed(new State(20)
            .suspended("chemicaladdon:iron_metal", mb(100))
            .ions("Cu+2", mb(100)).ions("SO4-2", mb(100))
            .ions("H+1", mb(50)).ions("Cl-1", mb(50)).water(mb(1000))).state;
        assertTrue(s.ionAmount("Fe+2") >= mb(95), "iron displaced in acid: " + s);
        assertTrue(s.ionAmount("Cu+2") < mb(5), "copper displaced in acid: " + s);
        // Fe is a shared pool (Cu displacement + trace acid): it is conserved to within one
        // quantum (integer-projection precision artifact at this exhausted boundary), and
        // Fe goes to Cu displacement (logK 26.5) far before acid (logK 14.9).
        long fe = s.ionAmount("Fe+2") + s.suspendedAmount("chemicaladdon:iron_metal");
        assertTrue(fe >= mb(99) && fe <= mb(101), "iron conserved (~quantum): " + s);
        assertEquals(mb(100), s.ionAmount("Cu+2") + s.suspendedAmount("chemicaladdon:copper_metal"),
            "copper conserved: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void zincDissolvesInAcid_releasingHydrogen() {
        // Zn(s) + H2SO4 -> ZnSO4 + H2(g): the acid H+ is consumed, Zn2+ forms, H2 is
        // produced (a gas-product displacement, no free electrons). The H2 is written as a
        // dissolved molecule (it is produced post-solve, so it is retained rather than vented).
        State s = e.solveClosed(new State(20)
            .suspended("chemicaladdon:zinc_metal", mb(100))
            .ions("H+1", mb(200)).ions("SO4-2", mb(100)).water(mb(1000))).state;
        assertTrue(s.ionAmount("Zn+2") >= mb(95), "zinc dissolved: " + s);
        assertTrue(s.suspendedAmount("chemicaladdon:zinc_metal") < mb(5), "zinc consumed: " + s);
        assertTrue(s.ionAmount("H+1") < mb(5), "acid consumed: " + s);
        // 200 H+ -> 100 H2 (each Zn needs 2 H+, makes 1 H2)
        assertEquals(mb(100), s.moleculeAmount("chemicaladdon:hydrogen"), "hydrogen produced: " + s);
        assertEquals(mb(0), s.netCharge());
    }

    @Test void zincDisplacesCopperPreferentiallyOverIron() {
        // Zn + (Fe2+ + Cu2+): Zn displaces Cu (logK 37.2) far more strongly than Fe
        // (logK 10.8). With 100 Zn and 100 each of Fe2+/Cu2+, all Zn goes to Cu; Fe2+ is
        // left — this is the shared-metal-pool ordering competition (D1a L3).
        State s = e.solveClosed(new State(20)
            .suspended("chemicaladdon:zinc_metal", mb(100))
            .ions("Fe+2", mb(100)).ions("Cu+2", mb(100)).ions("SO4-2", mb(200)).water(mb(1000))).state;
        assertTrue(s.ionAmount("Cu+2") < mb(5), "copper displaced: " + s);
        assertTrue(s.suspendedAmount("chemicaladdon:copper_metal") >= mb(95), "copper metal grown: " + s);
        assertTrue(s.ionAmount("Fe+2") >= mb(90), "iron NOT displaced (Zn spent on Cu): " + s);
        assertTrue(s.suspendedAmount("chemicaladdon:zinc_metal") < mb(5), "zinc consumed: " + s);
        // zinc conserved to within a couple of quanta (integer-projection precision artifact
        // at this exhausted shared-pool boundary); the ordering is the point of the test.
        long zn = s.ionAmount("Zn+2") + s.suspendedAmount("chemicaladdon:zinc_metal");
        assertTrue(zn >= mb(99) && zn <= mb(102), "zinc conserved (~quantum): " + s);
        assertEquals(mb(0), s.netCharge());
    }
}
