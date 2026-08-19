package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 硫氰酸钾 KSCN 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class PotassiumThiocyanateProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "硫氰酸钾 KSCN";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("potassium_thiocyanate_solution", "potassium_cyanide", "potassium_thiocyanate");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("NH4+1 + OH-1 = chemicaladdon:ammonia + water", "NH4SCN + KOH -> KSCN + NH3↑ + H2O（交换法：NH4+ + OH- -> NH3 + H2O，已实现）"),
        ProcessStep.yes("chemicaladdon:potassium_cyanide(s) + chemicaladdon:sulfur(s) = chemicaladdon:potassium_thiocyanate(s)",
            "KCN + S --熔融--> KSCN（剧毒路线，D3 纯计量净反应）")
    );

    private PotassiumThiocyanateProcess() {}
}
