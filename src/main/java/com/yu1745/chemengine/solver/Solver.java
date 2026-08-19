package com.yu1745.chemengine.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yu1745.chemengine.Ion;
import com.yu1745.chemengine.Species;
import com.yu1745.chemengine.SpeciesDatabase;
import com.yu1745.chemengine.State;

/**
 * The engine core: continuous master-species Newton solve (PHREEQC-style) with a
 * phase-assemblage outer loop for solids and a Henry degas loop for gases, followed
 * by a deterministic projection onto the integer lattice.
 *
 * <p>Unknowns are ln(component amounts) plus, for each present mineral, ln(solid
 * amount). Equations are per-component mass balance and per-present-mineral mass
 * action (Q = K). Only components that can actually carry mass are solved: a
 * component whose total is zero and whose secondaries all have non-negative
 * stoichiometry is pinned to zero and excluded (this keeps the Jacobian away from
 * the ill-conditioned 1e-9 diagonals of absent trace metals); H is always solved
 * because OH-1 and the ammine/ammonia family give it negative stoichiometry.
 *
 * <p>Reactions conserve charge, so satisfying every component total (each with its
 * signed charge signature) satisfies charge neutrality exactly, and the integer
 * projection — which allocates whole species, preserving every component total —
 * preserves it on the lattice.
 */
public final class Solver {

    public enum Vessel { CLOSED, OPEN }

    public static final double GAS_SOLUBILITY_DEFAULT = 1e-3;

    // ---------------------------------------------------------------- kinetics / energy
    /** "g solute per 100 g water" divisor of the real solubility tables. */
    public static final double SOLUBILITY_SCALE = 1.0;
    /** Per solve, at most this fraction of water x supersaturation drives crystallisation. */
    public static final double CRYSTAL_RATE_FRACTION = 0.1;
    /** Unseeded solutions only self-nucleate at this supersaturation (form/cap - 1). */
    public static final double NUCLEATION_AFFINITY = 0.5;
    /** Homogeneous nucleation: the first crystal grows this much slower. */
    public static final double NUCLEATION_PENALTY = 0.05;
    /** H+ + OH- -> H2O, per quanta pair (57.1 kJ/mol / 18 g, 1 quanta = 10^-7 g). */
    public static final double NEUTRALISATION_J_PER_PAIR = 3172.0 / State.QUANTA_PER_MB;
    /** Water vaporisation, per quanta vented as steam. */
    public static final double VAPORISATION_J_PER_UNIT = 2260.0 / State.QUANTA_PER_MB;
    /** Declared lumped specific heat of the vessel body (water), per quanta. */
    public static final double HEAT_CAPACITY_PER_UNIT = 4.18 / State.QUANTA_PER_MB;
    /**
     * Simplified conversion from PHREEQC-style delta_h (kJ/mol) to J per quanta.
     * Assumes 1 mB = 1 g and 18 g/mol (water-like) for the reaction unit. This is a
     * deliberate simplification until per-species molar masses are added.
     */
    public static final double HEAT_PER_KJ_MOL_PER_QUANTA = 1000.0 / (18.0 * State.QUANTA_PER_MB);

    private static final double LN10 = Math.log(10.0);
    private static final double TOL_F = 1e-12;
    private static final double TOL_G = 1e-10;
    /** Largest (in quanta) electron shortfall tolerated in the integer projection: e- is a
     *  pseudo-species whose remainder is a flooring artifact of e--coupled secondaries (each
     *  floored <1 quantum). Far below the mb(1)=1e7 audit charge budget, so genuine
     *  infeasibility (a non-converged, electron-starved state) is never masked. */
    private static final long ELECTRON_REMAINDER_TOL = 1 << 10; // 1024 quanta (~1e-4 mB)
    private static final int MAX_NEWTON = 120;
    private static final int MAX_PHASE = 64;
    private static final int MAX_DEGAS = 128;
    /** hSeedOverride sentinel selecting the stoichiometric pre-seed (see newtonSolve). */
    private static final double BALANCED_SEED = -1.0;

    public static final class Result {
        public final State state;
        public final Map<String, Long> gasVented;
        /** Net reaction energy of this solve in J (positive = released). */
        public final double energyJ;
        /** dT = Q / (feedUnits * c) with 1 unit = 1 g and c = water's specific heat. */
        public final double heatRiseC;
        /** Species whose rate-limited equilibrium was held back by kinetics this tick. */
        public final List<String> rateLimited;

        Result(State state, Map<String, Long> gasVented, double energyJ, double feedUnits,
               List<String> rateLimited) {
            this.state = state;
            this.gasVented = gasVented;
            this.energyJ = energyJ;
            this.heatRiseC = feedUnits <= 0 ? 0 : energyJ / (feedUnits * HEAT_CAPACITY_PER_UNIT);
            this.rateLimited = rateLimited;
        }
    }

    private final SystemModel model;
    private final double[] totals;
    private final double water;
    private final int tempC;
    private final double stirring;
    private final long inputFreeH;
    private final Map<String, Long> inert = new LinkedHashMap<>();
    /** Metal-solid initial amounts (displacement reactant+product solids, in quanta).
     *  A metal solid is a "reactant pool", not a dissolution source like a Ksp mineral:
     *  its amount is tracked here and consumed/grown by the displacement progress. */
    private final Map<String, Long> metalInit = new LinkedHashMap<>();
    private final Map<String, Long> seedSediment = new LinkedHashMap<>(); // curve solids carried as seed crystals
    private long suppressedPair; // H+/OH- quanta removed by suppressAutoionisation (audit support)
    private final double[] inputSpecies; // pre-tick species amounts (components + secondaries + solids)
    private final List<String> kineticHeld = new ArrayList<>(); // species held back by rate limits
    private final double[] secLogKOverride; // NaN = use model; else frozen logK for a rate-limited secondary

    private Solver(SystemModel model, double[] totals, double water, int tempC, double stirring,
                   long inputFreeH, Map<String, Long> inert, double[] inputSpecies) {
        this.model = model;
        this.totals = totals;
        this.water = water;
        this.tempC = tempC;
        this.stirring = stirring;
        this.inputFreeH = inputFreeH;
        this.inputSpecies = inputSpecies;
        this.inert.putAll(inert);
        this.secLogKOverride = new double[model.secondaries().size()];
        java.util.Arrays.fill(this.secLogKOverride, Double.NaN);
    }

    public static Result solve(SystemModel model, SpeciesDatabase db, State in, Vessel vessel) {
        int C = model.componentCount();
        double[] totals = new double[C];
        double water = in.waterAmount();
        Map<String, Long> inert = new LinkedHashMap<>();

        for (Map.Entry<String, Long> e : in.ions().entrySet()) addSignature(model, totals, inert, e.getKey(), e.getValue());
        for (Map.Entry<String, Long> e : in.molecules().entrySet()) {
            if (e.getKey().equals(State.WATER)) continue;
            addSignature(model, totals, inert, e.getKey(), e.getValue());
        }
        // suspended minerals are added to totals further below together with the
        // metal-solid pool collection (see solve(); sediment is captured as seed
        // crystals by the caller via seedSediment)

        if (water <= 0) {
            // evaporite dry-out: no solvent, dissolved curve species crash out wholesale
            State dry = new State(in.temperatureC());
            for (Map.Entry<String, Long> e : in.ions().entrySet()) dry.adjustIon(e.getKey(), e.getValue());
            for (Map.Entry<String, Long> e : in.molecules().entrySet()) dry.adjustMolecule(e.getKey(), e.getValue());
            for (Map.Entry<String, Long> e : in.suspended().entrySet()) dry.adjustSuspended(e.getKey(), e.getValue());
            for (Map.Entry<String, Long> e : in.sediment().entrySet()) dry.adjustSediment(e.getKey(), e.getValue());
            for (Species sp : model.crystallisable()) {
                long form = Long.MAX_VALUE;
                for (Species.IonComponent c : sp.ions()) form = Math.min(form, dry.ionAmount(c.ionId()) / c.count());
                if (form == Long.MAX_VALUE || form <= 0) continue;
                for (Species.IonComponent c : sp.ions()) dry.adjustIon(c.ionId(), -form * c.count());
                dry.adjustSediment(sp.solute(), form);
            }
            suppressAutoionisation(dry); // dry-out path: no audit
            return new Result(dry, java.util.Map.of(), 0, in.totalUnits(), java.util.List.of());
        }

        long inputCharge = 0;
        for (Map.Entry<String, Long> e : in.ions().entrySet()) inputCharge += (long) Ion.chargeOf(e.getKey()) * e.getValue();
        if (inputCharge != 0) {
            System.err.println("[chemengine] warning: input is not charge-neutral (net " + inputCharge + ")");
        }

        double[] inputSpecies = new double[model.speciesCount() + model.minerals().size()];
        for (Map.Entry<String, Long> e : in.ions().entrySet()) {
            Integer idx = model.speciesIndexOf(e.getKey());
            if (idx != null) inputSpecies[idx] += e.getValue();
        }
        for (Map.Entry<String, Long> e : in.molecules().entrySet()) {
            if (e.getKey().equals(State.WATER)) continue;
            Integer idx = model.speciesIndexOf(e.getKey());
            if (idx != null) inputSpecies[idx] += e.getValue();
        }
        for (Map.Entry<String, Long> e : in.suspended().entrySet()) {
            for (int j = 0; j < model.minerals().size(); j++) {
                if (model.minerals().get(j).solidKey.equals(e.getKey())) {
                    inputSpecies[model.speciesCount() + j] += e.getValue();
                    break;
                }
            }
        }

        // Metal solids (displacement reactant+product) are tracked as pools only: they
        // do NOT feed the solution totals (the metal is neutral until displaced, and the
        // displacement progress releases the product ion / absorbs the reactant ion).
        Map<String, Long> metalInit = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : in.suspended().entrySet()) {
            boolean isMetal = false;
            for (SystemModel.Mineral m : model.minerals()) if (m.isDisplacement())
                if (m.solidKey.equals(e.getKey())
                    || (m.productSolidKey != null && m.productSolidKey.equals(e.getKey()))) { isMetal = true; break; }
            if (isMetal) metalInit.merge(e.getKey(), e.getValue(), Long::sum);
            else addMineral(model, totals, inert, e.getKey(), e.getValue());
        }

        Solver s = new Solver(model, totals, water, in.temperatureC(), in.stirring(),
            in.ions().getOrDefault(SystemModel.H_PLUS, 0L), inert, inputSpecies);
        s.metalInit.putAll(metalInit);
        s.seedSediment.putAll(in.sediment());
        return s.run(db, vessel, in.totalUnits());
    }

    private static void addSignature(SystemModel model, double[] totals, Map<String, Long> inert,
                                     String key, long amount) {
        Integer idx = model.speciesIndexOf(key);
        if (idx == null) { inert.merge(key, amount, Long::sum); return; }
        double[] coeff = model.speciesCoeff(idx);
        for (int c = 0; c < totals.length; c++) totals[c] += amount * coeff[c];
    }

    private static void addMineral(SystemModel model, double[] totals, Map<String, Long> inert,
                                   String key, long amount) {
        for (SystemModel.Mineral m : model.minerals()) {
            if (m.solidKey.equals(key)) {
                for (int c = 0; c < totals.length; c++) {
                    if (m.isDisplacement() && m.coeff[c] < 0) continue; // product solid's ion is already in solution
                    totals[c] += amount * m.coeff[c];
                }
                return;
            }
        }
        inert.merge(key, amount, Long::sum);
    }

    private Result run(SpeciesDatabase db, Vessel vessel, long feedUnits) {
        suppressedPair = 0;
        double[] t = totals.clone();
        Map<String, Long> gasVented = new LinkedHashMap<>();

        boolean[] present = new boolean[model.minerals().size()];
        double[] solidAmt = new double[model.minerals().size()];

        double[] n = null;
        for (int degas = 0; degas <= MAX_DEGAS; degas++) {
            n = phaseAssemble(t, present, solidAmt);
            if (vessel != Vessel.OPEN) break;

            long removed = 0;
            List<String> gases = new ArrayList<>(model.gasSpecies());
            gases.sort(String::compareTo);
            for (String gas : gases) {
                Integer idx = model.speciesIndexOf(gas);
                if (idx == null) continue;
                double ng = speciesAmount(idx, n);
                double retention = db == null ? GAS_SOLUBILITY_DEFAULT * water : model.gasSolubilityOf(db, gas) * water;
                double excess = ng - retention;
                if (excess > 0.5 * State.QUANTA_PER_MB) {
                    long x = (long) Math.max(State.QUANTA_PER_MB,
                        Math.floor(excess / State.QUANTA_PER_MB) * State.QUANTA_PER_MB);
                    double[] sig = model.speciesCoeff(idx);
                    for (int c = 0; c < t.length; c++) t[c] -= x * sig[c];
                    gasVented.merge(gas, x, Long::sum);
                    removed += x;
                }
            }
            if (removed == 0) break;
        }

        n = phaseAssemble(t, present, solidAmt);

        // Fix solids to whole units, then re-solve the aqueous part conditioned on
        // those integers. With solids fixed, every component's balance can be met
        // exactly by its free component species, so the integer projection is exact
        // (no subset-sum search, no non-unimodular rounding deadlocks).
        long[] solidInt = new long[present.length];
        double[] tAq = t.clone();
        // floor first (guarantees the aqueous totals stay non-negative), then greedily
        // round the largest fractional solids up while their stoichiometry still fits.
        // Pass 1: floor every present solid.
        for (int j = 0; j < present.length; j++) {
            if (!present[j]) continue;
            solidInt[j] = (long) Math.floor(solidAmt[j] + 1e-9);
        }
        // Pass 2: shared-metal-pool allocation. Several displacement minerals may consume
        // the SAME reactant metal (e.g. Zn(s)+Fe2+ & Zn(s)+Cu2+, or Fe(s)+Cu2+ & Fe(s)+acid).
        // Each metal pool is allocated to its consuming minerals in descending thermodynamic
        // preference (logK), each capped by its consumed-ion availability and the pool
        // budget — this preserves ordering (Zn displaces Cu before Fe; Fe reduces Cu2+
        // before H+).
        java.util.Map<String, java.util.List<Integer>> byMetal = new java.util.LinkedHashMap<>();
        for (int j = 0; j < present.length; j++) {
            if (!present[j] || !model.minerals().get(j).isDisplacement()) continue;
            byMetal.computeIfAbsent(model.minerals().get(j).solidKey, k -> new java.util.ArrayList<>()).add(j);
        }
        for (java.util.Map.Entry<String, java.util.List<Integer>> e : byMetal.entrySet()) {
            long remaining = metalInit.getOrDefault(e.getKey(), 0L);
            e.getValue().sort((a, b) -> Double.compare(
                model.minerals().get(b).logKEff, model.minerals().get(a).logKEff));
            for (int j : e.getValue()) {
                long cap = (long) displacementIonCap(model.minerals().get(j), t);
                long si = Math.max(0, Math.min(solidInt[j], Math.min(cap, remaining)));
                solidInt[j] = si;
                remaining -= si;
            }
        }
        // Pass 3: aqueous-total deduction for every present solid (displacement releases
        // the product ion / absorbs the reactant ion, so its total is INCREASED by +coeff*x,
        // opposite sign to a Ksp mineral).
        for (int j = 0; j < present.length; j++) {
            if (!present[j]) continue;
            SystemModel.Mineral mj2 = model.minerals().get(j);
            double[] nu = mj2.coeff;
            for (int c = 0; c < tAq.length; c++)
                tAq[c] += (mj2.isDisplacement() ? 1.0 : -1.0) * nu[c] * solidInt[j];
        }
        Integer[] solidOrder = new Integer[present.length];
        for (int j = 0; j < present.length; j++) solidOrder[j] = j;
        final double[] amtRef = solidAmt;
        java.util.Arrays.sort(solidOrder, java.util.Comparator
            .<Integer>comparingDouble(j -> present[j] ? amtRef[j] - Math.floor(amtRef[j]) : -1.0).reversed());
        for (int j : solidOrder) {
            if (!present[j]) continue;
            if (model.minerals().get(j).isDisplacement()) continue; // progress already clamped
            double frac = solidAmt[j] - Math.floor(solidAmt[j]);
            if (frac < 0.5 - 1e-9) continue;
            double[] nu = model.minerals().get(j).coeff;
            boolean ok = true;
            for (int c = 0; c < tAq.length; c++) {
                if (nu[c] > 0 && tAq[c] - nu[c] < -1e-6) { ok = false; break; }
            }
            if (ok) {
                solidInt[j] += 1;
                for (int c = 0; c < tAq.length; c++) tAq[c] -= nu[c];
            }
        }
        double energyJ;
        State out;
        List<String> rateLimited = java.util.List.of();
        if (model.hasRateLimited()) {
            out = projectKinetic(n, present, solidAmt, t);
            energyJ = neutralisationEnergy(out) + enthalpyEnergy(out);
            rateLimited = kineticHeld;
        } else {
            double[] nAq = newtonSolve(tAq, new boolean[present.length], new double[present.length]);
            out = projectExact(nAq, tAq, solidInt);
            energyJ = neutralisationEnergy(out) + enthalpyEnergy(out);
        }
        curveBalance(out);
        if (Boolean.getBoolean("chemengine.audit")) {
            auditState(out);
        }
        return new Result(out, gasVented, energyJ, feedUnits, rateLimited);
    }

    /** Audit violations collected while -Dchemengine.audit=true (diagnostics +
     * regression testing; empty otherwise). Each entry is one "state totals=... -> ..."
     * line as printed by auditState. */
    public static final java.util.List<String> auditViolations = new java.util.ArrayList<>();

    /** Number of mass-action checks (secondaries + minerals) actually evaluated by the
     *  last auditState run, i.e. NOT skipped as quantum-limited or rate-limited.
     *  A value of 0 means the audit verified charge neutrality only — every checkable
     *  equilibrium was below the 1 mB quantum threshold (see auditState). Tests use
     *  this to distinguish a real "clean" result from a vacuous one. */
    public static int auditChecksRun = 0;

    /**
     * Audit hook (-Dchemengine.audit=true): verify the projected output state is
     * physically self-consistent — charge neutrality, mass action (Q = K) for every
     * non-rate-limited secondary, and mineral saturation indices for present solids.
     * A state produced from a non-converged Newton solve violates these by orders of
     * magnitude (e.g. NO2 absorption used to emit H+ = thousands of times the input).
     *
     * <p>This is deliberately an ORDER-OF-MAGNITUDE guard, not a tight consistency
     * check: the tolerances are |log10(Q/K)| > 2 (100x) for secondaries and |SI| > 1
     * (10x) for minerals, and quantum-limited states (any participating species under
     * 1 mB) are skipped entirely. A clean audit means "no garbage states", not "mass
     * action holds to numerical precision" — concentration-level assertions belong in
     * the scenario tests (see PhysicsAuditTest).
     *
     * <p>Note: the 1 mB quantum threshold skips nearly every mass-action check in
     * practice (H+ is sub-mB at the equilibrium pH of most scenarios), so the audit is
     * mostly a charge-neutrality guard. {@link #auditChecksRun} reports how many
     * checks actually ran so tests can avoid vacuous passes.
     */
    private void auditState(State out) {
        auditChecksRun = 0;
        int C = model.componentCount();
        double water = out.waterAmount();
        if (water < 1e7) {
            // Quantum-limited regime (e.g. InvariantsTest: water = 1000 quanta,
            // ions of 1-80 quanta): sub-quantum equilibria cannot satisfy mass
            // action after integer projection, so Q/K checks are meaningless here.
            return;
        }
        java.util.List<String> bad = new java.util.ArrayList<>();
        // Compensate the sub-mB autoionisation pair removed by suppressAutoionisation:
        // Q/K and SI checks are against the pre-suppression H+ amount.
        java.util.function.LongSupplier hAudit = () -> out.ionAmount(SystemModel.H_PLUS) + suppressedPair;
        for (int i = C; i < model.speciesCount(); i++) {
            SystemModel.Secondary sec = model.secondaries().get(i - C);
            if (sec.rate > 0) continue; // kinetic entries legitimately off-equilibrium
            boolean redox = false;
            for (int c = 0; c < C; c++) {
                if (sec.coeff[c] != 0 && model.components().get(c).equals(SystemModel.ELECTRON)) redox = true;
            }
            if (redox) continue; // e- is a pseudo-species: its sub-quantum remainder in the
            // integer state makes the mass action uncheckable here (continuous solve is
            // verified by the newton residual audit instead).
            boolean componentsPresent = true;
            for (int c = 0; c < C; c++) {
                if (sec.coeff[c] != 0 && compAmount(out, c) == 0) {
                    componentsPresent = false;
                    break;
                }
            }
            if (!componentsPresent) continue; // inconclusive: a reactant component is absent
            double ns = out.ionAmount(model.speciesKey(i)) + out.moleculeAmount(model.speciesKey(i));
            // Quantum-limited regime: when any participating component is under 1 mB,
            // its quantum rounding (0/1 quanta, or a sub-mB H+ remainder) dominates
            // log-Q and makes the check a lattice artifact (e.g. H+ = 2 quanta vs a
            // continuous 0.021, inflating carbonate Q/K by 2-4 orders).
            boolean quantumLimited = ns > 0 && ns < State.QUANTA_PER_MB;
            for (int c = 0; c < C && !quantumLimited; c++) {
                if (sec.coeff[c] != 0 && compAmount(out, c) < State.QUANTA_PER_MB) quantumLimited = true;
            }
            if (quantumLimited) continue;
            auditChecksRun++;
            double logQ = Math.log10(Math.max(ns, 1e-300))
                + (sum(sec.coeff) - 1.0) * Math.log10(water);
            for (int c = 0; c < C; c++) {
                if (sec.coeff[c] == 0) continue;
                double nc = compAmount(out, c);
                logQ -= sec.coeff[c] * Math.log10(Math.max(nc, 1e-300));
            }
            double logK = sec.logKEffAt(tempC);
            if (ns == 0) {
                if (logQ - logK > 2.0) bad.add("missing " + sec.key + " despite Q/K "
                    + String.format("%.1f", logQ - logK));
            } else if (Math.abs(logQ - logK) > 2.0) {
                bad.add("Q/K dev " + String.format("%.1f", Math.abs(logQ - logK)) + " for " + sec.key
                    + " (logK " + String.format("%.2f", logK) + ")");
            }
        }
        for (int j = 0; j < model.minerals().size(); j++) {
            SystemModel.Mineral m = model.minerals().get(j);
            if (m.rate > 0) continue;
            boolean componentsPresent = true;
            for (int c = 0; c < C; c++) {
                if (m.coeff[c] != 0 && compAmount(out, c) == 0) {
                    componentsPresent = false;
                    break;
                }
            }
            if (!componentsPresent) continue; // inconclusive: a dissolution product is absent
            boolean quantumLimited = false;
            for (int c = 0; c < C; c++) {
                if (m.coeff[c] != 0 && compAmount(out, c) < State.QUANTA_PER_MB) quantumLimited = true;
            }
            if (quantumLimited) continue; // quantum-limited (see above)
            auditChecksRun++;
            double si = 0;
            for (int c = 0; c < C; c++) {
                if (m.coeff[c] == 0) continue;
                double nc = compAmount(out, c);
                si += m.coeff[c] * (Math.log10(Math.max(nc, 1e-300)) - Math.log10(water));
            }
            si -= m.logKEffAt(tempC);
            long solid = out.suspended().getOrDefault(m.solidKey, 0L);
            if (solid > 0 && Math.abs(si) > 1.0) {
                bad.add("present mineral " + m.solidKey + " SI=" + String.format("%.2f", si));
            } else if (solid == 0 && si > 1.0) {
                bad.add("absent but supersaturated mineral " + m.solidKey + " SI=" + String.format("%.2f", si));
            }
        }
        long charge = 0;
        for (java.util.Map.Entry<String, Long> e : out.ions().entrySet()) charge += (long) Ion.chargeOf(e.getKey()) * e.getValue();
        // Sub-mB H+/OH- rounding (projection remainder + autoionisation pair
        // suppression) is expected; the tests use the same mb(1) tolerance.
        if (Math.abs(charge) >= State.QUANTA_PER_MB) bad.add("net charge " + charge);
        if (!bad.isEmpty()) {
            StringBuilder fp = new StringBuilder();
            for (int c = 0; c < C; c++) {
                double v = out.ionAmount(model.components().get(c)) + out.moleculeAmount(model.components().get(c));
                if (Math.abs(v) > 1e-6) {
                    if (fp.length() > 0) fp.append(' ');
                    fp.append(model.components().get(c)).append('=').append((long) v);
                }
            }
            String msg = "state totals=[" + fp + "] tempC=" + tempC + " -> " + String.join("; ", bad);
            auditViolations.add(msg);
            System.err.println("[audit] PHYSICS VIOLATION " + msg);
        }
    }

    private static double sum(double[] a) {
        double s = 0;
        for (double v : a) s += v;
        return s;
    }

    /** Component amount in a state, with the suppressed autoionisation pair restored for H+. */
    private double compAmount(State out, int c) {
        String key = model.components().get(c);
        long v = out.ionAmount(key) + out.moleculeAmount(key);
        if (key.equals(SystemModel.H_PLUS)) v += suppressedPair;
        return v;
    }

    /** True when {@code key} is the REACTANT solid of any displacement mineral (a metal pool
     *  that is consumed, never a pre-existing product of another displacement). */
    private boolean isReactantMetal(String key) {
        for (SystemModel.Mineral m : model.minerals())
            if (m.isDisplacement() && m.solidKey.equals(key)) return true;
        return false;
    }

    /** Upper bound on a displacement mineral's progress x from the available consumed
     *  ions: x cannot consume more of any reactant ion than the system holds (t excludes
     *  the metal pool). This prevents the progress from driving a reactant ion (e.g. Cu2+
     *  with no copper input) negative. Returns 1e18 when the mineral has no reactant ions. */
    private double displacementIonCap(SystemModel.Mineral m, double[] t) {
        double cap = Double.POSITIVE_INFINITY;
        for (int c = 0; c < model.componentCount(); c++) {
            if (m.coeff[c] < 0) {
                double avail = t[c] / -m.coeff[c];
                if (avail < cap) cap = avail;
            }
        }
        return Double.isFinite(cap) ? cap : 1e18;
    }

    /** SI threshold above which an absent solid precipitates (supersaturation noise). */
    private static final double PHASE_ADD_SI = 1e-4;
    /** SI threshold below which a PRESENT solid is clearly undersaturated and must dissolve.
     *  Loose enough to absorb the ~1e-3 SI noise of integer projection on legitimately
     *  present solids (a converged barite reads SI=-0.003), tight enough to dissolve any
     *  phase whose ion product sits >~10x below its Ksp — independent of how much of it
     *  happens to be present. */
    private static final double PHASE_REMOVE_SI = -0.1;

    private double[] phaseAssembleGreedy(double[] t, boolean[] present, double[] solidAmt) {
        // Metal-displacement minerals are present from the start while the reactant
        // metal pool has material; their solidAmt is the displacement progress (solved
        // by Newton, clamped to the pool size in projection). They are not driven by SI
        // add/remove like Ksp minerals.
        for (int j = 0; j < present.length; j++) {
            SystemModel.Mineral m = model.minerals().get(j);
            if (m.isDisplacement() && metalInit.getOrDefault(m.solidKey, 0L) > 0) {
                present[j] = true;
                if (solidAmt[j] <= 0)
                    solidAmt[j] = Math.min(Math.min(metalInit.get(m.solidKey), 1e9),
                        displacementIonCap(m, t));
            }
        }
        double[] n = null;
        // Canonical order: most insoluble first (effective logK ascending), a deterministic
        // order for the add pass.
        Integer[] order = new Integer[model.minerals().size()];
        for (int j = 0; j < order.length; j++) order[j] = j;
        java.util.Arrays.sort(order, java.util.Comparator.comparingDouble(j -> model.minerals().get(j).logKEff));

        boolean[] presentSnap = null;
        double[] solidSnap = null;
        double[] nSnap = null;
        java.util.Set<Integer> failed = new java.util.HashSet<>();
        int lastAdded = -1;
        for (int it = 0; it < MAX_PHASE; it++) {
            n = newtonSolve(t, present, solidAmt);
            if (residualNorm(n, t, present, solidAmt) > 0.02 && presentSnap != null) {
                // the last phase move made the joint Newton diverge: keep the last
                // converged sub-assemblage (deterministic, mass/charge-conserving)
                // and try the remaining candidate phases instead of giving up.
                System.arraycopy(presentSnap, 0, present, 0, present.length);
                System.arraycopy(solidSnap, 0, solidAmt, 0, solidAmt.length);
                n = nSnap;
                if (lastAdded >= 0) {
                    failed.add(lastAdded);
                }
                lastAdded = -1;
                presentSnap = null;
                solidSnap = null;
                nSnap = null;
                continue;
            }
            boolean changed = false;

            // (1) Dissolve ANY present solid that is clearly undersaturated, regardless of
            //     how much of it is present. This is the general KKT rule (present => SI~0):
            //     a phase kept only because it is "large" while its ion product is far below
            //     Ksp is thermodynamically invalid and must go back into solution. (The old
            //     code only dissolved *tiny* undersaturated phases, which could strand a
            //     large phase that a later addition had driven undersaturated.)
            int mostUnder = -1;
            double mostUnderSi = PHASE_REMOVE_SI;
            for (int j = 0; j < present.length; j++) {
                if (!present[j] || model.minerals().get(j).isDisplacement()) continue;
                double si = mineralSI(j, n);
                if (si < mostUnderSi) { mostUnderSi = si; mostUnder = j; }
            }
            if (mostUnder >= 0) {
                presentSnap = present.clone();
                solidSnap = solidAmt.clone();
                nSnap = n;
                present[mostUnder] = false;
                solidAmt[mostUnder] = 0;
                changed = true;
            }
            if (changed) continue;

            // (2) Precipitate the most supersaturated absent phase.
            for (int j : order) {
                if (present[j] || failed.contains(j)) continue;
                double si = mineralSI(j, n);
                if (si > PHASE_ADD_SI) {
                    if (Boolean.getBoolean("chemengine.debug")) System.err.println("  add solid " + model.minerals().get(j).solidKey);
                    presentSnap = present.clone();
                    solidSnap = solidAmt.clone();
                    nSnap = n;
                    present[j] = true;
                    lastAdded = j;
                    double[] nu = model.minerals().get(j).coeff;
                    double m0 = Double.POSITIVE_INFINITY;
                    for (int c = 0; c < nu.length; c++) {
                        if (nu[c] > 0) m0 = Math.min(m0, t[c] / nu[c]);
                    }
                    solidAmt[j] = Double.isFinite(m0) ? Math.max(m0, 1.0) : 1.0;
                    changed = true;
                    break;
                }
            }
            if (!changed) break;
        }
        return n;
    }

    /** Cap on candidate (non-displacement) minerals for the exhaustive global search; beyond
     *  this (2^n subsets) we fall back to the greedy active-set assembly. */
    private static final int GLOBAL_PHASE_CAP = 14;

    private double[] phaseAssemble(double[] t, boolean[] present, double[] solidAmt) {
        int cand = 0;
        for (int j = 0; j < model.minerals().size(); j++)
            if (!model.minerals().get(j).isDisplacement()) cand++;
        if (cand > 0 && cand <= GLOBAL_PHASE_CAP) {
            return phaseAssembleGlobal(t, present, solidAmt);
        }
        return phaseAssembleGreedy(t, present, solidAmt);
    }

    /**
     * GLOBAL phase assemblage (Track B correctness). The mineral phase set is chosen so that
     * the equilibrated state has the MINIMUM total Gibbs free energy — the rigorous criterion,
     * free of the greedy active-set's local-optimum bias. Every subset of the candidate
     * (non-displacement) minerals is equilibrated (Newton), discarded if any absent mineral is
     * supersaturated (not a valid equilibrium assemblage), and scored by {@link #gibbsEnergy}.
     * The lowest-energy feasible subset is returned. Because the ideal-dilute mass-action
     * problem is convex, this KKT-consistent minimum-Gibbs set is the global optimum.
     */
    private double[] phaseAssembleGlobal(double[] t, boolean[] present, double[] solidAmt) {
        // displacement minerals forced present (as in greedy).
        for (int j = 0; j < present.length; j++) {
            SystemModel.Mineral m = model.minerals().get(j);
            if (m.isDisplacement() && metalInit.getOrDefault(m.solidKey, 0L) > 0) {
                present[j] = true;
                if (solidAmt[j] <= 0)
                    solidAmt[j] = Math.min(Math.min(metalInit.get(m.solidKey), 1e9),
                        displacementIonCap(m, t));
            }
        }
        java.util.List<Integer> cand = new java.util.ArrayList<>();
        for (int j = 0; j < model.minerals().size(); j++)
            if (!model.minerals().get(j).isDisplacement()) cand.add(j);

        boolean[] bestPresent = present.clone();
        double[] bestSolid = solidAmt.clone();
        double[] bestN = null;
        double bestG = Double.POSITIVE_INFINITY;
        int limit = 1 << cand.size();
        for (int mask = 0; mask < limit; mask++) {
            boolean[] p = present.clone();
            double[] sa = solidAmt.clone();
            for (int k = 0; k < cand.size(); k++) {
                int j = cand.get(k);
                boolean on = (mask & (1 << k)) != 0;
                p[j] = on;
                if (on) {
                    if (sa[j] <= 0) sa[j] = phaseSeedAmt(j, t);
                } else {
                    sa[j] = 0;
                }
            }
            double[] n = newtonSolve(t, p, sa);
            if (residualNorm(n, t, p, sa) > 0.02) continue; // this subset's Newton diverged
            boolean feasible = true;
            for (int j = 0; j < present.length; j++) {
                if (p[j] || model.minerals().get(j).isDisplacement()) continue;
                if (mineralSI(j, n) > PHASE_ADD_SI) { feasible = false; break; } // absent but supersaturated
            }
            if (!feasible) continue;
            double g = gibbsEnergy(n, sa, p);
            if (g < bestG) { bestG = g; bestPresent = p; bestSolid = sa; bestN = n; }
        }
        System.arraycopy(bestPresent, 0, present, 0, present.length);
        System.arraycopy(bestSolid, 0, solidAmt, 0, solidAmt.length);
        return bestN;
    }

    /** Initial solid amount seed for a present phase (the ion-limited dissolution capacity). */
    private double phaseSeedAmt(int j, double[] t) {
        double[] nu = model.minerals().get(j).coeff;
        double m0 = Double.POSITIVE_INFINITY;
        for (int c = 0; c < nu.length; c++) if (nu[c] > 0) m0 = Math.min(m0, t[c] / nu[c]);
        return Double.isFinite(m0) ? Math.max(m0, 1.0) : 1.0;
    }

    /**
     * Total Gibbs free energy of an equilibrated state, relative to the (mass-conserved)
     * pure-component reference, in units of RT. Constant terms that cancel under mass balance
     * are dropped, leaving
     *
     * <pre>G/RT ~ Σ_species n_s·ln(n_s/V) − Σ_secondary n_s·ln K_s + Σ_present m_j·ln K_j</pre>
     *
     * (K_s = formation constant, K_j = effective dissolution constant, both as used by the
     * equilibrium itself, so G is minimized exactly at the equilibrium). The solid term is
     * +m_j·ln K_j because μ°_j = Σcoeff·μ°_c + RT·ln K_j for the dissolution constant; an
     * insoluble solid (ln K_j < 0) therefore LOWERS the free energy when it forms. Used only
     * to COMPARE phase sets, so the additive constant is irrelevant.
     */
    private double gibbsEnergy(double[] n, double[] solidAmt, boolean[] present) {
        int C = model.componentCount();
        double G = 0;
        for (int c = 0; c < C; c++) {
            if (n[c] <= 0) continue;
            G += n[c] * (Math.log(n[c]) - Math.log(water));
        }
        for (int s = 0; s < model.secondaries().size(); s++) {
            double ns = speciesAmount(C + s, n);
            if (ns <= 0) continue;
            G += ns * (Math.log(ns) - Math.log(water));
            G -= ns * model.speciesLogKEffAt(C + s, tempC) * LN10;
        }
        for (int j = 0; j < present.length; j++) {
            if (!present[j]) continue;
            double mj = solidAmt[j];
            if (mj <= 0) continue;
            G += mj * model.minerals().get(j).logKEffAt(tempC) * LN10;
        }
        return G;
    }

    private double mineralSI(int j, double[] n) {
        SystemModel.Mineral m = model.minerals().get(j);
        double sum = 0;
        for (int c = 0; c < model.componentCount(); c++) {
            if (m.coeff[c] == 0) continue;
            if (n[c] <= 0) return Double.NEGATIVE_INFINITY;
            sum += m.coeff[c] * (Math.log(n[c]) - Math.log(water));
        }
        return sum - m.logKEffAt(tempC) * LN10;
    }

    /** Amount of one species from component amounts (0 if a positive-stoich component is absent). */
    double speciesAmount(int idx, double[] n) {
        double[] coeff = model.speciesCoeff(idx);
        double s = 0;
        for (double v : coeff) s += v;
        double logK = model.speciesLogKEffAt(idx, tempC);
        int secIdx = idx - model.componentCount();
        if (secIdx >= 0 && !Double.isNaN(secLogKOverride[secIdx])) logK = secLogKOverride[secIdx];
        double ln = logK * LN10 + (1.0 - s) * Math.log(water);
        for (int c = 0; c < coeff.length; c++) {
            double v = coeff[c];
            if (v > 0 && n[c] <= 0) return 0.0;
            if (v != 0) ln += v * Math.log(Math.max(n[c], 1e-300));
        }
        return Math.exp(ln);
    }

    /**
     * Components worth solving: non-zero totals, or components consumed by a feasible
     * secondary. A zero-total component is only activated by a secondary if every
     * positive-stoichiometry reactant of that secondary is itself active; otherwise the
     * secondary cannot exist and must not drag extra components into the basis.
     */
    int[] activeComponents(double[] t) {
        int C = model.componentCount();
        boolean[] active = new boolean[C];
        int count = 0;
        // The electron pseudo-species is special: pe = -log[e-] is a free variable shared
        // by every redox couple, NOT gated on a nonzero total. A chemically neutral input
        // (donor + acceptor present in exactly balancing amounts, e.g. 2FeCl2 + Cl2) has
        // t[e-] == 0, yet the couples must still be able to equilibrate by exchanging
        // electrons internally (the electrons are in the input species, not a reservoir).
        // Activating e- unconditionally turns on its mass-balance residual (which sums to
        // the conserved t[e-], often 0) and lets the two half-reactions share a solved pe.
        int electronIdx = -1;
        for (int c = 0; c < C; c++) {
            if (model.components().get(c).equals(SystemModel.ELECTRON)) { electronIdx = c; break; }
        }
        for (int c = 0; c < C; c++) {
            if (c == model.chargeBalanceIndex() || Math.abs(t[c]) > 0 || c == electronIdx) {
                active[c] = true;
                count++;
            }
        }

        boolean changed;
        do {
            changed = false;
            for (SystemModel.Secondary sec : model.secondaries()) {
                // A secondary is feasible only if all its positive-coefficient reactants
                // are already in the active set.
                boolean feasible = true;
                for (int c = 0; c < C; c++) {
                    if (sec.coeff[c] > 1e-12 && !active[c]) {
                        feasible = false;
                        break;
                    }
                }
                if (!feasible) continue;
                // If it can exist, every component it consumes (negative coefficient)
                // must be solved too.
                for (int c = 0; c < C; c++) {
                    if (sec.coeff[c] < -1e-12 && !active[c]) {
                        active[c] = true;
                        count++;
                        changed = true;
                    }
                }
            }
        } while (changed);

        int[] out = new int[count];
        int k = 0;
        for (int c = 0; c < C; c++) if (active[c]) out[k++] = c;
        return out;
    }

    private double[] newtonSolve(double[] t, boolean[] present, double[] solidAmt) {
        int C = model.componentCount();
        int M = 0;
        for (boolean b : present) if (b) M++;
        double best = Double.POSITIVE_INFINITY;
        double[] bestN = null;
        double[] bestSolid = null;
        double[] hSeeds = new double[]{ Double.NaN, 1e-7 * water, 1e-3 * water, 1e-11 * water, 1e-5 * water };
        for (double hSeed : hSeeds) {
            double[] n = newtonSolveSeeded(t, present, solidAmt, hSeed);
            double score = residualNorm(n, t, present, solidAmt);
            if (score < best) { best = score; bestN = n; bestSolid = solidAmt.clone(); }
        }
        // Stoichiometric pre-seed: pre-advance every secondary to half its limiting
        // reactant extent. Product-heavy equilibria (e.g. NO2 absorption, whose
        // mass-action is Q^5) explode from a reactant-only start (n_secondary -> 1e54,
        // NO2 mass-balance row ~1e-8, line search stalls in a false basin); starting
        // on the mass-feasible product side lets Newton descend smoothly instead.
        {
            double[] n = newtonSolveSeeded(t, present, solidAmt, BALANCED_SEED);
            double score = residualNorm(n, t, present, solidAmt);
            if (score < best) { best = score; bestN = n; bestSolid = solidAmt.clone(); }
        }
        System.arraycopy(bestSolid, 0, solidAmt, 0, solidAmt.length);
        if (Boolean.getBoolean("chemengine.audit") && best > 1e-4) {
            // Diagnostics: log only NON-converged Newton finals (residual above ~1e-4)
            // with a totals fingerprint so the scenario can be identified from stderr.
            // Converged solves are ~1e-8..1e-12 and would drown the signal.
            StringBuilder fp = new StringBuilder();
            for (int c = 0; c < t.length; c++) {
                if (Math.abs(t[c]) > 1e-6) {
                    if (fp.length() > 0) fp.append(' ');
                    fp.append(model.components().get(c)).append('=').append((long) t[c]);
                }
            }
            System.err.println("[audit] newton residual=" + best + " totals=[" + fp + "]");
        }
        return bestN;
    }

    private double residualNorm(double[] n, double[] t, boolean[] present, double[] solidAmt) {
        int M = 0;
        for (boolean b : present) if (b) M++;
        double[] mj = new double[M];
        int k = 0;
        for (int j = 0; j < present.length; j++) if (present[j]) mj[k++] = solidAmt[j];
        int[] active = activeComponents(t);
        int[] posOf = new int[model.componentCount()];
        java.util.Arrays.fill(posOf, -1);
        for (int i = 0; i < active.length; i++) posOf[active[i]] = i;
        double[] f = residuals(n, mj, t, present, active, posOf);
        return maxNorm(f);
    }

    private double[] newtonSolveSeeded(double[] t, boolean[] present, double[] solidAmt, double hSeedOverride) {
        int C = model.componentCount();
        int[] active = activeComponents(t);
        int A = active.length;
        int M = 0;
        for (boolean b : present) if (b) M++;
        int dim = A + M;

        int[] posOf = new int[C];
        Arrays.fill(posOf, -1);
        for (int i = 0; i < A; i++) posOf[active[i]] = i;

        double[] y = new double[dim];
        int hComp = model.chargeBalanceIndex();
        if (hSeedOverride == BALANCED_SEED) {
            // Component-space stoichiometric pre-seed (see newtonSolve):
            //   bal = t - minerals - 0.5 * sum(secondary extent * coeff)
            double tMax = 0;
            for (int c = 0; c < C; c++) tMax = Math.max(tMax, Math.abs(t[c]));
            double floor = Math.max(1e-9 * water, tMax * 1e-12);
            double[] bal = t.clone();
            for (int j = 0; j < present.length; j++) {
                if (!present[j]) continue;
                SystemModel.Mineral mj3 = model.minerals().get(j);
                double[] nu = mj3.coeff;
                // displacement adds +coeff*x (metal pool releases/absorbs the ions);
                // a Ksp mineral subtracts coeff*m (the solid holds those ions).
                double sign = mj3.isDisplacement() ? -1.0 : 1.0;
                for (int c = 0; c < C; c++) bal[c] -= sign * nu[c] * solidAmt[j];
            }
            for (SystemModel.Secondary sec : model.secondaries()) {
                double xi = Double.POSITIVE_INFINITY;
                for (int c = 0; c < C; c++) {
                    if (sec.coeff[c] > 1e-12 && bal[c] > 0) xi = Math.min(xi, bal[c] / sec.coeff[c]);
                }
                if (!Double.isFinite(xi)) continue; // no positive reactant (autoionisation etc.)
                double advance = 0.5 * xi;
                for (int c = 0; c < C; c++) bal[c] -= sec.coeff[c] * advance;
            }
            for (int i = 0; i < A; i++) {
                int c = active[i];
                double seed;
                if (c == hComp) {
                    seed = bal[c] > 0 ? bal[c] : 1e-14 * water * water / Math.max(1.0, -bal[c]);
                } else {
                    seed = Math.max(bal[c], floor);
                }
                y[i] = Math.log(Math.max(seed, 1e-9));
            }
        } else {
            for (int i = 0; i < A; i++) {
                int c = active[i];
                double seed;
                if (c == hComp) {
                    // Seed H from its own signed total: acidic -> free H, basic -> the
                    // matching OH- = Kw*V^2/nH, zero -> neutral pH.
                    double th = t[c];
                    if (!Double.isNaN(hSeedOverride)) seed = hSeedOverride;
                    else if (th > 0) seed = th;
                    else if (th < 0) seed = 1e-14 * water * water / (-th);
                    else seed = 1e-7 * water;
                    seed = Math.max(seed, 1e-9);
                } else {
                    seed = Math.abs(t[c]);
                    for (int j = 0; j < present.length; j++) {
                        if (!present[j]) continue;
                        SystemModel.Mineral mj4 = model.minerals().get(j);
                        double sc = mj4.isDisplacement() ? -1.0 : 1.0;
                        seed -= sc * mj4.coeff[c] * solidAmt[j];
                    }
                }
                y[i] = Math.log(Math.max(seed, 1e-9));
            }
        }
        int k = A;
        for (int j = 0; j < present.length; j++) {
            if (present[j]) y[k++] = Math.log(Math.max(solidAmt[j], 1e-6));
        }

        for (int it = 0; it < MAX_NEWTON; it++) {
            double[] nFull = new double[C];
            for (int i = 0; i < A; i++) nFull[active[i]] = Math.exp(y[i]);
            double[] mj = new double[M];
            k = 0;
            for (int j = 0; j < present.length; j++) if (present[j]) { mj[k] = Math.exp(y[A + k]); k++; }

            double[] f = residuals(nFull, mj, t, present, active, posOf);
            double maxF = 0, maxG = 0;
            for (int i = 0; i < A; i++) maxF = Math.max(maxF, Math.abs(f[i]));
            for (int j = 0; j < M; j++) maxG = Math.max(maxG, Math.abs(f[A + j]));
            if (maxF < TOL_F && maxG < TOL_G) break;

            double[][] J = analyticJacobian(nFull, mj, t, present, active, posOf);
            double[] d = solveLinear(J, negate(f));
            if (d == null) d = lmStep(J, f);   // singular Gauss -> damped normal step
            if (d == null) break;

            double step = 1.0;
            double base = maxNorm(f);
            boolean moved = false;
            for (int ls = 0; ls < 60; ls++) {
                double[] yt = y.clone();
                for (int i = 0; i < dim; i++) {
                    double stepI = step * d[i];
                    if (stepI > 2.0) stepI = 2.0;
                    if (stepI < -2.0) stepI = -2.0;
                    yt[i] += stepI;
                }
                double[] nt = new double[C];
                boolean bad = false;
                for (int i = 0; i < A; i++) { nt[active[i]] = Math.exp(yt[i]); if (!(nt[active[i]] > 0) || nt[active[i]] > 1e300) bad = true; }
                if (!bad) {
                    double[] mjt = new double[M];
                    int kk = 0;
                    for (int j = 0; j < present.length; j++) if (present[j]) { mjt[kk] = Math.exp(yt[A + kk]); if (!(mjt[kk] > 0) || mjt[kk] > 1e300) bad = true; kk++; }
                    if (!bad) {
                        double[] ft = residuals(nt, mjt, t, present, active, posOf);
                        if (allFinite(ft) && maxNorm(ft) < base) { y = yt; moved = true; break; }
                    }
                }
                step *= 0.5;
            }
            if (!moved) break;
        }

        double[] n = new double[C];
        for (int i = 0; i < A; i++) n[active[i]] = Math.exp(y[i]);
        k = A;
        for (int j = 0; j < present.length; j++) if (present[j]) solidAmt[j] = Math.exp(y[k++]);
        if (Boolean.getBoolean("chemengine.debug")) {
            double[] mj2 = new double[M];
            k = 0;
            for (int j = 0; j < present.length; j++) if (present[j]) mj2[k++] = solidAmt[j];
            double[] f = residuals(n, mj2, t, present, active, posOf);
            System.err.println("[chemengine] newton final maxF=" + maxNorm(Arrays.copyOf(f, A))
                + " G=" + Arrays.toString(Arrays.copyOfRange(f, A, f.length))
                + " m=" + Arrays.toString(mj2));
            System.err.println("[chemengine] f=" + Arrays.toString(f));
            System.err.println("[chemengine] active=" + Arrays.toString(active));
        }
        return n;
    }

    double[] residuals(double[] n, double[] mj, double[] t, boolean[] present,
                       int[] active, int[] posOf) {
        int A = active.length;
        double[] f = new double[A + mj.length];

        List<SystemModel.Secondary> secs = model.secondaries();
        double[] ns = new double[secs.size()];
        for (int s = 0; s < secs.size(); s++) ns[s] = speciesAmount(model.speciesCount() - secs.size() + s, n);

        for (int i = 0; i < A; i++) {
            int c = active[i];
            double sum = n[c];
            for (int s = 0; s < secs.size(); s++) sum += secs.get(s).coeff[c] * ns[s];
            int k = 0;
            for (int j = 0; j < present.length; j++) {
                if (!present[j]) continue;
                SystemModel.Mineral m = model.minerals().get(j);
                // displacement: material contribution is the NEGATIVE of the mass-action
                // coeff (Fe2+ -1 -> Fe2+ grows with progress x; Cu2+ +1 -> Cu2+ shrinks),
                // because the metal pool releases/absorbs the ions (t excludes the pool).
                sum += (m.isDisplacement() ? -1.0 : 1.0) * m.coeff[c] * mj[k++];
            }
            // row scale to O(1): mass-balance rows carry large amounts
            f[i] = (sum - t[c]) / Math.max(Math.abs(t[c]), 1.0);
        }

        int k = 0;
        for (int j = 0; j < present.length; j++) {
            if (!present[j]) continue;
            SystemModel.Mineral m = model.minerals().get(j);
            double sum = 0;
            for (int c = 0; c < model.componentCount(); c++) sum += m.coeff[c] * (Math.log(Math.max(n[c], 1e-300)) - Math.log(water));
            f[A + k++] = sum - m.logKEffAt(tempC) * LN10;
        }
        return f;
    }

    /**
     * Analytic Jacobian of {@link #residuals}. Variables are ln(component amounts) and
     * ln(solid amounts). Rows are mass balance (row-scaled by 1/max(|t|,1)) then mineral
     * mass action. Derived from the formation expressions:
     *   n_s = exp(logK_s*ln10 + (1-sum coeff)*ln V + sum coeff*ln n_c)
     *   d n_s / d y_c = coeff_sc * n_s ;  d m_j / d y_m = m_j.
     */
    double[][] analyticJacobian(double[] n, double[] mj, double[] t, boolean[] present,
                                int[] active, int[] posOf) {
        int A = active.length;
        int M = mj.length;
        int dim = A + M;
        double[][] J = new double[dim][dim];

        List<SystemModel.Secondary> secs = model.secondaries();
        int secBase = model.speciesCount() - secs.size();
        double[] ns = new double[secs.size()];
        for (int s = 0; s < secs.size(); s++) ns[s] = speciesAmount(secBase + s, n);

        int[] solidOfCol = new int[M];
        int k = 0;
        for (int j = 0; j < present.length; j++) if (present[j]) solidOfCol[k++] = j;

        for (int i = 0; i < A; i++) {
            int c = active[i];
            double rowScale = 1.0 / Math.max(Math.abs(t[c]), 1.0);
            for (int dd = 0; dd < A; dd++) {
                int d = active[dd];
                double v = (c == d ? n[c] : 0);
                for (int s = 0; s < secs.size(); s++) v += secs.get(s).coeff[c] * secs.get(s).coeff[d] * ns[s];
                // note: d(m_j)/d(y_component) = 0 — solids are their own variables
                J[i][dd] = v * rowScale;
            }
            for (int jj = 0; jj < M; jj++) {
                SystemModel.Mineral mj2 = model.minerals().get(solidOfCol[jj]);
                double sign = mj2.isDisplacement() ? -1.0 : 1.0;
                J[i][A + jj] = sign * mj2.coeff[c] * mj[jj] * rowScale;
            }
        }
        for (int jj = 0; jj < M; jj++) {
            SystemModel.Mineral m = model.minerals().get(solidOfCol[jj]);
            for (int dd = 0; dd < A; dd++) J[A + jj][dd] = m.coeff[active[dd]];
            for (int kk = 0; kk < M; kk++) J[A + jj][A + kk] = 0;
        }
        return J;
    }

    /**
     * Levenberg-Marquardt step: d = -(J^T J + lambda diag(J^T J))^-1 J^T f, with lambda
     * grown on singular normal equations. Handles the ill-conditioning of coupled
     * mass-balance / mass-action rows far better than a raw Gauss step.
     */
    private static double[] lmStep(double[][] J, double[] f) {
        int n = f.length;
        double[][] A = new double[n][n];
        double[] b = new double[n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double s = 0;
                for (int k = 0; k < n; k++) s += J[k][i] * J[k][j];
                A[i][j] = s;
            }
            double s = 0;
            for (int k = 0; k < n; k++) s += J[k][i] * f[k];
            b[i] = -s;
        }
        double lambda = 1e-4;
        for (int attempt = 0; attempt < 24; attempt++) {
            double[][] M = new double[n][n];
            for (int i = 0; i < n; i++) {
                System.arraycopy(A[i], 0, M[i], 0, n);
                M[i][i] = A[i][i] * (1.0 + lambda);
            }
            double[] d = solveLinear(M, b);
            if (d != null) return d;
            lambda *= 10.0;
        }
        return null;
    }

    private static double[] negate(double[] f) {
        double[] out = new double[f.length];
        for (int i = 0; i < f.length; i++) out[i] = -f[i];
        return out;
    }

    // ------------------------------------------------------------------ projection

    /** Coarse Arrhenius: kinetic rates double per 25 C above the 25 C reference. */
    private double arrhenius() {
        return Math.pow(2, (tempC - 25) / 25.0);
    }

    /**
     * Rate-limited equilibria: pull the full-equilibrium species vector back along each
     * slow entry's stoichiometry so that entry advances at most its affinity-law budget
     * this tick:
     *   budget = rate * water * 2^((T-25)/25) * stirring * |Q/K - 1|   (reaction units)
     * The full solve gives the equilibrium species vector; the extent each slow entry
     * would have moved is recovered by least-squares projection of (n_eq - n_0) onto the
     * entry's stoichiometry, then clamped to +-budget. Fast entries re-equilibrate on the
     * next tick (they are "instant" by definition, so one tick of residual fast-equilibrium
     * error is immaterial). This is first-order: it matches the old engine's per-entry cap
     * for one slow entry, and gives a deterministic, conservation-preserving approximation
     * when several slow entries share species.
     */
    private State projectKinetic(double[] n, boolean[] present, double[] solidAmt, double[] t) {
        int C = model.componentCount();
        int S = model.speciesCount();
        int M = model.minerals().size();
        int dim = S + M;

        // 1) full-equilibrium species vector
        double[] sp = new double[dim];
        for (int c = 0; c < C; c++) sp[c] = n[c];
        List<SystemModel.Secondary> secs = model.secondaries();
        for (int s = 0; s < secs.size(); s++) sp[C + s] = speciesAmount(C + s, n);
        for (int j = 0; j < M; j++) sp[S + j] = present[j] ? solidAmt[j] : 0.0;
        double[] delta = new double[dim];
        for (int i = 0; i < dim; i++) delta[i] = sp[i] - inputSpecies[i];
        // 2) collect every rate-limited entry with its species-space stoichiometry
        //    (secondaries: formation s = sum coeff*component; minerals: precipitation)
        record Slow(int kind, int index, double[] nu, double budget) {}
        List<Slow> slows = new ArrayList<>();
        for (int s = 0; s < secs.size(); s++) {
            SystemModel.Secondary sec = secs.get(s);
            if (sec.rate <= 0) continue;
            double[] nu = new double[dim];
            nu[C + s] = 1.0;
            for (int c = 0; c < C; c++) nu[c] = -sec.coeff[c];
            slows.add(new Slow(0, s, nu, budgetFor(sec.rate, quotientRatio(C + s, inputSpecies))));
        }
        for (int j = 0; j < M; j++) {
            SystemModel.Mineral m = model.minerals().get(j);
            if (m.rate <= 0) continue;
            double[] nu = new double[dim];
            nu[S + j] = 1.0;
            for (int c = 0; c < C; c++) nu[c] = -m.coeff[c];
            slows.add(new Slow(1, j, nu, budgetFor(m.rate, mineralQuotientRatio(j, inputSpecies))));
        }

        // 3) clamp each entry's extent to +-budget, most-driven first, in two passes
        //    (the passes smooth the order dependence when slow entries share species)
        kineticHeld.clear();
        for (int pass = 0; pass < 2; pass++) {
            // extents from the current residual delta
            double[] xi = new double[slows.size()];
            for (int i = 0; i < slows.size(); i++) {
                double[] nu = slows.get(i).nu();
                xi[i] = dot(nu, delta) / dot(nu, nu);
            }
            Integer[] order = new Integer[slows.size()];
            for (int i = 0; i < order.length; i++) order[i] = i;
            java.util.Arrays.sort(order, java.util.Comparator.comparingDouble(i -> -Math.abs(xi[i])));
            for (int idx : order) {
                Slow slow = slows.get(idx);
                double clamped = Math.max(-slow.budget(), Math.min(slow.budget(), xi[idx]));
                if (Math.abs(xi[idx] - clamped) > 1e-9) {
                    String key = slow.kind() == 0 ? secs.get(slow.index()).key
                        : model.minerals().get(slow.index()).solidKey;
                    if (!kineticHeld.contains(key)) kineticHeld.add(key);
                }
                // Never let a kinetic correction drive a species amount negative.
                // The least-squares extent can overdraw components when several slow
                // entries share them; confine the move to the feasible polytope.
                double step = xi[idx] - clamped;
                double maxPos = Double.POSITIVE_INFINITY;
                double minNeg = Double.NEGATIVE_INFINITY;
                for (int i = 0; i < dim; i++) {
                    double nu = slow.nu()[i];
                    if (nu > 1e-15) {
                        maxPos = Math.min(maxPos, sp[i] / nu);
                    } else if (nu < -1e-15) {
                        minNeg = Math.max(minNeg, sp[i] / nu);
                    }
                }
                if (step > maxPos) step = maxPos;
                if (step < minNeg) step = minNeg;
                for (int i = 0; i < dim; i++) sp[i] -= step * slow.nu()[i];
                for (int i = 0; i < dim; i++) delta[i] = sp[i] - inputSpecies[i];
            }
        }

        // 4) freeze: fix the pulled-back solids to whole units, and hold each slow
        //    aqueous entry at its post-move quotient so the fast re-solve leaves it.
        long[] solidInt = new long[M];
        for (int j = 0; j < M; j++) solidInt[j] = (long) Math.floor(Math.max(sp[S + j], 0) + 1e-9);
        // shared-metal-pool allocation (see projectExact): allocate each metal pool to its
        // consuming displacement minerals in descending logK preference, capped by consumed
        // ion availability and pool budget.
        java.util.Map<String, java.util.List<Integer>> byMetal = new java.util.LinkedHashMap<>();
        for (int j = 0; j < M; j++) {
            if (!model.minerals().get(j).isDisplacement()) continue;
            byMetal.computeIfAbsent(model.minerals().get(j).solidKey, k -> new java.util.ArrayList<>()).add(j);
        }
        for (java.util.Map.Entry<String, java.util.List<Integer>> e : byMetal.entrySet()) {
            long remaining = metalInit.getOrDefault(e.getKey(), 0L);
            e.getValue().sort((a, b) -> Double.compare(
                model.minerals().get(b).logKEff, model.minerals().get(a).logKEff));
            for (int j : e.getValue()) {
                long cap = (long) displacementIonCap(model.minerals().get(j), t);
                long si = Math.max(0, Math.min(solidInt[j], Math.min(cap, remaining)));
                solidInt[j] = si;
                remaining -= si;
            }
        }
        double[] tAq = t.clone();
        for (int j = 0; j < M; j++) {
            double[] nu = model.minerals().get(j).coeff;
            for (int c = 0; c < C; c++)
                tAq[c] += (model.minerals().get(j).isDisplacement() ? 1.0 : -1.0) * nu[c] * solidInt[j];
        }
        // round the largest fractional solids up while their stoichiometry still fits
        Integer[] solidOrder = new Integer[M];
        for (int j = 0; j < M; j++) solidOrder[j] = j;
        java.util.Arrays.sort(solidOrder, java.util.Comparator
            .<Integer>comparingDouble(j -> sp[S + j] - Math.floor(sp[S + j])).reversed());
        for (int j : solidOrder) {
            double frac = sp[S + j] - Math.floor(sp[S + j]);
            if (frac < 0.5 - 1e-9) continue;
            SystemModel.Mineral mm = model.minerals().get(j);
            boolean disp = mm.isDisplacement();
            double[] nu = mm.coeff;
            boolean ok = true;
            for (int c = 0; c < C; c++) {
                // deltaAq in tAq[c] from rounding this mineral's progress up by 1
                double deltaAq = (disp ? 1.0 : -1.0) * nu[c];
                if (deltaAq < 0 && tAq[c] + deltaAq < -1e-6) { ok = false; break; }
            }
            if (ok) {
                solidInt[j] += 1;
                for (int c = 0; c < C; c++) tAq[c] += (disp ? 1.0 : -1.0) * nu[c];
            }
        }
        java.util.Arrays.fill(secLogKOverride, Double.NaN);
        for (Slow slow : slows) {
            if (slow.kind() != 0) continue;
            int s = slow.index();
            SystemModel.Secondary sec = secs.get(s);
            // frozen logK = log10 of the entry's quotient at the pulled-back state
            double q = speciesQuotient(sp, C + s);
            secLogKOverride[s] = q;
        }

        // 5) re-solve the fast (instant) entries with slow entries frozen
        double[] nAq = newtonSolve(tAq, new boolean[M], new double[M]);
        java.util.Arrays.fill(secLogKOverride, Double.NaN);
        return projectExact(nAq, tAq, solidInt);
    }

    /** log10(Q) of a secondary's formation quotient at a species vector (freeze support). */
    private double speciesQuotient(double[] sp, int speciesIdx) {
        int C = model.componentCount();
        double[] coeff = model.speciesCoeff(speciesIdx);
        double sum = 0;
        for (double v : coeff) sum += v;
        double q = Math.log10(Math.max(sp[speciesIdx], 1e-300)) + (sum - 1.0) * Math.log10(water);
        for (int c = 0; c < C; c++) {
            if (coeff[c] == 0) continue;
            q -= coeff[c] * Math.log10(Math.max(sp[c], 1e-300));
        }
        return q;
    }

    /** |Q/K - 1| for an aqueous secondary at a species vector, clamped to [0,1000]. */
    private double quotientRatio(int speciesIdx, double[] sp) {
        int C = model.componentCount();
        double[] coeff = model.speciesCoeff(speciesIdx);
        double sum = 0;
        for (double v : coeff) sum += v;
        double q = sp[speciesIdx] * Math.pow(water, sum - 1.0);
        for (int c = 0; c < C; c++) {
            if (coeff[c] == 0) continue;
            if (sp[c] <= 0) return 1000.0; // absent reactant -> far from equilibrium
            q /= Math.pow(sp[c], coeff[c]);
        }
        double k = Math.pow(10, model.speciesLogKEffAt(speciesIdx, tempC));
        return clampDrive(Math.abs(q / k - 1.0));
    }

    /** |Q/K - 1| for a mineral dissolution quotient at a species vector. */
    private double mineralQuotientRatio(int j, double[] sp) {
        int C = model.componentCount();
        SystemModel.Mineral m = model.minerals().get(j);
        double q = 1.0;
        for (int c = 0; c < C; c++) {
            if (m.coeff[c] == 0) continue;
            if (sp[c] <= 0) return 1000.0;
            q *= Math.pow(sp[c] / water, m.coeff[c]);
        }
        double k = Math.pow(10, m.logKEffAt(tempC));
        return clampDrive(Math.abs(q / k - 1.0));
    }

    private static double clampDrive(double drive) {
        return Math.max(0.0, Math.min(drive, 1000.0));
    }

    /**
     * Per-tick kinetic budget in reaction units. Scale-relative: multiplying by the
     * water mass makes a small pot and a big tank settle on the same clock (the same
     * relative supersaturation moves the same fraction of the vessel per tick).
     * 20 game ticks = 1 second, so rate is "fraction of the water body converted per
     * tick at unit drive" and the real first-order rate is rate * 20 s^-1.
     */
    private double budgetFor(double rate, double drive) {
        return Math.max(1.0, rate * water * arrhenius() * stirring * drive);
    }

    private static double dot(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    /** Exact integer projection for an aqueous-only solve with solids already fixed. */
    private State projectExact(double[] n, double[] tAq, long[] solidInt) {
        int C = model.componentCount();
        int S = model.speciesCount();
        int M = model.minerals().size();

        long[] all = new long[S];
        // secondaries: floor their continuous amounts
        for (int i = C; i < S; i++) {
            all[i] = (long) Math.floor(speciesAmount(i, n) + 1e-9);
        }
        // components: exact integer remainder from their mass balance
        int h = model.chargeBalanceIndex();
        int electronIdx = -1;
        for (int c = 0; c < C; c++) if (model.components().get(c).equals(SystemModel.ELECTRON)) { electronIdx = c; break; }
        // Feasibility repair to a fixpoint: reducing a secondary while repairing one
        // component's over-consumption can push ANOTHER component's remainder negative
        // (e.g. a B4O7-2 secondary consumes 4 BO3-3 while the same repair pass already
        // handled a different ion), so repeat the whole pass until stable or no progress.
        for (int pass = 0; pass < 4; pass++) {
            boolean anyRepair = false;
            for (int c = 0; c < C; c++) {
                double used = 0;
                for (int i = C; i < S; i++) used += model.speciesCoeff(i)[c] * all[i];
                double xc = tAq[c] - used;
                // Generic feasibility repair: if secondary species overconsume a component,
                // reduce them until the remainder is non-negative. Not ion-specific.
                if (xc < -1e-6 && c != h) {
                    for (int i = C; i < S && xc < -1e-6; i++) {
                        double coeff = model.speciesCoeff(i)[c];
                        if (all[i] > 0 && coeff > 1e-12) {
                            long reduce = (long) Math.min(all[i], Math.max(1, (long) Math.ceil(-xc / coeff)));
                            all[i] -= reduce;
                            used -= coeff * reduce;
                            xc = tAq[c] - used;
                            anyRepair = true;
                        }
                    }
                }
            }
            if (!anyRepair) break;
        }
        for (int c = 0; c < C; c++) {
            double used = 0;
            for (int i = C; i < S; i++) used += model.speciesCoeff(i)[c] * all[i];
            double xc = tAq[c] - used;
            if (Math.abs(xc - Math.rint(xc)) > 1e-6) {
                throw new IllegalStateException("non-integer component remainder " + xc + " for " + c);
            }
            long x = (long) Math.rint(xc);
            if (x < 0) {
                if (c != h) {
                    if (c == electronIdx && x >= -ELECTRON_REMAINDER_TOL) {
                        // e- is a pseudo-species, not a real conserved ion: the continuous
                        // solve satisfies the electron mass balance (newton residual ~1e-14),
                        // so a small NEGATIVE integer remainder here is only the flooring
                        // artifact of e--coupled secondaries (each floored <1 quantum off).
                        // Clamp to 0 (no free electrons) — mirroring the audit, which already
                        // skips e- sub-quantum remainders as uncheckable. Real infeasibility
                        // (e.g. an oxidant with no reductant) shows as a far larger shortfall
                        // that Newton could not converge and is not masked by this tolerance.
                        x = 0;
                    } else {
                        throw new IllegalStateException("negative component remainder for " + c + ": " + x);
                    }
                }
                int ohIdx = model.speciesIndexOf(SystemModel.OH_MINUS);
                while (x < 0) { all[ohIdx] += 1; x += 1; }
            }
            all[c] = x;
        }

        State out = new State(tempC).stirring(stirring);
        out.water((long) water);
        for (int i = 0; i < S; i++) {
            if (all[i] <= 0) continue;
            String key = model.speciesKey(i);
            // e- is a pseudo-species: it carries charge (-1) and must be written to
            // the state as an ion so the output stays charge-neutral (dropping it
            // left a small net charge equal to the electron remainder).
            if (model.speciesCharge(i) != 0) out.ions(key, all[i]);
            else out.molecule(key, all[i]);
        }
        // aggregate total reactant-metal consumed across all displacement minerals, so a
        // metal pool shared by several reactions (e.g. Zn displacing both Fe2+ and Cu2+,
        // or Fe used for both Cu displacement and acid) is written back once, net of ALL
        // consumption.
        java.util.Map<String, Long> metalConsumed = new java.util.LinkedHashMap<>();
        for (int j = 0; j < M; j++) {
            SystemModel.Mineral m = model.minerals().get(j);
            if (m.isDisplacement() && solidInt[j] > 0) metalConsumed.merge(m.solidKey, solidInt[j], Long::sum);
        }
        for (int j = 0; j < M; j++) {
            SystemModel.Mineral m = model.minerals().get(j);
            if (!m.isDisplacement()) {
                if (solidInt[j] > 0) out.suspended(m.solidKey, solidInt[j]);
                continue;
            }
            // metal displacement: solidInt[j] is the progress x. Write back
            // reactant solid left = init - (total consumed by this metal) and
            // product solid grown = initProduct + x.
            long initReact = metalInit.getOrDefault(m.solidKey, 0L);
            // A metal that is the REACTANT of any displacement is consumed as a pool, never
            // a pre-existing product of another reaction (e.g. Fe(s) is a reactant of
            // Fe+Cu/Fe+acid and a product of Zn+Fe — the input Fe belongs to the reactant
            // pool, so Zn+Fe's "pre-existing product" must be 0).
            long initProd = (m.productSolidKey != null && !isReactantMetal(m.productSolidKey))
                ? metalInit.getOrDefault(m.productSolidKey, 0L) : 0L;
            long x = solidInt[j];
            long totalConsumed = metalConsumed.getOrDefault(m.solidKey, 0L);
            long reactantLeft = initReact - totalConsumed;
            long product = initProd + x;
            if (reactantLeft > 0) out.suspended(m.solidKey, reactantLeft);
            if (m.productGasKey != null) {
                // metal + acid displacement: product is a gas (H2/NO/NO2/SO2), written as
                // a molecule and vented by an open vessel
                if (product > 0) out.molecule(m.productGasKey, product);
            } else if (product > 0) {
                out.suspended(m.productSolidKey, product);
            }
        }
        for (Map.Entry<String, Long> e : inert.entrySet()) {
            if (e.getKey().contains(":")) out.molecule(e.getKey(), e.getValue());
            else out.ions(e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Long> e : seedSediment.entrySet()) {
            out.adjustSediment(e.getKey(), e.getValue());
        }
        suppressedPair = suppressAutoionisation(out);
        return out;
    }

    // ---------------------------------------------------------------- kinetics & energy

    /**
     * Solubility-curve crystallisation (kinetic by default), run after the equilibrium solve.
     *
     * <p>Saturation is judged from the ion activity product rather than only the salt's own
     * limiting ion. This is a generic common-ion model: any other dissolved salt that shares
     * an ion can push this salt past saturation (salting-out) without special-casing the ion.
     */
    private void curveBalance(State out) {
        long water = out.waterAmount();
        for (Species sp : model.crystallisable()) {
            long form = formableUnits(out, sp);
            long settled = out.sedimentAmount(sp.solute());

            if (water <= 0) {
                // evaporite dry-out: no solvent left, everything dissolved crashes out
                if (form > 0) {
                    for (Species.IonComponent c : sp.ions()) out.adjustIon(c.ionId(), -form * c.count());
                    out.adjustSediment(sp.solute(), form);
                }
                continue;
            }

            double threshold = sp.solubilityAt(tempC) / 100.0 * SOLUBILITY_SCALE;
            long cap = (long) Math.floor(threshold * water);
            double ksp = saturationProduct(sp, cap);
            double product = ionProduct(out, sp);

            if (product > ksp * (1.0 + 1e-12)) {
                // Supersaturated: find how much salt must leave solution to restore equilibrium.
                double maxPrecip = form;
                double need = precipitateToSaturate(out, sp, ksp, maxPrecip);
                double excess = Math.max(0.0, need);
                // Keep the original solubility-curve affinity for the unseeded nucleation
                // gate so existing metastable behaviour is preserved. Once crystals are
                // present, use the thermodynamic ion-product supersaturation so common-ion
                // salting-out has a real driving force.
                double affinity = settled > 0
                    ? (ksp > 0 ? product / ksp - 1.0 : 1.0)
                    : (cap > 0 ? (double) form / cap - 1.0 : 1.0);
                if (settled <= 0 && affinity < NUCLEATION_AFFINITY) continue; // metastable
                double rate = CRYSTAL_RATE_FRACTION * water * affinity * stirring;
                if (settled <= 0) rate *= NUCLEATION_PENALTY;
                long move = (long) Math.min(Math.ceil(excess), Math.max(1, Math.round(rate)));
                move = Math.min(move, form);
                if (move > 0) {
                    for (Species.IonComponent c : sp.ions()) out.adjustIon(c.ionId(), -move * c.count());
                    out.adjustSediment(sp.solute(), move);
                }
            } else if (settled > 0 && product < ksp * (1.0 - 1e-12)) {
                // Undersaturated with crystals present: dissolve back toward the saturation product.
                double need = dissolveToSaturate(out, sp, ksp, settled);
                long move = (long) Math.min(Math.ceil(need), settled);
                if (move > 0) {
                    for (Species.IonComponent c : sp.ions()) out.adjustIon(c.ionId(), move * c.count());
                    out.adjustSediment(sp.solute(), -move);
                }
            }
        }
    }

    /** Product of dissolved ion amounts raised to their stoichiometric coefficients. */
    private static double ionProduct(State out, Species sp) {
        double p = 1.0;
        for (Species.IonComponent c : sp.ions()) {
            p *= Math.pow(Math.max(out.ionAmount(c.ionId()), 0.0), c.count());
        }
        return p;
    }

    /** Saturation ion product corresponding to a salt amount {@code cap} at the curve. */
    private static double saturationProduct(Species sp, long cap) {
        double p = 1.0;
        for (Species.IonComponent c : sp.ions()) {
            p *= Math.pow(Math.max(c.count() * cap, 1.0), c.count());
        }
        return p;
    }

    /**
     * Amount of salt that must precipitate to bring the ion product down to {@code ksp}.
     * {@code maxPrecip} is the largest amount removable (the limiting ion).
     */
    private static double precipitateToSaturate(State out, Species sp, double ksp, double maxPrecip) {
        double lo = 0.0, hi = Math.max(0.0, maxPrecip);
        if (ionProductAfterPrecipitate(out, sp, hi) > ksp) return hi;
        for (int i = 0; i < 60; i++) {
            double mid = (lo + hi) / 2.0;
            if (ionProductAfterPrecipitate(out, sp, mid) > ksp) lo = mid;
            else hi = mid;
        }
        return hi;
    }

    /**
     * Amount of salt that must dissolve to bring the ion product up to {@code ksp},
     * capped by available solid {@code maxDissolve}.
     */
    private static double dissolveToSaturate(State out, Species sp, double ksp, double maxDissolve) {
        double lo = 0.0, hi = Math.max(0.0, maxDissolve);
        if (ionProductAfterDissolve(out, sp, hi) < ksp) return hi;
        for (int i = 0; i < 60; i++) {
            double mid = (lo + hi) / 2.0;
            if (ionProductAfterDissolve(out, sp, mid) < ksp) lo = mid;
            else hi = mid;
        }
        return hi;
    }

    private static double ionProductAfterPrecipitate(State out, Species sp, double x) {
        double p = 1.0;
        for (Species.IonComponent c : sp.ions()) {
            p *= Math.pow(Math.max(out.ionAmount(c.ionId()) - c.count() * x, 0.0), c.count());
        }
        return p;
    }

    private static double ionProductAfterDissolve(State out, Species sp, double x) {
        double p = 1.0;
        for (Species.IonComponent c : sp.ions()) {
            p *= Math.pow(Math.max(out.ionAmount(c.ionId()) + c.count() * x, 0.0), c.count());
        }
        return p;
    }

    private static long formableUnits(State out, Species sp) {
        long form = Long.MAX_VALUE;
        for (Species.IonComponent c : sp.ions()) {
            form = Math.min(form, out.ionAmount(c.ionId()) / c.count());
        }
        return form == Long.MAX_VALUE ? 0 : form;
    }

    /** Remove the sub-mB water autoionisation pair (sqrt(Kw)*V quanta each) so a solved
     *  vessel still stacks with a freshly packed bucket of the same composition. */
    private static long suppressAutoionisation(State out) {
        long h = out.ionAmount(SystemModel.H_PLUS);
        long oh = out.ionAmount(SystemModel.OH_MINUS);
        long pair = Math.min(h, oh);
        if (pair > 0 && pair < State.QUANTA_PER_MB) {
            out.adjustIon(SystemModel.H_PLUS, -pair);
            out.adjustIon(SystemModel.OH_MINUS, -pair);
        }
        return pair; // recorded for the physics audit (see auditState)
    }

    /**
     * Reaction enthalpy contribution from every secondary/mineral that carries a
     * PHREEQC-style delta_h. Extent is the change in that species' amount between
     * input and output, which is a simple generic proxy for reaction advance.
     */
    private double enthalpyEnergy(State out) {
        double energy = 0.0;
        int C = model.componentCount();
        int S = model.speciesCount();
        var secs = model.secondaries();
        for (int s = 0; s < secs.size(); s++) {
            SystemModel.Secondary sec = secs.get(s);
            // The OH- autoionisation secondary is the water-Kw pair: its heat is charged by
            // the lumped NEUTRALISATION_J_PER_PAIR term (Hy+/OH- pairs consumed), and its
            // written enthalpy (+55.91 dissociation) would double-count every OH- flux from
            // entries that already bake autoionisation into their authored delta_h.
            if (sec.isWaterKw) continue;
            double h = Double.isNaN(sec.deltaH) ? sec.heatKJ : sec.deltaH;
            if (Double.isNaN(h)) continue;
            double finalAmt = speciesAmountInState(out, C + s);
            double extent = sec.authoredStoich == 0 ? 0.0
                : (finalAmt - inputSpecies[C + s]) / sec.authoredStoich;
            double mm = Double.isNaN(sec.molarMass) ? 18.0 : sec.molarMass;
            energy += -h * extent * 1000.0 / (mm * State.QUANTA_PER_MB);
        }
        var mins = model.minerals();
        for (int j = 0; j < mins.size(); j++) {
            SystemModel.Mineral min = mins.get(j);
            double h = Double.isNaN(min.deltaH) ? min.heatKJ : min.deltaH;
            if (Double.isNaN(h)) continue;
            double finalAmt = out.suspendedAmount(min.solidKey);
            double extent = inputSpecies[S + j] - finalAmt;
            double mm = Double.isNaN(min.molarMass) ? 18.0 : min.molarMass;
            energy += -h * extent * 1000.0 / (mm * State.QUANTA_PER_MB);
        }
        return energy;
    }

    private double speciesAmountInState(State out, int speciesIdx) {
        String key = model.speciesKey(speciesIdx);
        if (model.speciesCharge(speciesIdx) != 0) return out.ionAmount(key);
        return out.moleculeAmount(key);
    }

    /** Net neutralisation heat: each free H+ consumed this tick = one H/OH pair (lumped). */
    private double neutralisationEnergy(State out) {
        long pairs = Math.max(0, inputFreeH - out.ionAmount(SystemModel.H_PLUS));
        return pairs * NEUTRALISATION_J_PER_PAIR;
    }

    /**
     * Open-vessel evaporation helper: vents {@code units} of water from the state and
     * returns the energy carried away (negative). Call between ticks, then solve again
     * so the next crystallisation sees the higher concentration.
     */
    public static double evaporate(State in, long units) {
        long vented = in.evaporateWater(units);
        return -vented * VAPORISATION_J_PER_UNIT;
    }

    private static boolean allZero(long[] v) {
        for (long x : v) if (x != 0) return false;
        return true;
    }

    private static double maxNorm(double[] v) {
        double m = 0;
        for (double x : v) m = Math.max(m, Math.abs(x));
        return m;
    }

    private static boolean allFinite(double[] v) {
        for (double x : v) if (!Double.isFinite(x)) return false;
        return true;
    }

    private static double[] solveLinear(double[][] A, double[] b) {
        int n = b.length;
        double[][] m = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, m[i], 0, n);
            m[i][n] = b[i];
        }
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int r = col + 1; r < n; r++) if (Math.abs(m[r][col]) > Math.abs(m[pivot][col])) pivot = r;
            if (Math.abs(m[pivot][col]) < 1e-12) return null;
            double[] tmp = m[col]; m[col] = m[pivot]; m[pivot] = tmp;
            for (int r = 0; r < n; r++) {
                if (r == col) continue;
                double f = m[r][col] / m[col][col];
                for (int c = col; c <= n; c++) m[r][c] -= f * m[col][c];
            }
        }
        double[] x = new double[n];
        for (int i = 0; i < n; i++) x[i] = m[i][n] / m[i][i];
        return x;
    }
}
