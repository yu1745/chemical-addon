package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 硝酸银 AgNO3 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class SilverNitrateProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "硝酸银 AgNO3";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("silver_nitrate_solution", "silver_metal");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("chemicaladdon:silver_metal(s) + 2 H+1 + NO3-1 = Ag+1 + chemicaladdon:nitrogen_dioxide + water",
            "Ag + 2 HNO3(浓) -> AgNO3 + NO2↑ + H2O（D3 纯计量净反应）"),
        ProcessStep.yes("3 chemicaladdon:silver_metal(s) + 4 H+1 + 3 NO3-1 = 3 Ag+1 + chemicaladdon:nitric_oxide + 2 water",
            "3 Ag + 4 HNO3(稀) -> 3 AgNO3 + NO↑ + 2 H2O（D3 纯计量净反应）"),
        ProcessStep.yes(null, "AgNO3 晶体溶于水配液（silver_nitrate_solution，物理过程）")
    );

    private SilverNitrateProcess() {}
}
