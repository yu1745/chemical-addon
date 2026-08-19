package com.yu1745.chemengine.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * G1c 门面验收：ChemState 互译、DUMP 全精度存档/零漂移恢复、quanta 整数投影。
 */
class ChemFacadeTest {

    private static ChemState bleach() {
        return ChemState.builder("sodium hypochlorite bleach")
                .waterKg(1.0)
                .pHCharge()
                .pe(4)
                .total("Na", 0.150)
                .total("Cl", 0.100)
                .total("Hyp", 0.050)
                .build();
    }

    @Test
    @DisplayName("equilibrate：ChemState → pH/pe/总量 + 监视物种")
    void equilibrateParsesStateAndWatchSpecies() {
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.equilibrate(bleach(), "Hyp-", "HypH");
            assertEquals(1, r.rowCount(), r.rawLines().toString());
            assertEquals(0.100, r.row(0).d("Cl"), 1e-9, "Cl 总量");
            assertEquals(0.050, r.row(0).d("Hyp"), 1e-9, "Hyp 池");
            assertEquals(0.150, r.row(0).d("Na"), 1e-9, "Na 总量");
            assertEquals(9.993, r.row(0).d("pH"), 0.01, "电荷平衡 pH");
            double hyp = r.row(0).d("m_Hyp-");
            double hyph = r.row(0).d("m_HypH");
            assertTrue(hyp > 0.045 && hyp < 0.051, "m_Hyp-≈0.0499，实测 " + hyp);
            assertTrue(hyph > 1e-5 && hyph < 5e-4, "m_HypH≈1.3e-4，实测 " + hyph);
        }
    }

    @Test
    @DisplayName("存档→解析 ChemState：总量/水量/pH 全精度往返")
    void dumpParsesBackToChemState() {
        try (IPhreeqc q = IPhreeqc.create()) {
            String arch = q.archive(bleach());
            assertTrue(arch.contains("SOLUTION_RAW"), arch);

            ChemState back = ChemState.fromDump(arch);
            // dump 的 -totals 键可能是价态池形式（Cl(-1)），逐键前缀比对
            assertEquals(3, back.totals().size(), back.totals().toString());
            assertEquals(0.100, totalOf(back, "Cl"), 1e-9, "Cl 往返");
            assertEquals(0.050, back.totals().get("Hyp"), 1e-9, "Hyp 往返");
            assertEquals(0.150, back.totals().get("Na"), 1e-9, "Na 往返");
            assertEquals(1.0, back.kgw(), 1e-9, "水量往返");
            assertEquals(9.993, back.ph(), 0.01, "pH 往返（求解值）");
        }
    }

    /** 价态池键（Cl(-1)）按元素名（Cl）取总量。 */
    private static double totalOf(ChemState s, String element) {
        Double v = s.totals().get(element);
        if (v != null) {
            return v;
        }
        for (Map.Entry<String, Double> e : s.totals().entrySet()) {
            if (e.getKey().startsWith(element + "(")) {
                return e.getValue();
            }
        }
        throw new AssertionError("未找到元素 " + element + ": " + s.totals());
    }

    @Test
    @DisplayName("存档零漂移恢复：新会话 runRestored + 1µmol 触发器，总量精确")
    void archiveRestoresIntoFreshSessionWithoutDrift() {
        String arch;
        try (IPhreeqc q = IPhreeqc.create()) {
            arch = q.archive(bleach());
        }
        try (IPhreeqc q2 = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q2.runRestored(arch, """
                    USE solution 1
                    REACTION 1
                        Na 1
                        1 umol in 1 step
                    SELECTED_OUTPUT 1
                        -totals   Cl  Hyp  Na
                        -pH       true
                        -molalities Hyp-
                    """);
            assertEquals(1, r.rowCount(), r.rawLines().toString());
            assertEquals(0.100, r.row(0).d("Cl"), 1e-9, "恢复后 Cl 精确");
            assertEquals(0.050, r.row(0).d("Hyp"), 1e-9, "恢复后 Hyp 池精确（伪元素不塌缩）");
            assertEquals(0.150, r.row(0).d("Na"), 1e-6, "恢复后 Na（+1µmol 触发器）");
            assertEquals(9.994, r.row(0).d("pH"), 0.01, "恢复后 pH");
        }
    }

    @Test
    @DisplayName("quanta 投影：整数网格确定往返 + 水量换算")
    void quantaProjectionIsDeterministicAndConservative() {
        // 水：1000 mB = 1 kg = 10^10 quanta
        assertEquals(1.0, Quanta.kgWater(1000), 0, "1000 mB = 1 kg");
        assertEquals(1000, Quanta.milliBuckets(1.0), "1 kg = 1000 mB");
        assertEquals(10_000_000L * 1000, Quanta.PER_MB_WATER * 1000, "1 桶水 = 10^10 quanta");

        // 溶质：NaCl(M=58.44) 往返恒等（代表性量级：1 mol → 5.844e8 g/1e-7 = 5.844e15 quanta 在 long 内）
        double mm = 58.44;
        long[] samples = {1, 1000, 10_000_000L, 5_844_000_000L}; // 1 quanta → 1 mB-质量级
        for (long q0 : samples) {
            double mol = Quanta.mol(q0, mm);
            long q1 = Quanta.quanta(mol, mm);
            assertEquals(q0, q1, "quanta 往返恒等: " + q0);
        }

        // mB 量纲：1 mB 溶质 = 1 g → 500 g NaCl = 8.55 mol → 回 mB
        double mol = Quanta.molFromMilliBuckets(500, mm);
        assertEquals(500, Quanta.milliBucketsFromMol(mol, mm), "mB 往返恒等");

        // 组合投影：1000 g NaCl ≈ 17.12 mol
        double naclMol = Quanta.molFromMilliBuckets(1000, mm);
        assertTrue(naclMol > 17.1 && naclMol < 17.2, "1000 g NaCl ≈ 17.12 mol，实测 " + naclMol);
    }

    @Test
    @DisplayName("端到端：quanta 装配 → equilibrate → 投影回整数网格守恒")
    void endToEndQuantaAssemblyProjectsWithConservation() {
        // 游戏侧：1000 mB 水；NaCl 5 g；NaOCl(Hyp, M=74.44) 3.722 g → 全部走 quanta 网格
        long waterMb = 1000;
        long naclQuanta = Quanta.quanta(5.0 / 58.44, 58.44);       // 5 g NaCl
        long oclQuanta = Quanta.quanta(3.722 / 74.44, 74.44);      // 3.722 g NaOCl
        assertEquals(50_000_000L, naclQuanta, "5 g = 5e7 quanta（1 quanta = 1e-7 g）");
        assertEquals(37_220_000L, oclQuanta, "3.722 g = 3.722e7 quanta");

        ChemState s = ChemState.builder("game-assembled bleach")
                .waterKg(Quanta.kgWater(waterMb))
                .pHCharge()
                .pe(4)
                .total("Na", Quanta.mol(naclQuanta, 58.44) + Quanta.mol(oclQuanta, 74.44))
                .total("Cl", Quanta.mol(naclQuanta, 58.44))
                .total("Hyp", Quanta.mol(oclQuanta, 74.44))
                .build();

        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.equilibrate(s, "Hyp-");
            // 投影回整数网格：守恒断言（内核连续值×M/1e-7 应回到整数 quanta）
            long hypBack = Quanta.quanta(r.row(0).d("Hyp"), 74.44);
            long clBack = Quanta.quanta(r.row(0).d("Cl"), 58.44);
            assertEquals(oclQuanta, hypBack, "Hyp 池守恒（quanta 网格）");
            assertEquals(naclQuanta, clBack, "Cl 守恒（quanta 网格）");
            assertTrue(r.row(0).d("m_Hyp-") > 0.9 * Quanta.mol(oclQuanta, 74.44),
                    "Hyp- 主导: " + r.row(0).d("m_Hyp-"));
        }
    }
}
