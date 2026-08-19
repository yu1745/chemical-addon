package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 二氧化氮 NO2 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class NitrogenDioxideProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "二氧化氮 NO2";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("nitrogen_dioxide");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("2 chemicaladdon:nitric_oxide + chemicaladdon:oxygen = 2 chemicaladdon:nitrogen_dioxide",
            "2 NO + O2 -> 2 NO2（NO 氧化，D3 纯计量净反应）"),
        ProcessStep.yes("chemicaladdon:copper_metal(s) + 4 H+1 + 2 NO3-1 = Cu+2 + 2 chemicaladdon:nitrogen_dioxide + 2 water",
            "Cu + 4 HNO3(浓) -> Cu(NO3)2 + 2 NO2↑ + 2 H2O（D3 纯计量净反应）"),
        ProcessStep.yes("3 chemicaladdon:nitrogen_dioxide + water = 2 H+1 + 2 NO3-1 + chemicaladdon:nitric_oxide", "3 NO2 + H2O -> 2 HNO3 + NO（吸收，已实现；与硝酸类重复，保留）")
    );

    private NitrogenDioxideProcess() {}
}
