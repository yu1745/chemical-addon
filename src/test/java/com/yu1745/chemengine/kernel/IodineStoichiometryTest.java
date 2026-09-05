package com.yu1745.chemengine.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Quantitative acceptance for the supported acidic bleach/iodide endpoint.
 *
 * <p>The feed deliberately has excess iodide and a limiting amount of Hyp. The required endpoint is
 * {@code OCl- + 2 I- + 2 H+ -> Cl- + I2 + H2O}; excess bleach is a distinct regime which can
 * continue to iodate and is therefore not covered by this contract. I3- carries one I2-equivalent
 * ({@code I2 + I- = I3-}); ICl2- contains formal I(+1), so it likewise carries one I2-equivalent.
 * The I(-1) total cannot be used as an extent because sit.dat puts I3- in that DLP total.
 */
class IodineStoichiometryTest {
    private static final Curation CURATION = Curation.load();

    @Test
    @DisplayName("酸性限量漂白液：Hyp:I2 当量=1:1，2 I 原子氧化，不越级生成碘酸盐")
    void hypochloriteLimitedAcidicIodideFormsIodine() {
        IPhreeqc.RunResult.Row baseline = last(run(hypLimitedFeed(), 0.0));
        IPhreeqc.RunResult.Row active = last(run(hypLimitedFeed(), 1e-5, 1e-3, 1e-1));

        double hypExtent = mol(baseline, "Hyp") - mol(active, "Hyp");
        double chlorideGain = mol(active, "Cl") - mol(baseline, "Cl");
        double iodineProductGain = iodineProductEquivalents(active) - iodineProductEquivalents(baseline);
        double iodateGain = mol(active, "I(+5)") - mol(baseline, "I(+5)");

        assertEquals(1e-3, hypExtent, 5e-6,
                () -> "Hyp 是限量试剂，0.1 s 后应基本完全消耗 1 mmol; " + debug(baseline, active));
        assertEquals(hypExtent, chlorideGain, 5e-6,
                () -> "每 mol Hyp 的 Cl 原子必须进入原生 Cl 池; " + debug(baseline, active));
        assertEquals(hypExtent, iodineProductGain, 5e-6,
                () -> "每 mol Hyp 必须产生 1 mol I2 当量（I2(cr)、I3-、ICl2- 合计），即 2 mol I 原子被氧化; "
                        + debug(baseline, active));
        assertEquals(0.0, iodateGain, hypExtent * 0.01,
                () -> "Hyp 已限量时终点是 I2 当量；碘酸盐属于过量氧化剂的另一工况; " + debug(baseline, active));

        assertEquals(iodineAtoms(baseline), iodineAtoms(active), 5e-6,
                () -> "I 原子须在原生水相、I3-/ICl2- 等络合物和 I2(cr) 间守恒; " + debug(baseline, active));
        assertTrue(baseline.d("pH") < 5.0 && active.d("pH") < 5.0,
                () -> "该回归只覆盖酸性碘量法终点，实际 pH 必须保持酸性; " + debug(baseline, active));
    }

    @Test
    @DisplayName("无碘负对照：碘通道不消耗 Hyp，也不生成 Cl")
    void noIodideLeavesIodineChannelInert() {
        IPhreeqc.RunResult.Row baseline = last(run(noIodideFeed(), 0.0));
        IPhreeqc.RunResult.Row active = last(run(noIodideFeed(), 1e-3, 1, 100));
        assertEquals(mol(baseline, "Hyp"), mol(active, "Hyp"), 1e-9,
                () -> "无 I(-1) 时不得借由其他支路消耗 Hyp; " + debug(baseline, active));
        assertEquals(mol(baseline, "Cl"), mol(active, "Cl"), 1e-9,
                () -> "无 I(-1) 时不得生成反应性 Cl; " + debug(baseline, active));
        assertEquals(0.0, iodineProductEquivalents(active) - iodineProductEquivalents(baseline), 1e-12,
                () -> "无碘进料不得凭空形成含碘产物; " + debug(baseline, active));
    }

    @Test
    @DisplayName("碘限量、漂白液过量：碘酸盐生成，Hyp 与碘电子当量严格对应")
    void iodideLimitedExcessHypochloriteStopsAtIodineElectronInventory() {
        IPhreeqc.RunResult baselineResult = runExcessHypDiagnostic(0.0);
        IPhreeqc.RunResult activeResult = runExcessHypDiagnostic(1e-5, 1e-3, 1, 100);
        IPhreeqc.RunResult.Row baseline = last(baselineResult);
        IPhreeqc.RunResult.Row active = last(activeResult);
        double hypExtent = mol(baseline, "Hyp") - mol(active, "Hyp");
        double iodateGain = mol(active, "I(+5)") - mol(baseline, "I(+5)");
        double iodineElectronEquivalents = iodineOxidationEquivalents(active)
                - iodineOxidationEquivalents(baseline);
        System.out.println("[iodine excess-Hyp audit] headings=" + activeResult.rawLines().get(0)
                + " baseline=" + baseline + " active=" + active);

        assertTrue(iodateGain > 1e-4,
                () -> "漂白液过量时应允许原生 I(+5)/IO3- 正向生成; " + debug(baseline, active));
        assertTrue(iodineElectronEquivalents > 1e-4,
                () -> "必须有可观测的碘氧化产物当量; " + debug(baseline, active));
        assertEquals(hypExtent, iodineElectronEquivalents, 5e-6,
                () -> "只启用碘通道时，每 mol Hyp 必须对应一 mol 碘 I2 电子当量；不能用未启用的 O2/Cl2 支路掩盖 Hyp 消耗; "
                        + debug(baseline, active));
        assertTrue(mol(active, "Hyp") >= 0.012 - 5e-6,
                () -> "2 mmol I 即使全氧化到 I(+7) 最多消耗 8 mmol Hyp；20 mmol 投料必须至少余 12 mmol; "
                        + debug(baseline, active));
        assertEquals(iodineAtoms(baseline), iodineAtoms(active), 5e-6,
                () -> "过量 Hyp 工况也必须守恒全部 I 原子; " + debug(baseline, active));
    }

    @Test
    @DisplayName("同一原生会话续算：保存终态后逐段闭合 Hyp、碘电子与 O2")
    void savedNativeEndpointContinuesWithoutUnaccountedHypDecomposition() {
        IPhreeqc.RunResult.Row baseline = last(runExcessHypDiagnostic(0.0));
        IPhreeqc.RunResult first;
        IPhreeqc.RunResult second;
        try (IPhreeqc q = IPhreeqc.create()) {
            first = q.run(script(iodideLimitedFeed(), true, true, 1e-5, 1e-3, 1, 100));
            second = q.run(CURATION.ratesBlock() + "END\nUSE solution 1\nUSE equilibrium_phases 1\n"
                    + CURATION.kineticsBlock(Set.of("HypOxidisesIodide"), null, 1, 100)
                    + outputBlock(true));
        }
        IPhreeqc.RunResult.Row afterFirst = last(first);
        IPhreeqc.RunResult.Row afterSecond = last(second);
        double firstHypExtent = mol(baseline, "Hyp") - mol(afterFirst, "Hyp");
        double secondHypExtent = mol(afterFirst, "Hyp") - mol(afterSecond, "Hyp");
        double firstIodineElectrons = iodineOxidationEquivalents(afterFirst)
                - iodineOxidationEquivalents(baseline);
        double secondIodineElectrons = iodineOxidationEquivalents(afterSecond)
                - iodineOxidationEquivalents(afterFirst);
        double secondOxygenGain = mol(afterSecond, "m_O2") - mol(afterFirst, "m_O2");

        assertTrue(mol(afterFirst, "Hyp") >= 0.012 - 5e-6,
                () -> "续算前须先证明 Hyp 没有被碘通道烧光; " + debug(afterFirst, afterSecond));
        assertTrue(mol(afterSecond, "Hyp") >= 0.012 - 5e-6,
                () -> "续算后仍受有限碘最大氧化当量约束，不能额外烧穿 Hyp; "
                        + debug(afterFirst, afterSecond));
        assertTrue(mol(afterFirst, "m_I-") >= 0.0,
                () -> "自由 I- 不能为负；I(-1) 总池包含 formal I(+1) 的 ICl2-，不能用作耗尽判据; "
                        + debug(afterFirst, afterSecond));
        assertTrue(mol(afterSecond, "Hyp") >= -1e-10 && mol(afterSecond, "I(-1)") >= -1e-10,
                () -> "续算后原料库存不得为负，亦不得触发 negative-mole 恢复循环; "
                        + debug(afterFirst, afterSecond));
        assertEquals(firstHypExtent, firstIodineElectrons, 5e-6,
                () -> "第一段 Hyp 消耗必须由新增碘电子当量解释; " + debug(baseline, afterFirst));
        assertEquals(secondHypExtent, secondIodineElectrons, 5e-7,
                () -> "自由 I- 残量允许第二段继续反应，但 Hyp 消耗必须逐段对应新增碘电子当量; "
                        + debug(afterFirst, afterSecond));
        assertEquals(0.0, secondOxygenGain, 5e-8,
                () -> "保存后的续算不能重新触发无碘产物对应的 O2 分解尾反应; "
                        + debug(afterFirst, afterSecond));
        assertEquals(iodineAtoms(afterFirst), iodineAtoms(afterSecond), 5e-6,
                () -> "续算不得丢失或复制碘原子; " + debug(afterFirst, afterSecond));
    }

    private static String hypLimitedFeed() {
        return """
                SOLUTION 1 acidic iodide, Hyp-limited
                    temp 25
                    pH 4 charge
                    pe 4
                    water 1 kg
                    Na 11 mmol/kgw
                    Cl 10 mmol/kgw
                    I 8 mmol/kgw
                    Hyp 1 mmol/kgw
                END
                """;
    }

    private static String noIodideFeed() {
        return """
                SOLUTION 1 acidic bleach without iodide
                    temp 25
                    pH 4 charge
                    pe 4
                    water 1 kg
                    Na 11 mmol/kgw
                    Cl 30 mmol/kgw
                    Hyp 20 mmol/kgw
                END
                """;
    }

    private static String iodideLimitedFeed() {
        return """
                SOLUTION 1 acidic iodide, excess Hyp
                    temp 25
                    pH 4 charge
                    pe 4
                    water 1 kg
                    Na 32 mmol/kgw
                    Cl 30 mmol/kgw
                    I 2 mmol/kgw
                    Hyp 20 mmol/kgw
                END
                """;
    }

    private static IPhreeqc.RunResult run(String feed, double... steps) {
        try (IPhreeqc q = IPhreeqc.create()) {
            return q.run(script(feed, steps));
        }
    }

    /** Extra native redox columns are intentionally confined to the excess-Hyp failure diagnosis. */
    private static IPhreeqc.RunResult runExcessHypDiagnostic(double... steps) {
        try (IPhreeqc q = IPhreeqc.create()) {
            return q.run(script(iodideLimitedFeed(), true, steps));
        }
    }

    private static String script(String feed, double... steps) {
        return script(feed, false, steps);
    }

    private static String script(String feed, boolean nativeOxidantAudit, double... steps) {
        return script(feed, nativeOxidantAudit, false, steps);
    }

    /** SAVE is used only by the continuation contract; ordinary probes need no cross-RunString state. */
    private static String script(String feed, boolean nativeOxidantAudit, boolean saveEndpoint, double... steps) {
        String save = saveEndpoint ? "SAVE solution 1\nSAVE equilibrium_phases 1\n" : "";
        return feed + CURATION.ratesBlock() + "END\nUSE solution 1\n"
                + CURATION.kineticsBlock(Set.of("HypOxidisesIodide"), null, steps) + """
                EQUILIBRIUM_PHASES 1
                    I2(cr) 0 0
                """ + save + outputBlock(nativeOxidantAudit);
    }

    private static String outputBlock(boolean nativeOxidantAudit) {
        String nativeAuditTotals = nativeOxidantAudit ? " Cl(0) O(0)" : "";
        String nativeAuditMolalities = nativeOxidantAudit ? " Cl2 O2" : "";
        String nativeAuditPunch = nativeOxidantAudit ? """
                        20 PUNCH MOL("Cl2") * TOT("water")
                        30 PUNCH MOL("O2") * TOT("water")
                """ : "";
        String nativeAuditHeadings = nativeOxidantAudit ? " chlorine_molecular_mol oxygen_molecular_mol" : "";
        return """
                SELECTED_OUTPUT 1
                    -state true
                    -time true
                    -water true
                    -high_precision true
                    -pH true
                    -totals Hyp Cl I I(-1) I(1) I(+5) I(7)%s
                    -molalities I- I3- ICl2- IO- IO3- IO4-%s
                USER_PUNCH 1
                    -headings iodine_solid_mol%s
                    -start
                    10 PUNCH EQUI("I2(cr)")
                %s
                    -end
                END
                """.formatted(nativeAuditTotals, nativeAuditMolalities, nativeAuditHeadings, nativeAuditPunch);
    }

    private static IPhreeqc.RunResult.Row last(IPhreeqc.RunResult result) {
        return result.row(result.rowCount() - 1);
    }

    private static double mol(IPhreeqc.RunResult.Row row, String column) {
        return row.d(column) * row.d("mass_H2O");
    }

    /** Solution totals exclude equilibrium-phase inventory; EQUI is already an absolute mol amount. */
    private static double iodineAtoms(IPhreeqc.RunResult.Row row) {
        return mol(row, "I") + 2.0 * row.d("iodine_solid_mol");
    }

    /** I2(cr) is absolute mol; the two aqueous complexes are molality and must be water-scaled. */
    private static double iodineProductEquivalents(IPhreeqc.RunResult.Row row) {
        return row.d("iodine_solid_mol") + mol(row, "m_I3-") + mol(row, "m_ICl2-");
    }

    /** One mol I(+1), I2, I3- or ICl2- represents two released electrons; I(+5) represents six. */
    private static double iodineOxidationEquivalents(IPhreeqc.RunResult.Row row) {
        return iodineProductEquivalents(row) + mol(row, "I(1)") + 3.0 * mol(row, "I(+5)")
                + 4.0 * mol(row, "I(7)");
    }

    private static String debug(IPhreeqc.RunResult.Row baseline, IPhreeqc.RunResult.Row active) {
        return "baseline=" + baseline + "; active=" + active;
    }
}
