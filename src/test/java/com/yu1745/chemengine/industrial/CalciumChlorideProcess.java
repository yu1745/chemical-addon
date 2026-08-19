package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 氯化钙 CaCl2 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class CalciumChlorideProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "氯化钙 CaCl2";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("calcium_chloride_solution");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("chemicaladdon:limestone(s) + 2 H+1 = Ca+2 + chemicaladdon:carbon_dioxide + water", "CaCO3 + 2 HCl -> CaCl2 + CO2↑ + H2O（已实现 hclDescalesLimestone 同构）"),
        ProcessStep.yes("chemicaladdon:slaked_lime(s) + 2 NH4+1 = Ca+2 + 2 chemicaladdon:ammonia + 2 water", "索尔维废液回收：2 NH4Cl + Ca(OH)2 -> 2 NH3↑ + CaCl2 + 2 H2O（已实现 step5）")
    );

    private CalciumChlorideProcess() {}
}
