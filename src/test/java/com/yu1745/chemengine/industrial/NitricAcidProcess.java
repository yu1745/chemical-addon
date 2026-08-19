package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 硝酸 HNO3 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class NitricAcidProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "硝酸 HNO3";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("nitric_acid", "sodium_nitrate");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("4 chemicaladdon:ammonia + 5 chemicaladdon:oxygen = 4 chemicaladdon:nitric_oxide + 6 water",
            "4 NH3 + 5 O2 --Pt-Rh/800°C--> 4 NO + 6 H2O（氨氧化，D3 纯计量净反应）"),
        ProcessStep.yes("2 chemicaladdon:nitric_oxide + chemicaladdon:oxygen = 2 chemicaladdon:nitrogen_dioxide",
            "2 NO + O2 -> 2 NO2（NO 氧化，D3 纯计量净反应）"),
        ProcessStep.yes("3 chemicaladdon:nitrogen_dioxide + water = 2 H+1 + 2 NO3-1 + chemicaladdon:nitric_oxide", "3 NO2 + H2O -> 2 HNO3 + NO（已实现：logK 3.0，ΔH -138.18 kJ/mol）"),
        ProcessStep.yes("chemicaladdon:sodium_nitrate(s) + chemicaladdon:sulfuric_acid = chemicaladdon:sodium_bisulfate(s) + chemicaladdon:nitric_acid",
            "NaNO3 + H2SO4(浓) --微热--> NaHSO4 + HNO3（实验室，D3 浓酸模块纯计量净反应）")
    );

    private NitricAcidProcess() {}
}
