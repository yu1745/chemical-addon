package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 石膏 CaSO4·2H2O 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class GypsumProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "石膏 CaSO4·2H2O";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("gypsum", "gypsum_slurry");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("Ca+2 + SO4-2 = chemicaladdon:gypsum(s)", "Ca(OH)2 + H2SO4 -> CaSO4 + 2 H2O（已实现：gypsum Ksp phreeqc -4.58）"),
        ProcessStep.yes("chemicaladdon:limestone(s) + 2 H+1 = Ca+2 + chemicaladdon:carbon_dioxide + water",
            "CaCO3 + H2SO4 -> CaSO4 + CO2↑ + H2O（酸解，D3 纯计量净反应；Ca+2+SO4-2 后续沉淀石膏）"),
        ProcessStep.yes("2 chemicaladdon:calcium_sulfite(s) + chemicaladdon:oxygen = 2 chemicaladdon:gypsum(s)",
            "2 CaSO3 + O2 -> 2 CaSO4（烟气脱硫氧化，D3 纯计量净反应）"),
        ProcessStep.yes("Ca+2 + SO4-2 = chemicaladdon:gypsum(s)", "石膏沉淀/结晶（已实现：gypsum Ksp -4.58）")
    );

    private GypsumProcess() {}
}
