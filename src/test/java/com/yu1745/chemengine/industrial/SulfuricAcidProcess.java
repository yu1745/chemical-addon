package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 硫酸 H2SO4 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class SulfuricAcidProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "硫酸 H2SO4";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("sulfuric_acid", "sulfur");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("chemicaladdon:sulfur(s) + chemicaladdon:oxygen = chemicaladdon:sulfur_dioxide",
            "S + O2 --点燃--> SO2（硫燃烧，D3 纯计量净反应）"),
        ProcessStep.yes("2 chemicaladdon:sulfur_dioxide + chemicaladdon:oxygen = 2 chemicaladdon:sulfur_trioxide",
            "2 SO2 + O2 ⇌ 2 SO3（V2O5 催化，450°C，可逆；接触法核心步，D3 纯计量净反应）"),
        ProcessStep.yes("chemicaladdon:sulfur_trioxide + water = 2 H+1 + SO4-2", "SO3 + H2O -> H2SO4（已实现：logK 6.0，ΔH -227.72 kJ/mol）")
    );

    private SulfuricAcidProcess() {}
}
