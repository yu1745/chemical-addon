package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 烧碱 NaOH 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class CausticSodaProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "烧碱 NaOH";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("caustic_soda_solution");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("2 Cl-1 + 2 water = 2 OH-1 + chemicaladdon:hydrogen + chemicaladdon:chlorine",
            "2 NaCl + 2 H2O --电解--> 2 NaOH + H2↑ + Cl2↑（氯碱工业，D1b Electrolysis；Na+ 旁观，批式 Cl2 遇 OH- 歧化为次氯酸盐）"),
        ProcessStep.yes("chemicaladdon:slaked_lime(s) = Ca+2 + 2 OH-1", "Ca(OH)2 溶解（苛化法第一步；与熟石灰类重复，保留）"),
        ProcessStep.yes("Ca+2 + CO3-2 = chemicaladdon:limestone(s)", "Na2CO3 + Ca(OH)2 -> 2 NaOH + CaCO3↓（苛化法第二步，已实现 causticisation）")
    );

    private CausticSodaProcess() {}
}
