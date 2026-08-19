package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 氢氧化铝 Al(OH)3 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class AluminiumHydroxideProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "氢氧化铝 Al(OH)3";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("aluminium_hydroxide");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("Al+3 + 3 OH-1 = chemicaladdon:aluminium_hydroxide(s)", "AlCl3 + 3 NaOH -> Al(OH)3↓ + 3 NaCl（已实现）"),
        ProcessStep.yes("chemicaladdon:aluminium_hydroxide(s) + OH-1 = [Al(OH)4]-1", "Al(OH)3 + OH- -> [Al(OH)4]-（两性溶解，已实现 logK 1.3）")
    );

    private AluminiumHydroxideProcess() {}
}
