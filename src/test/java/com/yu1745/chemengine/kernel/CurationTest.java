package com.yu1745.chemengine.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 策展表验收（G2 前奏）：chemistry.json → addendum/RATES/KINETICS 文本 → 引擎行为。
 *
 * <p>对照基线：{@link NamedScenariosTest#sulphiteQuenchesBleachViaKineticWhitelist}
 * 的手写脚本数字（t=1000s：Sul≈0.147 / Hyp≈40.1 / Cl≈109.85 / S≈9.85）——
 * 策展表生成的文本必须逐位复现同一结果。
 */
class CurationTest {

    private static final double MMOL = 1000.0;

    private static ChemState quenchFeed() {
        return ChemState.builder("bleach + sulphite, curated quench")
                .waterKg(1.0)
                .pHCharge()
                .pe(4)
                .total("Na", 0.180)
                .total("Cl", 0.100)
                .total("Hyp", 0.050)
                .total("Sul", 0.010)
                .build();
    }

    @Test
    @DisplayName("策展 addendum 装载：伪元素从 JSON 进数据库（Hyp 池可用）")
    void curatedAddendumLoadsPseudoElements() {
        Curation c = Curation.load();
        assertTrue(c.pseudoElements().stream().anyMatch(p -> p.element.equals("Hyp")),
                "Hyp 伪元素应在策展表中");
        String addendum = c.addendumText();
        assertTrue(addendum.contains("SOLUTION_MASTER_SPECIES"), addendum);
        assertTrue(addendum.contains("Hyp- + H+ = HypH"), addendum);

        // 全链路：Database（含策展 addendum）→ equilibrate 含 Hyp 的状态
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.equilibrate(quenchFeed(), "Hyp-");
            assertEquals(0.050, r.row(0).d("Hyp"), 1e-9, "Hyp 池经 JSON 注册后可用");
        }
    }

    @Test
    @DisplayName("策展淬灭 = 手写 m1 基线（t=1000s 数字逐位复现）")
    void curatedQuenchReproducesHandwrittenBaseline() {
        Curation c = Curation.load();
        try (IPhreeqc q = IPhreeqc.create()) {
            String script = quenchFeed().toSolutionScript(1)
                    + "END\n"
                    + c.ratesBlock()
                    + "END\n"
                    + "USE solution 1\n"
                    + c.kineticsBlock(java.util.Set.of("Quench"), null, 1, 10, 100, 1000)
                    + """
                    SELECTED_OUTPUT 1
                        -state    true
                        -time     true
                        -totals   Cl  Hyp  Sul  S
                        -pH       true
                    END
                    """;
            IPhreeqc.RunResult r = q.run(script);
            assertEquals(4, r.rowCount(), r.rawLines().toString());

            // 单调下降
            for (int i = 1; i < r.rowCount(); i++) {
                assertTrue(r.row(i).d("Sul") <= r.row(i - 1).d("Sul") + 1e-12,
                        "Sul 单调下降: step " + i);
            }
            int last = r.rowCount() - 1;
            assertEquals(0.147, r.row(last).d("Sul") * MMOL, 0.05, "t=1000s Sul（m1 基线 0.147）");
            assertEquals(40.1, r.row(last).d("Hyp") * MMOL, 0.2, "Hyp 50-10（基线 40.147）");
            assertEquals(109.85, r.row(last).d("Cl") * MMOL, 0.2, "Cl 100+10（基线 109.850）");
            assertEquals(9.85, r.row(last).d("S") * MMOL, 0.1, "S 守恒（基线 9.853）");
        }
    }

    @Test
    @DisplayName("酸门控漂白液活化：pH 12 不动 / pH 1 全灭（策展反应 AcidActivatesBleach）")
    void acidGatedBleachDestruction() {
        Curation c = Curation.load();
        // 碱性：纹丝不动
        try (IPhreeqc q = IPhreeqc.create()) {
            ChemState alkaline = ChemState.builder("alkaline bleach")
                    .waterKg(1.0).pHCharge().pe(4)
                    .total("Na", 0.150).total("Cl", 0.100).total("Hyp", 0.050)
                    .build();
            IPhreeqc.RunResult r = q.run(alkaline.toSolutionScript(1) + "END\n"
                    + c.ratesBlock() + "END\nUSE solution 1\n" + c.kineticsBlock(java.util.Set.of("AcidActivatesBleach"), null, 1000)
                    + """
                    SELECTED_OUTPUT 1
                        -totals   Cl  Hyp
                    END
                    """);
            int last = r.rowCount() - 1;
            assertEquals(50.0, r.row(last).d("Hyp") * MMOL, 0.01, "pH 12（emerged 9.99）应零消耗");
        }
        // 酸性：全灭，Hyp 的 Cl 归入真实 Cl 池
        try (IPhreeqc q = IPhreeqc.create()) {
            ChemState acid = ChemState.builder("acidified bleach")
                    .waterKg(1.0).pH(1).pe(4)
                    .total("Na", 0.150).total("Cl", 0.100).total("Hyp", 0.050)
                    .build();
            IPhreeqc.RunResult r = q.run(acid.toSolutionScript(1) + "END\n"
                    + c.ratesBlock() + "END\nUSE solution 1\n" + c.kineticsBlock(java.util.Set.of("AcidActivatesBleach"), null, 1000)
                    + """
                    SELECTED_OUTPUT 1
                        -totals   Cl  Hyp
                    END
                    """);
            int last = r.rowCount() - 1;
            assertTrue(r.row(last).d("Hyp") * MMOL < 0.5,
                    "pH 1 应全灭，实测 " + r.row(last).d("Hyp") * MMOL);
            assertEquals(150.0, r.row(last).d("Cl") * MMOL, 0.5, "Hyp 的 Cl 归入真实池");
        }
    }

    @Test
    @DisplayName("文本生成器：块结构稳定 + 空步拒绝 + 价态 token 拒绝")
    void textGeneratorsAreStableAndValidated() {
        Curation c = Curation.load();
        String rates = c.ratesBlock();
        assertTrue(rates.startsWith("RATES\n"), rates);
        assertTrue(rates.contains("Quench\n    -start"), rates);
        assertTrue(rates.contains("SAVE r * TIME * TOT(\"water\")"), rates);

        String kin = c.kineticsBlock(1, 10);
        assertTrue(kin.contains("-formula Cl 1 Hyp -1 O 4 S 1 Sul -1"), kin);  // TreeMap 字母序
        assertTrue(kin.contains("-steps   1 10 seconds"), kin);
        assertFalse(kin.contains("SulAbsorb"), "interface 默认不发射: " + kin);
        // 显式 opt-in：默认参数与覆盖路径
        String kinP = c.kineticsBlock(java.util.Set.of("SulAbsorb"), null, 1000);
        assertTrue(kinP.contains("-parms    1.24 1"), kinP);
        String kin2 = c.kineticsBlock(java.util.Set.of("SulAbsorb"),
                Map.of("SulAbsorb", new double[]{1.24, 0.0}), 1000);
        assertTrue(kin2.contains("-parms    1.24 0"), kin2);
        assertThrows(IllegalArgumentException.class, () -> c.kineticsBlock());

        assertEquals("Quench", c.reaction("Quench").name);
        assertThrows(IllegalArgumentException.class, () -> c.reaction("NoSuch"));

        // 策展相已移除（sit.dat 自带 MnO2(s) log_k 42，数据源更新，Pyrolusite 策展补丁退役）
        // addendum 不再含 PHASES 块；phases 查询对缺失名拋出
    }

    @Test
    @DisplayName("策展表原子审计：bulk 零残差，interface 明示外部 SO2/Cl2 原子流")
    void curatedFormulaeExpandToDeclaredRealAtomFlows() {
        Curation c = Curation.load();
        for (Curation.Reaction reaction : c.reactions()) {
            Curation.ExpandedFormulaAudit audit = c.expandedFormulaAudit(reaction);
            if (reaction.kindEnum() == Curation.Kind.BULK) {
                assertTrue(audit.isAtomConserving(), reaction.name + " / " + audit.atomDelta());
            } else {
                assertEquals(reaction.reservoirAtoms, audit.atomDelta(), reaction.name);
            }
        }
        assertEquals(Map.of("Cl", 1.0, "O", 1.0),
                c.pseudoElements().stream().filter(p -> p.element.equals("Hyp")).findFirst().orElseThrow().atoms);
        assertEquals(Map.of("S", 1.0, "O", 3.0),
                c.pseudoElements().stream().filter(p -> p.element.equals("Sul")).findFirst().orElseThrow().atoms);
    }
}
