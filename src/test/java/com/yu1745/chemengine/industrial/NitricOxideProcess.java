package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 一氧化氮 NO 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class NitricOxideProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "一氧化氮 NO";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("nitric_oxide", "nitrogen");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("4 chemicaladdon:ammonia + 5 chemicaladdon:oxygen = 4 chemicaladdon:nitric_oxide + 6 water",
            "4 NH3 + 5 O2 --Pt/800°C--> 4 NO + 6 H2O（氨氧化；硝酸工艺中间体，D3 纯计量净反应）"),
        ProcessStep.yes("3 chemicaladdon:copper_metal(s) + 8 H+1 + 2 NO3-1 = 3 Cu+2 + 2 chemicaladdon:nitric_oxide + 4 water",
            "3 Cu + 8 HNO3(稀) -> 3 Cu(NO3)2 + 2 NO↑ + 4 H2O（D3 纯计量净反应）"),
        ProcessStep.yes("chemicaladdon:nitrogen + chemicaladdon:oxygen = 2 chemicaladdon:nitric_oxide",
            "N2 + O2 --放电/高温--> 2 NO（雷雨固氮，D3 纯计量净反应）")
    );

    private NitricOxideProcess() {}
}
