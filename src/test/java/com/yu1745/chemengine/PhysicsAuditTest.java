package com.yu1745.chemengine;

import static com.yu1745.chemengine.State.mb;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yu1745.chemengine.solver.Solver;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Physics-consistency regression guard: runs the historically problematic scenarios
 * with the solver's internal audit enabled and asserts the projected states satisfy
 * the audit invariants (charge neutrality, mass action for non-rate-limited species,
 * mineral saturation). This catches the "conservation-exact but thermodynamically
 * garbage" class of bug (e.g. the NO2 absorption state that used to carry H+ =
 * thousands of times the input while every conservation test stayed green).
 *
 * <p>The audit itself skips quantum-limited states (any participating species under
 * 1 mB), where sub-quantum equilibria legitimately round to 0/1 quanta. In practice
 * this skips nearly every mass-action check (H+ is sub-mB at the equilibrium pH of
 * most scenarios), so the audit is mostly a charge-neutrality guard; the scenarios
 * that DO exercise mass action are the strongly acidic ones (e.g. acid-excess with
 * dissolved HCl). {@link Solver#auditChecksRun} reports how many checks actually ran
 * and {@link #solveClosedChecked} requires it to be non-zero for the scenarios that
 * must genuinely exercise mass action — a 0-check "clean" result is not a physics
 * validation.
 *
 * <p>Note: the audit flag is a JVM-wide system property and Solver.auditViolations is a
 * static list; this class assumes the default sequential single-JVM test execution
 * (Gradle's default). If parallel execution is ever enabled, isolate the property and
 * the list per-thread first.
 */
class PhysicsAuditTest {

    private final Engine e = Harness.engine();

    @BeforeAll
    static void enableAudit() {
        System.setProperty("chemengine.audit", "true");
    }

    @AfterAll
    static void disableAudit() {
        System.clearProperty("chemengine.audit");
    }

    private State solve(State in) {
        return solveClosedChecked(in);
    }

    /** Solve one step and assert the audit log stayed clean (see class javadoc). */
    private State solveClosedChecked(State in) {
        return solveClosedChecked(in, 0);
    }

    /** Like {@link #solveClosedChecked(State)} but additionally requires at least
     *  {@code minChecks} mass-action checks to have actually run (guards vacuous
     *  "clean" passes in the quantum-limited regime). */
    private State solveClosedChecked(State in, int minChecks) {
        Solver.auditViolations.clear();
        Solver.auditChecksRun = 0;
        State out = e.solveClosed(in).state;
        assertTrue(Solver.auditViolations.isEmpty(),
            "physics violations: " + Solver.auditViolations);
        assertTrue(Solver.auditChecksRun >= minChecks,
            "expected at least " + minChecks + " mass-action checks, got " + Solver.auditChecksRun
                + " (quantum-limited skip made the audit vacuous): " + in);
        return out;
    }

    @Test
    void pureWaterIsClean() {
        solve(new State(20).water(mb(1000)));
    }

    @Test
    void causticSodaIsClean() {
        solve(new State(20).ions("Na+1", mb(300)).ions("OH-1", mb(300)).water(mb(1000)));
    }

    @Test
    void nitricAcidIsClean() {
        solve(new State(20).ions("H+1", mb(200)).ions("NO3-1", mb(200)).water(mb(1000)));
    }

    /** Acid-excess neutralisation leaves free H+ > 1 mB, so the HCl weak-electrolyte
     *  mass action is genuinely checkable (unlike near-neutral scenarios where the
     *  audit's 1 mB quantum threshold skips every check). */
    @Test
    void acidExcessWeakElectrolyteMassActionIsChecked() {
        State s = solveClosedChecked(new State(20).ions("H+1", mb(150)).ions("Cl-1", mb(150))
            .ions("Na+1", mb(100)).ions("OH-1", mb(100)).water(mb(1000)), 1);
        assertTrue(s.ions().getOrDefault("H+1", 0L) >= mb(40), "excess acid remains: " + s);
    }

    /** The fixed case: strongly non-linear Q^5 reaction at 20 C with a large input. */
    @Test
    void nitrogenDioxideAbsorptionIsCleanAtTwentyC() {
        State s = solve(new State(20)
            .molecule("chemicaladdon:nitrogen_dioxide", mb(100)).water(mb(1000)));
        // exact mass balance: 3 NO2 -> 2 H+ + 2 NO3- + NO, ~6 MB NO2 left
        assertTrue(s.ions().getOrDefault("H+1", 0L) > mb(60), "NO2 absorbs: " + s);
        assertTrue(s.molecules().getOrDefault("chemicaladdon:nitric_oxide", 0L) > mb(30), "NO forms: " + s);
    }

    @Test
    void aluminiumStoichiometricPrecipitationIsClean() {
        solve(new State(20)
            .ions("Al+3", mb(100)).ions("Cl-1", mb(300))
            .ions("Na+1", mb(300)).ions("OH-1", mb(300)).water(mb(1000)));
    }

    @Test
    void aluminiumExcessBaseIsClean() {
        solve(new State(20)
            .ions("Al+3", mb(100)).ions("Cl-1", mb(300))
            .ions("Na+1", mb(700)).ions("OH-1", mb(700)).water(mb(1000)));
    }

    @Test
    void malachiteProductionScaleIsClean() {
        solve(new State(20)
            .ions("Cu+2", mb(100)).ions("SO4-2", mb(100)).ions("Na+1", mb(400))
            .ions("CO3-2", mb(200)).water(mb(1000)));
    }

    /** Crude-salt refining: the multi-mineral phase-assembly scenario (4 steps). */
    @Test
    void crudeSaltRefiningStepsAreClean() {
        State brine = new State(20)
            .ions("Na+1", mb(1030)).ions("Cl-1", mb(1060))
            .ions("Ca+2", mb(20)).ions("Mg+2", mb(10)).ions("SO4-2", mb(15))
            .water(mb(1000));
        State s1 = settle(mix(brine).ions("Ba+2", mb(15)).ions("Cl-1", mb(30)).build());
        State s2 = settle(mix(s1).ions("Na+1", mb(44)).ions("CO3-2", mb(22)).build());
        State s3 = settle(mix(s2).ions("Na+1", mb(22)).ions("OH-1", mb(22)).build());
        settle(mix(s3).ions("H+1", mb(8)).ions("Cl-1", mb(8)).build());
        assertTrue(s3.ions().getOrDefault("Mg+2", 0L) <= mb(1), "Mg removed by step 3: " + s3);
    }

    /** Take the liquid part of a solved state and start a new mix from it. */
    private State.Builder mix(State liquid) {
        State.Builder b = new State.Builder();
        b.ions = new java.util.LinkedHashMap<>(liquid.ions());
        b.molecules = new java.util.LinkedHashMap<>(liquid.molecules());
        return b;
    }

    /** Run reaction ticks until the state stops moving (rate-limited steps need a few ticks);
     *  every tick is checked against the physics audit. */
    private State settle(State in) {
        State s = solveClosedChecked(in);
        for (int i = 0; i < 40; i++) {
            State next = solveClosedChecked(s);
            if (next.ions().equals(s.ions()) && next.suspended().equals(s.suspended())
                && next.sediment().equals(s.sediment()) && next.molecules().equals(s.molecules())) {
                return next;
            }
            s = next;
        }
        return s;
    }

    /** Limescale kinetics (rate-limited limestone): carbonate system must stay clean. */
    @Test
    void limescaleKineticTicksAreClean() {
        State s = new State(25).ions("Ca+2", mb(300)).ions("Cl-1", mb(300))
            .ions("Na+1", mb(300)).ions("CO3-2", mb(300)).water(mb(1000));
        for (int i = 0; i < 5; i++) {
            Solver.auditViolations.clear();
            State out = e.solveClosed(s).state;
            assertTrue(Solver.auditViolations.isEmpty(),
                "tick " + i + " physics violations: " + Solver.auditViolations);
            s = out;
        }
    }

    /** Redox couples: e- is a pseudo-species; its couples are skipped by the audit but
     *  the state must stay charge-neutral with the electron remainder written back. */
    @Test
    void redoxThreeCouplesAreClean() {
        com.google.gson.JsonObject fe = com.google.gson.JsonParser.parseString(
            "{\"formula\":\"FeCl3\",\"phase\":\"LIQUID\",\"ions\":[{\"ion\":\"Fe\",\"charge\":3,\"count\":1},{\"ion\":\"Cl\",\"charge\":-1,\"count\":3}],\"equilibria\":[{\"reaction\":\"Fe+3 + e- = Fe+2\",\"log_k\":13.0}]}"
        ).getAsJsonObject();
        com.google.gson.JsonObject cu = com.google.gson.JsonParser.parseString(
            "{\"formula\":\"CuCl2\",\"phase\":\"LIQUID\",\"ions\":[{\"ion\":\"Cu\",\"charge\":2,\"count\":1},{\"ion\":\"Cl\",\"charge\":-1,\"count\":2}],\"equilibria\":[{\"reaction\":\"Cu+2 + e- = Cu+1\",\"log_k\":6.0}]}"
        ).getAsJsonObject();
        com.google.gson.JsonObject ce = com.google.gson.JsonParser.parseString(
            "{\"formula\":\"CeCl4\",\"phase\":\"LIQUID\",\"ions\":[{\"ion\":\"Ce\",\"charge\":4,\"count\":1},{\"ion\":\"Cl\",\"charge\":-1,\"count\":4}],\"equilibria\":[{\"reaction\":\"Ce+4 + e- = Ce+3\",\"log_k\":15.0}]}"
        ).getAsJsonObject();
        SpeciesDatabase db = new SpeciesDatabase()
            .register(Species.parse("ferric_chloride", fe))
            .register(Species.parse("cupric_chloride", cu))
            .register(Species.parse("ceric_chloride", ce));
        Engine redox = Engine.from(db);
        Solver.auditViolations.clear();
        State out = redox.solveClosed(new State(25)
            .ions("Fe+2", mb(100)).ions("Cu+2", mb(100)).ions("Ce+4", mb(100))
            .ions("Cl-1", mb(800)).water(mb(1000))).state;
        assertTrue(Solver.auditViolations.isEmpty(), "redox violations: " + Solver.auditViolations);
        assertTrue(Math.abs(out.netCharge()) < mb(1), "redox state neutral: " + out);
    }
}
