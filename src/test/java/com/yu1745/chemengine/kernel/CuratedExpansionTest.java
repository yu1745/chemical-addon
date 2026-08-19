package com.yu1745.chemengine.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 策展表扩容验收：SO₂ 介稳动力学吸收（伪气相替代方案）+ Pyrolusite 相补丁。
 *
 * <p>对照基线（CLI 实测，/tmp/sa*.phq、/tmp/mn.phq）：
 * <ul>
 *   <li>吸收：P_SO2=1 atm 下 t=1000s Sul→1.240（=H×P 自限），S(6)=0（介稳！平衡参考会氧化 2/3），pH≈0.12；</li>
 *   <li>解吸：P→0 后 Sul→~0、pH 回 7；</li>
 *   <li>MnO₂+浓盐酸：MnO2(s)（sit.dat 原生相，原策展 Pyrolusite 已退役）全溶 0.1 mol，Cl₂ 1:1 计量（逸出+溶解=0.0997），Mn 以氯配合物留液。</li>
 * </ul>
 */
class CuratedExpansionTest {

    private static final Curation C = Curation.load();

    @Test
    @DisplayName("SO2 介稳吸收：自限于亨利溶解度、零氧化（vs 平衡参考的 2/3 氧化）")
    void so2MetastableAbsorptionSelfLimits() {
        try (IPhreeqc q = IPhreeqc.create()) {
            ChemState water = ChemState.builder("water").waterKg(1.0).pHCharge().pe(4).build();
            IPhreeqc.RunResult r = q.run(water.toSolutionScript(1) + "END\n"
                    + C.ratesBlock() + "END\nUSE solution 1\n"
                    + C.kineticsBlock(java.util.Set.of("SulAbsorb"), null, 100, 1000, 10000)
                    + """
                    SELECTED_OUTPUT 1
                        -state    true
                        -time     true
                        -totals   Sul  S
                        -pH       true
                    END
                    """);
            assertEquals(3, r.rowCount(), r.rawLines().toString());
            int last = r.rowCount() - 1;
            double sul = r.row(last).d("Sul");
            double s6 = r.row(last).d("S");
            System.out.printf("t=10000s: Sul=%.4f S(6)=%.1e pH=%.3f%n",
                    sul, s6, r.row(last).d("pH"));
            assertEquals(1.24, sul, 0.01, "Sul 自限于 H×P=1.24");
            assertEquals(0.0, s6, 1e-12, "介稳：零 S(6) 氧化（平衡参考 0.67 mol）");
            assertTrue(r.row(last).d("pH") < 0.5, "强酸化（与平衡参考同量级），实测 "
                    + r.row(last).d("pH"));
            // 中间步：仍在逼近（1.089 @100s → 1.240 @1000s）
            assertTrue(r.row(0).d("Sul") < 1.20 && r.row(0).d("Sul") > 1.0,
                    "t=100s 应在逼近中: " + r.row(0).d("Sul"));
        }
    }

    @Test
    @DisplayName("SO2 再生：P→0 逆向解吸，Sul 清空、pH 回中性（循环可用）")
    void so2DesorbsWhenPressureDrops() {
        try (IPhreeqc q = IPhreeqc.create()) {
            ChemState water = ChemState.builder("water").waterKg(1.0).pHCharge().pe(4).build();
            IPhreeqc.RunResult r = q.run(water.toSolutionScript(1) + "END\n"
                    + C.ratesBlock() + "END\n"
                    // 段1：常压吸收至饱和
                    + "USE solution 1\n" + C.kineticsBlock(10000)
                    + "SAVE solution 1\nEND\n"
                    // 段2：撤压（游戏侧 parm 覆盖 P_SO2=0）→ 解吸
                    + "USE solution 1\n"
                    + C.kineticsBlock(java.util.Set.of("SulAbsorb"),
                    Map.of("SulAbsorb", new double[]{1.24, 0.0}), 1000, 10000)
                    + """
                    SELECTED_OUTPUT 1
                        -state    true
                        -time     true
                        -totals   Sul
                        -pH       true
                    END
                    """);
            assertTrue(r.rowCount() >= 2, r.rawLines().toString());
            int last = r.rowCount() - 1;
            System.out.printf("解吸后: Sul=%.2e pH=%.2f%n",
                    r.row(last).d("Sul"), r.row(last).d("pH"));
            assertTrue(r.row(last).d("Sul") < 1e-6,
                    "t=10000s Sul 应基本清空，实测 " + r.row(last).d("Sul"));
            assertEquals(7.0, r.row(last).d("pH"), 0.5, "pH 回中性");
        }
    }

    @Test
    @DisplayName("实验室制氯：MnO2(策展相) + 浓盐酸 → Cl2 逸出 1:1 计量")
    void pyrolusitePlusConcentratedHclReleasesChlorine() {
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.run("""
                    SOLUTION 1 concentrated HCl
                        pH        0 charge
                        pe        4
                        Cl        12 mol/kgw
                    END
                    USE solution 1
                    EQUILIBRIUM_PHASES 1
                        MnO2(s)     0  0.1
                        Cl2(g)      0  10
                    SELECTED_OUTPUT 1
                        -state    true
                        -totals   Mn
                        -molalities Cl2  Mn+2
                        -equilibrium_phases MnO2(s) Cl2(g)
                        -pH       true
                    END
                    """);
            int last = r.rowCount() - 1;
            double mn = r.row(last).d("Mn");
            double cl2Vent = r.row(last).d("d_Cl2(g)");
            double cl2Aq = r.row(last).d("m_Cl2");
            double pyrLeft = r.row(last).d("MnO2(s)");
            System.out.printf("Mn(液)=%.4f Cl2(逸)=%.4f Cl2(aq)=%.4f 余MnO2=%.4f pH=%.2f%n",
                    mn, cl2Vent, cl2Aq, pyrLeft, r.row(last).d("pH"));
            assertEquals(0.0996, mn, 0.002, "MnO2 计量溶解入液（0.1 mol 投料）");
            assertEquals(0.0, pyrLeft, 1e-9, "MnO2(s) 耗尽");
            assertEquals(mn, cl2Vent + cl2Aq, 0.002, "Cl2 1:1 计量（逸出+溶解）");
            assertTrue(cl2Vent > 0.03, "应有显著 Cl2 逸出（实验室制氯），实测 " + cl2Vent);
            assertTrue(r.row(last).d("m_Mn+2") < 1e-5,
                    "浓盐酸下 Mn 以氯配合物留液（游离 Mn+2 极少），实测 "
                            + r.row(last).d("m_Mn+2"));
        }
    }
}
