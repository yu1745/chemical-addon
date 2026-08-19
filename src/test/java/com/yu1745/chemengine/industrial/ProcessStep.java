package com.yu1745.chemengine.industrial;

/**
 * 生产流程中的一步（Track C 蓝图）。
 *
 * @param reaction 引擎可解析的离子反应式（{@link com.yu1745.chemengine.Equilibrium#parse}
 *                 语法；null 表示该步无等价离子式，如电解/煅烧/物理结晶）
 * @param note     传统化学式 + 工艺条件/说明
 * @param expressible 引擎当前可表达性：YES=现有数据可直接模拟，PARTIAL=需补数据或引擎扩展，
 *                  NO=超出当前引擎形态（电解/煅烧/冶金/有机高压）
 */
public record ProcessStep(String reaction, String note, Expressible expressible) {

    public enum Expressible { YES, PARTIAL, NO }

    public static ProcessStep yes(String reaction, String note) {
        return new ProcessStep(reaction, note, Expressible.YES);
    }

    public static ProcessStep partial(String reaction, String note) {
        return new ProcessStep(reaction, note, Expressible.PARTIAL);
    }

    public static ProcessStep no(String note) {
        return new ProcessStep(null, note, Expressible.NO);
    }
}
