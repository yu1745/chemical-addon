package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 氢氧化铁 Fe(OH)3 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class IronHydroxideProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "氢氧化铁 Fe(OH)3";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("iron_hydroxide");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("Fe+3 + 3 OH-1 = chemicaladdon:iron_hydroxide(s)", "FeCl3 + 3 NaOH -> Fe(OH)3↓ + 3 NaCl（已实现 Ksp 竞争）"),
        ProcessStep.yes("Fe+3 + 3 OH-1 = chemicaladdon:iron_hydroxide(s)", "Fe2(SO4)3 + 6 NaOH -> 2 Fe(OH)3 + 3 Na2SO4（同式，流程完整性保留）")
    );

    private IronHydroxideProcess() {}
}
