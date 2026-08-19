package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 三氯化铁 FeCl3 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class FerricChlorideProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "三氯化铁 FeCl3";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("ferric_chloride_solution", "ferric_oxide");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.no("【留空·待实现】 2 FeCl2 + Cl2 -> 2 FeCl3（电子中性输入下 e- 中间体模型数值失败，见 docs/known_limitations.md §8）"),
        ProcessStep.yes("chemicaladdon:ferric_oxide(s) + 6 H+1 = 2 Fe+3 + 3 water",
            "Fe2O3 + 6 HCl -> 2 FeCl3 + 3 H2O（氧化铁酸溶，D3 纯计量净反应；Cl- 旁观，绕开 §9 氧化物相竞争）"),
        ProcessStep.yes(null, "FeCl3 溶液配制（ferric_chloride_solution，物理过程）")
    );

    private FerricChlorideProcess() {}
}
