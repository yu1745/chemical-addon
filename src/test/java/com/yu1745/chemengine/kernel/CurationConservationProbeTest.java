package com.yu1745.chemengine.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Native evidence for the JSON atom audit and the RATES mol/kgw/s contract. */
class CurationConservationProbeTest {
    private static final Curation CURATION = Curation.load();

    @Test
    @DisplayName("Quench 同 molality 的反应 extent 按水 kg 成比例")
    void quenchExtentScalesWithWaterMass() {
        double oneKg = quenchExtentMol(1.0);
        double twoKg = quenchExtentMol(2.0);
        assertTrue(oneKg > 0, "1 kg 应有正反应 extent");
        assertEquals(oneKg * 2.0, twoKg, oneKg * 0.02,
                "RATES r 为 mol/kgw/s，SAVE 必须以总水 kg 转为本体系 mol");
    }

    @Test
    @DisplayName("补齐 O 的 Fe/Nitra→Nitri：原生 Fe(2)/Fe(3) 与总 Fe 均可观测")
    void ferrousNitrateNativeValenceProbe() {
        ChemState feed = ChemState.builder("ferrous + curated nitrate")
                .waterKg(1.0).pHCharge().pe(0)
                .total("Fe", 0.010).total("Na", 0.010).total("Nitra", 0.020)
                .build();
        double baselineFe2;
        double baselineFe3;
        double baselineNitra;
        double baselineNitri;
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult baseline = q.run(feed.toSolutionScript(1) + "END\n"
                    + CURATION.ratesBlock() + "END\nUSE solution 1\n"
                    + CURATION.kineticsBlock(Set.of("FerrousReducesNitrate"), null, 0)
                    + selectedOutput());
            int last = baseline.rowCount() - 1;
            baselineFe2 = mol(baseline, last, "Fe(2)");
            baselineFe3 = mol(baseline, last, "Fe(3)");
            baselineNitra = mol(baseline, last, "Nitra");
            baselineNitri = mol(baseline, last, "Nitri");
        }
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.run(feed.toSolutionScript(1) + "END\n"
                    + CURATION.ratesBlock() + "END\nUSE solution 1\n"
                    + CURATION.kineticsBlock(Set.of("FerrousReducesNitrate"), null, 1000, 10_000)
                    + selectedOutput());
            int last = r.rowCount() - 1;
            double nitra = mol(r, last, "Nitra");
            double nitri = mol(r, last, "Nitri");
            double fe2 = mol(r, last, "Fe(2)");
            double fe3 = mol(r, last, "Fe(3)");
            System.out.printf("[curation conservation] Fe2 %.8g→%.8g Fe3 %.8g→%.8g; Nitra=%.8g Nitri=%.8g Fe=%.8g pH=%.6g pe=%.6g%n",
                    baselineFe2, fe2, baselineFe3, fe3,
                    nitra, nitri, mol(r, last, "Fe"),
                    r.row(last).d("pH"), r.row(last).d("pe"));
            assertEquals(0.020, nitra + nitri, 1e-5, "N 原子仍留在两个 N 池");
            assertEquals(0.010, fe2 + fe3, 1e-5, "Fe 总原子库存守恒");
            double nitraExtent = baselineNitra - nitra;
            double nitriExtent = nitri - baselineNitri;
            assertTrue(nitraExtent > 1e-6, "Nitra→Nitri 必须有正的 KINETICS extent");
            assertEquals(nitraExtent, nitriExtent, 1e-6, "Nitra/Nitri 的 N 原子按 1:1 转移");
            assertEquals(2.0 * nitriExtent, baselineFe2 - fe2, 1e-6,
                    "补齐 O 后原生 Fe(2) 损失 = 2 × 新增 Nitri");
            assertTrue(fe2 < baselineFe2, "补齐 O 的 KINETICS 后 Fe(2) 必须实际下降");
            assertTrue(fe3 > baselineFe3, "补齐 O 的 KINETICS 后 Fe(3) 必须实际上升");
        }
    }

    private static double quenchExtentMol(double waterKg) {
        ChemState feed = ChemState.builder("curated quench " + waterKg + " kg")
                .waterKg(waterKg).pHCharge().pe(4)
                // ChemState totals are vessel mol, so retain the same molality as water changes.
                .total("Na", 0.180 * waterKg).total("Cl", 0.100 * waterKg)
                .total("Hyp", 0.050 * waterKg).total("Sul", 0.010 * waterKg)
                .build();
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.run(feed.toSolutionScript(1) + "END\n"
                    + CURATION.ratesBlock() + "END\nUSE solution 1\n"
                    + CURATION.kineticsBlock(Set.of("Quench"), null, 1)
                    + """
                    SELECTED_OUTPUT 1
                        -water true
                        -high_precision true
                        -totals Sul
                    END
                    """);
            int last = r.rowCount() - 1;
            return 0.010 * waterKg - mol(r, last, "Sul");
        }
    }

    private static String selectedOutput() {
        return """
                SELECTED_OUTPUT 1
                    -state true
                    -time true
                    -water true
                    -high_precision true
                    -totals Nitra Nitri Fe Fe(2) Fe(3)
                    -pH true
                    -pe true
                END
                """;
    }

    private static double mol(IPhreeqc.RunResult result, int row, String column) {
        return result.row(row).d(column) * result.row(row).d("mass_H2O");
    }
}
