package com.yu1745.chemengine.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.yu1745.chemengine.Engine;
import com.yu1745.chemengine.State;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Analytic Jacobian must match finite differences (guards the Newton solver). */
class JacobianTest {

    @Test
    void analyticMatchesFiniteDifferences() throws Exception {
        Engine e = Engine.loadDirectory(Path.of("src/test/resources/species"));
        SystemModel m = e.model();
        int C = m.componentCount();
        double[] t = new double[C];
        t[m.indexOf("Cu+2")] = 100; t[m.indexOf("CO3-2")] = 200;
        t[m.indexOf("Na+1")] = 400; t[m.indexOf("SO4-2")] = 100;
        boolean[] present = new boolean[m.minerals().size()];
        double[] solidAmt = new double[m.minerals().size()];
        for (int j = 0; j < m.minerals().size(); j++)
            if (m.minerals().get(j).solidKey.equals("chemicaladdon:copper_carbonate")) { present[j] = true; solidAmt[j] = 1.0; }

        Solver s;
        try {
            var ctor = Solver.class.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            s = (Solver) ctor.newInstance(m, t, 1000.0, 20, 1.0, 0L, new java.util.LinkedHashMap<String,Long>(), new double[m.speciesCount() + m.minerals().size()]);
        } catch (Exception ex) { throw new AssertionError(ex); }

        int[] active = s.activeComponents(t);
        int[] posOf = new int[C]; Arrays.fill(posOf, -1);
        for (int i = 0; i < active.length; i++) posOf[active[i]] = i;
        int A = active.length, M = 1, dim = A + M;

        double[] y = new double[dim];
        for (int i = 0; i < A; i++) y[i] = Math.log(Math.max(Math.abs(t[active[i]]), 1e-9));
        y[A] = Math.log(1.0);

        double[] n = new double[C];
        for (int i = 0; i < A; i++) n[active[i]] = Math.exp(y[i]);
        double[] mj = new double[]{ Math.exp(y[A]) };

        double[] f = s.residuals(n, mj, t, present, active, posOf);
        double[][] J = s.analyticJacobian(n, mj, t, present, active, posOf);

        for (int col = 0; col < dim; col++) {
            double h = Math.max(Math.abs(y[col]) * 1e-6, 1e-7);
            double[] y2 = y.clone(); y2[col] += h;
            double[] n2 = new double[C];
            for (int i = 0; i < A; i++) n2[active[i]] = Math.exp(y2[i]);
            double[] mj2 = new double[]{ Math.exp(y2[A]) };
            double[] fp = s.residuals(n2, mj2, t, present, active, posOf);
            for (int row = 0; row < dim; row++) {
                double num = (fp[row] - f[row]) / h;
                assertEquals(num, J[row][col], Math.max(1e-6, Math.abs(num) * 1e-3),
                    "J[" + row + "][" + col + "]");
            }
        }
    }
}
