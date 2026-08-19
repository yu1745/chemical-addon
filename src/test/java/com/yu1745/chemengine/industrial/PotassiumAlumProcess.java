package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 明矾 KAl(SO4)2·12H2O 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class PotassiumAlumProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "明矾 KAl(SO4)2·12H2O";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("potassium_alum_solution");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("chemicaladdon:aluminium_hydroxide(s) + 3 H+1 = Al+3 + 3 water", "2 Al(OH)3 + 3 H2SO4 -> Al2(SO4)3 + 6 H2O（氢氧化铝酸溶）"),
        ProcessStep.yes(null, "Al2(SO4)3 + K2SO4 + 24 H2O -> 2 KAl(SO4)2·12H2O（复盐结晶，物理过程）")
    );

    private PotassiumAlumProcess() {}
}
