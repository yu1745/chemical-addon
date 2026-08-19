package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 二氧化硫 SO2 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class SulfurDioxideProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "二氧化硫 SO2";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("sulfur_dioxide", "sulfur", "pyrite");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("chemicaladdon:sulfur(s) + chemicaladdon:oxygen = chemicaladdon:sulfur_dioxide",
            "S + O2 --点燃--> SO2（硫燃烧，D3 纯计量净反应；SO2 捕集为亚硫酸是后续水相步）"),
        ProcessStep.yes("4 chemicaladdon:pyrite(s) + 11 chemicaladdon:oxygen = 2 chemicaladdon:ferric_oxide(s) + 8 chemicaladdon:sulfur_dioxide",
            "4 FeS2 + 11 O2 --高温--> 2 Fe2O3 + 8 SO2（黄铁矿焙烧，D3 纯计量净反应）"),
        ProcessStep.yes("SO3-2 + 2 H+1 = chemicaladdon:sulfur_dioxide + water", "Na2SO3 + 2 HCl -> 2 NaCl + SO2↑ + H2O（实验室，已实现：SO2 水合 pKa1/pKa2 组合 logK 8.99）")
    );

    private SulfurDioxideProcess() {}
}
