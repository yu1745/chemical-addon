package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 熟石灰 Ca(OH)2 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class SlakedLimeProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "熟石灰 Ca(OH)2";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("slaked_lime", "milk_of_lime", "quicklime");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("chemicaladdon:limestone(s) = chemicaladdon:quicklime(s) + chemicaladdon:carbon_dioxide",
            "CaCO3 --高温煅烧--> CaO + CO2↑（石灰窑，D3 纯计量净反应）"),
        ProcessStep.yes("chemicaladdon:quicklime(s) + water = chemicaladdon:slaked_lime(s)",
            "CaO + H2O -> Ca(OH)2（放热消化，D3 纯计量净反应）"),
        ProcessStep.yes("chemicaladdon:slaked_lime(s) = Ca+2 + 2 OH-1", "Ca(OH)2 溶解度（已实现：logK -5.2，ΔH -16.87 kJ/mol）")
    );

    private SlakedLimeProcess() {}
}
