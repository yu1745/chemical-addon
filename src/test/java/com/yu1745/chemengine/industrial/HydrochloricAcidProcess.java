package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 盐酸 HCl 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class HydrochloricAcidProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "盐酸 HCl";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("hydrochloric_acid", "hydrogen_chloride", "sodium_bisulfate");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("chemicaladdon:hydrogen + chemicaladdon:chlorine = 2 chemicaladdon:hydrogen_chloride",
            "H2 + Cl2 --点燃--> 2 HCl(g)（合成盐酸，D3 纯计量净反应；HCl 捕集溶于水为盐酸是后续水相步）"),
        ProcessStep.yes("chemicaladdon:rock_salt(s) + chemicaladdon:sulfuric_acid = chemicaladdon:sodium_bisulfate(s) + chemicaladdon:hydrogen_chloride",
            "NaCl + H2SO4(浓) --强热--> NaHSO4 + HCl↑（芒硝法，D3 浓酸模块纯计量净反应）"),
        ProcessStep.yes("chemicaladdon:hydrogen_chloride + water = H+1 + Cl-1", "HCl(g) 溶于水（已实现：hydrogen_chloride logK 1.0）")
    );

    private HydrochloricAcidProcess() {}
}
