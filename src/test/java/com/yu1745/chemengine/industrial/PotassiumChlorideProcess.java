package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 氯化钾 KCl 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class PotassiumChlorideProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "氯化钾 KCl";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("potassium_chloride_solution");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes(null, "光卤石 KCl·MgCl2·6H2O 溶解分离（溶解度差；物理过程）"),
        ProcessStep.yes("K+1 + Cl-1 = chemicaladdon:potassium_chloride(s)", "KCl 结晶（溶解度曲线）")
    );

    private PotassiumChlorideProcess() {}
}
