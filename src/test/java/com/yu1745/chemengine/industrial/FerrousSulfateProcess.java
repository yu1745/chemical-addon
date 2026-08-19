package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 硫酸亚铁/绿矾 FeSO4·7H2O 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class FerrousSulfateProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "硫酸亚铁/绿矾 FeSO4·7H2O";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("ferrous_sulfate_solution", "iron_metal");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("chemicaladdon:iron_metal(s) + 2 H+1 = Fe+2 + chemicaladdon:hydrogen",
            "Fe + H2SO4(稀) -> FeSO4 + H2↑（金属+酸，D1a 气体产物位移平衡条目；Fe 亦含 Cu 置换，L3 共享金属池分配）"),
        ProcessStep.yes("chemicaladdon:iron_metal(s) + Cu+2 = Fe+2 + chemicaladdon:copper_metal(s)",
            "Fe + CuSO4 -> FeSO4 + Cu（置换法，湿法炼铜/废液回收；D1a 金属置换平衡条目）"),
        ProcessStep.yes("Fe+2 + SO4-2 = chemicaladdon:ferrous_sulfate(s)", "绿矾结晶（溶解度曲线）")
    );

    private FerrousSulfateProcess() {}
}
