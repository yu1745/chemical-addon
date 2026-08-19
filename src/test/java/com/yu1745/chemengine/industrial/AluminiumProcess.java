package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 铝 Al 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class AluminiumProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "铝 Al";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("aluminium_oxide", "aluminium_metal");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("chemicaladdon:aluminium_oxide(s) + 2 OH-1 + 3 water = 2 [Al(OH)4]-1",
            "Al2O3 + 2 NaOH -> 2 NaAlO2 + H2O（拜耳法碱溶，D3 纯计量净反应）"),
        ProcessStep.yes("2 [Al(OH)4]-1 + chemicaladdon:carbon_dioxide = 2 chemicaladdon:aluminium_hydroxide(s) + CO3-2 + water", "2 NaAlO2 + CO2 + 3 H2O -> 2 Al(OH)3↓ + Na2CO3（分解析出，引擎可表达）"),
        ProcessStep.yes("2 chemicaladdon:aluminium_hydroxide(s) = chemicaladdon:aluminium_oxide(s) + 3 water",
            "2 Al(OH)3 --煅烧--> Al2O3 + 3 H2O（煅烧，D3 纯计量净反应）"),
        ProcessStep.yes("2 chemicaladdon:aluminium_oxide(s) = 4 chemicaladdon:aluminium_metal(s) + 3 chemicaladdon:oxygen",
            "2 Al2O3 --冰晶石/熔盐电解--> 4 Al + 3 O2↑（熔盐电解铝，D3 纯计量净反应）")
    );

    private AluminiumProcess() {}
}
