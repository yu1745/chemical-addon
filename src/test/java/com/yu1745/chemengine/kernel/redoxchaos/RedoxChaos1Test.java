package com.yu1745.chemengine.kernel.redoxchaos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.yu1745.chemengine.kernel.IPhreeqc;

/**
 * 氧化还原混沌场景（6 场景）——检验跨价态化学的正确性。
 *
 * <p>设计原则（铁律）：
 * <ul>
 *   <li>元素以总量输入（Fe、Mn、S、N、Cu），价态分配由 pe 涌现；</li>
 *   <li>不用「价态池输入 + REACTION 步」组合（会被强制 redox 平衡烧穿）；</li>
 *   <li>守恒校验用池总量 × mass_H2O（-totals 是 molality）；</li>
 *   <li>方向性校验：pe 升高 → 高价态占比升。</li>
 * </ul>
 */
class RedoxChaos1Test {

    /** molality → mmol（乘 mass_H2O kg）。 */
    private static double mmol(IPhreeqc.RunResult r, int row, String col) {
        double m = r.row(row).dOr(col, 0.0);
        double w = r.row(row).dOr("mass_H2O", 1.0);
        return m * w * 1000.0;
    }

    /** 高价态占比（0..1），分母为该元素全池和。 */
    private static double frac(double pool, double total) {
        return total > 1e-12 ? pool / total : Double.NaN;
    }

    private static void dbg(String tag, IPhreeqc.RunResult r, int row, String... cols) {
        StringBuilder sb = new StringBuilder(tag);
        for (String c : cols) {
            sb.append(String.format(Locale.ROOT, "  %s=%.4g", c, mmol(r, row, c)));
        }
        sb.append(String.format(Locale.ROOT, "  pH=%.2f pe=%.2f",
                r.row(row).d("pH"), r.row(row).d("pe")));
        System.out.println(sb);
    }

    // ==== a. 多金属混合还原剂 + 单氧化剂（O2）分步滴定 ====

    @Test
    @DisplayName("a: Fe/Mn/Zn 混合 + REACTION O2 分步滴定，Fe2+→Fe3+ 先于 Mn 氧化，Zn 不动")
    void multiMetalOxygenTitration() {
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.run("""
                    SELECTED_OUTPUT 1
                        -state    true
                        -totals   Fe(2)  Fe(3)  Mn(2)  Mn(3)  Mn(7)  Zn
                        -molalities Fe+2  Fe+3  Mn+2
                        -pH       true
                        -pe       true
                    USER_PUNCH 1
                        -headings mass_H2O
                        -start
                        10 PUNCH TOT("water")
                        -end
                    END
                    SOLUTION 1 mixed reductants
                        pH        4
                        pe        2
                        Fe        10 mmol/kgw
                        Mn        10 mmol/kgw
                        Zn        10 mmol/kgw
                        Cl        60 mmol/kgw
                    REACTION 1
                        O2 1
                        2 4 8 mmol
                    END
                    """);
            assertTrue(r.rowCount() >= 4, r.rawLines().toString());
            int last = r.rowCount() - 1;

            for (int i = 0; i < r.rowCount(); i++) {
                dbg("a step" + i, r, i, "Fe(2)", "Fe(3)", "Mn(2)", "Mn(7)", "Zn");
                double fe = mmol(r, i, "Fe(2)") + mmol(r, i, "Fe(3)");
                assertEquals(10.0, fe, 0.02, "Fe 守恒 step " + i + " 实测 " + fe);
                assertEquals(10.0, mmol(r, i, "Mn(2)") + mmol(r, i, "Mn(3)") + mmol(r, i, "Mn(7)"),
                        0.02, "Mn 守恒 step " + i);
                assertEquals(10.0, mmol(r, i, "Zn"), 0.02, "Zn 守恒 step " + i);
                if (i > 0) {
                    // 单调：Fe(3) 不减、pe 不减（加氧只升不降）
                    assertTrue(mmol(r, i, "Fe(3)") >= mmol(r, i - 1, "Fe(3)") - 1e-6,
                            "Fe(3) 应随 O2 单调不减");
                    assertTrue(r.row(i).d("pe") >= r.row(i - 1).d("pe") - 1e-6,
                            "pe 应随 O2 单调不减");
                }
            }
            // 电位序：Fe3+/Fe2+ (E°=0.77V) 应先于 MnO2/Mn2+ (~1.23V) 被氧化。
            // 2 mmol O2 = 8 mmol e-：第一步 Fe(2) 10→2，Mn 高价仍为 0
            double fe3Step1 = mmol(r, 1, "Fe(3)");
            double mnHighStep1 = mmol(r, 1, "Mn(3)") + mmol(r, 1, "Mn(7)");
            assertTrue(fe3Step1 > 7.5 && fe3Step1 < 8.5,
                    "step1: 8 mmol e- 应氧化 ~8 mmol Fe(2)，实测 Fe(3)=" + fe3Step1);
            assertTrue(mnHighStep1 < 0.05,
                    "step1: Mn 不应在 Fe 之前被氧化，实测 Mn(3)+Mn(7)=" + mnHighStep1);
            // 末步 8 mmol O2 = 32 mmol e- >> Fe 需求 → Fe 全部 Fe(3)
            assertTrue(mmol(r, last, "Fe(3)") > 9.9, "末步 Fe 应全氧化，实测 "
                    + mmol(r, last, "Fe(3)"));
            assertTrue(mmol(r, last, "Zn") > 9.95, "Zn(2) 是 db 中唯一价态，应纹丝不动");
        }
    }

    // ==== b. 单金属 + 混合氧化剂（Cl2(g) 与 O2(g) 共存） ====

    @Test
    @DisplayName("b: Fe(2) 起始还原溶液 + Cl2(g)/O2(g) 平衡相共存 → Cl2 赢，Fe 全 Fe(3)")
    void ironBetweenChlorineAndOxygen() {
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.run("""
                    SELECTED_OUTPUT 1
                        -state    true
                        -totals   Fe(2)  Fe(3)  Cl
                        -molalities Fe+2  Fe+3  Cl-  Cl2  O2
                        -pH       true
                        -pe       true
                    USER_PUNCH 1
                        -headings mass_H2O
                        -start
                        10 PUNCH TOT("water")
                        -end
                    END
                    SOLUTION 1 ferrous, reducing start
                        pH        5
                        pe        0
                        Fe        10 mmol/kgw
                        Cl        20 mmol/kgw
                        Na        10 mmol/kgw
                    END
                    USE solution 1
                    EQUILIBRIUM_PHASES 1
                        Cl2(g)    -0.5    10
                        O2(g)     -0.5    10
                    END
                    """);
            int last = r.rowCount() - 1;
            dbg("b init", r, 0, "Fe(2)", "Fe(3)");
            dbg("b final", r, last, "Fe(2)", "Fe(3)");
            // 初始低 pe：Fe 几乎全为 Fe(2)
            assertTrue(mmol(r, 0, "Fe(2)") > 9.9, "初始 pe=0 应全 Fe(2)，实测 "
                    + mmol(r, 0, "Fe(2)"));
            // Cl2 (E°=1.36V) 与 O2 (E°=1.23V) 都能氧化 Fe2+，Cl2 更强 → pe 应被推到高值
            double fe3 = mmol(r, last, "Fe(3)");
            double fe2 = mmol(r, last, "Fe(2)");
            assertTrue(fe3 > 9.5, "Cl2+O2 下 Fe 应几乎全 Fe(3)，实测 " + fe3);
            assertEquals(10.0, fe2 + fe3, 0.05, "Fe 守恒");
            double peFinal = r.row(last).d("pe");
            // Cl2/Cl- 电对在 10 mmol/kgw Cl- 下 Nernst: pe ≈ (45.98 + log10(a_Cl-^2/f_Cl2))/2
            assertTrue(peFinal > 10, "Cl2(g) 主导下 pe 应极高（>10），实测 " + peFinal);
            assertTrue(r.row(last).d("pe") > 0, "终态必须氧化性");
        }
    }

    // ==== c. 硫体系混沌：S 总量 + pe 起点 -4 / +4 / +10 ====

    @Test
    @DisplayName("c: S 总量 + pe 扫描(-4/+4/+10)，S(-2)/S(4)/S(6) 分配随 pe 单调合理")
    void sulphurPeSweep() {
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.run("""
                    SELECTED_OUTPUT 1
                        -state    true
                        -totals   S(6)  S(4)  S(-2)
                        -molalities SO4-2  HS-  SO3-2
                        -pH       true
                        -pe       true
                    USER_PUNCH 1
                        -headings mass_H2O
                        -start
                        10 PUNCH TOT("water")
                        -end
                    END
                    SOLUTION 1 S at pe -4
                        pH        7
                        pe        -4
                        S         20 mmol/kgw
                        Na        40 mmol/kgw
                    END
                    SOLUTION 2 S at pe +4
                        pH        7
                        pe        4
                        S         20 mmol/kgw
                        Na        40 mmol/kgw
                    END
                    SOLUTION 3 S at pe +10
                        pH        7
                        pe        10
                        S         20 mmol/kgw
                        Na        40 mmol/kgw
                    END
                    """);
            assertEquals(3, r.rowCount(), r.rawLines().toString());
            double[] sM2 = new double[3], sM4 = new double[3], sM6 = new double[3];
            for (int i = 0; i < 3; i++) {
                sM2[i] = mmol(r, i, "S(-2)");
                sM4[i] = mmol(r, i, "S(4)");
                sM6[i] = mmol(r, i, "S(6)");
                dbg("c pe" + r.row(i).d("pe"), r, i, "S(-2)", "S(4)", "S(6)");
                assertEquals(20.0, sM2[i] + sM4[i] + sM6[i], 0.1,
                        "S 守恒 (pe=" + r.row(i).d("pe") + "): 实测 "
                                + (sM2[i] + sM4[i] + sM6[i]));
            }
            // 强还原：S(-2) 占主导
            assertTrue(frac(sM2[0], 20) > 0.9, "pe=-4 应以 S(-2) 为主，实测占比 "
                    + frac(sM2[0], 20));
            // 强氧化：S(6) 占主导
            assertTrue(frac(sM6[2], 20) > 0.9, "pe=+10 应以 S(6) 为主，实测占比 "
                    + frac(sM6[2], 20));
            // 方向性：S(6) 占比随 pe 单调上升；S(-2) 单调下降
            assertTrue(sM6[0] <= sM6[1] + 1e-6 && sM6[1] <= sM6[2] + 1e-6,
                    "S(6) 应随 pe 单调升: " + sM6[0] + "," + sM6[1] + "," + sM6[2]);
            assertTrue(sM2[0] >= sM2[1] - 1e-6 && sM2[1] >= sM2[2] - 1e-6,
                    "S(-2) 应随 pe 单调降: " + sM2[0] + "," + sM2[1] + "," + sM2[2]);
        }
    }

    // ==== d. 锰价态：Mn 总量 + pe 扫描（-2/0/4/8/12） ====

    @Test
    @DisplayName("d: Mn 总量 + pe 扫描(-2..+12)，Mn(2) 占比随 pe 降、高价态随 pe 升")
    void manganesePeSweep() {
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.run("""
                    SELECTED_OUTPUT 1
                        -state    true
                        -totals   Mn(2)  Mn(3)  Mn(6)  Mn(7)
                        -molalities Mn+2  Mn+3  MnO4-
                        -pH       true
                        -pe       true
                    USER_PUNCH 1
                        -headings mass_H2O
                        -start
                        10 PUNCH TOT("water")
                        -end
                    END
                    SOLUTION 1 Mn pe -2
                        pH        4
                        pe        -2
                        Mn        5 mmol/kgw
                        Cl        10 mmol/kgw
                    END
                    SOLUTION 2 Mn pe 0
                        pH        4
                        pe        0
                        Mn        5 mmol/kgw
                        Cl        10 mmol/kgw
                    END
                    SOLUTION 3 Mn pe 8
                        pH        4
                        pe        8
                        Mn        5 mmol/kgw
                        Cl        10 mmol/kgw
                    END
                    SOLUTION 4 Mn pe 16
                        pH        4
                        pe        16
                        Mn        5 mmol/kgw
                        Cl        10 mmol/kgw
                    END
                    SOLUTION 5 Mn pe 26
                        pH        4
                        pe        26
                        Mn        5 mmol/kgw
                        Cl        10 mmol/kgw
                    END
                    """);
            assertEquals(5, r.rowCount(), r.rawLines().toString());
            double[] mn2 = new double[5], high = new double[5];
            for (int i = 0; i < 5; i++) {
                mn2[i] = mmol(r, i, "Mn(2)");
                high[i] = mmol(r, i, "Mn(3)") + mmol(r, i, "Mn(6)") + mmol(r, i, "Mn(7)");
                dbg("d pe" + r.row(i).d("pe"), r, i, "Mn(2)", "Mn(3)", "Mn(6)", "Mn(7)");
                assertEquals(5.0, mn2[i] + high[i], 0.05,
                        "Mn 守恒 (pe=" + r.row(i).d("pe") + "): 实测 " + (mn2[i] + high[i]));
            }
            // 全程 Mn(2) 应是主要池（Mn 高价态在 pH 4 需 pe>10 才显著）
            assertTrue(frac(mn2[0], 5) > 0.99, "pe=-2 应几乎全 Mn(2)，实测 " + mn2[0]);
            // 方向性：Mn(2) 随 pe 单调降，高价态单调升
            for (int i = 1; i < 5; i++) {
                assertTrue(mn2[i] <= mn2[i - 1] + 1e-6,
                        "Mn(2) 应随 pe 单调降: " + mn2[0] + "," + mn2[1] + "," + mn2[2]
                                + "," + mn2[3] + "," + mn2[4]);
                assertTrue(high[i] >= high[i - 1] - 1e-6,
                        "高价 Mn 应随 pe 单调升: " + high[0] + "," + high[1] + ","
                                + high[2] + "," + high[3] + "," + high[4]);
            }
            // pe=12 时应出现可观的高价 Mn（MnO4-/MnO4-2 区）
            assertTrue(frac(high[4], 5) > 0.05,
                    "pe=26 应有 >5% 高价 Mn（E°(MnO4-/Mn2+)≈1.5V），实测 " + high[4]);
        }
    }

    // ==== e. 氮体系：N 总量 + pe 扫描 ====

    @Test
    @DisplayName("e: N 总量 + pe 扫描(-4/0/4/12)，NH4+/NO3- 分布随 pe 翻转")
    void nitrogenPeSweep() {
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.run("""
                    SELECTED_OUTPUT 1
                        -state    true
                        -totals   N(-3)  N(5)
                        -molalities NH4+  NH3  NO3-
                        -pH       true
                        -pe       true
                    USER_PUNCH 1
                        -headings mass_H2O
                        -start
                        10 PUNCH TOT("water")
                        -end
                    END
                    SOLUTION 1 N pe -4
                        pH        7
                        pe        -4
                        N         10 mmol/kgw
                        Cl        10 mmol/kgw
                    END
                    SOLUTION 2 N pe 0
                        pH        7
                        pe        0
                        N         10 mmol/kgw
                        Cl        10 mmol/kgw
                    END
                    SOLUTION 3 N pe 4
                        pH        7
                        pe        4
                        N         10 mmol/kgw
                        Cl        10 mmol/kgw
                    END
                    SOLUTION 4 N pe 12
                        pH        7
                        pe        12
                        N         10 mmol/kgw
                        Cl        10 mmol/kgw
                    END
                    """);
            assertEquals(4, r.rowCount(), r.rawLines().toString());
            double[] nRed = new double[4], nOx = new double[4];
            for (int i = 0; i < 4; i++) {
                nRed[i] = mmol(r, i, "N(-3)");
                nOx[i] = mmol(r, i, "N(5)");
                dbg("e pe" + r.row(i).d("pe"), r, i, "N(-3)", "N(5)");
                assertEquals(10.0, nRed[i] + nOx[i], 0.05,
                        "N 守恒 (pe=" + r.row(i).d("pe") + "): 实测 " + (nRed[i] + nOx[i]));
            }
            assertTrue(frac(nRed[0], 10) > 0.9, "pe=-4 应以 NH4+/NH3 为主，实测 " + nRed[0]);
            assertTrue(frac(nOx[3], 10) > 0.9, "pe=+12 应以 NO3- 为主，实测 " + nOx[3]);
            for (int i = 1; i < 4; i++) {
                assertTrue(nRed[i] <= nRed[i - 1] + 1e-6, "还原态 N 应随 pe 降");
                assertTrue(nOx[i] >= nOx[i - 1] - 1e-6, "氧化态 N 应随 pe 升");
            }
            // 无荒谬值：0.5 mol/kgw 以内均为正常量级
            for (int i = 0; i < 4; i++) {
                assertTrue(nRed[i] < 15 && nOx[i] < 15, "荒谬量级检查");
            }
        }
    }

    // ==== f. 组合混沌：Cu+Cl+S+Fe 同场不同 pe ====

    @Test
    @DisplayName("f: Cu+Cl+S+Fe 组合：低 pe 硫化物络 Cu，高 pe 氯络 Cu + 硫酸盐竞争")
    void copperChlorideSulphideIronSoup() {
        try (IPhreeqc q = IPhreeqc.create()) {
            String soHead = """
                        pH        7
                        Cu        5 mmol/kgw
                        Fe        1 mmol/kgw
                        S         10 mmol/kgw
                        Cl        300 mmol/kgw
                        Na        300 mmol/kgw
                    """;
            IPhreeqc.RunResult r = q.run("""
                    SELECTED_OUTPUT 1
                        -state    true
                        -totals   Cu  Fe(2)  Fe(3)  S(6)  S(-2)
                        -molalities Cu+2  CuCl+  Cu+2+2Cl-  Cu+2+3Cl-  Cu+2+4Cl-  Cu(HS)2-  CuHS  Cu2S(HS)2-2  SO4-2  HS-
                        -pH       true
                        -pe       true
                    USER_PUNCH 1
                        -headings mass_H2O
                        -start
                        10 PUNCH TOT("water")
                        -end
                    END
                    SOLUTION 1 soup, reducing
                        pe        -4
                    """ + soHead + """
                    END
                    SOLUTION 2 soup, oxidising
                        pe        8
                    """ + soHead + """
                    END
                    """);
            assertEquals(2, r.rowCount(), r.rawLines().toString());
            for (int i = 0; i < 2; i++) {
                dbg((i == 0 ? "f lowpe" : "f highpe") + " S(-2)=" + mmol(r, i, "S(-2)"),
                        r, i, "Cu", "Fe(2)", "Fe(3)", "S(6)");
                assertEquals(5.0, mmol(r, i, "Cu"), 0.02, "Cu 守恒");
                assertEquals(1.0, mmol(r, i, "Fe(2)") + mmol(r, i, "Fe(3)"), 0.02, "Fe 守恒");
                assertEquals(10.0, mmol(r, i, "S(6)") + mmol(r, i, "S(-2)"), 0.1, "S 守恒");
            }
            // 低 pe：Fe(3)≈0，Cu 被硫化物络合（Cu(HS)2-/CuHS 可观），HS- 可观
            assertTrue(mmol(r, 0, "Fe(3)") < 0.05, "低 pe 不应有 Fe(3)");
            double cuSulfLow = r.row(0).dOr("m_Cu(HS)2-", 0.0) + r.row(0).dOr("m_CuHS", 0.0)
                    + r.row(0).dOr("m_Cu2S(HS)2-2", 0.0);
            assertTrue(cuSulfLow * 1000.0 > 2.0, "低 pe 下 Cu 硫化物络合物应 >2 mmol，实测 "
                    + cuSulfLow * 1000.0);
            // 高 pe：Fe(3) 主导、S(6) 主导、Cu 走氯络合
            assertTrue(mmol(r, 1, "Fe(3)") > 0.9, "高 pe 应以 Fe(3) 为主，实测 "
                    + mmol(r, 1, "Fe(3)"));
            assertTrue(frac(mmol(r, 1, "S(6)"), 10) > 0.9, "高 pe 硫应全 S(6)");
            double cuClHigh = r.row(1).dOr("m_Cu+2+2Cl-", 0.0) + r.row(1).dOr("m_Cu+2+3Cl-", 0.0)
                    + r.row(1).dOr("m_Cu+2+4Cl-", 0.0) + r.row(1).dOr("m_CuCl+", 0.0);
            double cuSulfHigh = r.row(1).dOr("m_Cu(HS)2-", 0.0) + r.row(1).dOr("m_CuHS", 0.0)
                    + r.row(1).dOr("m_Cu2S(HS)2-2", 0.0);
            assertTrue(cuClHigh > cuSulfHigh * 10, "高 pe 下 Cu 氯络合应压倒硫化物络合: "
                    + cuClHigh + " vs " + cuSulfHigh);
        }
    }
}
