package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 硫酸铜/胆矾 CuSO4·5H2O 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class CopperSulfateProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "硫酸铜/胆矾 CuSO4·5H2O";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("copper_sulfate", "copper_sulfate_solution", "cupric_oxide");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("chemicaladdon:copper_metal(s) + 4 H+1 + SO4-2 = Cu+2 + chemicaladdon:sulfur_dioxide + 2 water",
            "Cu + 2 H2SO4(浓) --△--> CuSO4 + SO2↑ + 2 H2O（D3 纯计量净反应）"),
        ProcessStep.yes("chemicaladdon:cupric_oxide(s) + 2 H+1 = Cu+2 + water",
            "CuO + H2SO4 -> CuSO4 + H2O（D3 纯计量净反应，绕开 §9 氧化物相竞争）"),
        ProcessStep.yes("chemicaladdon:copper_carbonate(s) + 4 H+1 = 2 Cu+2 + chemicaladdon:carbon_dioxide + 3 water", "Cu2(OH)2CO3 + 2 H2SO4 -> 2 CuSO4 + CO2↑ + 3 H2O（孔雀石酸溶）"),
        ProcessStep.yes("Cu+2 + SO4-2 = chemicaladdon:copper_sulfate(s)", "胆矾结晶：CuSO4 + 5 H2O ⇌ CuSO4·5H2O（溶解度曲线）")
    );

    private CopperSulfateProcess() {}
}
