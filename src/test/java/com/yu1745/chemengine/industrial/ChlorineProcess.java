package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 氯气 Cl2 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class ChlorineProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "氯气 Cl2";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("chlorine", "manganese_dioxide");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("2 Cl-1 + 2 water = 2 OH-1 + chemicaladdon:hydrogen + chemicaladdon:chlorine",
            "2 NaCl + 2 H2O --电解--> 2 NaOH + H2↑ + Cl2↑（氯碱阳极，D1b Electrolysis；Na+ 旁观，批式 Cl2 遇 OH- 歧化为次氯酸盐）"),
        ProcessStep.yes("chemicaladdon:manganese_dioxide(s) + 4 H+1 + 2 Cl-1 = Mn+2 + chemicaladdon:chlorine + 2 water",
            "MnO2 + 4 HCl(浓) --△--> MnCl2 + Cl2↑ + 2 H2O（D3 纯计量净反应）")
    );

    private ChlorineProcess() {}
}
