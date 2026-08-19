package com.yu1745.chemengine.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 定向压力锅 6 发：每锅一个引擎级假设（见 PLAN.md「六锅收集」）。目的不是断言化学
 * 正确性，是把引擎级问题（收敛失败/语义缺口/静默失效）暴露成显式证据。
 *
 * <p>本类测试的断言故意宽松（能跑完 + 不死 + 关键守恒），发现的问题进 PLAN.md 统一
 * 构思改法，不在本类逐个打补丁。
 */
class ProbeSoupsTest {

    private static final Curation C = Curation.load();

    // ==== 锅1：敌意浓汤（多相同沉 + 动力学 + 高浓度，假设：收敛失败/慢） ====

    @Test
    @DisplayName("锅1 敌意浓汤：多相同沉+动力学+2.5M 离子强度——求解器存活与守恒")
    void hostileConcentratedSoupSurvives() {
        try (IPhreeqc q = IPhreeqc.create()) {
            long t0 = System.nanoTime();
            IPhreeqc.RunResult r = q.run("""
                    SOLUTION 1 hostile concentrated soup
                        pH        7 charge
                        pe        4
                        Na        2.5 mol/kgw
                        Cl        2.5 mol/kgw
                        Ca        0.8 mol/kgw
                        S         1.2 mol/kgw
                        C         0.5 mol/kgw
                        Fe        0.3 mol/kgw
                        Mg        0.6 mol/kgw
                        Hyp       0.2 mol/kgw
                        Sul       0.1 mol/kgw
                    END
                    """ + C.ratesBlock() + """
                    USE solution 1
                    KINETICS 1
                    Quench
                        -formula Cl 1 Hyp -1 O 4 S 1 Sul -1
                        -m        1000
                        -m0       1000
                        -steps    1000 seconds in 1 step
                    EQUILIBRIUM_PHASES 1
                        Calcite   0  0
                        Barite    0  0
                        Gypsum    0  0
                        Ferrihydrite(am) 0 0
                        Brucite   0  0
                        Celestite 0  0
                        Dolomite  0  0
                    SELECTED_OUTPUT 1
                        -state    true
                        -water    true
                        -totals   Na Cl Ca S C Fe Mg Hyp Sul
                        -equilibrium_phases Calcite Barite Gypsum Ferrihydrite(am) Brucite Celestite Dolomite
                        -pH true
                        -pe true
                    END
                    """);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            int last = r.rowCount() - 1;
            double w = r.row(last).d("mass_H2O");
            System.out.printf("[锅1] %d ms, pH=%.2f pe=%.2f, 相: Calcite=%.3f Barite=%.3f "
                            + "Gypsum=%.3f Ferrih=%.3f Brucite=%.3f Celestite=%.3f Dolomite=%.3f%n",
                    ms, r.row(last).d("pH"), r.row(last).d("pe"),
                    r.row(last).d("Calcite"), r.row(last).d("Barite"), r.row(last).d("Gypsum"),
                    r.row(last).d("Ferrihydrite(am)"), r.row(last).d("Brucite"),
                    r.row(last).d("Celestite"), r.row(last).d("Dolomite"));
            // 存活 + 汇总量守恒（S: 液 + Barite + Celestite；Ca: 液 + Calcite + Gypsum + ...）
            double sAll = r.row(last).d("S") * w + r.row(last).d("Barite")
                    + r.row(last).d("Celestite") + r.row(last).d("Gypsum");
            assertEquals(1.2 + 0.1, sAll, 0.02, "S 总账（液+Barite+Celestite+Gypsum）");
            double caAll = r.row(last).d("Ca") * w + r.row(last).d("Calcite") + r.row(last).d("Gypsum")
                    + r.row(last).d("Dolomite");
            assertEquals(0.8, caAll, 0.02, "Ca 总账（液+全部钙相）");
            assertTrue(ms < 20000, "求解时间应在事件预算内（20s）: " + ms + " ms");
        }
    }

    // ==== 锅2：熬干汤（动力学耗水至干，假设：水量趋零引擎行为未定义） ====

    @Test
    @DisplayName("锅2 熬干汤：SulAbsorb 大 P 大步长吃水——趋干时的引擎行为")
    void boilingDrySoupBehaviour() {
        try (IPhreeqc q = IPhreeqc.create()) {
            // 1 kg 水，SulAbsorb 2 atm 大通量：饱和需 ~2.5 mol Sul = 耗 2.5 mol 水 ≈ 4.5% 水
            // 极限压榨：给超大步长 + P=2 让积分器大步跨进
            IPhreeqc.RunResult r;
            try {
                r = q.run(ChemState.builder("drying soup").waterKg(0.05).pHCharge().pe(4)
                                .total("Na", 0.01).total("Cl", 0.01).build()
                        .toSolutionScript(1) + "END\n"
                        + C.ratesBlock() + "END\nUSE solution 1\n"
                        + C.kineticsBlock(java.util.Set.of("SulAbsorb"),
                                java.util.Map.of("SulAbsorb", new double[]{1.24, 2.0}),
                                1000, 10000, 100000)
                        + """
                        SELECTED_OUTPUT 1
                            -state true
                            -time true
                            -water true
                            -totals Sul
                            -pH true
                        END
                        """);
            } catch (IPhreeqcException e) {
                System.out.println("[锅2] 引擎抛错（合法结局之一）: " + firstLine(e.getMessage()));
                return;
            }
            if (r.rowCount() == 0) {
                // 引擎级发现：run rc=0 但零 punch 行（熬干边缘的静默行为）——记录为证据
                System.out.println("[锅2] 发现: rc=0 但 0 punch 行（warnings=" + firstLine(r.warnings()) + "）");
                return;
            }
            int last = r.rowCount() - 1;
            double w = r.row(last).d("mass_H2O");
            double sul = r.row(last).d("Sul") * w;
            System.out.printf("[锅2] 末态: mass_H2O=%.4f kg Sul=%.4f mol pH=%.2f (rows=%d)%n",
                    w, sul, r.row(last).d("pH"), r.rowCount());
            // 关键观察：水被吃掉多少？Sul/水 比逼近多少？是否出现荒谬负数/NaN？
            assertTrue(w > 0, "水量必须为正: " + w);
            assertTrue(sul >= 0, "Sul 不得为负: " + sul);
            assertTrue(!Double.isNaN(w) && !Double.isNaN(sul), "不得 NaN");
        }
    }

    // ==== 锅3：中断存档汤（假设：SOLUTION_RAW 不含 KINETICS 状态 → 恢复后淬灭重置） ====

    @Test
    @DisplayName("锅3 中断存档汤：淬灭半途 archive→restore——KINETICS 进度是否丢失")
    void interruptedArchiveLosesKineticProgress() {
        String archive;
        double hypAtArchive;
        try (IPhreeqc q1 = IPhreeqc.create()) {
            // 跑到半途（t=300s，淬灭未完成）
            IPhreeqc.RunResult half = q1.run(ChemState.builder("quench half")
                            .waterKg(1.0).pHCharge().pe(4)
                            .total("Na", 0.18).total("Cl", 0.10)
                            .total("Hyp", 0.05).total("Sul", 0.01).build()
                    .toSolutionScript(1) + "END\n"
                    + C.ratesBlock() + "END\nUSE solution 1\n"
                    + C.kineticsBlock(java.util.Set.of("Quench"), null, 300)
                    + """
                    SELECTED_OUTPUT 1
                        -totals Hyp Sul
                    END
                    """);
            hypAtArchive = half.row(half.rowCount() - 1).d("Hyp");
            archive = q1.runDump(1);
        }
        // 恢复到新会话，继续淬灭 1000s
        try (IPhreeqc q2 = IPhreeqc.create()) {
            IPhreeqc.RunResult cont = q2.run(archive + "\nEND\n"
                    + C.ratesBlock() + "\nUSE solution 1\n"
                    + C.kineticsBlock(java.util.Set.of("Quench"), null, 1000)
                    + """
                    SELECTED_OUTPUT 1
                        -totals Hyp Sul
                    END
                    """);
            double hypAfter = cont.row(cont.rowCount() - 1).d("Hyp");
            double sulAfter = cont.row(cont.rowCount() - 1).d("Sul");
            System.out.printf("[锅3] 存档时 Hyp=%.5f Sul≈%.5f; 恢复续跑 1000s 后 Hyp=%.5f Sul=%.5f%n",
                    hypAtArchive, 0.0, hypAfter, sulAfter);
            // 证据收集：如果 Sul 在恢复时凭空出现（淬灭重置），说明 KINETICS 状态未持久化
            // （元素账守恒——Hyp+Sul+Cl 总量不变——但"进度"语义丢失）
            assertTrue(sulAfter <= 0.0101, "恢复后 Sul 不得超过初始 10 mmol（账本守恒线）");
        }
    }

    // ==== 锅4：氧化还原汤（假设：多电对下 pe 涌现值荒谬是否有害） ====

    @Test
    @DisplayName("锅4 氧化还原汤：Fe/Mn/N/S 多电对元素总量输入——pe 涌现与电荷平衡")
    void redoxSoupEmergentPe() {
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.run("""
                    SOLUTION 1 redox soup
                        pH        7 charge
                        pe        4
                        Fe        10 mmol/kgw
                        Mn        5 mmol/kgw
                        N         20 mmol/kgw
                        S         30 mmol/kgw
                        Na        40 mmol/kgw
                        Cl        60 mmol/kgw
                    END
                    USE solution 1
                    REACTION 1
                        Fe 1
                        5 mmol in 1 step
                    SELECTED_OUTPUT 1
                        -state true
                        -totals Fe Mn N S
                        -molalities Fe+2 Fe+3 Mn+2 NO3- NH4+ SO4-2 SO3-2
                        -pH true
                        -pe true
                    END
                    """);
            int last = r.rowCount() - 1;
            System.out.printf("[锅4] pH=%.2f pe=%.2f Fe(2)=%.2e Fe(3)=%.2e Mn+2=%.2e "
                            + "NO3-=%.2e NH4+=%.2e SO4=%.2e SO3=%.2e%n",
                    r.row(last).d("pH"), r.row(last).d("pe"),
                    r.row(last).d("m_Fe+2"), r.row(last).d("m_Fe+3"), r.row(last).d("m_Mn+2"),
                    r.row(last).d("m_NO3-"), r.row(last).d("m_NH4+"),
                    r.row(last).d("m_SO4-2"), r.row(last).d("m_SO3-2"));
            assertEquals(0.015, r.row(last).d("Fe"), 1e-4, "Fe 总量守恒（+5 mmol 元素铁投料）");
            assertEquals(0.020, r.row(last).d("N"), 1e-4, "N 守恒");
            assertEquals(0.030, r.row(last).d("S"), 1e-4, "S 守恒");
            assertTrue(!Double.isNaN(r.row(last).d("pe")), "pe 不得 NaN");
        }
    }

    // ==== 锅5：热汤（假设：van't Hoff 外推出界/溶解度反转） ====

    @Test
    @DisplayName("锅5 热汤：25→150°C 碳酸钙/硫酸钙体系——K(T) 外推行为")
    void hotSoupVanTHoffExtrapolation() {
        try (IPhreeqc q = IPhreeqc.create()) {
            for (double t : new double[]{25, 80, 150}) {
                IPhreeqc.RunResult r = q.run(ChemState.builder("hot soup " + (int) t)
                                .waterKg(1.0).pHCharge().pe(4).tempC(t)
                                .total("Ca", 0.02).total("C", 0.04)
                                .total("S", 0.03).total("Cl", 0.04).build()
                        .toSolutionScript(1) + """
                        END
                        USE solution 1
                        EQUILIBRIUM_PHASES 1
                            Calcite  0  0
                            Gypsum   0  0
                            Anhydrite 0 0
                        SELECTED_OUTPUT 1
                            -totals Ca S C
                            -molalities Ca+2 SO4-2 HCO3-
                            -equilibrium_phases Calcite Gypsum Anhydrite
                            -pH true
                        END
                        """);
                int last = r.rowCount() - 1;
                System.out.printf("[锅5] T=%d°C: pH=%.2f Calcite=%.4f Gypsum=%.4f Anhydrite=%.4f "
                                + "m_Ca+2=%.4f m_SO4=%.4f%n",
                        (int) t, r.row(last).d("pH"),
                        r.row(last).d("Calcite"), r.row(last).d("Gypsum"), r.row(last).d("Anhydrite"),
                        r.row(last).d("m_Ca+2"), r.row(last).d("m_SO4-2"));
                double caAll = r.row(last).d("Ca") + r.row(last).d("Calcite") + r.row(last).d("Gypsum")
                        + r.row(last).d("Anhydrite");
                assertEquals(0.02, caAll, 5e-4, "Ca 总账 @T=" + t);
            }
        }
    }

    // ==== 锅6：卤水极限汤（假设：SIT 活度模型有效域外静默失效） ====

    @Test
    @DisplayName("锅6 卤水极限汤：>6 molal NaCl——SIT 外推的静默程度")
    void brineLimitSoupBeyondSitDomain() {
        try (IPhreeqc q = IPhreeqc.create()) {
            for (double m : new double[]{1.0, 4.0, 6.5}) {
                // punch 铁律：SELECTED_OUTPUT 必须与被 punch 的计算同模拟
                // （END 分隔后定义只对后续模拟生效，i_soln 不追溯——PE 探针实证）
                IPhreeqc.RunResult r = q.run(ChemState.builder("brine limit " + m)
                                .waterKg(1.0).pHCharge().pe(4)
                                .total("Na", m).total("Cl", m).build()
                        .toSolutionScript(1) + """
                        SELECTED_OUTPUT 1
                            -totals Na Cl
                            -molalities Na+ Cl-
                            -pH true
                        END
                        """);
                if (r.rowCount() == 0) {
                    // 引擎级发现：rc=0 但 0 punch 行 = SIT 域外的静默失效假设命中
                    System.out.printf("[锅6] m=%2.1f: 发现静默 0 行 (warnings=%s)%n", m,
                            r.warnings().isBlank() ? "无" : firstLine(r.warnings()));
                    continue;
                }
                int last = r.rowCount() - 1;
                System.out.printf("[锅6] m=%2.1f: pH=%.2f (警告: %s)%n",
                        m, r.row(last).d("pH"),
                        r.warnings().isBlank() ? "无" : firstLine(r.warnings()));
                assertEquals(m, r.row(last).d("Na"), 1e-6, "Na 守恒 @m=" + m);
            }
        }
    }

    // ==== 附：SI 扫描器自证（用一锅汤的 Fe 场景） ====

    @Test
    @DisplayName("附: SiProbe——Fe 汤扫描出 Ferrihydrite(am) 缺策展")
    void siProbeFlagsMissingFerrihydriteCuration() {
        try (IPhreeqc q = IPhreeqc.create()) {
            q.run("""
                    SOLUTION 1 fe soup without fe mineral phases
                        pH        9 charge
                        pe        4
                        Fe        10 mmol/kgw
                        Na        10 mmol/kgw
                        Cl        30 mmol/kgw
                    END
                    """);
            List<SiProbe.Finding> risks = SiProbe.scan(q, 1.0,
                    java.util.Arrays.asList("Ferrihydrite(am)", "Ferrihydrite(s)", "Ferrihydrite(cr)",
                            "Goethite", "Hematite(cr)", "Siderite", "Calcite", "Barite"),
                    "Calcite");
            System.out.println("[SI] 过饱和未声明相 top5: "
                    + risks.subList(0, Math.min(5, risks.size())));
            assertTrue(risks.stream().anyMatch(f -> f.phase().startsWith("Ferrihydrite")),
                    "应报出 Ferrihydrite 缺策展: " + risks);
        }
    }

    private static String firstLine(String s) {
        int i = s == null ? -1 : s.indexOf('\n');
        return i < 0 ? String.valueOf(s) : s.substring(0, i);
    }
}
