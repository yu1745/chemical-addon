package com.yu1745.chemengine.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Quantitative contracts for the two supported sulfite/permanganate endpoints. */
class ManganesePermanganateStoichiometryTest {
    private static final Curation C = Curation.load();

    @Test
    @DisplayName("强酸性：5 Sul 对 2 Mnvii，产物为溶解 Mn(II)")
    void acidicPathConsumesFiveSulfitesPerTwoPermanganates() {
        var base = last(run(acidicFeed(), 0.0));
        var active = last(run(acidicFeed(), 1e-4, 1e-2, 1.0));
        double mnvii = mol(base, "Mnvii") - mol(active, "Mnvii");
        double sul = mol(base, "Sul") - mol(active, "Sul");
        double mn = mol(active, "Mn") - mol(base, "Mn");
        double mn2 = mol(active, "Mn(+2)") - mol(base, "Mn(+2)");
        assertTrue(mnvii > 1e-6, () -> debug(base, active));
        assertEquals(2.5 * mnvii, sul, 2e-6, () -> debug(base, active));
        assertEquals(mnvii, mn, 2e-6, () -> debug(base, active));
        assertEquals(mnvii, mn2, 2e-6, () -> debug(base, active));
        assertEquals(0.0, active.d("mn_o2_s"), 1e-8, () -> debug(base, active));
        assertNoMolecularRedoxByproduct(base, active);
    }

    @Test
    @DisplayName("碱性：3 Sul 对 2 Mnvii，产物为 MnO2 固相")
    void alkalinePathConsumesThreeSulfitesPerTwoPermanganates() {
        var base = last(run(alkalineFeed(), 0.0));
        var active = last(run(alkalineFeed(), 1e-4, 1e-2, 1.0));
        double mnvii = mol(base, "Mnvii") - mol(active, "Mnvii");
        double sul = mol(base, "Sul") - mol(active, "Sul");
        double solid = active.d("mn_o2_s") - base.d("mn_o2_s");
        assertTrue(mnvii > 1e-6, () -> debug(base, active));
        assertEquals(1.5 * mnvii, sul, 2e-6, () -> debug(base, active));
        assertEquals(mnvii, solid, 2e-6, () -> debug(base, active));
        assertTrue(mol(active, "Mn(+2)") < 1e-8, () -> debug(base, active));
        assertNoMolecularRedoxByproduct(base, active);
    }

    @Test
    @DisplayName("两条默认Mn支路：有限 Mnvii 耗尽后长续算不再消耗 Sul")
    void finitePermanganateStopsBothDefaultBranches() {
        assertStopsAtPermanganateInventory(acidicFeed(), 2.5);
        assertStopsAtPermanganateInventory(alkalineFeed(), 1.5);
    }

    @Test
    @DisplayName("Sul 限量时 Mnvii 保留；pH>12 时两条策展支路均关闭")
    void sulfiteLimitAndStrongAlkaliGateAreFinite() {
        var limitedBase = last(run(feed(2, 0.001, 0.010), 0));
        var limited = last(run(feed(2, 0.001, 0.010), 1, 100, 10_000));
        assertTrue(limited.d("pH") >= 3.5 && limited.d("pH") <= 12, () -> debug(limitedBase, limited));
        assertEquals(0.001, mol(limitedBase, "Sul") - mol(limited, "Sul"), 5e-6, () -> debug(limitedBase, limited));
        assertEquals(0.010 - 0.001 * 2.0 / 3.0, mol(limited, "Mnvii"), 5e-6, () -> debug(limitedBase, limited));

        var highBase = last(run(feed(12.5, 0.010, 0.001, 0.1), 0));
        var high = last(run(feed(12.5, 0.010, 0.001, 0.1), 1, 100, 10_000));
        assertTrue(highBase.d("pH") > 12 && high.d("pH") > 12, () -> debug(highBase, high));
        assertEquals(mol(highBase, "Sul"), mol(high, "Sul"), 1e-8, () -> debug(highBase, high));
        assertEquals(mol(highBase, "Mnvii"), mol(high, "Mnvii"), 1e-8, () -> debug(highBase, high));
    }

    private static void assertStopsAtPermanganateInventory(String solution, double sulPerMn) {
        var base = last(run(solution, 0.0));
        var active = last(run(solution, 1, 100, 10_000));
        double mnvii = mol(base, "Mnvii") - mol(active, "Mnvii");
        double sul = mol(base, "Sul") - mol(active, "Sul");
        assertEquals(1e-3, mnvii, 5e-6, () -> debug(base, active));
        assertEquals(sulPerMn * mnvii, sul, 8e-6, () -> debug(base, active));
    }

    private static String acidicFeed() {
        return """
                SOLUTION 1 acidic sulfite + permanganate
                    temp 25
                    pH 2 charge
                    pe 4
                    water 1 kg
                    Na 20 mmol/kgw
                    K 1 mmol/kgw
                    Cl 100 mmol/kgw
                    Sul 10 mmol/kgw
                    Mnvii 1 mmol/kgw
                """;
    }

    private static String alkalineFeed() {
        return feed(10, 0.010, 0.001);
    }

    private static String feed(double pH, double sul, double mnvii) {
        return feed(pH, sul, mnvii, 0.0);
    }

    /** extraNaOh is an explicit strong-base inventory, not a pH initial guess. */
    private static String feed(double pH, double sul, double mnvii, double extraNaOh) {
        return """
                SOLUTION 1 alkaline sulfite + permanganate
                    temp 25
                    pH %g charge
                    pe 4
                    water 1 kg
                    Na %g mol/kgw
                    K %g mol/kgw
                    Sul %g mol/kgw
                    Mnvii %g mol/kgw
                """.formatted(pH, 2.0 * sul + extraNaOh, mnvii, sul, mnvii);
    }

    private static IPhreeqc.RunResult run(String solution, double... steps) {
        try (IPhreeqc q = IPhreeqc.create()) {
            return q.run(solution + "END\n" + C.ratesBlock() + "END\nUSE solution 1\n"
                    + C.kineticsBlock(Set.of("SulOxidisedByPermanganate",
                            "SulOxidisedByPermanganateToManganeseDioxide"), null, steps) + """
                    EQUILIBRIUM_PHASES 1
                        MnO2(s) 0 0
                    SELECTED_OUTPUT 1
                        -state true
                        -time true
                        -water true
                        -high_precision true
                        -pH true
                        -totals Sul Mnvii Mn Mn(+2) S S(+6) O(0)
                        -molalities H2 O2 Mn+2
                    USER_PUNCH 1
                        -headings mn_o2_s
                        -start
                        10 PUNCH EQUI("MnO2(s)")
                        -end
                    END
                    """);
        }
    }

    private static void assertNoMolecularRedoxByproduct(IPhreeqc.RunResult.Row base, IPhreeqc.RunResult.Row active) {
        assertEquals(0.0, mol(active, "m_H2") - mol(base, "m_H2"), 1e-8, () -> debug(base, active));
        assertEquals(0.0, mol(active, "m_O2") - mol(base, "m_O2"), 1e-8, () -> debug(base, active));
    }

    private static IPhreeqc.RunResult.Row last(IPhreeqc.RunResult result) {
        return result.row(result.rowCount() - 1);
    }

    private static double mol(IPhreeqc.RunResult.Row row, String column) {
        return row.d(column) * row.d("mass_H2O");
    }

    private static String debug(IPhreeqc.RunResult.Row base, IPhreeqc.RunResult.Row active) {
        return "baseline=" + base + "; active=" + active;
    }
}
