package com.yu1745.chemengine.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Quantitative contract for the curated aqueous sulfite + dissolved-oxygen route. */
class SulfiteOxygenStoichiometryTest {
    private static final Curation CURATION = Curation.load();

    @Test
    @DisplayName("亚硫酸盐慢氧化：相对同相零步基线 Sul→S(VI) 且每 mol Sul 耗半 mol O2")
    void sulfiteOxidationHasNativeStoichiometry() {
        IPhreeqc.RunResult zero = run(0.010, 0.001, 0);
        IPhreeqc.RunResult active = run(0.010, 0.001, 100_000);
        Amounts before = amounts(last(zero));
        Amounts after = amounts(last(active));
        double extent = before.sul() - after.sul();
        double sulfateGain = after.sixValentSulfur() - before.sixValentSulfur();
        double oxygenLossAsO2 = (before.oxygenAtoms() - after.oxygenAtoms()) / 2d;
        assertTrue(extent > 1e-6, "dissolved oxygen must produce a positive Sul oxidation extent");
        assertEquals(extent, sulfateGain, 1e-7, "each curated Sul pool unit becomes one native S(VI) unit");
        assertEquals(extent / 2d, oxygenLossAsO2, 1e-7, "SO3-- + 1/2 O2 consumes half a mol O2 per mol Sul");
        assertEquals(before.totalSulfur(), after.totalSulfur(), 1e-7,
                "Sul pseudo-pool plus native sulfur remain one conserved sulfur inventory");
    }

    @Test
    @DisplayName("亚硫酸盐慢氧化：无氧不动，氧不足后长时间续算也不越过氧库存")
    void oxygenGateAndOxygenLimitStopTheReaction() {
        Amounts anoxicBefore = amounts(last(run(0.100, 0, 0)));
        Amounts anoxicAfter = amounts(last(run(0.100, 0, 1_000_000)));
        assertEquals(anoxicBefore.sul(), anoxicAfter.sul(), 1e-10, "without O2 the Sul gate is inert");
        assertEquals(anoxicBefore.sixValentSulfur(), anoxicAfter.sixValentSulfur(), 1e-10,
                "without O2 no sulfur is routed to sulfate");

        Amounts before = amounts(last(run(0.100, 0.001, 0)));
        IPhreeqc.RunResult limited = run(0.100, 0.001, 100_000, 1_000_000, 1_000_000);
        Amounts middle = amounts(limited.row(limited.rowCount() - 2));
        Amounts finalState = amounts(last(limited));
        double extent = before.sul() - finalState.sul();
        assertTrue(extent > 0.00095 && extent <= 0.0010001,
                "the 0.001 mol O-atom reservoir limits extent to one millimol (got " + extent + ")");
        assertEquals(0d, middle.sul() - finalState.sul(), 1e-8,
                "after oxygen depletion an additional long integration does not consume Sul");
        assertEquals(0d, middle.oxygenAtoms() - finalState.oxygenAtoms(), 1e-8,
                "after oxygen depletion an additional long integration does not create or consume O2");
    }

    @Test
    @DisplayName("亚硫酸盐不足：过量氧不创造 Sul，耗尽后长时间续算保持硫库存")
    void sulfiteLimitStopsTheReaction() {
        Amounts before = amounts(last(run(0.001, 0.100, 0)));
        IPhreeqc.RunResult limited = run(0.001, 0.100, 1_000_000, 1_000_000);
        Amounts middle = amounts(limited.row(limited.rowCount() - 2));
        Amounts finalState = amounts(last(limited));
        double extent = before.sul() - finalState.sul();
        assertTrue(extent > 0.00099 && extent <= 0.0010001,
                "the one-millimol Sul pool limits the reaction (got " + extent + ")");
        assertEquals(before.totalSulfur(), finalState.totalSulfur(), 1e-8, "sulfur remains conserved at Sul depletion");
        assertEquals(0d, middle.sul() - finalState.sul(), 1e-8,
                "after Sul depletion an additional long integration does not regenerate or consume Sul");
    }

    private static IPhreeqc.RunResult run(double sulMol, double oxygenAtomMol, double... steps) {
        ChemState feed = ChemState.builder("sulfite oxygen stoichiometry")
                .waterKg(1).pHCharge().pe(4).total("Na", sulMol * 6).total("Sul", sulMol)
                .total("O(0)", oxygenAtomMol).build();
        try (IPhreeqc q = IPhreeqc.create()) {
            return q.run(feed.toSolutionScript(1) + "END\n" + CURATION.ratesBlock() + "END\nUSE solution 1\n"
                    + CURATION.kineticsBlock(Set.of("SulOxidisesSlowly"), null, steps) + """
                    SELECTED_OUTPUT 1
                        -state true
                        -time true
                        -water true
                        -high_precision true
                        -totals Sul S S(+6) O(0)
                        -molalities O2 SO3-2 SO4-2
                    END
                    """);
        }
    }

    private record Amounts(double sul, double sulfur, double sixValentSulfur, double oxygenAtoms) {
        double totalSulfur() { return sul + sulfur; }
    }

    private static Amounts amounts(IPhreeqc.RunResult.Row row) {
        double water = row.d("mass_H2O");
        return new Amounts(row.d("Sul") * water, row.d("S") * water,
                row.d("S(+6)") * water, row.d("O(0)") * water);
    }

    private static IPhreeqc.RunResult.Row last(IPhreeqc.RunResult result) {
        return result.row(result.rowCount() - 1);
    }
}
