package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 铁 Fe（炼铁） 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class IronSmeltingProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "铁 Fe（炼铁）";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("iron_metal", "carbon", "carbon_monoxide", "ferric_oxide");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("chemicaladdon:carbon(s) + chemicaladdon:oxygen = chemicaladdon:carbon_dioxide",
            "C + O2 --点燃--> CO2（焦炭燃烧，D3 纯计量净反应）"),
        ProcessStep.yes("chemicaladdon:carbon_dioxide + chemicaladdon:carbon(s) = 2 chemicaladdon:carbon_monoxide",
            "CO2 + C --高温--> 2 CO（CO 再生，D3 纯计量净反应）"),
        ProcessStep.yes("chemicaladdon:ferric_oxide(s) + 3 chemicaladdon:carbon_monoxide = 2 chemicaladdon:iron_metal(s) + 3 chemicaladdon:carbon_dioxide",
            "Fe2O3 + 3 CO --高温--> 2 Fe + 3 CO2（高炉还原，D3 纯计量净反应）")
    );

    private IronSmeltingProcess() {}
}
