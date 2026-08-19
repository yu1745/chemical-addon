package com.yu1745.chemengine;

import java.util.ArrayList;
import java.util.List;

/**
 * One mass-action equilibrium entry: a reaction string plus log10(K) as written.
 * Solids and the solvent ("water") have unit activity and do not enter Q.
 *
 * <pre>
 *   "limestone(s) = Ca+2 + CO3-2"                       // mineral: as-written K = Ksp
 *   "CO3-2 + water = HCO3-1 + OH-1"                     // aqueous: solvent ignored in Q
 *   "Cu+2 + 4 chemicaladdon:ammonia = [Cu(NH3)4]+2"     // complexation
 * </pre>
 */
public final class Equilibrium {

    public enum TermPhase { ION, MOLECULE, SOLID }

    public static final class Term {
        private final String key;      // ion id, or species id string for MOLECULE/SOLID
        private final TermPhase phase;
        private final int count;
        private final int charge;      // 0 for molecules

        Term(String key, TermPhase phase, int count, int charge) {
            this.key = key;
            this.phase = phase;
            this.count = count;
            this.charge = charge;
        }

        public String key() { return key; }
        public TermPhase phase() { return phase; }
        public int count() { return count; }
        public int charge() { return charge; }

        /** True when the term participates in Q (aqueous, not the solvent). */
        public boolean isAqueous() { return phase != TermPhase.SOLID && !isSolvent(); }

        public boolean isSolvent() { return phase == TermPhase.MOLECULE && key.equals(SOLVENT); }

        @Override public String toString() {
            return (count > 1 ? count + " " : "") + key;
        }
    }

    public static final String SOLVENT = "water";

    /** Namespace applied to short solid names in reactions (matches species ids). */
    public static final String NAMESPACE = "chemicaladdon:";

    private final List<Term> left;
    private final List<Term> right;
    private final double logK;
    private final double deltaH;   // kJ/mol, NaN = not authored (van't Hoff T correction)
    private final double heatKJ;   // kJ per reaction unit, used only for energy balance; NaN = use deltaH
    private final double molarMass; // g/mol of the owning species; NaN if unknown
    private final double rate;     // fraction of water per tick @25C, 0 = instantaneous (20 ticks = 1 s)

    private Equilibrium(List<Term> left, List<Term> right, double logK, double deltaH, double heatKJ, double molarMass, double rate) {
        this.left = List.copyOf(left);
        this.right = List.copyOf(right);
        this.logK = logK;
        this.deltaH = deltaH;
        this.heatKJ = heatKJ;
        this.molarMass = molarMass;
        this.rate = rate;
    }

    public List<Term> left() { return left; }
    public List<Term> right() { return right; }
    public double logK() { return logK; }
    public double deltaH() { return deltaH; }
    public double heatKJ() { return heatKJ; }
    public double molarMass() { return molarMass; }
    public double rate() { return rate; }

    /** Van't Hoff: log10 K at temperature (K authored at 25 C = 298.15 K). */
    public double logKAt(int tempC) {
        if (Double.isNaN(deltaH)) return logK;
        double tK = tempC + 273.15;
        // log10 K(T) = log10 K(T0) - (dH / (R ln10)) * (1/T - 1/T0), dH in J/mol
        double shift = -1000.0 * deltaH / (8.314 * Math.log(10.0)) * (1.0 / tK - 1.0 / 298.15);
        return logK + shift;
    }

    /** The solid (s) term if this is a mineral entry, else null. */
    public Term solidTerm() {
        for (Term t : left) if (t.phase == TermPhase.SOLID) return t;
        for (Term t : right) if (t.phase == TermPhase.SOLID) return t;
        return null;
    }

    /** True when both sides are fully aqueous (complexation / weak electrolyte / autoionisation). */
    public boolean isAqueous() { return solidTerm() == null; }

    /**
     * Parse a {@code {"reaction": "...", "log_k": n}} entry. Returns null on malformed
     * reactions (a broken entry silently missing from the solver is a bug — fail loud).
     */
    public static Equilibrium parse(String reaction, double logK) {
        return parse(reaction, logK, Double.NaN, Double.NaN, Double.NaN, 0.0);
    }

    public static Equilibrium parse(String reaction, double logK, double deltaH, double rate) {
        return parse(reaction, logK, deltaH, Double.NaN, Double.NaN, rate);
    }

    public static Equilibrium parse(String reaction, double logK, double deltaH, double heatKJ, double rate) {
        return parse(reaction, logK, deltaH, heatKJ, Double.NaN, rate);
    }

    public static Equilibrium parse(String reaction, double logK, double deltaH, double heatKJ, double molarMass, double rate) {
        try {
            String[] sides = reaction.split("=");
            if (sides.length != 2) throw new IllegalArgumentException("expected exactly one '='");
            List<Term> l = parseSide(sides[0]);
            List<Term> r = parseSide(sides[1]);
            if (l.isEmpty() || r.isEmpty()) throw new IllegalArgumentException("empty side");
            // At most one solid per side; a solid on BOTH sides is allowed (a metal
            // displacement reaction such as "Fe(s) + Cu+2 = Fe+2 + Cu(s)"), but two
            // solids on the SAME side are not.
            int solidL = 0, solidR = 0;
            for (Term t : l) if (t.phase == TermPhase.SOLID) solidL++;
            for (Term t : r) if (t.phase == TermPhase.SOLID) solidR++;
            if (solidL > 1 || solidR > 1) throw new IllegalArgumentException("two solid terms on one side");
            return new Equilibrium(l, r, logK, deltaH, heatKJ, molarMass, rate);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse equilibrium '" + reaction + "': " + e.getMessage(), e);
        }
    }

    /** Parse a reaction into its left/right term lists WITHOUT the one-solid-per-side
     *  restriction of {@link #parse}. Used by {@code Electrolysis} for net reactions that
     *  may legitimately have several solids on a side (e.g. "CaO(s) + 3 C(s) = CaC2(s) + CO").
     *  @return {@code List.of(leftTerms, rightTerms)}
     */
    public static List<List<Term>> parseReactionSides(String reaction) {
        try {
            String[] sides = reaction.split("=");
            if (sides.length != 2) throw new IllegalArgumentException("expected exactly one '='");
            List<Term> l = parseSide(sides[0]);
            List<Term> r = parseSide(sides[1]);
            if (l.isEmpty() || r.isEmpty()) throw new IllegalArgumentException("empty side");
            return List.of(l, r);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse reaction '" + reaction + "': " + e.getMessage(), e);
        }
    }

    private static List<Term> parseSide(String side) {        List<Term> out = new ArrayList<>();
        for (String raw : side.split(" \\+ ")) { // joiner is " + " so ion ids like Cu+2 stay intact
            String token = raw.trim();
            if (token.isEmpty()) continue;
            int count = 1;
            int space = token.indexOf(' ');
            if (space > 0) {
                String head = token.substring(0, space);
                if (head.matches("\\d+")) {
                    count = Integer.parseInt(head);
                    token = token.substring(space + 1).trim();
                }
            }
            boolean solid = token.endsWith("(s)");
            if (solid) token = token.substring(0, token.length() - 3).trim();
            if (token.equals(SOLVENT)) {
                out.add(new Term(SOLVENT, TermPhase.MOLECULE, count, 0));
            } else if (token.contains(":")) {
                out.add(new Term(token, solid ? TermPhase.SOLID : TermPhase.MOLECULE, count, 0));
            } else if (solid) {
                out.add(new Term(NAMESPACE + token, TermPhase.SOLID, count, 0)); // short solid name -> mod namespace
            } else {
                out.add(new Term(token, TermPhase.ION, count, Ion.chargeOf(token)));
            }
        }
        return out;
    }

    @Override public String toString() {
        return terms(left) + " = " + terms(right) + " (log_k " + logK + ")";
    }

    private static String terms(List<Term> ts) {
        List<String> s = new ArrayList<>();
        for (Term t : ts) s.add(t.toString());
        return String.join(" + ", s);
    }
}
