package com.yu1745.chemengine.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Track C 迁移第一批（G1c 后）：旧引擎（tag self-engine-final）工业场景在 IPhreeqc
 * 内核上的重表达。蓝图来源 {@code com.yu1745.chemengine.industrial.*Process}（旧方言），
 * 此处按 PHREEQC 语义重写为验收标准。
 *
 * <p>场景选择原则：优先旧引擎翻车点（相竞争 §9、电解 §10、气液界面）+ 三酸两碱经典。
 */
class IndustrialAcceptanceTest {

    private static double mmol(IPhreeqc.RunResult r, int row, String col) {
        return r.row(row).d(col) * 1000.0;
    }

    @Test
    @DisplayName("盐酸+烧碱中和：等摩尔 → pH 7、NaCl 盐水（旧引擎最经典场景）")
    void hclPlusNaohNeutralisation() {
        try (IPhreeqc q = IPhreeqc.create()) {
            // 50 mmol NaOH：Na 50 mmol + pH charge → [OH-]=50 mmol（定值 pH 会电荷失衡）
            // 50 mmol HCl：REACTION 加入
            IPhreeqc.RunResult r = q.run("""
                    SOLUTION 1 NaOH
                        pH        7 charge
                        pe        4
                        Na        50 mmol/kgw
                    END
                    USE solution 1
                    REACTION 1
                        HCl 1
                        50 mmol in 1 step
                    SELECTED_OUTPUT 1
                        -state    true
                        -totals   Na  Cl
                        -molalities H+  OH-  Na+  Cl-
                        -pH       true
                    END
                    """);
            int last = r.rowCount() - 1;
            double ph = r.row(last).d("pH");
            System.out.printf("中和后 pH=%.3f Na=%s Cl=%s%n",
                    ph, r.row(last).s("Na"), r.row(last).s("Cl"));
            assertEquals(7.0, ph, 0.2, "等摩尔强酸强碱 → pH 7");
            assertEquals(50.0, mmol(r, last, "Na"), 0.5, "钠守恒（punch 精度内）");
            assertEquals(50.0, mmol(r, last, "Cl"), 0.5, "氯守恒");
        }
    }

    @Test
    @DisplayName("索尔维碳化：饱和盐水+氨+CO₂(加压) → NaHCO₃(s)↓ + NH4Cl（旧引擎 §9 相竞争翻车场景）")
    void solvayCarbonationPrecipitatesNahcolite() {
        try (IPhreeqc q = IPhreeqc.create()) {
            // 真实索尔维条件：饱和盐水 ~4.4 molal、氨过量、CO2 ~6 atm、降温（NaHCO3 溶解度随 T 降）
            IPhreeqc.RunResult r = q.run("""
                    SOLUTION 1 saturated brine + ammonia (Solvay feed, 15C)
                        temp      15
                        pH        9 charge
                        pe        -4
                        Na        4.4 mol/kgw
                        Cl        4.4 mol/kgw
                        N         4.6 mol/kgw
                        C         4.0 mol/kgw
                    END
                    USE solution 1
                    EQUILIBRIUM_PHASES 1
                        Nahcolite  0    10
                        CO2(g)     0.8 10
                    SELECTED_OUTPUT 1
                        -state    true
                        -totals   Na  Cl  N
                        -molalities NH4+  HCO3-  Na+
                        -equilibrium_phases Nahcolite
                        -pH       true
                    END
                    """);
            int last = r.rowCount() - 1;
            double nahcolite = r.row(last).d("Nahcolite");
            double precipitated = nahcolite - 10.0;   // 初始投 10 mol
            double naInSolution = r.row(last).d("Na");
            double nh4 = r.row(last).d("m_NH4+");
            System.out.printf("NaHCO3(s)沉淀=%.2f mol Na(液)=%.3f NH4+=%.3f pH=%.2f%n",
                    precipitated, naInSolution, nh4, r.row(last).d("pH"));
            assertTrue(precipitated > 3.0,
                    "Nahcolite 应显著沉淀（索尔维主反应），实测 " + precipitated + " mol");
            assertTrue(precipitated < 4.4, "不可能超过投料钠 4.4 mol");
            assertEquals(4.4, precipitated + naInSolution, 0.15,
                    "钠守恒：沉淀 + 液中 = 4.4 mol");
            assertTrue(nh4 > 4.0, "NH4+ 应大量生成（副产氯化铵），实测 " + nh4);
        }
    }

    @Test
    @DisplayName("氯碱电解：REACTION 受迫进度 → NaOH + Cl- 线性耗尽（旧 D1b 语义）")
    void chlorAlkaliElectrolysisForcedProgress() {
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.run("""
                    SOLUTION 1 brine for electrolysis
                        pH        7 charge
                        pe        4
                        Na        1.0 mol/kgw
                        Cl        1.0 mol/kgw
                    END
                    USE solution 1
                    REACTION 1
                        Cl -2  H  2  O  2
                        0.25 mol in 4 steps
                    SELECTED_OUTPUT 1
                        -state    true
                        -step     true
                        -totals   Na  Cl
                        -molalities Na+  Cl-  OH-
                        -pH       true
                    END
                    """);
            assertEquals(4, r.rowCount(), r.rawLines().toString());
            // 每步: 移除 0.5 mol Cl(以 Cl2 逸出), 生成 0.5 mol OH-(留在液中); Na+ 旁观
            for (int i = 0; i < 4; i++) {
                double cl = r.row(i).d("Cl");
                double oh = r.row(i).d("m_OH-");
                assertEquals(1.0 - 0.125 * (i + 1), cl, 1e-6, "step " + (i + 1) + " Cl 线性耗尽");
                assertEquals(0.125 * (i + 1), oh, 1e-6, "step " + (i + 1) + " OH- 线性生成");
                assertEquals(1.0, r.row(i).d("Na"), 1e-6, "Na+ 旁观不动");
            }
            assertTrue(r.row(3).d("pH") > 13.0, "碱液 pH 应强碱性: " + r.row(3).d("pH"));
        }
    }

    @Test
    @DisplayName("石灰石酸浸：HCl 溶解方解石 → CaCl2 溶液（闭合体系 CO₂ 留液）")
    void hclDissolvesLimestone() {
        try (IPhreeqc q = IPhreeqc.create()) {
            // "Cl 100 mmol + pH 7 charge" = 精确 100 mmol HCl（charge 把 pH 拉到 ~1）
            IPhreeqc.RunResult r = q.run("""
                    SOLUTION 1 dilute HCl
                        pH        7 charge
                        pe        4
                        Cl        100 mmol/kgw
                    END
                    USE solution 1
                    EQUILIBRIUM_PHASES 1
                        Calcite   0    50
                    SELECTED_OUTPUT 1
                        -state    true
                        -totals   Ca  Cl  C
                        -molalities Ca+2  HCO3-
                        -equilibrium_phases Calcite
                        -pH       true
                    END
                    """);
            int last = r.rowCount() - 1;
            double ca = mmol(r, last, "Ca");
            double ph = r.row(last).d("pH");
            System.out.printf("Ca=%.2f mmol pH=%.2f (酸耗尽后回升)%n", ca, ph);
            // 闭合体系酸耗尽后 HCO3- 主导（~1 H+/CaCO3 边际）：Ca 介于 50-62
            assertTrue(ca > 50 && ca < 62, "Ca 计量溶解（酸耗尽饱和），实测 " + ca);
            assertEquals(100.0, mmol(r, last, "Cl"), 0.5, "氯守恒");
            assertEquals(ca, mmol(r, last, "C"), 0.5, "碳守恒（闭合：C==Ca）");
            assertTrue(ph > 5 && ph < 9, "酸耗尽 pH 回升到近中性，实测 " + ph);
        }
    }

    @Test
    @DisplayName("复分解沉淀：BaCl2 + Na2SO4 → BaSO4(s)↓（Ksp 限制，留液 2.9e-5 M）")
    void bariumSulfateMetathesisPrecipitatesBarite() {
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.run("""
                    SOLUTION 1 BaCl2
                        pH        7 charge
                        pe        4
                        Ba        50 mmol/kgw
                        Cl        100 mmol/kgw
                    END
                    USE solution 1
                    REACTION 1
                        Na 2  S 1  O 4
                        50 mmol in 1 step
                    EQUILIBRIUM_PHASES 1
                        Barite  0  0
                    SELECTED_OUTPUT 1
                        -state    true
                        -totals   Ba  S
                        -molalities Ba+2
                        -equilibrium_phases Barite
                        -pH       true
                    END
                    """);
            int last = r.rowCount() - 1;
            double baLeft = r.row(last).d("Ba");
            double barite = r.row(last).d("Barite");
            System.out.printf("Ba(液)=%.2e mol Barite=%.3f mol%n", baLeft, barite);
            assertEquals(0.04997, barite, 0.001, "Barite 沉淀≈50 mmol（计量）");
            assertTrue(baLeft < 1e-4, "留液 Ba 受 Ksp 限制 <0.1 mmol，实测 " + baLeft);
        }
    }

    /**
     * SO2(g) 平衡相吸收的<b>全平衡参考</b>（介稳教训存档）：
     * 吸收量 ≈1 mol（亨利）+ 强酸化 pH&lt;1 是对的；但全平衡会把 2/3 的 S(+4)
     * 氧化成 S(6)（pe "Adjusted to redox equilibrium"，本实验实测 S(6)=0.67 mol）——
     * 真实 SO2 水溶液介稳（氧化需 O2 + 时间）。介稳亚硫酸的建模路径 = Sul 伪元素池
     * + Sul(g) 伪气相（策展表 TODO，见 chemistry.json 的 reactions 注释）。
     */
    @Test
    @DisplayName("SO2 吸收：亨利溶解 + 强酸化（全平衡参考：S(4)→S(6) 过快氧化是介稳缺口）")
    void so2AbsorptionAcidifiesWater() {
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.run("""
                    SOLUTION 1 pure water
                        pH        7 charge
                        pe        4
                    END
                    USE solution 1
                    EQUILIBRIUM_PHASES 1
                        SO2(g)   0   1
                    SELECTED_OUTPUT 1
                        -state    true
                        -totals   S
                        -molalities SO2  HSO3-  H(SO4)-
                        -pH       true
                        -pe       true
                    END
                    """);
            int last = r.rowCount() - 1;
            double s = r.row(last).d("S");
            double ph = r.row(last).d("pH");
            double hso4 = r.row(last).d("m_H(SO4)-");
            System.out.printf("S吸收=%.3f mol pH=%.3f 全平衡氧化产物HSO4-=%.3f M pe=%.2f%n",
                    s, ph, hso4, r.row(last).d("pe"));
            assertTrue(s > 0.9 && s < 1.1, "SO2 溶解 ≈1 mol（亨利），实测 " + s);
            assertTrue(ph < 1.0, "强酸化，实测 pH " + ph);
            // 全平衡的诚实答案：显著氧化（介稳缺口的实证，非断言目标化学）
            assertTrue(hso4 > 0.5, "全平衡下 S(4)→S(6)（介稳缺口实证，见方法注释），实测 " + hso4);
        }
    }
}
