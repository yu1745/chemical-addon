package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 铜 Cu 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class CopperProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "铜 Cu";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("copper_metal", "cuprous_sulfide", "cuprous_oxide");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("chemicaladdon:iron_metal(s) + Cu+2 = Fe+2 + chemicaladdon:copper_metal(s)",
            "Fe + CuSO4 -> FeSO4 + Cu（湿法炼铜/置换，D1a 金属置换平衡条目）"),
        ProcessStep.yes("2 chemicaladdon:cuprous_sulfide(s) + 3 chemicaladdon:oxygen = 2 chemicaladdon:cuprous_oxide(s) + 2 chemicaladdon:sulfur_dioxide",
            "2 Cu2S + 3 O2 --高温--> 2 Cu2O + 2 SO2（火法炼铜·焙烧，D3 纯计量净反应）"),
        ProcessStep.yes("chemicaladdon:cuprous_sulfide(s) + 2 chemicaladdon:cuprous_oxide(s) = 6 chemicaladdon:copper_metal(s) + chemicaladdon:sulfur_dioxide",
            "Cu2S + 2 Cu2O --高温--> 6 Cu + SO2（火法炼铜·还原，D3 纯计量净反应）")
    );

    private CopperProcess() {}
}
