package com.yu1745.chemengine.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 策展缺口补全验收：硝酸/亚硝酸介稳池 + 铁污染漂白液 + 氯气制漂白液。
 *
 * <p>每项动力学断言均与相同进料的零步 KINETICS 基线比较。这样不会把
 * SOLUTION 初始平衡或数据库版本造成的物种分配误报为策展反应 extent。
 */
class CurationGapTest {

    private static final Curation C = Curation.load();

    @Test
    @DisplayName("硝酸/亚硝酸池：介稳共存（真实 N 与池互不转化）")
    void nitrateAndNitritePoolsAreMetastable() {
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.run("""
                    SOLUTION 1 nitrate salt + real ammonium
                        pH        7 charge
                        pe        -4
                        Na        30 mmol/kgw
                        Nitra     20 mmol/kgw
                        Nitri     2 mmol/kgw
                        N         5 mmol/kgw
                    SELECTED_OUTPUT 1
                        -totals   Nitra  Nitri  N
                        -molalities Nitra-  Nitri-  NO3-
                        -pH true
                        -pe true
                    END
                    """);
            assertEquals(1, r.rowCount(), r.rawLines().toString());
            // 介稳本质：强还原 pe=-4 下，池 Nitra 不塌成 NH4+（锅4 的平衡参考里 N 全塌）
            assertEquals(0.020, r.row(0).d("Nitra"), 1e-6, "Nitra 池纹丝不动");
            assertEquals(0.002, r.row(0).d("Nitri"), 1e-6, "Nitri 池纹丝不动");
            assertTrue(r.row(0).d("m_NO3-") < 1e-6,
                    "对照：真实 N 在 pe -4 下被还原（不驻留 NO3-），实测 " + r.row(0).d("m_NO3-"));
        }
    }

    @Test
    @DisplayName("Fe²⁺/硝酸盐：Nitra→Nitri 1:1，原生 Fe²⁺ 损失 = 2×Nitri 新增")
    void ferrousReducesNitrateToNitrite() {
        var base = last(runFerrousNitrate(0));
        var active = last(runFerrousNitrate(1000, 10_000));
        double nitraExtent = mol(base, "Nitra") - mol(active, "Nitra");
        double nitriExtent = mol(active, "Nitri") - mol(base, "Nitri");
        double fe2Loss = mol(base, "Fe(2)") - mol(active, "Fe(2)");
        double fe3Gain = mol(active, "Fe(3)") - mol(base, "Fe(3)");
        assertTrue(nitraExtent > 1e-6, "Nitra→Nitri 必须发生正 extent");
        assertEquals(nitraExtent, nitriExtent, 1e-6, "N 原子在两个伪池间 1:1 转移");
        assertEquals(2.0 * nitriExtent, fe2Loss, 1e-6, "原生 Fe²⁺ 损失 = 2×Nitri 新增");
        assertEquals(fe2Loss, fe3Gain, 1e-6, "Fe 只在 Fe(2)/Fe(3) 价态间迁移");
        assertEquals(mol(base, "Fe"), mol(active, "Fe"), 1e-6, "Fe 原子总量守恒");
    }

    @Test
    @DisplayName("Fe 污染漂白液：相对零步基线 Hyp→Cl 1:1，Fe/Na 原子守恒")
    void ferrousContaminationConsumesBleach() {
        var base = last(runFerrousBleach(0));
        var active = last(runFerrousBleach(100, 1000));
        double hypExtent = mol(base, "Hyp") - mol(active, "Hyp");
        double clGain = mol(active, "Cl") - mol(base, "Cl");
        assertTrue(hypExtent > 1e-6, "Fe 门控下 Hyp 必须相对零步基线发生正的失效 extent");
        assertEquals(hypExtent, clGain, 1e-6, "Hyp 的 Cl 原子按 1:1 进入真实 Cl 池");
        assertEquals(mol(base, "Hyp") + mol(base, "Cl"), mol(active, "Hyp") + mol(active, "Cl"),
                1e-6, "Hyp/Cl 的总 Cl 原子守恒");
        assertEquals(mol(base, "Fe"), mol(active, "Fe"), 1e-6, "Fe 原子总量守恒");
        assertEquals(mol(base, "Na"), mol(active, "Na"), 1e-6, "Na 旁观原子守恒");
    }

    @Test
    @DisplayName("氯气入碱制漂白液：Hyp=Cl 1:1 增长，OH- 门控自停（电解链闭合）")
    void chlorineAbsorbsIntoCausticMakesBleach() {
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.run(ChemState.builder("caustic for Cl2 absorption")
                            .waterKg(1.0).pHCharge().pe(4)
                            .total("Na", 0.200).build().toSolutionScript(1) + "END\n"
                    + C.ratesBlock() + "\nUSE solution 1\n"
                    + C.kineticsBlock(Set.of("ChlorineAbsorbs"),
                            Map.of("ChlorineAbsorbs", new double[]{0.002, 1.0}),
                            10, 100, 1000)
                    + """
                    SELECTED_OUTPUT 1
                        -state    true
                        -time     true
                        -totals   Hyp  Cl  Na
                        -pH       true
                    END
                    """);
            int last = r.rowCount() - 1;
            double hyp = r.row(last).d("Hyp");
            double cl = r.row(last).d("Cl");
            System.out.printf("[Cl2吸收] t=1ks: Hyp=%.4f Cl=%.4f Na=%.4f pH=%.2f%n",
                    hyp, cl, r.row(last).d("Na"), r.row(last).d("pH"));
            assertEquals(hyp, cl, 1e-4, "Hyp 与 Cl 1:1（Cl2+2OH-→Cl-+OCl-+H2O）");
            assertTrue(hyp > 0.10 && hyp < 0.20, "t=1ks 吸收 0.1-0.2 mol（CLI 基线 0.159）");
            assertEquals(0.200, r.row(last).d("Na"), 5e-4, "Na 守恒（punch 精度内）");
            assertTrue(r.row(last).d("pH") < 10.0, "OH- 被耗，pH 大降（13→<10）");
            // 门控生效：最后一步增量远小于满速率（碱耗尽自停）
            double inc3 = hyp - r.row(last - 1).d("Hyp");
            assertTrue(inc3 < 0.05, "OH- 门控减速（末步增量 " + inc3 + " < 满速率 0.02×900）");
        }
    }

    @Test
    @DisplayName("加载器防线：池销毁速率门静态可查 + 价态 token 依旧拒绝")
    void loaderGuardsRemain() {
        // 每个销毁池的反应必须在其 rateExpression 中 TOT 门控（防无限恢复循环挂死）
        for (Curation.Reaction rx : C.reactions()) {
            for (String token : rx.formulaView().keySet()) {
                boolean pseudo = C.pseudoElements().stream()
                        .anyMatch(pe -> pe.element.equals(token));
                if (pseudo) {
                    double coef = rx.formulaView().get(token);
                    if (coef < 0) {
                        assertTrue(rx.rateExpression.contains("TOT(\"" + token + "\")"),
                                rx.name + " 销毁池 " + token + " 必须以 TOT 门控速率（否则挂死）");
                    }
                }
            }
        }
        // 价态 token 拒绝（结构性不可能，源码已证）
        assertTrue(C.reaction("FerrousReducesNitrate").rateExpression.contains("TOT(\"Fe(+2)\")"),
                "Fe(+2) 只能出现在速率表达式（BASIC TOT 合法），formula 中仍非法");
        assertEquals(Curation.Kind.INTERFACE, C.reaction("ChlorineAbsorbs").kindEnum());
    }

    private static IPhreeqc.RunResult runFerrousNitrate(double... steps) {
        ChemState feed = ChemState.builder("nitrate + ferrous")
                .waterKg(1.0).pHCharge().pe(0)
                .total("Fe", 0.010).total("Na", 0.010).total("Nitra", 0.020)
                .build();
        try (IPhreeqc q = IPhreeqc.create()) {
            return q.run(feed.toSolutionScript(1) + "END\n" + C.ratesBlock() + "END\nUSE solution 1\n"
                    + C.kineticsBlock(Set.of("FerrousReducesNitrate"), null, steps) + selectedOutput());
        }
    }

    private static IPhreeqc.RunResult runFerrousBleach(double... steps) {
        String feed = """
                SOLUTION 1 bleach contaminated by ferrous
                    pH        11 charge
                    pe        4
                    water     1 kg
                    Na        15 mmol/kgw
                    Cl        10 mmol/kgw
                    Hyp       25 mmol/kgw
                    Fe        10 mmol/kgw
                END
                """;
        try (IPhreeqc q = IPhreeqc.create()) {
            return q.run(feed + C.ratesBlock() + "END\nUSE solution 1\n"
                    + C.kineticsBlock(Set.of("HypOxidisesFerrous"), null, steps) + selectedOutput());
        }
    }

    private static String selectedOutput() {
        return """
                SELECTED_OUTPUT 1
                    -state true
                    -time true
                    -water true
                    -high_precision true
                    -totals Nitra Nitri Hyp Cl Fe Fe(2) Fe(3) Na
                    -pH true
                    -pe true
                END
                """;
    }

    private static IPhreeqc.RunResult.Row last(IPhreeqc.RunResult result) {
        return result.row(result.rowCount() - 1);
    }

    private static double mol(IPhreeqc.RunResult.Row row, String column) {
        return row.d(column) * row.d("mass_H2O");
    }
}
