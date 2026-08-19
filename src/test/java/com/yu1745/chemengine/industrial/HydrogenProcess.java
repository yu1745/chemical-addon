package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 氢气 H2 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class HydrogenProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "氢气 H2";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("hydrogen", "carbon_monoxide");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("2 water = 2 chemicaladdon:hydrogen + chemicaladdon:oxygen",
            "2 H2O --电解--> 2 H2↑ + O2↑（电解水，D1b Electrolysis 受迫进度机制）"),
        ProcessStep.yes("2 Cl-1 + 2 water = 2 OH-1 + chemicaladdon:hydrogen + chemicaladdon:chlorine",
            "2 NaCl + 2 H2O --电解--> 2 NaOH + H2↑ + Cl2↑（氯碱副产，Na+ 旁观；批式混合时 Cl2 与 OH- 歧化为次氯酸盐漂白）"),
        ProcessStep.yes("chemicaladdon:zinc_metal(s) + 2 H+1 = Zn+2 + chemicaladdon:hydrogen",
            "Zn + 2 HCl -> ZnCl2 + H2↑（金属+酸，D1a 气体产物位移平衡条目，无自由电子；绕开 §7）；Fe + H2SO4(稀) -> FeSO4 + H2↑ 同理但 Fe 已含 Cu 置换共享金属池，待 L3 多重置换支持"),
        ProcessStep.yes("chemicaladdon:carbon(s) + water = chemicaladdon:carbon_monoxide + chemicaladdon:hydrogen",
            "C + H2O --高温--> CO + H2（水煤气，D3 纯计量净反应）")
    );

    private HydrogenProcess() {}
}
