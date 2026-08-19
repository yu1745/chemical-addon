package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 氧气 O2 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class OxygenProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "氧气 O2";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("oxygen", "hydrogen_peroxide", "potassium_chlorate", "potassium_chloride");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("2 water = 2 chemicaladdon:hydrogen + chemicaladdon:oxygen",
            "2 H2O --电解--> 2 H2↑ + O2↑（电解水副产，D1b Electrolysis）"),
        ProcessStep.yes("2 chemicaladdon:hydrogen_peroxide = 2 water + chemicaladdon:oxygen", "2 H2O2 -> 2 H2O + O2↑（已实现：hydrogen_peroxide 分解平衡，logK 20.9）"),
        ProcessStep.yes("2 chemicaladdon:potassium_chlorate(s) = 2 chemicaladdon:potassium_chloride(s) + 3 chemicaladdon:oxygen",
            "2 KClO3 --MnO2/△--> 2 KCl + 3 O2↑（D3 纯计量净反应）"),
        ProcessStep.partial(null, "空气液化分馏 --物理--> N2↑ + O2↑（物理分离，无化学反应式；产物 N2/O2 物种已具备）")
    );

    private OxygenProcess() {}
}
