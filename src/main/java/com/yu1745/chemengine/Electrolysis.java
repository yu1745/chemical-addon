package com.yu1745.chemengine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A forced, non-spontaneous net reaction driven by an applied potential (electrolysis),
 * modelled as a fixed stoichiometric progress per call. Unlike a displacement or Ksp
 * mineral, electrolysis is NOT an equilibrium entry: it is externally powered, so it
 * cannot be a mass-action equilibrium. The correct model is "advance the net reaction
 * by a fixed amount, then let the equilibrium solver re-balance the resulting state"
 * (D1b in PLAN.md). Downstream equilibria (pH from produced OH-, gas venting of H2/Cl2/O2,
 * Cl2 disproportionation, etc.) are handled by the subsequent {@link Engine#solveOpen}
 * solve.
 *
 * <p>The reaction is written in engine species syntax ({@link Equilibrium#parse} grammar),
 * e.g. water electrolysis as {@code 2 water = 2 chemicaladdon:hydrogen + chemicaladdon:oxygen}
 * or chlor-alkali as {@code 2 Cl-1 + 2 water = 2 OH-1 + chemicaladdon:hydrogen
 * + chemicaladdon:chlorine}. The net reaction contains no free electrons (they are
 * supplied by the power source and cancel), so it does not trigger the e- pathologies
 * of known_limitations §7/§8.
 */
public final class Electrolysis {

    /** A consumed (reactant) or produced (product) term: species key + whole-unit count. */
    public record Term(String key, Equilibrium.TermPhase phase, int count) {}

    private final List<Term> consume;
    private final List<Term> produce;

    private Electrolysis(List<Term> consume, List<Term> produce) {
        this.consume = consume;
        this.produce = produce;
    }

    /** Parse a net electrolysis/forced reaction written in {@link Equilibrium} species
     *  syntax (allows multiple solids per side, unlike a single equilibrium entry). */
    public static Electrolysis parse(String reaction) {
        java.util.List<java.util.List<Equilibrium.Term>> sides = Equilibrium.parseReactionSides(reaction);
        return new Electrolysis(terms(sides.get(0)), terms(sides.get(1)));
    }

    private static List<Term> terms(List<Equilibrium.Term> src) {
        List<Term> out = new java.util.ArrayList<>();
        for (Equilibrium.Term t : src) out.add(new Term(t.key(), t.phase(), t.count()));
        return out;
    }

    /**
     * Advance the electrolysis cell by {@code units} of the net reaction on a copy of
     * {@code in}: consume every reactant term by coeff*units and produce every product
     * term by coeff*units. The returned state is NOT yet re-equilibrated — call
     * {@link Engine#solveOpen} (or {@link Engine#solveClosed}) on it.
     *
     * @throws IllegalStateException if a reactant is not available in the required amount
     */
    public State advance(State in, long units) {
        if (units < 0) throw new IllegalArgumentException("negative electrolysis progress");
        State out = new State(in.temperatureC()).stirring(in.stirring());
        in.ions().forEach(out::ions);
        in.molecules().forEach(out::molecule);
        in.suspended().forEach(out::suspended);
        in.sediment().forEach(out::sediment);
        Map<String, Long> available = new LinkedHashMap<>();
        for (Equilibrium.Term t : termsOf(consume)) available.merge(t.key(), (long) t.count() * units, Long::sum);
        for (Map.Entry<String, Long> e : available.entrySet()) {
            long have = amount(out, e.getKey());
            if (have < e.getValue()) {
                throw new IllegalStateException("electrolysis overdraws " + e.getKey()
                    + ": need " + e.getValue() + " quanta, have " + have);
            }
        }
        for (Equilibrium.Term t : termsOf(consume)) add(out, t, -((long) t.count()) * units);
        for (Equilibrium.Term t : termsOf(produce)) add(out, t, ((long) t.count()) * units);
        return out;
    }

    private static List<Equilibrium.Term> termsOf(List<Term> list) {
        List<Equilibrium.Term> out = new java.util.ArrayList<>();
        for (Term t : list) out.add(new Equilibrium.Term(t.key(), t.phase(), t.count(), 0));
        return out;
    }

    private static long amount(State s, String key) {
        if (key.equals(State.WATER)) return s.waterAmount();
        if (key.contains(":")) return s.moleculeAmount(key) + s.suspendedAmount(key);
        return s.ionAmount(key);
    }

    private static void add(State s, Equilibrium.Term t, long delta) {
        switch (t.phase()) {
            case ION -> s.adjustIon(t.key(), delta);
            case SOLID -> s.adjustSuspended(t.key(), delta);
            case MOLECULE -> s.adjustMolecule(t.key(), delta);
        }
    }
}
