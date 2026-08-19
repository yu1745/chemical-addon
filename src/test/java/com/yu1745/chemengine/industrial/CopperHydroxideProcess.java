package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 氢氧化铜 Cu(OH)2 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class CopperHydroxideProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "氢氧化铜 Cu(OH)2";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("copper_hydroxide");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("Cu+2 + 2 OH-1 = chemicaladdon:copper_hydroxide(s)", "CuSO4 + 2 NaOH -> Cu(OH)2↓ + Na2SO4（已实现）")
    );

    private CopperHydroxideProcess() {}
}
