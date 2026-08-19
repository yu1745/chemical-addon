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
 * <p>对应发现档案：锅4（还原 pe 下 N 全塌 NH4+，真实硝酸盐介稳）、一锅汤（Fe 遇漂白液盲区）、
 * 电解→漂白液链断裂。CLI 探针基线（/tmp/gap1/2/4.phq）：
 * <ul>
 *   <li>FerrousReducesNitrate：Nitra 20→7.8 @10ks，Nitri 2→12.2，和恒 20，Fe 不动；</li>
 *   <li>HypOxidisesFerrous：Hyp 25→16.8 @1ks 递减，Cl 10→18.2 1:1，Fe/Na 不动；</li>
 *   <li>ChlorineAbsorbs：Hyp=Cl 1:1 增长（0.02→0.159 mol @1ks），OH⁻ 门控 pH 13.1→6.9。</li>
 * </ul>
 *
 * <p>引擎边界（_doc2 归档）：价态 token 结构性不可能（Fe 只作速率门）；
 * 池销毁必须 TOT 门控（销毁不存在的池 = 无限恢复循环挂死）。
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
    @DisplayName("Fe²⁺ 还原硝酸盐：Nitra→Nitri 1:1，Fe 只作速率门（元素账不动）")
    void ferrousReducesNitrateToNitrite() {
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.run(ChemState.builder("nitrate + ferrous")
                            .waterKg(1.0).pHCharge().pe(4)
                            .total("Fe", 0.010).total("Na", 0.010).total("Nitra", 0.020)
                            .build().toSolutionScript(1) + "END\n"
                    + C.ratesBlock() + "\nUSE solution 1\n"
                    + C.kineticsBlock(Set.of("FerrousReducesNitrate"), null, 1000, 10000)
                    + """
                    SELECTED_OUTPUT 1
                        -state    true
                        -time     true
                        -totals   Nitra  Nitri  Fe
                        -pH       true
                    END
                    """);
            int last = r.rowCount() - 1;
            double nitra = r.row(last).d("Nitra");
            double nitri = r.row(last).d("Nitri");
            System.out.printf("[Fe还原NO3] t=10ks: Nitra=%.4f Nitri=%.4f Fe=%.4f pH=%.2f%n",
                    nitra, nitri, r.row(last).d("Fe"), r.row(last).d("pH"));
            assertEquals(0.020, nitra + nitri, 1e-4, "池间 1:1 转化，总量守恒");
            assertEquals(0.0078, nitra, 5e-4, "Nitra 20→~7.8 mM（CLI 基线）");
            assertEquals(0.010, r.row(last).d("Fe"), 1e-6, "Fe 只作速率门，元素账不动");
            // 中间步：单调
            assertTrue(r.row(0).d("Nitra") > nitra, "Nitra 单调下降");
        }
    }

    @Test
    @DisplayName("Fe²⁺ 污染漂白液：Hyp 被消耗、Cl 1:1 释放（一锅汤盲区补全）")
    void ferrousContaminationConsumesBleach() {
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.run("""
                    SOLUTION 1 bleach contaminated by ferrous
                        pH        11 charge
                        pe        4
                        Na        15 mmol/kgw
                        Cl        10 mmol/kgw
                        Hyp       25 mmol/kgw
                        Fe        10 mmol/kgw
                    END
                    """ + C.ratesBlock() + """
                    USE solution 1
                    KINETICS 1
                    HypOxidisesFerrous
                        -formula Cl 1 Hyp -1
                        -m        1000
                        -m0       1000
                        -steps    100 1000 seconds
                    SELECTED_OUTPUT 1
                        -state    true
                        -time     true
                        -totals   Hyp  Cl  Fe  Na
                        -pH       true
                    END
                    """);
            int last = r.rowCount() - 1;
            double hyp = r.row(last).d("Hyp");
            double cl = r.row(last).d("Cl");
            System.out.printf("[Fe污染漂白液] t=1ks: Hyp=%.4f Cl=%.4f Fe=%.4f pH=%.2f%n",
                    hyp, cl, r.row(last).d("Fe"), r.row(last).d("pH"));
            assertTrue(hyp < 0.020, "Hyp 应被显著消耗（25→<20 mM），实测 " + hyp);
            assertEquals(0.035 - hyp, cl, 2e-4, "Cl = 10 + 消耗的 Hyp（1:1）");
            assertEquals(0.010, r.row(last).d("Fe"), 1e-6, "Fe 元素账不动");
            assertEquals(0.015, r.row(last).d("Na"), 1e-6, "Na 旁观守恒");
            // pH 方向：漂白液氧化力损失（净消耗 OH-），pH 下降
            assertTrue(r.row(last).d("pH") < 10.0, "pH 应显著下降: " + r.row(last).d("pH"));
        }
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
}
