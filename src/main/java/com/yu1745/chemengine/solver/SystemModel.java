package com.yu1745.chemengine.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.yu1745.chemengine.Equilibrium;
import com.yu1745.chemengine.Ion;
import com.yu1745.chemengine.Species;
import com.yu1745.chemengine.SpeciesDatabase;

/**
 * The compiled chemistry: a master-species basis with H+1 as the charge-balance
 * component and OH-1 as the water-autoionisation secondary. Every freely-dissociating
 * ion authored in a species' "ions" field is a pinned master species; complexes,
 * bicarbonate and dissolved gases are expressed over the masters by leaf elimination.
 *
 * <p>Mineral entries split into two groups. A plain Ksp entry ({@code solid = ions})
 * defines the solid's dissolution stoichiometry and becomes the phase equilibrium.
 * An entry with aqueous species on both sides ({@code solid + OH = [Al(OH)4]}) is an
 * amphoteric relation: it is combined with the same solid's Ksp vector into a pure
 * aqueous species relation ({@code Al + 4 OH = [Al(OH)4]}), so the species is
 * expressible and only the Ksp entry remains as a phase equilibrium.
 *
 * <p>The game treats water as an infinite fixed-volume solvent (unit activity, never
 * mass-tracked), so hydrogen is not conserved — charge is. The solver therefore
 * replaces H's mass balance with a charge-balance equation; all other components keep
 * ordinary mass balance. Reactions conserve charge, which is what makes the scheme
 * exact (and preserves neutrality after integer projection).
 */
public final class SystemModel {

    public static final String NAMESPACE = "chemicaladdon:";
    public static final String H_PLUS = "H+1";
    public static final String OH_MINUS = "OH-1";
    public static final String ELECTRON = "e-";

    /** Mineral-only log-K offset (unit convention, plans/03 §8): -2 makes log_k <= -5
     *  minerals leave < 1 residual unit in 1000 mB of water ("fully precipitated"). */
    public static final double MINERAL_LOG_OFFSET = -2.0;

    public static final class Secondary {
        public final String key;
        public final double[] coeff;   // over components
        public final double logKEff;   // base 10, at 25 C
        public final double authoredLogK; // the defining entry's raw log_k (freeze support)
        public final double deltaH;    // authored written-direction enthalpy, kJ/mol, NaN if not authored (energy accounting)
        public final double deltaHVan; // component-space enthalpy, kJ/mol: deltaH carried through the same leaf-elimination
                                       // algebra as logK, so Van't Hoff sees the effective direction of THIS secondary's
                                       // mass action (e.g. OH- = -H+ carries +55.91, not the authored -55.91 formation).
                                       // NaN if not authored.
        public final boolean isWaterKw; // the OH- autoionisation secondary: its heat is the lumped pair term in the solver,
                                        // so the enthalpy path must not charge it again (double counting).
        public final double heatKJ;    // kJ per reaction unit for energy balance, NaN = use deltaH
        public final double rate;      // reaction units per tick @25C, 0 = instantaneous
        public final int charge;
        public final int authoredStoich; // coefficient of this secondary in its defining reaction
        public final double molarMass;   // g/mol, NaN if unknown

        Secondary(String key, double[] coeff, double logKEff, double authoredLogK,
                  double deltaH, double deltaHVan, boolean isWaterKw, double heatKJ, double rate,
                  int charge, int authoredStoich, double molarMass) {
            this.key = key;
            this.coeff = coeff;
            this.logKEff = logKEff;
            this.authoredLogK = authoredLogK;
            this.deltaH = deltaH;
            this.deltaHVan = deltaHVan;
            this.isWaterKw = isWaterKw;
            this.heatKJ = heatKJ;
            this.rate = rate;
            this.charge = charge;
            this.authoredStoich = authoredStoich;
            this.molarMass = molarMass;
        }

        public double logKEffAt(int tempC) { return vanthoff(logKEff, deltaHVan, tempC); }
    }

    public static final class Mineral {
        public final String solidKey;
        /** For a metal-displacement mineral (Fe(s)+Cu2+=Fe2++Cu(s)): the product solid;
         *  null for ordinary Ksp minerals. coeff then carries MIXED signs (product ions
         *  positive, reactant ions negative) and solidAmt is the displacement progress. */
        public final String productSolidKey;
        /** For a metal + acid displacement (Zn(s)+2H+=Zn2++H2(g)): the product gas species
         *  key (vented in an open vessel); null otherwise. Mutually exclusive with
         *  {@link #productSolidKey} for a given mineral. */
        public final String productGasKey;
        public final double[] coeff;   // dissolution products over components
        public final double logKEff;   // base 10, in component space, at 25 C
        public final double authoredLogK; // the Ksp entry's raw log_k (freeze support)
        public final double deltaH;    // authored written-direction enthalpy of the Ksp entry, kJ/mol, NaN if not authored (energy)
        public final double deltaHVan; // component-space enthalpy: deltaH minus the eliminated secondaries' combined
                                       // enthalpies (constLog's heat parallel), so Van't Hoff sees the effective
                                       // component-space direction (e.g. Mg(OH)2 dissolution over Mg+2 - 2H+ carries
                                       // -111.3, the acid-dissolution enthalpy, not +0.47). NaN if not authored.
        public final double heatKJ;    // kJ per reaction unit for energy balance, NaN = use deltaH
        public final double rate;      // reaction units per tick @25C, 0 = instantaneous
        public final double molarMass; // g/mol, NaN if unknown

        Mineral(String solidKey, String productSolidKey, String productGasKey, double[] coeff, double logKEff, double authoredLogK,
                double deltaH, double deltaHVan, double heatKJ, double rate, double molarMass) {
            this.solidKey = solidKey;
            this.productSolidKey = productSolidKey;
            this.productGasKey = productGasKey;
            this.coeff = coeff;
            this.logKEff = logKEff;
            this.authoredLogK = authoredLogK;
            this.deltaH = deltaH;
            this.deltaHVan = deltaHVan;
            this.heatKJ = heatKJ;
            this.rate = rate;
            this.molarMass = molarMass;
        }

        /** True for a metal-displacement reaction (two solids or metal+gas, mixed-sign coeff). */
        public boolean isDisplacement() { return productSolidKey != null || productGasKey != null; }

        public double logKEffAt(int tempC) { return vanthoff(logKEff, deltaHVan, tempC); }
    }

    /** Van't Hoff temperature shift for a base-10 logK authored at 25 C. */
    public static double vanthoff(double logK25, double deltaHKj, int tempC) {
        if (Double.isNaN(deltaHKj)) return logK25;
        double tK = tempC + 273.15;
        double shift = -1000.0 * deltaHKj / (8.314 * Math.log(10.0)) * (1.0 / tK - 1.0 / 298.15);
        return logK25 + shift;
    }

    /** One aqueous reaction with signed counts (+ product, - reactant) and its logK. */
    private record AqReaction(String name, Map<String, Integer> terms, double logK, double deltaH, double heatKJ, double molarMass, double rate) {}

    private List<String> components;
    private Map<String, Integer> componentIndex;
    private int chargeBalanceIndex;   // H+1
    private List<Secondary> secondaries = new ArrayList<>();
    private List<Mineral> minerals = new ArrayList<>();
    private List<String> gasSpecies = new ArrayList<>();
    private List<Species> crystallisable = new ArrayList<>();

    private List<String> allSpecies = new ArrayList<>();
    private Map<String, Integer> speciesIndex = new LinkedHashMap<>();
    private List<double[]> speciesCoeff = new ArrayList<>();
    private List<Double> speciesLogKEff = new ArrayList<>();
    private List<Integer> speciesCharge = new ArrayList<>();
    private List<String> droppedEquilibria = new ArrayList<>();

    /** No-op constructor for the free-energy factory ({@link #fromFreeEnergy}). The
     *  inline-initialised lists start empty; the factory populates every field. */
    private SystemModel() {}

    public SystemModel(SpeciesDatabase db) {
        List<Equilibrium> equilibria = new ArrayList<>(db.allEquilibria());

        // ---- classify equilibria ----
        List<AqReaction> aqueous = new ArrayList<>();
        Map<String, AqReaction> ksp = new LinkedHashMap<>();     // solidKey -> dissolution terms (products positive)
        List<Equilibrium> extraEntries = new ArrayList<>();      // solid + aq = aq (amphoteric etc.)
        List<Equilibrium> displacements = new ArrayList<>();     // solid + aq = aq + solid (metal displacement)

        for (Equilibrium eq : equilibria) {
            if (eq.isAqueous()) {
                aqueous.add(toAq(eq));
                continue;
            }
            boolean solidOnLeft = eq.left().stream().anyMatch(t -> t.phase() == Equilibrium.TermPhase.SOLID);
            boolean solidOnRight = eq.right().stream().anyMatch(t -> t.phase() == Equilibrium.TermPhase.SOLID);
            Map<String, Integer> leftAq = sideTerms(eq.left());
            Map<String, Integer> rightAq = sideTerms(eq.right());
            if (solidOnLeft && solidOnRight) {
                // metal displacement: Fe(s) + Cu+2 = Fe+2 + Cu(s) (no free e-, both
                // solids are phase quantities tracked by the displacement progress)
                displacements.add(eq);
            } else if (leftAq.isEmpty() || rightAq.isEmpty()) {
                // Ksp: dissolution products = the aqueous side (as products, positive)
                Map<String, Integer> products = solidOnLeft ? rightAq : leftAq;
                ksp.put(eq.solidTerm().key(), new AqReaction(eq.toString(), products, eq.logK(), eq.deltaH(), eq.heatKJ(), eq.molarMass(), eq.rate()));
            } else if (isMetalPlusGasDisplacement(eq, solidOnLeft)) {
                // metal + acid: Zn(s) + 2 H+ = Zn+2 + H2(g) — a displacement whose product
                // is a GAS (vented in an open vessel) rather than a second solid. No free
                // e- (the electron is supplied/released by the acid reduction to H2).
                displacements.add(eq);
            } else {
                extraEntries.add(eq);
            }
        }

        // amphoteric entries: solid + L = R  ->  (Ksp products) + L = R, logK = logK_e - logK_sp
        for (Equilibrium eq : extraEntries) {
            AqReaction k = ksp.get(eq.solidTerm().key());
            if (k == null) { System.err.println("[chemengine] no Ksp entry for " + eq.solidTerm().key()); continue; }
            boolean solidOnLeft = eq.left().stream().anyMatch(t -> t.phase() == Equilibrium.TermPhase.SOLID);
            Map<String, Integer> leftAq = sideTerms(eq.left());
            Map<String, Integer> rightAq = sideTerms(eq.right());
            Map<String, Integer> terms = new LinkedHashMap<>();
            // Ksp products become reactants (negative), entry's own aqueous reactants too
            for (Map.Entry<String, Integer> e : k.terms().entrySet()) terms.merge(e.getKey(), -e.getValue(), Integer::sum);
            for (Map.Entry<String, Integer> e : leftAq.entrySet()) terms.merge(e.getKey(), -e.getValue(), Integer::sum);
            for (Map.Entry<String, Integer> e : rightAq.entrySet()) terms.merge(e.getKey(), e.getValue(), Integer::sum);
            double logK = eq.logK() - k.logK();
            aqueous.add(new AqReaction(eq.toString() + " (combined)", terms, logK, eq.deltaH(), eq.heatKJ(), eq.molarMass(), eq.rate()));
        }

        // ---- species set + pinned masters ----
        Set<String> speciesSet = new LinkedHashSet<>();
        for (AqReaction r : aqueous) speciesSet.addAll(r.terms().keySet());
        Set<String> pinned = new LinkedHashSet<>();
        pinned.add(H_PLUS);
        if (speciesSet.contains(ELECTRON)) pinned.add(ELECTRON);
        for (Species s : db.all()) {
            for (Species.IonComponent c : s.ions()) {
                speciesSet.add(c.ionId());
                pinned.add(c.ionId());
            }
            if (s.isGas()) speciesSet.add(NAMESPACE + s.id());
        }
        // mineral dissolution products are master species too (e.g. Ba+2 appears only in Ksp entries).
        // But if a product is already defined by an aqueous equilibrium (e.g. HCO3-), leave it
        // eligible for leaf elimination so adding a solid does not force a new component basis.
        for (AqReaction r : ksp.values()) {
            for (String term : r.terms().keySet()) {
                boolean alreadyAqueous = speciesSet.contains(term);
                speciesSet.add(term);
                if (!alreadyAqueous) pinned.add(term);
            }
        }

        // ---- forced basis: OH- = autoionisation secondary, H+ pinned component ----
        AqReaction kw = null;
        for (AqReaction r : aqueous) {
            if (r.terms().size() == 2 && r.terms().containsKey(H_PLUS) && r.terms().containsKey(OH_MINUS)) {
                kw = r;
                break;
            }
        }
        if (kw == null) throw new IllegalStateException("water autoionisation equilibrium not found");

        List<String> eliminatedOrder = new ArrayList<>();
        Map<String, AqReaction> secondaryReaction = new LinkedHashMap<>();
        eliminatedOrder.add(OH_MINUS);
        secondaryReaction.put(OH_MINUS, kw);
        List<AqReaction> remaining = new ArrayList<>(aqueous);
        remaining.remove(kw);

        Map<String, Integer> degree = new LinkedHashMap<>();
        for (String sp : speciesSet) degree.put(sp, 0);
        for (AqReaction r : remaining) for (String sp : r.terms().keySet()) degree.merge(sp, 1, Integer::sum);

        while (!remaining.isEmpty()) {
            String leaf = null;
            for (String sp : new TreeSet<>(degree.keySet())) {
                if (pinned.contains(sp)) continue;
                if (degree.get(sp) == 1) { leaf = sp; break; }
            }
            if (leaf != null) {
                AqReaction r = null;
                for (AqReaction cand : remaining) if (cand.terms().containsKey(leaf)) { r = cand; break; }
                secondaryReaction.put(leaf, r);
                eliminatedOrder.add(leaf);
                remaining.remove(r);
                for (String sp : r.terms().keySet()) degree.merge(sp, -1, Integer::sum);
            } else {
                AqReaction drop = findDependentReaction(remaining, degree.keySet());
                if (drop == null) drop = remaining.get(0);
                System.err.println("[chemengine] dropping redundant equilibrium: " + drop.name());
                droppedEquilibria.add(drop.name());
                remaining.remove(drop);
                for (String sp : drop.terms().keySet()) degree.merge(sp, -1, Integer::sum);
            }
        }

        components = new ArrayList<>(new TreeSet<>(speciesSet));
        components.removeAll(secondaryReaction.keySet());
        if (!components.contains(H_PLUS)) components.add(H_PLUS);
        components.sort(String::compareTo);
        componentIndex = new LinkedHashMap<>();
        for (int i = 0; i < components.size(); i++) componentIndex.put(components.get(i), i);
        chargeBalanceIndex = componentIndex.get(H_PLUS);

        // ---- expressions over components ----
        Map<String, double[]> exprCoeff = new LinkedHashMap<>();
        Map<String, Double> exprLogK = new LinkedHashMap<>();
        Map<String, Double> exprDeltaH = new LinkedHashMap<>();
        for (String c : components) {
            double[] id = new double[components.size()];
            id[componentIndex.get(c)] = 1.0;
            exprCoeff.put(c, id);
            exprLogK.put(c, 0.0);
            exprDeltaH.put(c, 0.0);   // components carry no reaction heat
        }

        // OH first (pre-assigned, depends only on H), then the rest in reverse elimination order
        buildExpression(OH_MINUS, secondaryReaction.get(OH_MINUS), exprCoeff, exprLogK, exprDeltaH);
        for (int i = eliminatedOrder.size() - 1; i >= 1; i--) {
            String key = eliminatedOrder.get(i);
            buildExpression(key, secondaryReaction.get(key), exprCoeff, exprLogK, exprDeltaH);
        }

        // register species (components first, then secondaries), derive/verify charges
        for (String c : components) registerSpecies(c, exprCoeff.get(c), 0.0, chargeOf(c));
        for (int i = eliminatedOrder.size() - 1; i >= 0; i--) {
            String key = eliminatedOrder.get(i);
            double[] coeff = exprCoeff.get(key);
            double logK = exprLogK.get(key);
            double charge = 0;
            for (int c = 0; c < components.size(); c++) charge += coeff[c] * chargeOf(components.get(c));
            int authored = chargeOf(key);
            if (Math.abs(charge - authored) > 1e-6) {
                System.err.println("[chemengine] charge mismatch for " + key
                    + ": derived " + charge + " vs authored " + authored);
            }
            registerSpecies(key, coeff, logK, authored);
            int authoredStoich = secondaryReaction.get(key).terms().getOrDefault(key, 0);
            secondaries.add(new Secondary(key, coeff, logK, secondaryReaction.get(key).logK(),
                secondaryReaction.get(key).deltaH(), exprDeltaH.get(key), key.equals(OH_MINUS),
                secondaryReaction.get(key).heatKJ(), secondaryReaction.get(key).rate(), authored,
                authoredStoich, secondaryReaction.get(key).molarMass()));
        }

        // ---- minerals from Ksp entries ----
        for (Map.Entry<String, AqReaction> e : ksp.entrySet()) {
            AqReaction r = e.getValue();
            double[] coeff = new double[components.size()];
            double constLog = 0.0;
            double constDeltaH = 0.0;
            for (Map.Entry<String, Integer> term : r.terms().entrySet()) {
                String tk = term.getKey();
                double[] ex = exprCoeff.get(tk);
                if (ex == null) { System.err.println("[chemengine] mineral term not a species: " + tk); continue; }
                double k = exprLogK.get(tk);
                double dh = exprDeltaH.get(tk);
                double cnt = term.getValue();
                for (int c = 0; c < components.size(); c++) coeff[c] += cnt * ex[c];
                constLog += cnt * k;
                constDeltaH += cnt * dh;
            }
            minerals.add(new Mineral(e.getKey(), null, null, coeff, r.logK() + MINERAL_LOG_OFFSET - constLog,
                r.logK(), r.deltaH(), r.deltaH() - constDeltaH, r.heatKJ(), r.rate(), r.molarMass()));
        }

        // ---- metal-displacement minerals: Fe(s) + Cu+2 = Fe+2 + Cu(s) ----
        // coeff carries product ions positive and reactant ions negative (mixed sign);
        // the two solids are tracked by the displacement progress. No MINERAL_LOG_OFFSET:
        // the -2 is a Ksp "fully precipitates" unit convention; displacement logK comes
        // from real electrode potentials (n*E0/0.05916) and must not be shifted.
        for (Equilibrium eq : displacements) {
            String reactantSolid = null, productSolid = null, productGas = null;
            for (Equilibrium.Term t : eq.left()) if (t.phase() == Equilibrium.TermPhase.SOLID) reactantSolid = t.key();
            for (Equilibrium.Term t : eq.right()) if (t.phase() == Equilibrium.TermPhase.SOLID) productSolid = t.key();
            // a gas product (Zn(s)+2H+=Zn2++H2(g)) is a non-solvent molecule on the right;
            // it is written back as a gas and vented, and is excluded from the aqueous
            // mass-action coeff (activity ~1, not a component).
            for (Equilibrium.Term t : eq.right())
                if (t.phase() == Equilibrium.TermPhase.MOLECULE && !t.isSolvent()) productGas = t.key();
            double[] coeff = new double[components.size()];
            double constLog = 0.0;
            double constDeltaH = 0.0;
            for (Equilibrium.Term t : eq.right()) {
                if (t.phase() == Equilibrium.TermPhase.SOLID || t.isSolvent()) continue;
                if (productGas != null && t.key().equals(productGas)) continue;
                double[] ex = exprCoeff.get(t.key());
                if (ex == null) continue;
                for (int c = 0; c < components.size(); c++) coeff[c] += t.count() * ex[c];
                constLog += t.count() * exprLogK.get(t.key());
                constDeltaH += t.count() * exprDeltaH.get(t.key());
            }
            for (Equilibrium.Term t : eq.left()) {
                if (t.phase() == Equilibrium.TermPhase.SOLID || t.isSolvent()) continue;
                double[] ex = exprCoeff.get(t.key());
                if (ex == null) continue;
                for (int c = 0; c < components.size(); c++) coeff[c] -= t.count() * ex[c];
                constLog -= t.count() * exprLogK.get(t.key());
                constDeltaH -= t.count() * exprDeltaH.get(t.key());
            }
            minerals.add(new Mineral(reactantSolid, productSolid, productGas, coeff, eq.logK() - constLog,
                eq.logK(), eq.deltaH(), eq.deltaH() - constDeltaH, eq.heatKJ(), eq.rate(), eq.molarMass()));
        }

        for (Species s : db.all()) {
            if (s.isGas()) gasSpecies.add(NAMESPACE + s.id());
            if (s.isCrystallisable()) crystallisable.add(s);
        }
    }

    private void buildExpression(String key, AqReaction r, Map<String, double[]> exprCoeff,
                                 Map<String, Double> exprLogK, Map<String, Double> exprDeltaH) {
        if (exprCoeff.containsKey(key)) return;
        double[] coeff = new double[components.size()];
        double logK = 0.0;
        double deltaH = 0.0;
        int self = r.terms().get(key);
        boolean selfProduct = self > 0;
        double cs = Math.abs(self);
        for (Map.Entry<String, Integer> e : r.terms().entrySet()) {
            if (e.getKey().equals(key)) continue;
            String tk = e.getKey();
            double[] ex = exprCoeff.get(tk);
            if (ex == null) { System.err.println("[chemengine] term not expressed yet: " + tk); continue; }
            double tlogK = exprLogK.get(tk);
            double tdh = exprDeltaH.get(tk);
            int cnt = e.getValue();
            double sign = selfProduct ? (cnt < 0 ? +1 : -1) : (cnt > 0 ? +1 : -1);
            logK += sign * Math.abs(cnt) * tlogK;
            deltaH += sign * Math.abs(cnt) * tdh;   // deltaH is linear over reactions, same algebra as logK
            for (int c = 0; c < components.size(); c++) coeff[c] += sign * Math.abs(cnt) * ex[c];
        }
        logK += selfProduct ? r.logK() : -r.logK();
        deltaH += selfProduct ? r.deltaH() : -r.deltaH();
        for (int c = 0; c < components.size(); c++) coeff[c] /= cs;
        logK /= cs;
        deltaH /= cs;
        exprCoeff.put(key, coeff);
        exprLogK.put(key, logK);
        exprDeltaH.put(key, deltaH);
    }

    private static AqReaction toAq(Equilibrium eq) {
        Map<String, Integer> terms = new LinkedHashMap<>();
        for (Equilibrium.Term t : eq.left()) if (t.isAqueous()) terms.merge(t.key(), -t.count(), Integer::sum);
        for (Equilibrium.Term t : eq.right()) if (t.isAqueous()) terms.merge(t.key(), t.count(), Integer::sum);
        return new AqReaction(eq.toString(), terms, eq.logK(), eq.deltaH(), eq.heatKJ(), eq.molarMass(), eq.rate());
    }

    private static Map<String, Integer> sideTerms(List<Equilibrium.Term> side) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Equilibrium.Term t : side) if (t.isAqueous()) out.merge(t.key(), t.count(), Integer::sum);
        return out;
    }

    /** True for a metal(s) + acid -> aq + gas displacement, e.g. Zn(s)+2H+=Zn2++H2(g):
     *  exactly one side carries a METAL solid and the aqueous side has a gaseous
     *  (non-solvent molecule) product. Restricted to metals so acid-carbonate reactions
     *  (CaCO3+2H+=Ca2++CO2+H2O) are not mistaken for a displacement. */
    private static boolean isMetalPlusGasDisplacement(Equilibrium eq, boolean solidOnLeft) {
        List<Equilibrium.Term> solidSide = solidOnLeft ? eq.left() : eq.right();
        List<Equilibrium.Term> aqSide = solidOnLeft ? eq.right() : eq.left();
        String solidKey = null;
        for (Equilibrium.Term t : solidSide) if (t.phase() == Equilibrium.TermPhase.SOLID) { solidKey = t.key(); break; }
        if (solidKey == null || !solidKey.endsWith("_metal")) return false;
        for (Equilibrium.Term t : aqSide)
            if (t.phase() == Equilibrium.TermPhase.MOLECULE && !t.isSolvent()) return true;
        return false;
    }

    private static AqReaction findDependentReaction(List<AqReaction> reactions, Set<String> species) {
        List<String> sp = new ArrayList<>(new TreeSet<>(species));
        double[][] m = new double[reactions.size()][sp.size()];
        for (int i = 0; i < reactions.size(); i++) {
            for (int j = 0; j < sp.size(); j++) m[i][j] = reactions.get(i).terms().getOrDefault(sp.get(j), 0);
        }
        boolean[] used = new boolean[reactions.size()];
        for (int col = 0; col < sp.size(); col++) {
            int pivot = -1;
            for (int row = 0; row < reactions.size(); row++) if (!used[row] && Math.abs(m[row][col]) > 1e-9) { pivot = row; break; }
            if (pivot < 0) continue;
            used[pivot] = true;
            double pv = m[pivot][col];
            for (int row = 0; row < reactions.size(); row++) {
                if (row != pivot && Math.abs(m[row][col]) > 1e-9) {
                    double f = m[row][col] / pv;
                    for (int k = col; k < sp.size(); k++) m[row][k] -= f * m[pivot][k];
                }
            }
        }
        for (int row = 0; row < reactions.size(); row++) {
            boolean zero = true;
            for (int j = 0; j < sp.size(); j++) if (Math.abs(m[row][j]) > 1e-9) zero = false;
            if (zero) return reactions.get(row);
        }
        return null;
    }

    private void registerSpecies(String key, double[] coeff, double logK, int charge) {
        allSpecies.add(key);
        speciesIndex.put(key, speciesCoeff.size());
        speciesCoeff.add(coeff);
        speciesLogKEff.add(logK);
        speciesCharge.add(charge);
    }

    private static int chargeOf(String key) {
        return key.contains(":") ? 0 : Ion.chargeOf(key);
    }

    public int componentCount() { return components.size(); }
    public List<String> components() { return components; }
    public int indexOf(String key) { return componentIndex.getOrDefault(key, -1); }
    public int chargeBalanceIndex() { return chargeBalanceIndex; }
    public List<Secondary> secondaries() { return secondaries; }
    public List<Mineral> minerals() { return minerals; }
    public List<String> gasSpecies() { return gasSpecies; }
    public List<Species> crystallisable() { return crystallisable; }

    /** Equilibria dropped by the leaf-elimination basis reduction (linearly dependent
     *  on surviving entries). Their log_k / delta_h are DEAD DATA: never applied by
     *  the solver. DataIntegrityTest requires this list to be empty so no authored
     *  equilibrium silently loses its thermodynamic content. */
    public List<String> droppedEquilibria() { return droppedEquilibria; }

    /** True when any authored equilibrium entry is rate-limited (kinetic). */
    public boolean hasRateLimited() {
        for (Secondary s : secondaries) if (s.rate > 0) return true;
        for (Mineral m : minerals) if (m.rate > 0) return true;
        return false;
    }

    public int speciesCount() { return allSpecies.size(); }
    public String speciesKey(int i) { return allSpecies.get(i); }
    public double[] speciesCoeff(int i) { return speciesCoeff.get(i); }
    public double speciesLogKEff(int i) { return speciesLogKEff.get(i); }

    /** Formation constant of a species at tempC (components have no T dependence). */
    public double speciesLogKEffAt(int i, int tempC) {
        if (i < componentCount()) return 0.0;
        return secondaries.get(i - componentCount()).logKEffAt(tempC);
    }
    public int speciesCharge(int i) { return speciesCharge.get(i); }
    public Integer speciesIndexOf(String key) { return speciesIndex.get(key); }

    public double gasSolubilityOf(SpeciesDatabase db, String gasKey) {
        String shortName = gasKey.startsWith(NAMESPACE) ? gasKey.substring(NAMESPACE.length()) : gasKey;
        Species s = db.get(shortName);
        if (s == null || Double.isNaN(s.gasSolubility())) return Solver.GAS_SOLUBILITY_DEFAULT;
        return s.gasSolubility();
    }

    // ------------------------------------------------------------- free-energy path

    /** RT·ln10 at 25 C in kJ/mol (used to turn ΔG° into log10 K). */
    private static final double RT_LN10_KJ = 8.314 * 298.15 * Math.log(10.0) / 1000.0;
    /** ΔG_f° of liquid water (kJ/mol) — the unit-activity solvent; its ΔG° still enters the
     *  free energy of any reaction that produces/consumes water (hydroxides/hydrates). */
    private static final double DGF_WATER_KJ = -237.13;

    /**
     * Track B: build the model from FORMATION FREE ENERGIES instead of balanced reaction
     * strings (see {@link FreeEnergyDatabase}). Every aqueous species and candidate solid is
     * given only an elemental composition and a standard ΔG_f° (kJ/mol). The master-ion
     * basis provides the components; each species' component coefficient vector is derived by
     * solving element + charge balance, and its equilibrium constant is DERIVED from ΔG_f° via
     *
     * <pre>logK = −ΔG°rxn / (RT·ln10) = (Σ ν_i·ΔG_f°(products) − Σ ν_i·ΔG_f°(reactants)) / (RT·ln10)</pre>
     *
     * <p>Solids are candidate precipitates whose Ksp comes out of ΔG_f°; they emerge in the
     * phase-assemblage exactly when their saturation index exceeds 0 — no solubility value
     * is ever authored by hand, and no reaction string is written.
     *
     * <p>Water stays the infinite unit-activity solvent (H/O balance is governed by charge
     * neutrality + the Kw secondary, as in the reaction-string model); OH-1 is that Kw
     * secondary. Species not expressible as an integer combination of the master ions are
     * recorded in {@code droppedEquilibria} (the reason real GEM uses an element basis and
     * treats water as a phase).
     */
    public static SystemModel fromFreeEnergy(FreeEnergyDatabase fdb) {
        SystemModel m = new SystemModel();
        Map<String, FreeEnergyDatabase.IonSpec> basis = fdb.basis();

        // components = master ions; OH-1 stays a Kw secondary, H+1 is the charge anchor.
        List<String> components = new ArrayList<>(new TreeSet<>(basis.keySet()));
        components.remove(OH_MINUS);
        if (!components.contains(H_PLUS)) components.add(H_PLUS);
        components.sort(String::compareTo);
        m.components = components;
        m.componentIndex = new LinkedHashMap<>();
        for (int i = 0; i < components.size(); i++) m.componentIndex.put(components.get(i), i);
        m.chargeBalanceIndex = m.componentIndex.get(H_PLUS);

        // master ions aligned to components (synthesise H+1: ΔGf(H+,aq) = 0, {H:1}).
        FreeEnergyDatabase.IonSpec[] ions = new FreeEnergyDatabase.IonSpec[components.size()];
        for (int i = 0; i < components.size(); i++) {
            String key = components.get(i);
            FreeEnergyDatabase.IonSpec sp = basis.get(key);
            ions[i] = sp != null ? sp
                : (key.equals(H_PLUS)
                    ? new FreeEnergyDatabase.IonSpec(key, 1, 0.0, Map.of("H", 1))
                    : new FreeEnergyDatabase.IonSpec(key, Ion.chargeOf(key), 0.0, Map.of()));
        }

        // register master ions as identity species (logK 0).
        for (int i = 0; i < components.size(); i++) {
            m.allSpecies.add(components.get(i));
            m.speciesIndex.put(components.get(i), m.speciesCoeff.size());
            double[] id = new double[components.size()];
            id[i] = 1.0;
            m.speciesCoeff.add(id);
            m.speciesLogKEff.add(0.0);
            m.speciesCharge.add(Ion.chargeOf(components.get(i)));
        }

        // water autoionisation: OH-1 = -H+1 (water unit-activity solvent), Kw = 1e-14.
        {
            double[] cv = new double[components.size()];
            cv[m.componentIndex.get(H_PLUS)] = -1.0;
            registerFreeEnergySecondary(m, OH_MINUS, cv, -14.0, -1);
        }

        // aqueous secondaries, logK derived from ΔG_f°.
        for (FreeEnergyDatabase.SpeciesSpec sp : fdb.species()) {
            if (sp.key.equals(OH_MINUS)) continue;
            Balance bal = balance(ions, sp.elements, sp.charge);
            if (bal == null) {
                m.droppedEquilibria.add(sp.key + " (not expressible over master-ion basis)");
                continue;
            }
            double logK = (dot(bal.coeff, ions) + bal.water * DGF_WATER_KJ - sp.dGfKj) / RT_LN10_KJ;
            registerFreeEnergySecondary(m, sp.key, toDouble(bal.coeff), logK, sp.charge);
        }

        // candidate solids: Ksp derived from ΔG_f°; the phase-assemblage decides emergence.
        // Hydroxides/hydrates are expressed in the acid-dissolution form (H2O unit-activity
        // solvent), so their Ksp includes the water ΔG° term (bal.water).
        for (FreeEnergyDatabase.SolidSpec sd : fdb.solids()) {
            Balance bal = balance(ions, sd.elements, 0);
            if (bal == null) {
                m.droppedEquilibria.add(sd.key + " (solid not expressible over master-ion basis)");
                continue;
            }
            double ksp = (sd.dGfKj - dot(bal.coeff, ions) - bal.water * DGF_WATER_KJ) / RT_LN10_KJ;
            // No MINERAL_LOG_OFFSET here: that -2 is a legacy integer-projection convention,
            // not a physical free energy. In the free-energy path the Ksp is exactly what
            // ΔG_f° dictates, so it must be used as-is for a thermodynamically correct phase
            // assemblage and Gibbs ranking.
            m.minerals.add(new Mineral(sd.key, null, null, toDouble(bal.coeff), ksp, ksp,
                Double.NaN, Double.NaN, Double.NaN, 0.0, Double.NaN));
        }
        return m;
    }

    private static void registerFreeEnergySecondary(SystemModel m, String key, double[] cv,
                                                    double logK, int charge) {
        m.allSpecies.add(key);
        m.speciesIndex.put(key, m.speciesCoeff.size());
        m.speciesCoeff.add(cv);
        m.speciesLogKEff.add(logK);
        m.speciesCharge.add(charge);
        m.secondaries.add(new Secondary(key, cv, logK, logK, Double.NaN, Double.NaN,
            key.equals(OH_MINUS), Double.NaN, 0.0, charge, 1, Double.NaN));
    }

    /** Σ_j coeff[j] · ΔG_f°(basis ion j). */
    private static double dot(int[] coeff, FreeEnergyDatabase.IonSpec[] ions) {
        double s = 0;
        for (int j = 0; j < coeff.length; j++) s += coeff[j] * ions[j].dGfKj;
        return s;
    }

    private static double[] toDouble(int[] c) {
        double[] d = new double[c.length];
        for (int j = 0; j < c.length; j++) d[j] = c[j];
        return d;
    }

    /**
     * Solve Σ_j coeff[j]·basisIon[j] = target (element counts + charge) over the master ions.
     * Returns integer coefficients aligned to {@code ions}, or null when the target is not an
     * exact integer combination (i.e. not expressible over this basis).
     */
    /** Result of a basis-expression solve: coefficients over the master ions plus the number
     *  of unit-activity water molecules involved (>= 0). */
    private record Balance(int[] coeff, int water) {}

    /**
     * Express a target species/solid over the master-ion basis, allowing water as the
     * unit-activity solvent. Solves element+charge balance over the NON-H/non-O elements
     * (charge too), then determines the water coefficient m from the oxygen balance and
     * verifies the hydrogen balance:  target_H = Σ coeff·ion_H + 2m. Returns null when the
     * target is not expressible (e.g. H/O can't be satisfied by ions + water).
     */
    private static Balance balance(FreeEnergyDatabase.IonSpec[] ions,
                                   Map<String, Integer> targetElements, int targetCharge) {
        // Candidate building-block ions: those carrying any target non-H/O element, plus H+1
        // (the charge-balancer). Sorting by complexity (fewest elements first) makes the search
        // prefer bare cations (Al+3) over complexes (Al(OH)4-1, Fe(CN)6) for simple solids.
        Set<String> targetNonHo = new TreeSet<>();
        for (String e : targetElements.keySet()) if (!e.equals("H") && !e.equals("O")) targetNonHo.add(e);
        List<Integer> rel = new ArrayList<>();
        int eIdx = -1;
        for (int j = 0; j < ions.length; j++) {
            if (ions[j].key.equals(H_PLUS)) { rel.add(j); continue; }
            // the electron pool has no elements: it never appears as an unknown in the square
            // solve; instead trySubset enumerates its coefficient ε (a redox step's electrons)
            // as a parameter — see balance(). The largest |ε| needed by the catalog is 22
            // (Cr2O7-2), so the window below bounds the enumeration.
            if (ions[j].key.equals(ELECTRON)) { eIdx = j; continue; }
            // candidate only if its non-H/O elements are a non-empty SUBSET of the target's,
            // so cyanide/thiocyanate complexes (extra elements) never build simple solids.
            boolean subset = true, hasNonHo = false;
            for (String e : ions[j].elements.keySet()) {
                if (e.equals("H") || e.equals("O")) continue;
                hasNonHo = true;
                if (!targetNonHo.contains(e)) { subset = false; break; }
            }
            if (subset && hasNonHo) rel.add(j);
        }
        if (rel.size() < targetNonHo.size() + 1) return null;
        rel.sort((a, b) -> {
            int ca = ions[a].elements.size(), cb = ions[b].elements.size();
            return ca != cb ? ca - cb : ions[a].key.compareTo(ions[b].key);
        });
        int k = targetNonHo.size() + 1;                       // rows: elements + charge
        if (rel.size() > 12) return null;                     // avoid combinatorial explosion

        int[] combo = new int[k];
        for (int i = 0; i < k; i++) combo[i] = i;
        while (true) {
            if (eIdx >= 0) {
                // enumerate the electron coefficient ε: the charge equation becomes
                // Σ coeff·q_ion = targetCharge + ε (e- contributes -ε once moved to the RHS).
                // Window covers the catalog's largest redox span (|ε| <= 60: six cyano ligands
                // each reduce CO3/NO3 masters by 10 e-).
                for (int eps = -64; eps <= 64; eps++) {
                    Balance b = trySubset(ions, rel, combo, targetElements, targetCharge, targetNonHo,
                        eps, eIdx);
                    if (b != null) return b;
                }
            } else {
                // no electron pool in this basis: only the ε=0 (no redox) decomposition.
                Balance b = trySubset(ions, rel, combo, targetElements, targetCharge, targetNonHo,
                    0, -1);
                if (b != null) return b;
            }
            int i = k - 1;
            while (i >= 0 && combo[i] == rel.size() - k + i) i--;
            if (i < 0) break;
            combo[i]++;
            for (int m = i + 1; m < k; m++) combo[m] = combo[m - 1] + 1;
        }
        return null;
    }

    /** Solve the square (elements+charge) x (chosen ions) system with an electron coefficient
     *  {@code eps} folded into the charge target; return a valid integer decomposition (with
     *  water from the O balance, sign-free so reduction reactions may release water as a
     *  product, and an exact H check) or null. */
    private static Balance trySubset(FreeEnergyDatabase.IonSpec[] ions, List<Integer> rel,
                                     int[] combo, Map<String, Integer> targetElements,
                                     int targetCharge, Set<String> targetNonHo,
                                     int eps, int eIdx) {
        int k = combo.length;
        List<String> rows = new ArrayList<>(targetNonHo);
        rows.add("\u0000charge");
        double[][] M = new double[k][k + 1];
        for (int r = 0; r < k; r++) {
            String name = rows.get(r);
            for (int c = 0; c < k; c++) {
                FreeEnergyDatabase.IonSpec sp = ions[rel.get(combo[c])];
                M[r][c] = name.equals("\u0000charge") ? sp.charge : sp.elements.getOrDefault(name, 0);
            }
            M[r][k] = name.equals("\u0000charge") ? targetCharge + eps
                : targetElements.getOrDefault(name, 0);
        }
        // Gauss-Jordan on the square system.
        int[] pivotRowForCol = new int[k];
        Arrays.fill(pivotRowForCol, -1);
        for (int col = 0; col < k; col++) {
            int piv = -1;
            double best = 0;
            for (int r = 0; r < k; r++) {
                boolean usedRow = false;
                for (int c2 = 0; c2 < col; c2++) if (pivotRowForCol[c2] == r) { usedRow = true; break; }
                if (usedRow) continue;
                if (Math.abs(M[r][col]) > best) { best = Math.abs(M[r][col]); piv = r; }
            }
            if (piv < 0 || best < 1e-9) return null;
            pivotRowForCol[col] = piv;
            double pv = M[piv][col];
            for (int j = 0; j <= k; j++) M[piv][j] /= pv;
            for (int r = 0; r < k; r++) {
                if (r == piv) continue;
                double f = M[r][col];
                if (Math.abs(f) < 1e-12) continue;
                for (int j = 0; j <= k; j++) M[r][j] -= f * M[piv][j];
            }
        }
        int[] coeff = new int[ions.length];
        for (int col = 0; col < k; col++) {
            int r = pivotRowForCol[col];
            if (r < 0) return null;
            double v = M[r][k];
            if (Math.abs(v - Math.rint(v)) > 1e-6) return null;   // non-integer → not this combo
            coeff[rel.get(combo[col])] = (int) Math.rint(v);
        }
        if (eIdx >= 0) coeff[eIdx] = eps;
        // verify elements + charge, then water from O and H consistency.
        for (String e : targetNonHo) {
            int sum = 0;
            for (int c = 0; c < k; c++) sum += coeff[rel.get(combo[c])] * ions[rel.get(combo[c])].elements.getOrDefault(e, 0);
            if (sum != targetElements.getOrDefault(e, 0)) return null;
        }
        int q = 0;
        for (int c = 0; c < k; c++) q += coeff[rel.get(combo[c])] * ions[rel.get(combo[c])].charge;
        if (q != targetCharge + eps) return null;   // e- contributes -eps on the LHS
        int oTotal = 0, hTotal = 0;
        for (int c = 0; c < k; c++) {
            oTotal += coeff[rel.get(combo[c])] * ions[rel.get(combo[c])].elements.getOrDefault("O", 0);
            hTotal += coeff[rel.get(combo[c])] * ions[rel.get(combo[c])].elements.getOrDefault("H", 0);
        }
        // water coefficient may be negative: reduction reactions release water as a product
        // (e.g. S-2 = SO4-2 + 8e- + 8H+ - 4H2O); the H check below stays exact either way.
        int water = targetElements.getOrDefault("O", 0) - oTotal;
        if (targetElements.getOrDefault("H", 0) - hTotal != 2 * water) return null;
        return new Balance(coeff, water);
    }
}
