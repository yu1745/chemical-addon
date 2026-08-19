package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 合成氨 NH3 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class AmmoniaProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "合成氨 NH3";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("ammonia", "nitrogen");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("chemicaladdon:nitrogen + 3 chemicaladdon:hydrogen = 2 chemicaladdon:ammonia",
            "N2 + 3 H2 ⇌ 2 NH3（哈伯法：400–500°C、10–30 MPa、Fe 催化；D3 纯计量净反应表达净合成，真实为高压部分转化每程 ~15–25%，转化率属工艺细节）"),
        ProcessStep.yes("chemicaladdon:slaked_lime(s) + 2 NH4+1 = Ca+2 + 2 chemicaladdon:ammonia + 2 water", "Ca(OH)2 + 2 NH4Cl --△--> CaCl2 + 2 NH3↑ + 2 H2O（实验室制氨，已实现 Solvay step5 同构）")
    );

    private AmmoniaProcess() {}
}
