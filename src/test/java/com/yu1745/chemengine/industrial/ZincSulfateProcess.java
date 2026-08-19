package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 硫酸锌 ZnSO4 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class ZincSulfateProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "硫酸锌 ZnSO4";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("zinc_sulfate_solution", "zinc_metal", "zinc_oxide");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("chemicaladdon:zinc_metal(s) + 2 H+1 = Zn+2 + chemicaladdon:hydrogen",
            "Zn + H2SO4(稀) -> ZnSO4 + H2↑（金属+酸，D1a 气体产物位移平衡条目，无自由电子；绕开 §7）"),
        ProcessStep.yes("chemicaladdon:zinc_oxide(s) + 2 H+1 = Zn+2 + water",
            "ZnO + H2SO4 -> ZnSO4 + H2O（D3 纯计量净反应）"),
        ProcessStep.yes(null, "ZnSO4 溶液配制（zinc_sulfate_solution，物理过程）")
    );

    private ZincSulfateProcess() {}
}
