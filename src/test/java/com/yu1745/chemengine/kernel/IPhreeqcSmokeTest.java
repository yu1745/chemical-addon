package com.yu1745.chemengine.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * G1a 冒烟：JNA 绑定 + sit.dat(+addendum) 装载 + 最小 speciation。
 * 这些测试通过即代表整条 native→JNA→数据库→求解→SELECTED_OUTPUT 通道打通。
 */
class IPhreeqcSmokeTest {

    @Test
    @DisplayName("原生库加载与实例创建/销毁")
    void nativeLibLoadsAndInstanceCreates() {
        IPhreeqcLib lib = NativeLoader.lib();
        assertNotNull(lib);
        int id = lib.CreateIPhreeqc();
        assertTrue(id >= 0, "CreateIPhreeqc 应返回非负 id: " + id);
        assertEquals(0, lib.DestroyIPhreeqc(id), "DestroyIPhreeqc 应返回 OK");
    }

    @Test
    @DisplayName("sit.dat + HOCl 补丁装载成功")
    void sitDatabaseWithAddendumLoads() {
        try (IPhreeqc q = IPhreeqc.create()) {
            q.loadDatabase();
            // 装载后再空跑一个最小脚本，确认数据库态可用
            IPhreeqc.RunResult r = q.run("SOLUTION 1\n    pH 7\nEND\n");
            assertNotNull(r);
        }
    }

    @Test
    @DisplayName("纯水 25°C → pH ≈ 7")
    void pureWaterIsNeutral() {
        try (IPhreeqc q = IPhreeqc.create()) {
            IPhreeqc.RunResult r = q.run("""
                    SOLUTION 1 Pure water
                        temp      25
                        pH        7
                    SELECTED_OUTPUT 1
                        -pH       true
                        -soln     true
                    END
                    """);
            assertEquals(1, r.rowCount(), "应恰有一行解: " + r.rawLines());
            double pH = r.row(0).d("pH");
            assertTrue(pH > 6.5 && pH < 7.5, "纯水 pH 应≈7，实测 " + pH);
        }
    }

    @Test
    @DisplayName("错误通道：非法脚本抛 IPhreeqcException 且携带原生错误文本")
    void badScriptThrowsWithNativeErrorText() {
        try (IPhreeqc q = IPhreeqc.create()) {
            String bad = "SOLUTION 1\n    pH 7\nEQUILIBRIUM_PHASES 1\n    NonexistentPhase 0 10\nEND\n";
            boolean threw = false;
            try {
                q.run(bad);
            } catch (IPhreeqcException e) {
                threw = true;
                System.out.println("[badScript] 原生错误文本: " + e.getMessage());
            }
            assertTrue(threw, "引用不存在的相应触发 ERROR");
        }
    }
}
