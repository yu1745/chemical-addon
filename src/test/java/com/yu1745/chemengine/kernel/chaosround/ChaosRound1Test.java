package com.yu1745.chemengine.kernel.chaosround;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yu1745.chemengine.kernel.Curation;
import com.yu1745.chemengine.kernel.IPhreeqc;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Chaos Round 1：策展动力学的动态行为验收测试。
 *
 * <p>与静态防线测试（CurationTest/CurationGapTest）互补：这里只验证
 * KINETICS 实际积分后的方向与化学计量——池动没动、动多少、比例对不对。
 *
 * <p>约定：include 机制 = Curation.kineticsBlock(Set.of(name), null, steps)；
 * totals 列是 molality，乘 mass_H2O 得 mol；molalities 列前缀 m_。
 */
class ChaosRound1Test {

    private static final Curation C = Curation.load();

    private static final String PUNCH = """
            SELECTED_OUTPUT 1
                -state            true
                -time             true
                -water            true
                -high_precision   true
                -pH               true
                -pe               true
                -totals           Na Cl S N Fe Mn I Hyp Sul Sulfide Szero Mnvii Nitri Nitra Fe(2)
                -molalities       O2 HNitri HypH Mnvii- Mn+2
            """;

    /** 跑一个场景：SOLUTION(用户给) + RATES + KINETICS(单反应 include) + 额外块 + SELECTED_OUTPUT。 */
    private static IPhreeqc.RunResult active(String solution, String reaction, String extraBlocks,
                                             double... steps) {
        return active(solution, Set.of(reaction), extraBlocks, steps);
    }

    private static IPhreeqc.RunResult active(String solution, Set<String> reactions, String extraBlocks,
                                             double... steps) {
        try (IPhreeqc q = IPhreeqc.create()) {
            return q.run("""
                    %s
                    END
                    """.formatted(solution)
                    + C.ratesBlock()
                    + "USE solution 1\n"
                    + C.kineticsBlock(reactions, null, steps)
                    + extraBlocks
                    + PUNCH
                    + "\nEND\n");
        }
    }

    /** 基线：同一 SOLUTION（含同样的额外平衡相），无 KINETICS。返回 row(0)。 */
    private static IPhreeqc.RunResult.Row baseline(String solution, String extraBlocks) {
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.run("""
                    %s
                    END
                    """.formatted(solution)
                    + "USE solution 1\n" + extraBlocks
                    // 纯 USE solution + END 不触发计算/输出，加 1e-9 mol 水逼 PHREEQC 出一行
                    + "REACTION 1\n H2O 1\n 1e-9 moles in 1 step\n"
                    + PUNCH + "\nEND\n");
            return r.row(r.rowCount() - 1);
        }
    }

    /** totals molality → mol（乘 mass_H2O）。 */
    private static double mol(IPhreeqc.RunResult.Row row, String col) {
        return row.d(col) * row.d("mass_H2O");
    }

    private static void assertRatio(String what, double expected, double actual, double relTol) {
        double denom = Math.max(Math.abs(expected), Math.abs(actual));
        assertTrue(denom > 1e-12, () -> what + ": 变化量为零，无法验证比例");
        assertTrue(Math.abs(actual - expected) / denom < relTol,
                () -> what + String.format(": 期望 %.6e 实测 %.6e (相对误差 %.2e, 容差 %.2e)",
                        expected, actual, Math.abs(actual - expected) / denom, relTol));
    }

    // ==== 1. HypDecay：漂白液自分解 ====

    @Test
    @DisplayName("HypDecay: Hyp↓ Cl↑ 1:1，Na 守恒")
    void hypDecay_stoichiometry() {
        String sol = """
                SOLUTION 1
                    temp 25
                    pH   7 charge
                    pe   4
                    water 1 kg
                    Na   15 mmol/kgw
                    Cl   14 mmol/kgw
                    Hyp  5  mmol/kgw
                """;
        var base = baseline(sol, "");
        var last = lastRow(active(sol, "HypDecay", "", 50, 50, 100, 200));
        double dHyp = mol(last, "Hyp") - mol(base, "Hyp");
        double dCl = mol(last, "Cl") - mol(base, "Cl");
        assertTrue(dHyp < -1e-4, () -> "Hyp 应显著下降, dHyp=" + dHyp);
        assertRatio("HypDecay ΔCl:-ΔHyp", -dHyp, dCl, 0.01);
        assertEquals(mol(base, "Na"), mol(last, "Na"), 1e-4);
    }

    @Test
    @DisplayName("HypDecay: 高 pH 门控自停（HypH 消失）")
    void hypDecay_phGated() {
        String sol = """
                SOLUTION 1
                    temp 25
                    pH   12 charge
                    pe   4
                    water 1 kg
                    Na   20 mmol/kgw
                    Cl   10 mmol/kgw
                    Hyp  5  mmol/kgw
                """;
        var base = baseline(sol, "");
        var last = lastRow(active(sol, "HypDecay", "", 1e4));
        double dHyp = mol(last, "Hyp") - mol(base, "Hyp");
        assertTrue(dHyp > -0.01 * mol(base, "Hyp"),
                () -> "高 pH 下 HypDecay 应 <1% 烧穿, dHyp=" + dHyp);
    }

    // ==== 2. HypOxidisesIodide ====

    @Test
    @DisplayName("HypOxidisesIodide: 自由 I- 门控，Cl↑ 1:1")
    void hypOxidisesIodide_stoichiometry() {
        String sol = """
                SOLUTION 1
                    temp 25
                    pH   7 charge
                    pe   4
                    water 1 kg
                    Na   30 mmol/kgw
                    Cl   10 mmol/kgw
                    I    8  mmol/kgw
                    Hyp  20 mmol/kgw
                """;
        var base = baseline(sol, "");
        var last = lastRow(active(sol, "HypOxidisesIodide", "", 100));
        double dHyp = mol(last, "Hyp") - mol(base, "Hyp");
        double dCl = mol(last, "Cl") - mol(base, "Cl");
        // I 不进元素 formula；但自由 I- 门使通道在碘被氧化后自停。
        assertRatio("Iodide: ΔCl:-ΔHyp", -dHyp, dCl, 0.01);
        assertTrue(dHyp < -1e-4, "快通道下 Hyp 应显著消耗, dHyp=" + dHyp);
    }

    @Test
    @DisplayName("HypOxidisesIodide 负对照: 无 I 时池完全不动")
    void hypOxidisesIodide_noIodide_inert() {
        String sol = """
                SOLUTION 1
                    temp 25
                    pH   7 charge
                    pe   4
                    water 1 kg
                    Na   30 mmol/kgw
                    Cl   10 mmol/kgw
                    Hyp  20 mmol/kgw
                """;
        var base = baseline(sol, "");
        var last = lastRow(active(sol, "HypOxidisesIodide", "", 1e4));
        assertEquals(mol(base, "Hyp"), mol(last, "Hyp"), 1e-9);
        assertEquals(mol(base, "Cl"), mol(last, "Cl"), 1e-9);
    }

    // ==== 3. HypOxidisesSulfide ====

    @Test
    @DisplayName("HypOxidisesSulfide: Hyp:Sulfide 1:1，产物进入受保护 Szero 池")
    void hypOxidisesSulfide_stoichiometry() {
        String sol = """
                SOLUTION 1
                    temp 25
                    pH   9 charge
                    pe   -4
                    water 1 kg
                    Na   20 mmol/kgw
                    Cl   10 mmol/kgw
                    Sulfide 10 mmol/kgw
                    Hyp  5  mmol/kgw
                """;
        var base = baseline(sol, "");
        var last = lastRow(active(sol, "HypOxidisesSulfide", "", 10));
        double dHyp = mol(last, "Hyp") - mol(base, "Hyp");
        double dCl = mol(last, "Cl") - mol(base, "Cl");
        double dSulfide = mol(last, "Sulfide") - mol(base, "Sulfide");
        double dSzero = mol(last, "Szero") - mol(base, "Szero");
        assertTrue(dHyp < -1e-4, () -> "Hyp 应显著下降, dHyp=" + dHyp);
        assertRatio("Sulfide: ΔCl:-ΔHyp", -dHyp, dCl, 0.01);
        assertRatio("Sulfide: -ΔSulfide:-ΔHyp", -dHyp, -dSulfide, 0.01);
        assertRatio("Sulfide: ΔSzero:-ΔHyp", -dHyp, dSzero, 0.01);
    }

    // ==== 4. HypDecayCatalysedByManganese ====

    @Test
    @DisplayName("HypDecayCatalysedByManganese: 策展表 TOT(\"Mn+2\") 零速率(疑似表错); 正确记法下 Hyp↓Cl↑1:1, Mn 守恒")
    void hypDecayMn_catalysis() {
        String sol = """
                SOLUTION 1
                    temp 25
                    pH   7 charge
                    pe   4
                    water 1 kg
                    Na   15 mmol/kgw
                    Cl   14 mmol/kgw
                    Mn   1  mmol/kgw
                    Hyp  5  mmol/kgw
                """;
        // (a) 策展表已修复（轮1实锤 TOT("Mn+2") 静默返 0 → 改为价态 master Mn(+2)）：策展通道本身应有效
        var base = baseline(sol, "");
        var curated = lastRow(active(sol, "HypDecayCatalysedByManganese", "", 10, 10, 20, 40));
        assertTrue(mol(curated, "Hyp") - mol(base, "Hyp") < -1e-4,
                "策展表 Mn 催化通道应有效（TOT(\"Mn(+2)\") 已修复）；若归零说明回归");

        // (b) 意图化学（手写 RATES, TOT("Mn(+2)") 正确记法）: 同 HypDecay 流 + 催化加速 + Mn 守恒
        var cat = lastRow(runHandRates(sol, "10 * TOT(\"Hyp\") * TOT(\"Mn(+2)\")", 10, 10, 20, 40));
        String solNoMn = sol.replace("    Mn   1  mmol/kgw\n", "");
        var ctl = lastRow(runHandRates(solNoMn, "10 * TOT(\"Hyp\") * TOT(\"Mn(+2)\")", 10, 10, 20, 40));
        var baseNoMn = baseline(solNoMn, "");
        double dHypCat = mol(cat, "Hyp") - mol(base, "Hyp");
        double dHypCtl = mol(ctl, "Hyp") - mol(baseNoMn, "Hyp");
        double dCl = mol(cat, "Cl") - mol(base, "Cl");
        assertTrue(dHypCat < -1e-4, () -> "正确记法下 Mn 催化 Hyp 应显著下降, dHyp=" + dHypCat);
        assertRatio("MnCat: ΔCl:-ΔHyp", -dHypCat, dCl, 0.01);
        assertEquals(mol(base, "Mn"), mol(cat, "Mn"), 1e-5, "催化剂 Mn 元素账必须守恒");
        assertTrue(Math.abs(dHypCtl) < Math.abs(dHypCat) / 10,
                () -> String.format("无 Mn 对照应慢 ≥10x: dHypCat=%.3e dHypCtl=%.3e", dHypCat, dHypCtl));
    }

    /** 手写 RATES（意图化学验证用）: HypDecay 净流 + 指定速率表达式。 */
    private static IPhreeqc.RunResult runHandRates(String solution, String rateExpr, double... steps) {
        StringBuilder stepsTxt = new StringBuilder();
        for (double s : steps) {
            stepsTxt.append(' ').append(s);
        }
        try (IPhreeqc q = IPhreeqc.create()) {
            return q.run("""
                    %s
                    END
                    """.formatted(solution)
                    + "USE solution 1\n"
                    + "RATES\n Dbg\n -start\n 10 r = " + rateExpr + "\n 20 SAVE r * TIME\n -end\n"
                    + "KINETICS 1\n Dbg\n  -formula Cl 1 H 1 Hyp -1 O 1\n  -m 1000\n  -m0 1000\n"
                    + "  -steps  " + stepsTxt.toString().trim() + " seconds\n"
                    + PUNCH + "\nEND\n");
        }
    }

    // ==== 5. SulOxidisesSlowly ====

    @Test
    @DisplayName("SulOxidisesSlowly: Sul↓ 真实S↑ 1:1（慢通道, O2 相恒供）")
    void sulOxidisesSlowly_stoichiometry() {
        String sol = """
                SOLUTION 1
                    temp 25
                    pH   7
                    pe   4
                    water 1 kg
                    Na   60 mmol/kgw
                    Cl   20 mmol/kgw
                    S    10 mmol/kgw
                    Sul  10 mmol/kgw
                """;
        String phases = """
                EQUILIBRIUM_PHASES 1
                    O2(g)  -0.67  1
                """;
        var base = baseline(sol, phases);
        var last = lastRow(active(sol, "SulOxidisesSlowly", phases, 1e5, 1e5));
        double dSul = mol(last, "Sul") - mol(base, "Sul");
        double dS = mol(last, "S") - mol(base, "S");
        assertTrue(dSul < -1e-9, () -> "Sul 应缓慢下降, dSul=" + dSul);
        assertRatio("Slow: ΔS:-ΔSul", -dSul, dS, 0.02);
        assertEquals(mol(base, "Na"), mol(last, "Na"), 1e-4);
    }

    // ==== 6. SulOxidisedByPermanganate ====

    @Test
    @DisplayName("SulOxidisedByPermanganate: 中性支路 Sul:Mnvii=3:2")
    void sulOxidisedByPermanganate() {
        // pH 10 的实测收敛区间处于 EPA 所列 MnO2 支路 3.5--12 内；同时启用两条
        // 策展速率，断言酸性支路不会混入这套 3:2 计量。
        String solNormal = """
                SOLUTION 1
                    temp 25
                    pH   10 charge
                    pe   4
                    water 1 kg
                    Na   20 mmol/kgw
                    K    1 mmol/kgw
                    Sul  10 mmol/kgw
                    Mnvii 1 mmol/kgw
                """;
        var base = baseline(solNormal, "");
        var last = lastRow(active(solNormal, Set.of("SulOxidisedByPermanganate",
                "SulOxidisedByPermanganateToManganeseDioxide"), "", 0.02, 0.02, 0.04));
        double dSul = mol(last, "Sul") - mol(base, "Sul");
        double dMnvii = mol(last, "Mnvii") - mol(base, "Mnvii");
        assertTrue(last.d("pH") >= 3.5 && last.d("pH") <= 12,
                () -> "中性 MnO2 支路的实际 pH 必须在 [3.5, 12]: " + last.d("pH"));
        assertTrue(last.d("m_Mnvii-") > 1e-5, () -> "Mnvii 池应存在: " + last.d("m_Mnvii-"));
        assertTrue(dSul < -1e-7, () -> "Sul 应被 Mnvii 氧化, dSul=" + dSul);
        assertRatio("Permanganate: -ΔSul: -ΔMnvii", 1.5 * -dMnvii, -dSul, 0.01);
        assertRatio("Permanganate: native Mn product:-ΔMnvii", -dMnvii,
                mol(last, "Mn") - mol(base, "Mn"), 0.01);
    }

    // ==== 7. HypOxidisesNitrite ====

    @Test
    @DisplayName("HypOxidisesNitrite: Hyp:Nitri:Cl:Nitra = 1:1:1:1, Na 守恒")
    void hypOxidisesNitrite_stoichiometry() {
        String sol = """
                SOLUTION 1
                    temp 25
                    pH   7 charge
                    pe   4
                    water 1 kg
                    Na   30 mmol/kgw
                    Cl   10 mmol/kgw
                    Hyp  20 mmol/kgw
                    Nitri 8  mmol/kgw
                """;
        var base = baseline(sol, "");
        var last = lastRow(active(sol, "HypOxidisesNitrite", "", 1e4));
        double dHyp = mol(last, "Hyp") - mol(base, "Hyp");
        double dNitri = mol(last, "Nitri") - mol(base, "Nitri");
        double dCl = mol(last, "Cl") - mol(base, "Cl");
        double dNitra = mol(last, "Nitra") - mol(base, "Nitra");
        assertRatio("Nitrite: -ΔNitri ≈ 8 mmol(Nitri 限量)", 8e-3, -dNitri, 0.01);
        assertRatio("Nitrite: -ΔHyp:-ΔNitri", -dNitri, -dHyp, 0.01);
        assertRatio("Nitrite: ΔCl:-ΔNitri", -dNitri, dCl, 0.01);
        assertRatio("Nitrite: ΔNitra:-ΔNitri", -dNitri, dNitra, 0.01);
        assertEquals(mol(base, "Na"), mol(last, "Na"), 1e-4);
    }

    // ==== 8. NitriteOxidisesFerrous ====

    @Test
    @DisplayName("NitriteOxidisesFerrous: Nitri↓ N↑ 1:1, Fe 守恒(仅速率门)")
    void nitriteOxidisesFerrous_stoichiometry() {
        String sol = """
                SOLUTION 1
                    temp 25
                    pH   7 charge
                    pe   4
                    water 1 kg
                    Na   20 mmol/kgw
                    Cl   20 mmol/kgw
                    Fe(+2) 10 mmol/kgw
                    Nitri 20 mmol/kgw
                """;
        var base = baseline(sol, "");
        var last = lastRow(active(sol, "NitriteOxidisesFerrous", "", 1e4, 1e4));
        double dNitri = mol(last, "Nitri") - mol(base, "Nitri");
        double dN = mol(last, "N") - mol(base, "N");
        assertTrue(dNitri < -5e-4, () -> "Nitri 应显著下降(≥0.5 mmol), dNitri=" + dNitri);
        assertRatio("Ferrous: ΔN:-ΔNitri", -dNitri, dN, 0.02);
        assertEquals(mol(base, "Fe"), mol(last, "Fe"), 1e-5, "Fe 仅速率门, 守恒");
        assertEquals(mol(base, "Na"), mol(last, "Na"), 1e-4);
    }

    // ==== 9. NitriDisproportionates ====

    @Test
    @DisplayName("NitriDisproportionates: 酸催化 Nitri→N 2:-2, 中性 pH 自停")
    void nitriDisproportionates_acidCatalysed() {
        String acid = """
                SOLUTION 1
                    temp 25
                    pH   2
                    pe   4
                    water 1 kg
                    Na   10 mmol/kgw
                    Cl   10 mmol/kgw
                    Nitri 10 mmol/kgw
                """;
        var base = baseline(acid, "");
        var last = lastRow(active(acid, "NitriDisproportionates", "", 10, 20, 50, 100, 200));
        double dNitri = mol(last, "Nitri") - mol(base, "Nitri");
        double dN = mol(last, "N") - mol(base, "N");
        assertTrue(dNitri < -0.01 * mol(base, "Nitri"),
                () -> "pH2 下歧化应烧穿 >1%, dNitri=" + dNitri);
        assertRatio("Disprop: ΔN:-ΔNitri (2 Nitri→2 N)", -dNitri, dN, 0.01);

        // 负对照: pH 7 (Na 补齐电荷) 下 HNitri≈0 → 自停
        String neutral = acid.replace("Na   10 mmol/kgw", "Na   20 mmol/kgw").replace("pH   2", "pH   7");
        var baseN = baseline(neutral, "");
        var lastN = lastRow(active(neutral, "NitriDisproportionates", "", 1e4));
        double dNitriN = mol(lastN, "Nitri") - mol(baseN, "Nitri");
        assertTrue(dNitriN > -0.02 * mol(baseN, "Nitri"),
                () -> "pH7 下歧化应 <2%, dNitri=" + dNitriN);
    }

    // ==== 10. NitriOxidisedByO2 ====

    @Test
    @DisplayName("NitriOxidisedByO2: Nitri↓ Nitra↑ 1:1 池间平移, Na 守恒")
    void nitriOxidisedByO2_stoichiometry() {
        String sol = """
                SOLUTION 1
                    temp 25
                    pH   7
                    pe   4
                    water 1 kg
                    Na   100 mmol/kgw
                    Nitri 100 mmol/kgw
                """;
        String phases = """
                EQUILIBRIUM_PHASES 1
                    O2(g)  0  2
                """;
        var base = baseline(sol, phases);
        var last = lastRow(active(sol, "NitriOxidisedByO2", phases, 5e5, 5e5));
        double dNitri = mol(last, "Nitri") - mol(base, "Nitri");
        double dNitra = mol(last, "Nitra") - mol(base, "Nitra");
        assertTrue(dNitri < -1e-9, () -> "Nitri 应缓慢下降, dNitri=" + dNitri);
        assertRatio("O2: ΔNitra:-ΔNitri", -dNitri, dNitra, 0.03);
        assertEquals(mol(base, "Na"), mol(last, "Na"), 1e-4);
    }

    private static IPhreeqc.RunResult.Row lastRow(IPhreeqc.RunResult r) {
        return r.row(r.rowCount() - 1);
    }
}
