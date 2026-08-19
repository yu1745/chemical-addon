package com.yu1745.chemengine.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 一锅汤压力测试：多离子混投（5 阳离子 + 5 阴离子池 + 介稳池）同场演算。
 *
 * <p>同场过程：动力学淬灭（Quench）+ 五相平衡竞争（Calcite/Barite/Gypsum/Brucite/Witherite）
 * + 碳酸/硫酸/介稳池酸碱 + 电荷平衡 pH 涌现。这是旧引擎 §9「相竞争」的翻车区。
 *
 * <p>手工推演的预期（CLI 探针 /tmp/soup.phq 定标）：
 * <ul>
 *   <li>淬灭 1:1：Sul 15 全灭，Hyp 25→10，Cl 90→105，S 30→45（其中 5 进 Barite）；</li>
 *   <li>相竞争裁决：Calcite 吃掉几乎全部 Ca（Ksp 最小、C 过量），Gypsum 零沉淀——
 *       SO4 虽 45 mmol 但 Ca 已被 Calcite 锁死；</li>
 *   <li>Barite 定量沉淀（Ksp 1e-10 量级），Ba 留液 &lt;1e-6；Brucite/Witherite 不饱和零沉淀；</li>
 *   <li>Fe 是<b>白名单盲区</b>的活档案：无 Fe-Hyp 策展反应 → Fe 总量纹丝不动（介稳架构的
 *       设计性失明，修复方式 = chemistry.json 加一条反应）。</li>
 * </ul>
 */
class MultiIonSoupTest {

    @Test
    @DisplayName("一锅汤：淬灭+五相竞争+酸碱同场，全部质量账精确闭合")
    void multiIonSoupFullLedgerCloses() {
        Curation c = Curation.load();
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.run("""
                    SOLUTION 1 multi-ion soup
                        temp      25
                        pH        7 charge
                        pe        4
                        water     1 kg
                        Fe        10 mmol/kgw
                        Ca        20 mmol/kgw
                        Ba        5 mmol/kgw
                        Mg        10 mmol/kgw
                        Na        195 mmol/kgw
                        Cl        90 mmol/kgw
                        S         30 mmol/kgw
                        C         40 mmol/kgw
                        Hyp       25 mmol/kgw
                        Sul       15 mmol/kgw
                    END
                    """ + c.ratesBlock() + """
                    USE solution 1
                    KINETICS 1
                    Quench
                        -formula Cl 1 Hyp -1 O 4 S 1 Sul -1
                        -m        1000
                        -m0       1000
                        -steps    1000 10000 seconds
                    EQUILIBRIUM_PHASES 1
                        Calcite   0  0
                        Barite    0  0
                        Gypsum    0  0
                        Brucite   0  0
                        Witherite 0  0
                    SELECTED_OUTPUT 1
                        -state    true
                        -time     true
                        -water    true
                        -totals   Fe Ca Ba Mg Na Cl S C Hyp Sul
                        -molalities Ba+2 SO4-2 HCO3-
                        -equilibrium_phases Calcite Barite Gypsum Brucite Witherite
                        -pH       true
                        -pe       true
                    END
                    """);
            assertEquals(2, r.rowCount(), r.rawLines().toString());
            int last = r.rowCount() - 1;
            double w = r.row(last).d("mass_H2O");
            java.util.function.ToDoubleFunction<String> mol = col -> r.row(last).d(col) * w;
            double quenched = 0.015 - mol.applyAsDouble("Sul");

            System.out.printf("t=10000s: pH=%.2f Sul=%.2e Hyp=%.4f 淬灭=%.4f mol "
                            + "Calcite=%.4f Barite=%.4f Gypsum=%.1f%n",
                    r.row(last).d("pH"), mol.applyAsDouble("Sul"), mol.applyAsDouble("Hyp"), quenched,
                    r.row(last).d("Calcite"), r.row(last).d("Barite"),
                    r.row(last).d("Gypsum"));

            // === 动力学：淬灭完成、1:1 化学计量 ===
            assertTrue(mol.applyAsDouble("Sul") < 1e-4, "t=10000s Sul 应耗尽，实测 " + mol.applyAsDouble("Sul"));
            assertEquals(0.015, quenched, 1e-4, "淬灭量 = 初始 Sul");
            assertEquals(0.010, mol.applyAsDouble("Hyp"), 1e-4, "Hyp 25-15=10 mmol（1:1）");

            // === 全元素摩尔守恒（punch molality × 水量） ===
            assertEquals(0.010, mol.applyAsDouble("Fe"), 1e-5, "Fe 总量守恒（白名单盲区档案：无人动它）");
            assertEquals(0.195, mol.applyAsDouble("Na"), 1e-4, "Na 旁观守恒");
            assertEquals(0.010, mol.applyAsDouble("Mg"), 1e-5, "Mg 守恒（Brucite 未沉淀）");
            assertEquals(0.090 + quenched, mol.applyAsDouble("Cl"), 2e-4, "Cl = 初始 90 + 淬灭（1:1）");
            assertEquals(0.030 + quenched, mol.applyAsDouble("S") + r.row(last).d("Barite"), 2e-4,
                    "S = 液中 + Barite");
            assertEquals(0.040, mol.applyAsDouble("C") + r.row(last).d("Calcite"), 2e-4,
                    "C = 液中 + Calcite");
            assertEquals(0.020, mol.applyAsDouble("Ca") + r.row(last).d("Calcite"), 2e-4,
                    "Ca = 液中 + Calcite（Gypsum/Brucite 零）");

            // === 相竞争裁决 ===
            assertEquals(0.01996, r.row(last).d("Calcite"), 1e-3,
                    "Calcite 吃掉几乎全部 Ca（相竞争赢家）");
            assertEquals(0.0, r.row(last).d("Gypsum"), 1e-12,
                    "Gypsum 零沉淀：Ca 被 Calcite 锁死（旧引擎 §9 翻车场景，新内核免费做对）");
            assertEquals(0.005, r.row(last).d("Barite"), 1e-4, "Barite 定量沉淀");
            assertEquals(0.0, r.row(last).d("Brucite"), 1e-12, "Brucite 未饱和");
            assertEquals(0.0, r.row(last).d("Witherite"), 1e-12, "Witherite 未饱和");
            assertTrue(r.row(last).d("m_Ba+2") < 1e-6, "Ba 留液 <1e-6，实测 "
                    + r.row(last).d("m_Ba+2"));

            // === 酸碱：碳酸盐缓冲的弱碱，pe 涌现 ===
            assertTrue(r.row(last).d("pH") > 8.5 && r.row(last).d("pH") < 10.5,
                    "碳酸缓冲弱碱 pH，实测 " + r.row(last).d("pH"));
        }
    }
}
