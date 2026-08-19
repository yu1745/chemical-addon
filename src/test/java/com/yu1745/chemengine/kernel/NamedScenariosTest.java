package com.yu1745.chemengine.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * G1b 三场景验收——旧引擎（tag self-engine-final）点名的翻车场景，现在作为新内核的验收标准。
 *
 * <p>PHREEQC 语义要点：价态写法（S(4)、Fe(2)）是互不转化的独立守恒池；
 * 要让氧化还原发生，必须以<b>元素总量</b>（S、Fe、Cl）输入，价态分配由 pe 平衡涌现。
 * 化学计量由电荷平衡自动锁定（电子不守恒的价态分配无法电中性）。
 * <b>介稳物种</b>（HOCl/OCl⁻ 等动力学冻结的氧化态）用伪元素（Hyp/Sul）表示，
 * 跨池耦合在脚本层用 KINETICS 白名单声明（见 Database 的 addendum 注释）。
 */
class NamedScenariosTest {

    private static double mmol(IPhreeqc.RunResult r, int row, String col) {
        return r.row(row).d(col) * 1000.0;
    }

    @Test
    @DisplayName("FeCl2 溶液通 Cl₂ → 2Fe³⁺ + 2Cl⁻（元素总量 + REACTION Cl2）")
    void ferrousChlorideOxidisedByChlorine() {
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.run("""
                    SOLUTION 1 FeCl2 solution
                        Fe        10 mmol/kgw
                        Cl        20 mmol/kgw
                        pH        4
                        pe        4
                    REACTION 1
                        Cl2 1
                        2 mmol in 1 step
                    SELECTED_OUTPUT 1
                        -state    true
                        -totals   Fe(2)  Fe(3)  Cl
                        -molalities Fe+2  Fe+3  Cl-  Cl2
                        -pH       true
                        -pe       true
                    END
                    """);
            int last = r.rowCount() - 1;   // 末行 = 反应后（i_soln + react）
            assertTrue(r.rowCount() >= 2, "应有初始+反应两行: " + r.rawLines().toString());

            // 2 mmol Cl2 = 4 mmol Cl 原子；电荷平衡强制 4 mmol Fe(2) → Fe(3)
            double fe2 = mmol(r, last, "Fe(2)");
            double fe3 = mmol(r, last, "Fe(3)");
            System.out.printf("Fe(2)=%.3f Fe(3)=%.3f Cl=%s pH=%.2f pe=%.2f%n",
                    fe2, fe3, r.row(last).s("Cl"), r.row(last).d("pH"), r.row(last).d("pe"));
            assertTrue(fe3 > 3.5 && fe3 < 4.2,
                    "Fe(3) 应≈4 mmol（完全氧化），实测 " + fe3);
            assertTrue(fe2 > 5.8 && fe2 < 6.5,
                    "Fe(2) 应≈6 mmol，实测 " + fe2);
            assertEquals(10.0, fe2 + fe3, 0.05, "铁总量守恒 10 mmol");

            double clTotal = mmol(r, last, "Cl");
            assertEquals(24.0, clTotal, 0.05, "氯总量 20+2×2=24 mmol");
        }
    }

    @Test
    @DisplayName("亚硫酸盐通 O₂ → S(6)：还原态初始 + O2(g) 平衡相驱动氧化")
    void sulphiteOxidisedByOxygen() {
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.run("""
                    SOLUTION 1 sodium sulphite (reducing)
                        pH        8
                        pe        -4
                        S         20 mmol/kgw
                        Na        40 mmol/kgw
                    END
                    USE solution 1
                    EQUILIBRIUM_PHASES 1
                        O2(g)     0     10
                    SELECTED_OUTPUT 1
                        -state    true
                        -totals   S(4)  S(6)
                        -molalities SO3-2  HSO3-  SO4-2
                        -pH       true
                        -pe       true
                    END
                    """);
            assertTrue(r.rowCount() >= 1, r.rawLines().toString());
            int last = r.rowCount() - 1;   // 最后一行 = 反应后（USE+EQUILIBRIUM_PHASES）

            double s4 = mmol(r, last, "S(4)");
            double s6 = mmol(r, last, "S(6)");
            System.out.printf("S(4)=%.3f S(6)=%.3f pH=%.2f pe=%.2f%n",
                    s4, s6, r.row(last).d("pH"), r.row(last).d("pe"));
            assertTrue(s6 > 19.5,
                    "O2(g) 平衡下 S(4)→S(6) 应几乎完全，实测 S(6)=" + s6);
            assertTrue(s4 < 0.5, "残余 S(4) 应 <0.5 mmol，实测 " + s4);
            assertEquals(20.0, s4 + s6, 0.2, "硫总量守恒 20 mmol");
        }
    }

    @Test
    @DisplayName("次氯酸钠漂白液（Hyp 伪元素，介稳池）——验证 addendum 补丁")
    void hypochloriteBleachViaPseudoElement() {
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.run("""
                    SOLUTION 1 sodium hypochlorite bleach
                        pH        12 charge
                        pe        4
                        Na        150 mmol/kgw
                        Cl        100 mmol/kgw
                        Hyp       50 mmol/kgw
                    SELECTED_OUTPUT 1
                        -state    true
                        -totals   Cl  Hyp
                        -molalities Hyp-  HypH  Cl-
                        -pH       true
                    END
                    """);
            assertEquals(1, r.rowCount(), r.rawLines().toString());

            double hyp = r.row(0).d("m_Hyp-") * 1000.0;
            double hyph = r.row(0).d("m_HypH") * 1000.0;
            double ph = r.row(0).d("pH");
            System.out.printf("Hyp-=%.3f HypH=%.4f pH=%.2f%n", hyp, hyph, ph);

            // 介稳池 50 mmol 在碱液中以 Hyp-（次氯酸根）为主
            assertTrue(hyp > 45.0, "Hyp- 应≈49.9 mmol，实测 " + hyp);
            assertTrue(hyph < 0.5, "强碱下 HypH（HOCl）应很小，实测 " + hyph);
            assertEquals(100.0, mmol(r, 0, "Cl"), 0.5, "氯元素总量守恒 100 mmol");
            assertEquals(50.0, mmol(r, 0, "Hyp"), 0.5, "Hyp 池守恒 50 mmol");
        }
    }

    /**
     * 池坍缩回归防线（PLAN.md「G1b 补充实验」q0 定论）：
     * 价态池（如 Cl(1)）在任何批式反应步都会被坍缩到统一 pe（"Adjusted to redox
     * equilibrium"）；伪元素 Hyp/Sul 必须穿过反应步分毫不动。
     * 本测试在价态池方案下必失败（Cl(1) 被 1 µmol 无关 REACTION 烧穿至 ~1e-16）。
     */
    @Test
    @DisplayName("介稳池穿越反应步：无关 REACTION 不得烧穿 Hyp/Sul（q0 回归）")
    void metastablePoolsSurviveReactionSteps() {
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.run("""
                    SOLUTION 1 bleach + sulphite contaminant
                        pH        12 charge
                        pe        4
                        Na        180 mmol/kgw
                        Cl        100 mmol/kgw
                        Hyp       50 mmol/kgw
                        Sul       10 mmol/kgw
                    END
                    USE solution 1
                    REACTION 1
                        Na 1
                        1 umol in 1 step
                    SELECTED_OUTPUT 1
                        -state    true
                        -totals   Cl  Hyp  Sul
                        -molalities Hyp-  Sul-2
                        -pH       true
                    END
                    """);
            int last = r.rowCount() - 1;
            assertTrue(r.rowCount() >= 1, r.rawLines().toString());

            assertEquals(50.0, mmol(r, last, "Hyp"), 0.01, "Hyp 池必须精确穿过反应步");
            assertEquals(10.0, mmol(r, last, "Sul"), 0.01, "Sul 池必须精确穿过反应步");
            assertEquals(100.0, mmol(r, last, "Cl"), 0.01, "Cl 元素守恒");
        }
    }

    /**
     * 跨池白名单耦合（m1 实验）：漂白液遇亚硫酸盐自动淬灭，
     * OCl⁻ + SO₃²⁻ → Cl⁻ + SO₄²⁻，二级速率律 + 真实时间刻度盘。
     * 化学计量精确：10 mmol Sul 消耗 10 mmol Hyp，产出 10 mmol Cl⁻/SO₄²⁻。
     */
    @Test
    @DisplayName("淬灭动力学：KINETICS 白名单 Hyp+Sul→Cl+S(6)，时间刻度盘真实")
    void sulphiteQuenchesBleachViaKineticWhitelist() {
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.run("""
                    SOLUTION 1 bleach + sulphite, kinetic quench
                        pH        12 charge
                        pe        4
                        Na        180 mmol/kgw
                        Cl        100 mmol/kgw
                        Hyp       50 mmol/kgw
                        Sul       10 mmol/kgw
                    END
                    RATES
                    Quench
                        -start
                        10  k = 0.1
                        20  r = k * TOT("Hyp") * TOT("Sul")
                        30  SAVE r * TIME
                        -end
                    USE solution 1
                    KINETICS 1
                    Quench
                        -formula Hyp -1 Sul -1 Cl 1 S 1 O 4
                        -m        10
                        -m0       10
                        -steps    1 10 100 1000 seconds
                    SELECTED_OUTPUT 1
                        -state    true
                        -time     true
                        -totals   Cl  Hyp  Sul  S
                        -molalities Hyp-  Sul-2  SO4-2
                        -pH       true
                    END
                    """);
            assertTrue(r.rowCount() >= 4, r.rawLines().toString());

            double sulFirst = mmol(r, 0, "Sul");
            for (int i = 1; i < r.rowCount(); i++) {
                assertTrue(mmol(r, i, "Sul") <= mmol(r, i - 1, "Sul") + 1e-9,
                        "Sul 应随时间单调下降: step " + i);
            }

            int last = r.rowCount() - 1;   // t = 1000 s
            double sul = mmol(r, last, "Sul");
            double hyp = mmol(r, last, "Hyp");
            double cl = mmol(r, last, "Cl");
            double s = mmol(r, last, "S");
            System.out.printf("t=1000s: Sul=%.3f Hyp=%.3f Cl=%.3f S=%.3f (首步 Sul=%.2f)%n",
                    sul, hyp, cl, s, sulFirst);
            assertTrue(sul < 0.5, "t=1000s Sul 应基本耗尽，实测 " + sul);
            assertTrue(hyp > 39.5 && hyp < 40.5, "Hyp 50-10=40 mmol，实测 " + hyp);
            assertTrue(cl > 109.5 && cl < 110.5, "Cl 100+10=110 mmol，实测 " + cl);
            assertTrue(s > 9.5 && s < 10.5, "S 总量守恒 10 mmol，实测 " + s);
            assertTrue(sul < sulFirst / 10.0, "淬灭必须实际发生（单调下降且显著）");
        }
    }
}
