package com.yu1745.chemengine.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 方案 A+B 验收：界面/液内二分（kind）+ 多反应协同积分。
 *
 * <p>A（隔离防线）：include=null 的全量发射只含 bulk；interface（SulAbsorb）必须显式
 * opt-in——防"虚拟大气"污染场景。
 *
 * <p>B（协同涌现）：SO₂ 涓流通入漂白液——SulAbsorb 持续造 Sul、Quench 即时吃 Sul+Hyp，
 * 两个速率方程联立自寻稳态：吸收通量 0.02·(H·P−Sul) = 淬灭通量 0.1·Hyp·Sul，
 * 理论稳态 Sul* = kLa·H·P / (kLa + k·Hyp) ≈ 0.0198 &lt; H·P=0.0248（被淬灭压制）。
 *
 * <p><b>度量教训</b>（三反应全开版实验 cp1 定谳）：punch 的 -totals 是 <b>molality</b>，
 * 而策展 formula 会消耗水（SO₂+H₂O→H₂SO₃，1.25 mol 吸收吃掉 ~4% 水），mol/kgw 全体
 * 虚涨（曾误读为"Na 增加"）；守恒断言必须乘 -water 换算成摩尔。另：Hyp 少、SO₂ 浓时
 * pH 崩溃会激活 AcidActivatesBleach 主导烧池（t=100s 烧光）——B 场景用涓流 + 排除该反应，
 * 三反应全开的行为留作已知语义（酸活化=真实化学）不再断言。
 */
class CuratedCouplingTest {

    private static final Curation C = Curation.load();

    private static ChemState bleachFeed() {
        return ChemState.builder("bleach for gas treatment")
                .waterKg(1.0)
                .pHCharge()
                .pe(4)
                .total("Na", 0.150)
                .total("Cl", 0.100)
                .total("Hyp", 0.050)
                .build();
    }

    // ==== A：kind 二分 ====

    @Test
    @DisplayName("A: 全量发射（include=null）只含 bulk——interface 不在场")
    void bulkOnlyByDefaultInterfaceOptIn() {
        String kin = C.kineticsBlock(1000);
        assertTrue(kin.contains("Quench"), kin);
        assertTrue(kin.contains("AcidActivatesBleach"), kin);
        assertFalse(kin.contains("SulAbsorb"), "interface 反应不得默认发射: \n" + kin);
        assertEquals(Curation.Kind.INTERFACE, C.reaction("SulAbsorb").kindEnum());
        assertEquals(Curation.Kind.BULK, C.reaction("Quench").kindEnum());
    }

    // ==== B：多反应协同 ====

    @Test
    @DisplayName("B: SO2 涓流通入漂白液：吸收与淬灭联立稳态（Sul 被压制、Hyp 单调耗、摩尔守恒）")
    void so2TrickleIntoBleachReachesQuenchedSteadyState() {
        try (IPhreeqc q = IPhreeqc.create()) {
            double p = 0.02;   // 涓流：稳态 Sul*≈0.0198（若 Quench 缺席则积累到 H·P=0.0248）
            IPhreeqc.RunResult r = q.run(bleachFeed().toSolutionScript(1) + "END\n"
                    + C.ratesBlock() + "END\nUSE solution 1\n"
                    + C.kineticsBlock(Set.of("SulAbsorb", "Quench"),
                            Map.of("SulAbsorb", new double[]{1.24, p}),
                            10, 100, 300)
                    + """
                    SELECTED_OUTPUT 1
                        -state    true
                        -time     true
                        -water    true
                        -totals   Cl  Hyp  Sul  S  Na
                        -pH       true
                    END
                    """);
            assertTrue(r.rowCount() >= 3, r.rawLines().toString());
            int last = r.rowCount() - 1;

            // 1) Sul 稳态被 Quench 压制在 H*P 之下（联立稳态 Sul*≈0.0198 < 0.0248）
            double sul = r.row(last).d("Sul");
            double henryCap = 1.24 * p;
            assertTrue(sul < henryCap * 0.95,
                    "Sul 应被淬灭压制在 H·P 之下，实测 " + sul + " vs H·P=" + henryCap);
            assertTrue(sul > henryCap * 0.5, "稳态 Sul 不应趋零（吸收仍在进行）: " + sul);

            // 2) Hyp 单调衰减（被持续淬灭）
            for (int i = 1; i < r.rowCount(); i++) {
                assertTrue(r.row(i).d("Hyp") <= r.row(i - 1).d("Hyp") + 1e-12,
                        "Hyp 单调衰减: step " + i);
            }
            double hyp = r.row(last).d("Hyp");
            assertTrue(hyp < 0.049, "Hyp 应被持续消耗，实测 " + hyp);

            // 3) 摩尔守恒（punch 为 molality，策展反应消耗水 → 必须乘水量换算）
            double kgw = r.row(last).d("mass_H2O");
            assertTrue(kgw > 0.95 && kgw <= 1.0, "水量应在 [0.95,1]（吸收耗水），实测 " + kgw);
            double clMol = r.row(last).d("Cl") * kgw;
            double sMol = r.row(last).d("S") * kgw;
            double naMol = r.row(last).d("Na") * kgw;
            double quenched = 0.050 - r.row(last).d("Hyp") * kgw;   // 每淬灭 1 Sul 耗 1 Hyp
            assertEquals(0.100 + quenched, clMol, 0.002,
                    "Cl 摩尔 = 初始 + 淬灭量（1:1）");
            assertEquals(quenched, sMol, 0.002, "S 摩尔 = 已淬灭量（全来自气源）");
            assertEquals(0.150, naMol, 1e-4, "Na 摩尔守恒（molality×水量换算精度内）");
        }
    }

    @Test
    @DisplayName("B: 负对照——无 Quench 时 Sul 自由积累到 H·P")
    void withoutQuenchSulAccumulatesToHenrySaturation() {
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.run(bleachFeed().toSolutionScript(1) + "END\n"
                    + C.ratesBlock() + "END\nUSE solution 1\n"
                    + C.kineticsBlock(Set.of("SulAbsorb"),
                            Map.of("SulAbsorb", new double[]{1.24, 0.02}),
                            10, 100, 300)
                    + """
                    SELECTED_OUTPUT 1
                        -state    true
                        -time     true
                        -water    true
                        -totals   Hyp  Sul
                        -pH       true
                    END
                    """);
            int last = r.rowCount() - 1;
            double sul = r.row(last).d("Sul");
            System.out.printf("负对照: Sul=%.4f Hyp=%.4f pH=%.3f%n",
                    sul, r.row(last).d("Hyp"), r.row(last).d("pH"));
            assertEquals(1.24 * 0.02, sul, 0.001, "无 Quench：Sul 自由积累到 H·P");
            assertEquals(0.050, r.row(last).d("Hyp") * r.row(last).d("mass_H2O"), 1e-6,
                    "Hyp 无人消耗");
        }
    }
}
