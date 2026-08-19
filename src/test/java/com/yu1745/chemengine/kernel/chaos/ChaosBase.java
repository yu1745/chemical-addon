package com.yu1745.chemengine.kernel.chaos;

import com.yu1745.chemengine.kernel.Curation;
import com.yu1745.chemengine.kernel.IPhreeqc;

/**
 * 混沌锅基类：随机混投多元素进 1 kg 水，打印 punch 结果供手工审查。
 */
class ChaosBase {

    static void runSoup(String label, String solutionBlock, String extraBlocks,
                        String[] elements, String[] phases, String[] siProbes) {
        Curation c = Curation.load();
        StringBuilder so = new StringBuilder();
        so.append("    SELECTED_OUTPUT 1\n        -state true\n        -water true\n")
          .append("        -pH true\n        -pe true\n")
          .append("        -totals ").append(String.join(" ", elements)).append("\n");
        if (phases.length > 0) {
            so.append("        -equilibrium_phases ").append(String.join(" ", phases)).append("\n");
        }
        if (siProbes.length > 0) {
            so.append("        -saturation_indices ").append(String.join(" ", siProbes)).append("\n");
        }
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.run("""
                    %s
                    END
                    """.formatted(solutionBlock)
                    + c.ratesBlock()
                    + """
                    USE solution 1
                    """ + extraBlocks
                    + (phases.length > 0 ? "    EQUILIBRIUM_PHASES 1\n" + phaseList(phases) : "")
                    + so + "END\n");
            System.out.println("=== " + label + " ===");
            System.out.println(r.rawLines().toString());
            int last = r.rowCount() - 1;
            var row = r.row(last);
            System.out.printf("[%s] row%d(last): pH=%.3f pe=%.3f mass_H2O=%.5f%n",
                    label, last, row.d("pH"), row.d("pe"), row.d("mass_H2O"));
            for (String e : elements) {
                System.out.printf("[%s]   total %-6s = %10.4f mmol/kgw (x mass_H2O = %.4f mmol)%n",
                        label, e, row.d(e) * 1000, row.d(e) * row.d("mass_H2O") * 1000);
            }
            for (String p : phases) {
                System.out.printf("[%s]   phase %-16s = %s mol%n", label, p, row.s(p));
            }
            for (String p : siProbes) {
                System.out.printf("[%s]   SI(%s) = %s%n", label, p, row.s("si_" + p));
            }
        }
    }

    private static String phaseList(String[] phases) {
        StringBuilder sb = new StringBuilder();
        for (String p : phases) sb.append("        ").append(p).append("    0  0\n");
        return sb.toString();
    }
}
