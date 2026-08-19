package com.yu1745.chemengine.kernel;

/**
 * 游戏整数网格 ↔ 内核连续量 的投影层（G1c）。
 *
 * <p>沿用旧引擎（tag self-engine-final）的约定：1 quanta = 10⁻⁷ g 水，
 * 即 1 mB 水 = 10⁷ quanta。内核解算用连续 double（mol），仅在游戏边界
 * （FluidStack mB / 物品数量 ↔ 溶质摩尔）做确定取整；守恒由边界断言保证。
 *
 * <p>换算基准：水密度 1 g/mB（模组约定），溶质按摩尔质量换算质量。
 */
public final class Quanta {

    /** 1 mB 水 = 10⁷ quanta（1 quanta = 10⁻⁷ g）。 */
    public static final long PER_MB_WATER = 10_000_000L;

    /** quanta 对应的质量（g）。 */
    public static final double GRAMS_PER_QUANTUM = 1.0e-7;

    private Quanta() {}

    /** 水：mB → kg（1 mB = 1 g）。 */
    public static double kgWater(long milliBuckets) {
        return milliBuckets / 1000.0;
    }

    /** 水：kg → mB（确定性取整）。 */
    public static long milliBuckets(double kgWater) {
        return Math.round(kgWater * 1000.0);
    }

    /** 溶质：quanta（质量网格）→ mol。 */
    public static double mol(long quanta, double molarMassG) {
        return quanta * GRAMS_PER_QUANTUM / molarMassG;
    }

    /** 溶质：mol → quanta（确定性取整；配合断言用，避免静默丢量）。 */
    public static long quanta(double mol, double molarMassG) {
        return Math.round(mol * molarMassG / GRAMS_PER_QUANTUM);
    }

    /** mB（整数网格）→ mol（按 1 mB 溶质 = 1 g 水当量质量计）。 */
    public static double molFromMilliBuckets(long milliBuckets, double molarMassG) {
        return milliBuckets / molarMassG;
    }

    /** mol → mB（确定性取整）。 */
    public static long milliBucketsFromMol(double mol, double molarMassG) {
        return Math.round(mol * molarMassG);
    }
}
