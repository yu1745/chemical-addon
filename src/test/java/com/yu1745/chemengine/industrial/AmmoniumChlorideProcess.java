package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 氯化铵 NH4Cl 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class AmmoniumChlorideProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "氯化铵 NH4Cl";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("ammonium_chloride_solution");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("chemicaladdon:ammonia + H+1 = NH4+1", "NH3 + HCl -> NH4Cl（氨吸收氯化氢）"),
        ProcessStep.yes("Na+1 + HCO3-1 = chemicaladdon:sodium_bicarbonate(s)", "侯氏母液副产：NaCl + NH4HCO3 -> NaHCO3↓ + NH4Cl（与纯碱类重复，保留流程完整性）"),
        ProcessStep.yes("NH4+1 + Cl-1 = chemicaladdon:ammonium_chloride(s)", "冷却结晶（已实现：0°C 29.4 g/100g 溶解度曲线）")
    );

    private AmmoniumChlorideProcess() {}
}
