package com.yu1745.chemengine;

import com.yu1745.chemengine.solver.FreeEnergyDatabase;
import com.yu1745.chemengine.solver.Solver;
import com.yu1745.chemengine.solver.SystemModel;
import java.util.Map;

/**
 * Track B scenario probe: build a model purely from ΔG_f° (no reaction strings), solve a
 * multi-ion solution, and show that precipitation EMERGES from the thermodynamics.
 *
 * <p>Run with {@code java -cp <classes>:<gson.jar> com.yu1745.chemengine.FreeEnergyProbe}.
 */
public final class FreeEnergyProbe {

    /** Alkaline-earth carbonate/sulfate thermodynamic data (ΔG_f°, kJ/mol, 25 C). */
    private static FreeEnergyDatabase db() {
        return new FreeEnergyDatabase()
            // master ions (component basis)
            .basis("Ca+2", +2, -553.58, "Ca", 1)
            .basis("Ba+2", +2, -560.77, "Ba", 1)
            .basis("Sr+2", +2, -559.48, "Sr", 1)
            .basis("CO3-2", -2, -527.81, "C", 1, "O", 3)
            .basis("SO4-2", -2, -744.53, "S", 1, "O", 4)
            .basis("Cl-1", -1, -131.23, "Cl", 1)
            // aqueous secondary (speciation emerges from ΔG_f° too)
            .species("HCO3-1", -1, -586.77, "H", 1, "C", 1, "O", 3)
            // candidate precipitates: Ksp is DERIVED from ΔG_f°, never authored
            .solid("barite",     -1362.2, "Ba", 1, "S", 1, "O", 4)
            .solid("strontianite", -1140.1, "Sr", 1, "C", 1, "O", 3)
            .solid("witherite",  -1137.5, "Ba", 1, "C", 1, "O", 3)
            .solid("calcite",    -1129.0, "Ca", 1, "C", 1, "O", 3)
            .solid("celestite",  -1341.0, "Sr", 1, "S", 1, "O", 4)
            .solid("anhydrite",  -1321.8, "Ca", 1, "S", 1, "O", 4);
    }

    public static void main(String[] args) throws Exception {
        FreeEnergyDatabase fdb = db();
        SystemModel model = SystemModel.fromFreeEnergy(fdb);
        System.out.println("=== Track B: free-energy-driven model ===");
        System.out.println("components (" + model.componentCount() + "): " + model.components());
        System.out.println("secondaries: " + model.secondaries().size() + " (OH-1 autoionisation Kw + derived species)");
        System.out.println("minerals (candidate precipitates, Ksp from ΔG_f°):");
        for (SystemModel.Mineral m : model.minerals()) {
            StringBuilder sb = new StringBuilder("  ").append(m.solidKey).append(" : ");
            for (int c = 0; c < model.componentCount(); c++)
                if (m.coeff[c] != 0) sb.append(m.coeff[c]).append("*").append(model.components().get(c)).append(" ");
            sb.append(String.format("  logKsp=%.2f  logKEff(with offset)=%.2f", m.authoredLogK, m.logKEff));
            System.out.println(sb);
        }
        System.out.println("dropped (not expressible over basis): " + model.droppedEquilibria());

        System.out.println("\n=== Solve: Ba2+ + Sr2+ + Ca2+ + SO4-2 + CO3-2 in water ===");
        State in = new State(25).water(State.mb(1000))
            .ions("Ba+2", State.mb(50))
            .ions("Sr+2", State.mb(50))
            .ions("Ca+2", State.mb(100))
            .ions("SO4-2", State.mb(100))
            .ions("CO3-2", State.mb(100));
        System.out.println("input net charge: " + in.netCharge());
        System.out.println("input ions: " + in.ions());

        long baIn = in.ions().getOrDefault("Ba+2", 0L), srIn = in.ions().getOrDefault("Sr+2", 0L);
        long caIn = in.ions().getOrDefault("Ca+2", 0L), so4In = in.ions().getOrDefault("SO4-2", 0L);
        long co3In = in.ions().getOrDefault("CO3-2", 0L);

        Solver.Result r = Solver.solve(model, null, in, Solver.Vessel.CLOSED);
        State o = r.state;
        System.out.println("\noutput ions      : " + o.ions());
        System.out.println("output suspended : " + o.suspended());
        System.out.println("output net charge: " + o.netCharge());

        System.out.println("\n=== interpretation ===");
        long baOut = o.ions().getOrDefault("Ba+2", 0L) + o.molecules().getOrDefault("Ba+2", 0L);
        long srOut = o.ions().getOrDefault("Sr+2", 0L);
        long caOut = o.ions().getOrDefault("Ca+2", 0L);
        // carbonate total: free CO3 + HCO3 (both species) + bound in solids (each solid's C count)
        long co3Free = o.ions().getOrDefault("CO3-2", 0L);
        long hco3 = o.ions().getOrDefault("HCO3-1", 0L);
        long solidsCo3 = o.suspended().getOrDefault("calcite", 0L)
            + o.suspended().getOrDefault("strontianite", 0L)
            + o.suspended().getOrDefault("witherite", 0L);
        long baBound = o.suspended().getOrDefault("barite", 0L) + o.suspended().getOrDefault("witherite", 0L);
        long srBound = o.suspended().getOrDefault("strontianite", 0L);
        long caBound = o.suspended().getOrDefault("calcite", 0L);
        System.out.printf("Ba  : input %d -> free %d + barite/witherite %d (delta %d)%n",
            baIn, baOut, baBound, baIn - (baOut + baBound));
        System.out.printf("Sr  : input %d -> free %d + strontianite %d (delta %d)%n",
            srIn, srOut, srBound, srIn - (srOut + srBound));
        System.out.printf("Ca  : input %d -> free %d + calcite %d (delta %d)%n",
            caIn, caOut, caBound, caIn - (caOut + caBound));
        System.out.printf("SO4 : input %d -> free %d + barite %d (delta %d)%n",
            so4In, o.ions().getOrDefault("SO4-2", 0L), o.suspended().getOrDefault("barite", 0L),
            so4In - (o.ions().getOrDefault("SO4-2", 0L) + o.suspended().getOrDefault("barite", 0L)));
        System.out.printf("CO3 : input %d -> free %d + HCO3 %d + carbonate-solids %d (delta %d)%n",
            co3In, co3Free, hco3, solidsCo3, co3In - (co3Free + hco3 + solidsCo3));

        // ---- KKT check: present solids must have SI~0, absent solids SI<0 ----
        System.out.println("\n=== phase-assemblage KKT check (SI = log(IAP/Ksp)) ===");
        double water = o.waterAmount();
        java.util.Map<String, Long> ionAmt = o.ions();
        java.util.Map<String, Long> molAmt = o.molecules();
        boolean consistent = true;
        for (SystemModel.Mineral m : model.minerals()) {
            double si = 0;
            boolean allPresent = true;
            for (int c = 0; c < model.componentCount(); c++) {
                if (m.coeff[c] == 0) continue;
                long amount = ionAmt.getOrDefault(model.components().get(c), 0L)
                    + molAmt.getOrDefault(model.components().get(c), 0L);
                if (amount <= 0) { allPresent = false; break; }
                si += m.coeff[c] * (Math.log(amount) - Math.log(water));
            }
            si -= m.logKEffAt(o.temperatureC()) * Math.log(10.0);
            boolean present = o.suspended().containsKey(m.solidKey);
            String ok = "";
            if (present && Math.abs(si) > 0.05) { ok = "  <-- PRESENT but SI != 0"; consistent = false; }
            else if (!present && si > 0.05) { ok = "  <-- ABSENT but SI > 0"; consistent = false; }
            else if (!allPresent) ok = "  (inconclusive: a dissolution ion is absent)";
            System.out.printf("  %-12s %s  SI=%+8.3f%s%n", m.solidKey,
                present ? "PRESENT" : "absent  ", si, ok);
        }
        System.out.println("KKT-consistent phase assemblage? " + consistent);

        System.out.println("\nsolids precipitated (emergent from ΔG_f°-derived Ksp): " + o.suspended());
        System.out.println("least-soluble first (barite logKsp -9.97 vs strontianite -9.25 vs calcite -8.34):");
        System.out.println("  barite present? " + o.suspended().containsKey("barite"));
        System.out.println("  strontianite present? " + o.suspended().containsKey("strontianite"));
        System.out.println("  calcite present? " + o.suspended().containsKey("calcite"));
        System.out.println("net charge ~0? " + (Math.abs(o.netCharge()) < State.QUANTA_PER_MB));
    }
}
