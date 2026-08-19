package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 碱式碳酸铜 Cu2(OH)2CO3 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class MalachiteProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "碱式碳酸铜 Cu2(OH)2CO3";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("copper_carbonate");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("2 Cu+2 + 2 CO3-2 + water = chemicaladdon:copper_carbonate(s) + chemicaladdon:carbon_dioxide", "2 CuSO4 + 2 Na2CO3 + H2O -> Cu2(OH)2CO3↓ + 2 Na2SO4 + CO2↑（已实现 malachite）"),
        ProcessStep.yes("2 chemicaladdon:copper_metal(s) + chemicaladdon:oxygen + chemicaladdon:carbon_dioxide + water = chemicaladdon:copper_carbonate(s)",
            "2 Cu + O2 + CO2 + H2O --自然--> Cu2(OH)2CO3（铜绿自然生成，D3 环境规则，纯计量净反应）")
    );

    private MalachiteProcess() {}
}
