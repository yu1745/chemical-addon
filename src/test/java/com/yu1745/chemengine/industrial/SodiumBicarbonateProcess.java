package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 小苏打 NaHCO3 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class SodiumBicarbonateProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "小苏打 NaHCO3";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("sodium_bicarbonate", "sodium_bicarbonate_slurry");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("Na+1 + HCO3-1 = chemicaladdon:sodium_bicarbonate(s)", "Na+ + HCO3- -> NaHCO3↓（碳化析出，已实现）"),
        ProcessStep.yes("CO3-2 + chemicaladdon:carbon_dioxide + water = 2 HCO3-1", "Na2CO3 + CO2 + H2O -> 2 NaHCO3（饱和纯碱液碳化）")
    );

    private SodiumBicarbonateProcess() {}
}
