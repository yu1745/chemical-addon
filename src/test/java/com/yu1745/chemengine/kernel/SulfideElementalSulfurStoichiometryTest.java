package com.yu1745.chemengine.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SulfideElementalSulfurStoichiometryTest {
    private static final Curation C = Curation.load();
    private static final Set<String> ROUTES = Set.of("HypOxidisesSulfide", "HypOxidisesElementalSulfur");

    @Test void hypLimitedSulfideMakesOneSulfurAtomOfEngineSulfurPerHyp() {
        var b = last(run(0.005, 0.010, 0));
        var a = last(run(0.005, 0.010, 1e-5, 1e-4, 0.01, 1.0));
        double hyp = mol(b, "Hyp") - mol(a, "Hyp");
        double sulfide = mol(b, "Sulfide") - mol(a, "Sulfide");
        double solid = a.d("engine_sulfur") - b.d("engine_sulfur");
        assertEquals(0.005, hyp, 5e-6);
        assertEquals(hyp, sulfide, 5e-6);
        assertEquals(hyp, solid, 5e-6);
        assertEquals(0, mol(a, "S"), 1e-8, "Szero不得坍缩回原生DLP硫");
        assertEquals(mol(b, "Sulfide"), mol(a, "Sulfide") + mol(a, "Szero") + a.d("engine_sulfur"),
                5e-6, "硫库存必须闭合于剩余硫化物、水相 Szero 与 EngineSulfur 固相");
    }

    @Test void sulfideLimitedExcessHypOxidisesSulfurToSulfateWithoutCreatingSulfur() {
        var b = last(run(0.010, 0.001, 0));
        var a = last(run(0.010, 0.001, 1e-4, 1, 1e5, 1e7, 1e9));
        double sulfide = mol(b, "Sulfide") - mol(a, "Sulfide");
        double sulfate = mol(a, "S(+6)") - mol(b, "S(+6)");
        assertEquals(0.001, sulfide, 5e-6);
        assertTrue(sulfate > 9e-4, () -> "过量Hyp应最终把Szero氧化到硫酸盐: " + a);
        assertEquals(0, a.d("engine_sulfur"), 5e-6, "过量Hyp终态不保留硫固相");
    }

    @Test void noSulfideOrNoHypLeavesBothSulfurRoutesInert() {
        var sulfideOnlyBase = last(run(0, 0.010, 0));
        var sulfideOnly = last(run(0, 0.010, 1, 100));
        assertEquals(mol(sulfideOnlyBase, "Sulfide"), mol(sulfideOnly, "Sulfide"), 1e-8);
        assertEquals(0, sulfideOnly.d("engine_sulfur"), 1e-8);

        var hypOnlyBase = last(run(0.010, 0, 0));
        var hypOnly = last(run(0.010, 0, 1, 100));
        assertEquals(mol(hypOnlyBase, "Hyp"), mol(hypOnly, "Hyp"), 1e-8);
        assertEquals(0, hypOnly.d("engine_sulfur"), 1e-8);
    }

    private static IPhreeqc.RunResult run(double hyp, double sulfide, double... steps) {
        try (IPhreeqc q = IPhreeqc.create()) {
            return q.run("""
                    SOLUTION 1
                      temp 25
                      pH 9 charge
                      pe -4
                      water 1 kg
                      Na 20 mmol/kgw
                      Cl 10 mmol/kgw
                      Hyp %g mol/kgw
                      Sulfide %g mol/kgw
                    END
                    """.formatted(hyp, sulfide) + C.ratesBlock() + "END\nUSE solution 1\n"
                    + C.kineticsBlock(ROUTES, null, steps) + """
                    EQUILIBRIUM_PHASES 1
                      EngineSulfur 0 0
                    SELECTED_OUTPUT 1
                      -water true
                      -high_precision true
                      -totals Hyp Sulfide Szero S S(+6)
                    USER_PUNCH 1
                      -headings engine_sulfur
                      -start
                      10 PUNCH EQUI("EngineSulfur")
                      -end
                    END
                    """);
        }
    }
    private static IPhreeqc.RunResult.Row last(IPhreeqc.RunResult r) { return r.row(r.rowCount()-1); }
    private static double mol(IPhreeqc.RunResult.Row r, String c) { return r.d(c)*r.d("mass_H2O"); }
}
