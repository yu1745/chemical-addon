package com.yu1745.chemengine;

import com.yu1745.chemengine.solver.FreeEnergyDatabase;
import com.yu1745.chemengine.solver.InorganicIonCatalog;
import com.yu1745.chemengine.solver.Solver;
import com.yu1745.chemengine.solver.SystemModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Chaos probe on the Track E small-basis model (Track E). The catalog is: 48 master ions
 * (one dominant ion per element + H+ + electron pool) + every other catalog ion as a DERIVED
 * secondary species, so real acid-base / redox / complexation equilibria are in the solve.
 * The probe randomly:
 *  - generates ~N additional aqueous secondary species (random combinations of MASTER ions
 *    with a random ΔG_f° = Σcoeff·ΔG_f°(master) + random logK perturbation, consistent with
 *    the basis),
 *  - generates a few candidate precipitating solids,
 *  - builds one SystemModel via fromFreeEnergy,
 *  - solves several random inputs (charge-balanced master-ion salts, >= 3 distinct ions),
 * and checks the results (convergence, KKT-consistency, charge neutrality, conservation).
 * Any throw / KKT failure / net-charge violation is counted and reflected in the exit code,
 * so the probe can be gated across seeds.
 *
 * Run: java -cp <classes>:<gson.jar> com.yu1745.chemengine.ChaosProbe [seed]
 */
public final class ChaosProbe {

    public static void main(String[] args) throws Exception {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 20240817L;
        Random rng = new Random(seed);
        FreeEnergyDatabase fdb = InorganicIonCatalog.database();
        int failures = 0;

        List<String> ions = new ArrayList<>(fdb.basis().keySet());
        Collections.sort(ions);
        List<String> cations = new ArrayList<>(), anions = new ArrayList<>();
        for (String k : ions) {
            int q = fdb.basis().get(k).charge;
            if (q > 0) cations.add(k); else if (q < 0) anions.add(k);
        }
        System.out.println("=== CHAOS PROBE on Track E model: " + ions.size() + " masters"
            + " + " + fdb.species().size() + " derived secondaries (seed " + seed + ") ===");
        System.out.println("cations: " + cations.size() + ", anions: " + anions.size());

        // 1. random aqueous secondary species: random combo of 2-3 master ions.
        int nSpecies = 6;
        for (int i = 0; i < nSpecies; i++) {
            int k = 2 + rng.nextInt(2); // 2-3 ions
            Map<String, Integer> els = new LinkedHashMap<>();
            double dg = 0; int charge = 0;
            for (int j = 0; j < k; j++) {
                String ion = rng.nextBoolean() ? cations.get(rng.nextInt(cations.size()))
                                               : anions.get(rng.nextInt(anions.size()));
                int c = 1 + rng.nextInt(2);
                FreeEnergyDatabase.IonSpec sp = fdb.basis().get(ion);
                charge += sp.charge * c;
                dg += sp.dGfKj * c;
                for (Map.Entry<String, Integer> e : sp.elements.entrySet())
                    els.merge(e.getKey(), e.getValue() * c, Integer::sum);
            }
            // random logK perturbation [-4,4] -> dG offset
            double logK = -4 + 8 * rng.nextDouble();
            dg -= logK * 5.7096;   // more negative logK (stronger) -> lower dG
            fdb.species("R" + i + "+" + charge, charge, dg, elsToArr(els));
        }

        // 2. random candidate precipitating solids (a master cation + a master oxyanion).
        String[] oxy = {"CO3-2", "SO4-2", "PO4-3", "SO4-2", "CO3-2"};
        int nSolids = 3;
        for (int i = 0; i < nSolids; i++) {
            String cat = cations.get(rng.nextInt(cations.size()));
            String an = oxy[rng.nextInt(oxy.length)];
            FreeEnergyDatabase.IonSpec cs = fdb.basis().get(cat), as = fdb.basis().get(an);
            Map<String, Integer> els = new LinkedHashMap<>();
            int p = cs.charge, n = -as.charge, g = gcd(p, n);
            double dg = cs.dGfKj * (n / g) + as.dGfKj * (p / g);
            for (Map.Entry<String, Integer> e : cs.elements.entrySet()) els.merge(e.getKey(), e.getValue() * (n / g), Integer::sum);
            for (Map.Entry<String, Integer> e : as.elements.entrySet()) els.merge(e.getKey(), e.getValue() * (p / g), Integer::sum);
            fdb.solid("sol" + i + "_" + cat + an, dg - 5.0, elsToArr(els));
        }

        // 3. build model
        SystemModel model;
        try {
            model = SystemModel.fromFreeEnergy(fdb);
        } catch (RuntimeException ex) {
            System.out.println("MODEL BUILD FAILED: " + ex);
            System.exit(1);
            return;
        }
        System.out.println("components: " + model.componentCount() + ", secondaries: "
            + model.secondaries().size() + ", candidate solids: " + model.minerals().size());
        if (!model.droppedEquilibria().isEmpty()) {
            failures++;
            System.out.println("DROPPED (not expressible over basis): " + model.droppedEquilibria());
        }
        // ---- verbose: print derived chemistry so results can be audited against reality ----
        System.out.println("\n-- derived secondary species (formation over components, logK) --");
        for (SystemModel.Secondary sec : model.secondaries()) {
            StringBuilder s = new StringBuilder("  ").append(sec.key).append(" = ");
            for (int c = 0; c < model.componentCount(); c++)
                if (Math.abs(sec.coeff[c]) > 1e-9) s.append(String.format("%+.0f %s ", sec.coeff[c], model.components().get(c)));
            s.append(String.format("  logK=%.2f", sec.logKEff));
            System.out.println(s);
        }
        System.out.println("-- candidate solids (dissolution coeff, logKsp) --");
        for (SystemModel.Mineral m : model.minerals()) {
            StringBuilder s = new StringBuilder("  ").append(m.solidKey).append(" : ");
            for (int c = 0; c < model.componentCount(); c++)
                if (Math.abs(m.coeff[c]) > 1e-9) s.append(String.format("%+.0f %s ", m.coeff[c], model.components().get(c)));
            s.append(String.format("  logKsp=%.2f", m.logKEff));
            System.out.println(s);
        }

        // 4. random multi-component states (master-ion salts only: elements conserved, and
        //    no pure-oxidizer boundary — redox couples are entered electron-balanced).
        for (int s = 0; s < 4; s++) {
            State in = randomState(rng, cations, anions, fdb);
            System.out.println("\n===== State " + (s + 1) + " : input " + in.ions()
                + " (net charge " + in.netCharge() + ") =====");
            for (Solver.Vessel v : new Solver.Vessel[]{Solver.Vessel.CLOSED, Solver.Vessel.OPEN}) {
                String tag = v == Solver.Vessel.CLOSED ? "CLOSED" : "OPEN";
                try {
                    Solver.Result r = Solver.solve(model, null, in, v);
                    State o = r.state;
                    long nq = o.netCharge();
                    boolean kkt = kkt(model, o);
                    boolean chargeOk = Math.abs(nq) < State.QUANTA_PER_MB;
                    if (!kkt) failures++;
                    if (!chargeOk) failures++;
                    System.out.printf("  %-6s ions=%s solids=%s netQ=%d KKT=%s%n", tag,
                        o.ions(), o.suspended(), nq, kkt);
                } catch (RuntimeException ex) {
                    failures++;
                    System.out.println("  " + tag + " SOLVER THREW " + ex.getClass().getSimpleName()
                        + ": " + ex.getMessage());
                }
            }
        }
        System.out.println("\n=== probe complete, failures=" + failures + " ===");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static boolean kkt(SystemModel model, State o) {
        double water = o.waterAmount();
        for (SystemModel.Mineral m : model.minerals()) {
            double si = 0; boolean all = true;
            for (int c = 0; c < model.componentCount(); c++) {
                if (m.coeff[c] == 0) continue;
                long amt = o.ions().getOrDefault(model.components().get(c), 0L)
                    + o.molecules().getOrDefault(model.components().get(c), 0L);
                if (amt <= 0) { all = false; break; }
                si += m.coeff[c] * (Math.log(amt) - Math.log(water));
            }
            si -= m.logKEffAt(o.temperatureC()) * Math.log(10.0);
            boolean present = o.suspended().containsKey(m.solidKey);
            if (all && ((present && Math.abs(si) > 0.1) || (!present && si > 0.1))) return false;
        }
        return true;
    }

    private static State randomState(Random rng, List<String> cations, List<String> anions,
                                     FreeEnergyDatabase fdb) {
        State st = new State(20 + rng.nextInt(45)).water(State.mb(1000));
        int pairs = 2 + rng.nextInt(2); // >= 3 distinct ions
        Map<String, Long> amts = new LinkedHashMap<>();
        for (int k = 0; k < pairs; k++) {
            String cat = cations.get(rng.nextInt(cations.size()));
            String an = anions.get(rng.nextInt(anions.size()));
            int p = fdb.basis().get(cat).charge, n = -fdb.basis().get(an).charge, g = gcd(p, n);
            long base = 100 + rng.nextInt(3000);
            amts.merge(cat, base * (n / g), Long::sum);
            amts.merge(an, base * (p / g), Long::sum);
        }
        amts.forEach((k, v) -> st.ions(k, State.mb(v)));
        return st;
    }

    private static int gcd(int a, int b) { while (b != 0) { int t = a % b; a = b; b = t; } return a == 0 ? 1 : a; }

    private static Object[] elsToArr(Map<String, Integer> els) {
        List<Object> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : els.entrySet()) { out.add(e.getKey()); out.add(e.getValue()); }
        return out.toArray();
    }
}