package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 亚硫酸钙 CaSO3 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class CalciumSulfiteProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "亚硫酸钙 CaSO3";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("calcium_sulfite_slurry", "calcium_sulfite");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("Ca+2 + SO3-2 = chemicaladdon:calcium_sulfite(s)", "Ca(OH)2 + SO2 -> CaSO3↓ + H2O（烟气脱硫，已实现：SO2 水合 + CaSO3 Ksp）")
    );

    private CalciumSulfiteProcess() {}
}
