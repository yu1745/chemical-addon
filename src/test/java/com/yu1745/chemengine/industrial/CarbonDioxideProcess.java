package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 二氧化碳 CO2 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class CarbonDioxideProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "二氧化碳 CO2";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("carbon_dioxide", "quicklime");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("chemicaladdon:limestone(s) = chemicaladdon:quicklime(s) + chemicaladdon:carbon_dioxide",
            "CaCO3 --高温--> CaO + CO2↑（石灰窑煅烧，D3 纯计量净反应）"),
        ProcessStep.yes("chemicaladdon:limestone(s) + 2 H+1 = Ca+2 + chemicaladdon:carbon_dioxide + water", "CaCO3 + 2 HCl -> CaCl2 + CO2↑ + H2O（酸溶法，已实现；与氯化钙类重复，保留）"),
        ProcessStep.yes("CO3-2 + 2 H+1 = chemicaladdon:carbon_dioxide + water", "CO3²- + 2 H+ -> CO2↑ + H2O（酸+碳酸盐，已实现 hclPlusSodaAsh）")
    );

    private CarbonDioxideProcess() {}
}
