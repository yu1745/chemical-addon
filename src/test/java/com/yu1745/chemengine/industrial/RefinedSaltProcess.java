package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 精制食盐 NaCl 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class RefinedSaltProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "精制食盐 NaCl";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("brine", "rock_salt");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("Ba+2 + SO4-2 = chemicaladdon:barium_sulfate(s)", "粗盐除 SO4²-：BaCl2 + Na2SO4 -> BaSO4↓ + 2 NaCl（已实现）"),
        ProcessStep.yes("Ca+2 + CO3-2 = chemicaladdon:limestone(s)", "除 Ca²+：CaCl2 + Na2CO3 -> CaCO3↓ + 2 NaCl（已实现）"),
        ProcessStep.yes("Ba+2 + CO3-2 = chemicaladdon:barium_carbonate(s)", "除过量 Ba²+：BaCl2 + Na2CO3 -> BaCO3↓ + 2 NaCl（已实现）"),
        ProcessStep.yes("Mg+2 + 2 OH-1 = chemicaladdon:magnesium_hydroxide(s)", "除 Mg²+：MgCl2 + 2 NaOH -> Mg(OH)2↓ + 2 NaCl（已实现）"),
        ProcessStep.yes("H+1 + OH-1 = water", "过量碱/纯碱用 HCl 回调（中和）"),
        ProcessStep.yes(null, "精盐水蒸发结晶得 NaCl（rock_salt 溶解度曲线，已实现 brineEvaporatesToDrySalt；物理过程无反应式）")
    );

    private RefinedSaltProcess() {}
}
