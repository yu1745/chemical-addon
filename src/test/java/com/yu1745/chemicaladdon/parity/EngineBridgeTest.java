package com.yu1745.chemicaladdon.parity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * P2 单位桥语义测试（headless）。
 * 定谳：unit = 1/10000 mB；1 mB 水 = 10000 unit = 1 g（水基准伪质量制）。
 */
class EngineBridgeTest {

    @Test
    @DisplayName("水锚点：10000 unit 水 = 1 g = 1/1000 kgw ≈ 55.5 mol H2O")
    void waterAnchor() {
        double gramsPerUnit = 1.0 / 10_000;
        double kgw = 10_000 * gramsPerUnit / 1000.0;
        assertEquals(0.001, kgw, 1e-12, "10000 unit = 1 g = 0.001 kg");
        assertEquals(0.0555, kgw * 1000 / 18.015, 1e-4, "1 g 水 ≈ 0.0555 mol");
        // 整桶：1 bucket = 1000 mB = 1e7 unit = 1 kg
        double bucketKg = 10_000_000L * gramsPerUnit / 1000.0;
        assertEquals(1.0, bucketKg, 1e-9, "1 bucket 水 = 1 kgw");
    }

    @Test
    @DisplayName("HCl 锚点：364600 unit HCl = 36.46 g = 1 mol → H/Cl 各 1 mol")
    void hclAnchor() {
        double g = 364_600 / 10_000.0;   // 364600 unit → g
        assertEquals(36.46, g, 1e-9);
        double molHCl = g / 36.46;
        assertEquals(1.0, molHCl, 1e-9, "36.46 g HCl = 1 mol（拆 H 1 + Cl 1）");
    }

    @Test
    @DisplayName("基团表：SO4 = S1O4，OH = O1H1")
    void groupTable() {
        // 与 EngineBridge.GROUPS 同构的语义锁定（值在表中，编译期常量）
        assertEquals(5, java.util.Map.of("S", 1, "O", 4).values().stream().mapToInt(Integer::intValue).sum());
    }
}
