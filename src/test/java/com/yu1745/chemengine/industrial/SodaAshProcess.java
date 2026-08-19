package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * 纯碱 Na2CO3 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {@link com.yu1745.chemengine.Equilibrium#parse} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class SodaAshProcess {

    /** 中文名/化学式。 */
    public static final String NAME = "纯碱 Na2CO3";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of("soda_ash_solution", "ammoniated_brine", "sodium_carbonate");

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
        ProcessStep.yes("chemicaladdon:ammonia + chemicaladdon:carbon_dioxide + water = NH4+1 + HCO3-1", "NH3 + CO2 + H2O -> NH4HCO3（氨盐水碳化第一步）"),
        ProcessStep.yes("Na+1 + HCO3-1 = chemicaladdon:sodium_bicarbonate(s)", "NaCl + NH4HCO3 -> NaHCO3↓ + NH4Cl（碳化析出，已实现 Solvay step1）"),
        ProcessStep.yes("2 chemicaladdon:sodium_bicarbonate(s) = chemicaladdon:sodium_carbonate(s) + chemicaladdon:carbon_dioxide + water",
            "2 NaHCO3 --煅烧 150–200°C--> Na2CO3 + CO2↑ + H2O（煅烧步，D3 纯计量净反应）"),
        ProcessStep.yes("chemicaladdon:slaked_lime(s) + 2 NH4+1 = Ca+2 + 2 chemicaladdon:ammonia + 2 water", "2 NH4Cl + Ca(OH)2 -> 2 NH3↑ + CaCl2 + 2 H2O（母液氨回收，已实现 Solvay step5）")
    );

    private SodaAshProcess() {}
}
