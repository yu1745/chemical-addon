package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 过磷酸钙（磷肥） 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class SuperphosphateProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "过磷酸钙（磷肥）";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("calcium_phosphate", "phosphoric_acid");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("chemicaladdon:calcium_phosphate(s) + 2 H+1 = 3 Ca+2 + 2 H2PO4-1", "Ca3(PO4)2 + 2 H2SO4 -> Ca(H2PO4)2 + 2 CaSO4（已实现：磷灰石酸溶 + 磷酸质子化 + 石膏沉淀）")
    );

    private SuperphosphateProcess() {}
}
