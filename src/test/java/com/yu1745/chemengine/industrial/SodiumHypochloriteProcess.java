package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 次氯酸钠 NaClO 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class SodiumHypochloriteProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "次氯酸钠 NaClO";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of();

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("chemicaladdon:chlorine + 2 OH-1 = Cl-1 + ClO-1 + water", "Cl2 + 2 NaOH -> NaCl + NaClO + H2O（冷稀碱，已实现：Cl2 碱性歧化 logK 15.3）"),
        ProcessStep.no("【留空·待实现】 3 Cl2 + 6 NaOH --热--> 5 NaCl + NaClO3 + 3 H2O（热浓碱歧化加深，生成氯酸盐）")
    );

    private SodiumHypochloriteProcess() {}
}
