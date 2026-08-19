package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 硝酸钾 KNO3 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class PotassiumNitrateProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "硝酸钾 KNO3";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("potassium_nitrate_solution");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes(null, "NaNO3 + KCl ⇌ KNO3 + NaCl（复分解；热溶液浓缩、冷却结晶，溶解度差分离）"),
        ProcessStep.yes("K+1 + NO3-1 = chemicaladdon:potassium_nitrate(s)", "KNO3 冷却结晶（已实现：0°C 13.3 g/100g 曲线）"),
        ProcessStep.yes("H+1 + OH-1 = water", "KOH + HNO3 -> KNO3 + H2O（中和路线；中和步与其他类重复，保留）")
    );

    private PotassiumNitrateProcess() {}
}
