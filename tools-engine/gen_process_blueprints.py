#!/usr/bin/env python3
"""Generate the industrial raw-material process blueprint classes.

Each material becomes one Java class in
  src/test/java/com/yu1745/chemengine/industrial/<Name>Process.java
listing the complete production-reaction chain for that material. Steps are
deliberately NOT deduplicated across classes (process completeness first).

Reaction-string conventions (engine-parseable ion form):
  - single '=', terms joined by ' + ', integer count prefix ("2 Na+1"),
  - solids as "<name>(s)" (short names get the chemicaladdon: namespace),
  - solvent "water", ions like "SO4-2"/"[Al(OH)4]-1", molecules "chemicaladdon:xxx".
Traditional equations + process conditions go in the step note.

Regenerate with:  python3 tools/gen_process_blueprints.py
"""
import pathlib

OUT = pathlib.Path("src/test/java/com/yu1745/chemengine/industrial")

# (class-suffix, 中文名, [plugin species ids], [(engine_reaction|None, note, flag)])
# flag: Y = engine can simulate today, P = partial (data/engine extension needed),
#       N = outside the engine's current form (electrolysis/calcination/metallurgy/organic).
MATERIALS = [
    ("HydrochloricAcid", "盐酸 HCl", ["hydrochloric_acid", "hydrogen_chloride"], [
        (None, "H2 + Cl2 --点燃--> 2 HCl(g)（合成盐酸）", "N"),
        (None, "NaCl + H2SO4(浓) --强热--> NaHSO4 + HCl↑；NaCl + NaHSO4 --强热--> Na2SO4 + HCl↑（芒硝法）", "N"),
        ("chemicaladdon:hydrogen_chloride + water = H+1 + Cl-1",
         "HCl(g) 溶于水（已实现：hydrogen_chloride logK 1.0）", "Y"),
    ]),
    ("SulfuricAcid", "硫酸 H2SO4", ["sulfuric_acid"], [
        (None, "S + O2 --点燃--> SO2（硫燃烧）", "N"),
        (None, "2 SO2 + O2 ⇌ 2 SO3（V2O5 催化，450°C，可逆；接触法核心步）", "N"),
        ("chemicaladdon:sulfur_trioxide + water = 2 H+1 + SO4-2",
         "SO3 + H2O -> H2SO4（已实现：logK 6.0，ΔH -227.72 kJ/mol）", "Y"),
    ]),
    ("NitricAcid", "硝酸 HNO3", ["nitric_acid"], [
        (None, "4 NH3 + 5 O2 --Pt-Rh/800°C--> 4 NO + 6 H2O（氨氧化）", "N"),
        (None, "2 NO + O2 -> 2 NO2（NO 氧化）", "N"),
        ("3 chemicaladdon:nitrogen_dioxide + water = 2 H+1 + 2 NO3-1 + chemicaladdon:nitric_oxide",
         "3 NO2 + H2O -> 2 HNO3 + NO（已实现：logK 3.0，ΔH -138.18 kJ/mol）", "Y"),
        (None, "NaNO3 + H2SO4(浓) --微热--> NaHSO4 + HNO3↑（实验室）", "N"),
    ]),
    ("CausticSoda", "烧碱 NaOH", ["caustic_soda_solution"], [
        (None, "2 NaCl + 2 H2O --电解--> 2 NaOH + H2↑ + Cl2↑（氯碱工业）", "N"),
        ("chemicaladdon:slaked_lime(s) = Ca+2 + 2 OH-1",
         "Ca(OH)2 溶解（苛化法第一步；与熟石灰类重复，保留）", "Y"),
        ("Ca+2 + CO3-2 = chemicaladdon:limestone(s)",
         "Na2CO3 + Ca(OH)2 -> 2 NaOH + CaCO3↓（苛化法第二步，已实现 causticisation）", "Y"),
    ]),
    ("SodaAsh", "纯碱 Na2CO3", ["soda_ash_solution", "ammoniated_brine"], [
        ("chemicaladdon:ammonia + chemicaladdon:carbon_dioxide + water = NH4+1 + HCO3-1",
         "NH3 + CO2 + H2O -> NH4HCO3（氨盐水碳化第一步）", "Y"),
        ("Na+1 + HCO3-1 = chemicaladdon:sodium_bicarbonate(s)",
         "NaCl + NH4HCO3 -> NaHCO3↓ + NH4Cl（碳化析出，已实现 Solvay step1）", "Y"),
        (None, "2 NaHCO3 --煅烧 150–200°C--> Na2CO3 + CO2↑ + H2O（煅烧步，缺 Na2CO3(s) 数据）", "P"),
        ("chemicaladdon:slaked_lime(s) + 2 NH4+1 = Ca+2 + 2 chemicaladdon:ammonia + 2 water",
         "2 NH4Cl + Ca(OH)2 -> 2 NH3↑ + CaCl2 + 2 H2O（母液氨回收，已实现 Solvay step5）", "Y"),
    ]),
    ("SodiumBicarbonate", "小苏打 NaHCO3", ["sodium_bicarbonate", "sodium_bicarbonate_slurry"], [
        ("Na+1 + HCO3-1 = chemicaladdon:sodium_bicarbonate(s)",
         "Na+ + HCO3- -> NaHCO3↓（碳化析出，已实现）", "Y"),
        ("CO3-2 + chemicaladdon:carbon_dioxide + water = 2 HCO3-1",
         "Na2CO3 + CO2 + H2O -> 2 NaHCO3（饱和纯碱液碳化）", "Y"),
    ]),
    ("Limestone", "石灰石 CaCO3", ["limestone"], [
        (None, "CaCO3 --高温 900–1200°C--> CaO + CO2↑（石灰窑煅烧，生石灰/石灰链原料步）", "P"),
        ("chemicaladdon:limestone(s) + 2 H+1 = Ca+2 + chemicaladdon:carbon_dioxide + water",
         "CaCO3 + 2 HCl -> CaCl2 + CO2↑ + H2O（酸溶，已实现 hclDescalesLimestone；与氯化钙/二氧化碳类重复，保留）", "Y"),
        ("chemicaladdon:limestone(s) = Ca+2 + CO3-2",
         "CaCO3 溶解度（已实现：logK -8.3，ΔH -9.61 kJ/mol，rate 0.0001 结垢动力学）", "Y"),
    ]),
    ("SlakedLime", "熟石灰 Ca(OH)2", ["slaked_lime", "milk_of_lime"], [
        (None, "CaCO3 --高温煅烧--> CaO + CO2↑（石灰窑）", "P"),
        (None, "CaO + H2O -> Ca(OH)2（放热消化；缺 CaO 物种）", "P"),
        ("chemicaladdon:slaked_lime(s) = Ca+2 + 2 OH-1",
         "Ca(OH)2 溶解度（已实现：logK -5.2，ΔH -16.87 kJ/mol）", "Y"),
    ]),
    ("Quicklime", "生石灰 CaO", [], [
        (None, "CaCO3 --高温 900–1200°C--> CaO + CO2↑（石灰窑煅烧）", "N"),
    ]),
    ("AmmoniaWater", "氨水 NH3·H2O", ["ammonia_water"], [
        ("chemicaladdon:ammonia + water = NH4+1 + OH-1",
         "NH3 + H2O ⇌ NH3·H2O ⇌ NH4+ + OH-（已实现：Kb 1.8e-5）", "Y"),
    ]),
    ("Ammonia", "合成氨 NH3", ["ammonia"], [
        (None, "N2 + 3 H2 ⇌ 2 NH3（哈伯法：400–500°C、10–30 MPa、Fe 催化；缺 N2/H2 数据）", "P"),
        ("chemicaladdon:slaked_lime(s) + 2 NH4+1 = Ca+2 + 2 chemicaladdon:ammonia + 2 water",
         "Ca(OH)2 + 2 NH4Cl --△--> CaCl2 + 2 NH3↑ + 2 H2O（实验室制氨，已实现 Solvay step5 同构）", "Y"),
    ]),
    ("AmmoniumChloride", "氯化铵 NH4Cl", ["ammonium_chloride_solution"], [
        ("chemicaladdon:ammonia + H+1 = NH4+1",
         "NH3 + HCl -> NH4Cl（氨吸收氯化氢）", "Y"),
        ("Na+1 + HCO3-1 = chemicaladdon:sodium_bicarbonate(s)",
         "侯氏母液副产：NaCl + NH4HCO3 -> NaHCO3↓ + NH4Cl（与纯碱类重复，保留流程完整性）", "Y"),
        ("NH4+1 + Cl-1 = chemicaladdon:ammonium_chloride(s)",
         "冷却结晶（已实现：0°C 29.4 g/100g 溶解度曲线）", "Y"),
    ]),
    ("AmmoniumSulfate", "硫酸铵 (NH4)2SO4", ["ammonium_sulfate_solution"], [
        ("2 chemicaladdon:ammonia + 2 H+1 = 2 NH4+1",
         "2 NH3 + H2SO4 -> (NH4)2SO4（硫酸吸收氨）", "Y"),
    ]),
    ("AmmoniumNitrate", "硝酸铵 NH4NO3", ["ammonium_nitrate_solution"], [
        ("chemicaladdon:ammonia + H+1 = NH4+1",
         "NH3 + HNO3 -> NH4NO3（中和放热）", "Y"),
    ]),
    ("AmmoniumBicarbonate", "碳酸氢铵 NH4HCO3", [], [
        ("chemicaladdon:ammonia + chemicaladdon:carbon_dioxide + water = NH4+1 + HCO3-1",
         "NH3 + CO2 + H2O -> NH4HCO3（氨水碳化；与纯碱类重复，保留）", "Y"),
    ]),
    ("RefinedSalt", "精制食盐 NaCl", ["brine", "rock_salt"], [
        ("Ba+2 + SO4-2 = chemicaladdon:barium_sulfate(s)",
         "粗盐除 SO4²-：BaCl2 + Na2SO4 -> BaSO4↓ + 2 NaCl（已实现）", "Y"),
        ("Ca+2 + CO3-2 = chemicaladdon:limestone(s)",
         "除 Ca²+：CaCl2 + Na2CO3 -> CaCO3↓ + 2 NaCl（已实现）", "Y"),
        ("Ba+2 + CO3-2 = chemicaladdon:barium_carbonate(s)",
         "除过量 Ba²+：BaCl2 + Na2CO3 -> BaCO3↓ + 2 NaCl（已实现）", "Y"),
        ("Mg+2 + 2 OH-1 = chemicaladdon:magnesium_hydroxide(s)",
         "除 Mg²+：MgCl2 + 2 NaOH -> Mg(OH)2↓ + 2 NaCl（已实现）", "Y"),
        ("H+1 + OH-1 = water",
         "过量碱/纯碱用 HCl 回调（中和）", "Y"),
        (None, "精盐水蒸发结晶得 NaCl（rock_salt 溶解度曲线，已实现 brineEvaporatesToDrySalt；物理过程无反应式）", "Y"),
    ]),
    ("PotassiumNitrate", "硝酸钾 KNO3", ["potassium_nitrate_solution"], [
        (None, "NaNO3 + KCl ⇌ KNO3 + NaCl（复分解；热溶液浓缩、冷却结晶，溶解度差分离）", "Y"),
        ("K+1 + NO3-1 = chemicaladdon:potassium_nitrate(s)",
         "KNO3 冷却结晶（已实现：0°C 13.3 g/100g 曲线）", "Y"),
        ("H+1 + OH-1 = water",
         "KOH + HNO3 -> KNO3 + H2O（中和路线；中和步与其他类重复，保留）", "Y"),
    ]),
    ("CalciumChloride", "氯化钙 CaCl2", ["calcium_chloride_solution"], [
        ("chemicaladdon:limestone(s) + 2 H+1 = Ca+2 + chemicaladdon:carbon_dioxide + water",
         "CaCO3 + 2 HCl -> CaCl2 + CO2↑ + H2O（已实现 hclDescalesLimestone 同构）", "Y"),
        ("chemicaladdon:slaked_lime(s) + 2 NH4+1 = Ca+2 + 2 chemicaladdon:ammonia + 2 water",
         "索尔维废液回收：2 NH4Cl + Ca(OH)2 -> 2 NH3↑ + CaCl2 + 2 H2O（已实现 step5）", "Y"),
    ]),
    ("MagnesiumChloride", "氯化镁 MgCl2", ["magnesium_chloride_solution", "magnesium_carbonate"], [
        ("chemicaladdon:magnesium_hydroxide(s) + 2 H+1 = Mg+2 + 2 water",
         "海水提镁：Mg(OH)2 + 2 HCl -> MgCl2 + 2 H2O", "Y"),
        ("chemicaladdon:magnesium_carbonate(s) + 2 H+1 = Mg+2 + chemicaladdon:carbon_dioxide + water",
         "MgCO3 + 2 HCl -> MgCl2 + CO2↑ + H2O", "Y"),
    ]),
    ("CopperSulfate", "硫酸铜/胆矾 CuSO4·5H2O", ["copper_sulfate", "copper_sulfate_solution"], [
        (None, "Cu + 2 H2SO4(浓) --△--> CuSO4 + SO2↑ + 2 H2O（需 Cu(s) 物种）", "P"),
        (None, "CuO + H2SO4 -> CuSO4 + H2O（需 CuO 物种；注意：氧化物相会颠覆现有氢氧化物相竞争（亚稳相动力学缺失），见 docs/known_limitations.md §9）", "P"),
        ("chemicaladdon:copper_carbonate(s) + 4 H+1 = 2 Cu+2 + chemicaladdon:carbon_dioxide + 3 water",
         "Cu2(OH)2CO3 + 2 H2SO4 -> 2 CuSO4 + CO2↑ + 3 H2O（孔雀石酸溶）", "Y"),
        ("Cu+2 + SO4-2 = chemicaladdon:copper_sulfate(s)",
         "胆矾结晶：CuSO4 + 5 H2O ⇌ CuSO4·5H2O（溶解度曲线）", "Y"),
    ]),
    ("FerrousSulfate", "硫酸亚铁/绿矾 FeSO4·7H2O", ["ferrous_sulfate_solution"], [
        (None, "Fe + H2SO4(稀) -> FeSO4 + H2↑（需 Fe(s) 物种）", "P"),
        (None, "Fe + CuSO4 -> FeSO4 + Cu（置换法，湿法炼铜/废液回收；需 Fe(s)/Cu(s)）", "P"),
        ("Fe+2 + SO4-2 = chemicaladdon:ferrous_sulfate(s)",
         "绿矾结晶（溶解度曲线）", "Y"),
    ]),
    ("PotassiumAlum", "明矾 KAl(SO4)2·12H2O", ["potassium_alum_solution"], [
        ("chemicaladdon:aluminium_hydroxide(s) + 3 H+1 = Al+3 + 3 water",
         "2 Al(OH)3 + 3 H2SO4 -> Al2(SO4)3 + 6 H2O（氢氧化铝酸溶）", "Y"),
        (None, "Al2(SO4)3 + K2SO4 + 24 H2O -> 2 KAl(SO4)2·12H2O（复盐结晶，物理过程）", "Y"),
    ]),
    ("PotassiumThiocyanate", "硫氰酸钾 KSCN", ["potassium_thiocyanate_solution"], [
        ("NH4+1 + OH-1 = chemicaladdon:ammonia + water",
         "NH4SCN + KOH -> KSCN + NH3↑ + H2O（交换法：NH4+ + OH- -> NH3 + H2O，已实现）", "Y"),
        (None, "KCN + S --熔融--> KSCN（剧毒路线）", "N"),
    ]),
    ("SilverNitrate", "硝酸银 AgNO3", ["silver_nitrate_solution"], [
        (None, "Ag + 2 HNO3(浓) -> AgNO3 + NO2↑ + H2O（需 Ag(s) 物种）", "P"),
        (None, "3 Ag + 4 HNO3(稀) -> 3 AgNO3 + NO↑ + 2 H2O（需 Ag(s) 物种）", "P"),
        (None, "AgNO3 晶体溶于水配液（silver_nitrate_solution，物理过程）", "Y"),
    ]),
    ("SodiumHypochlorite", "次氯酸钠 NaClO", [], [
        ("chemicaladdon:chlorine + 2 OH-1 = Cl-1 + ClO-1 + water",
         "Cl2 + 2 NaOH -> NaCl + NaClO + H2O（冷稀碱，已实现：Cl2 碱性歧化 logK 15.3）", "Y"),
        (None, "3 Cl2 + 6 NaOH --热--> 5 NaCl + NaClO3 + 3 H2O（热浓碱歧化加深，生成氯酸盐）", "N"),
    ]),
    ("BleachingPowder", "漂白粉 Ca(ClO)2", [], [
        ("chemicaladdon:chlorine + 2 OH-1 = Cl-1 + ClO-1 + water",
         "2 Cl2 + 2 Ca(OH)2 -> Ca(ClO)2 + CaCl2 + 2 H2O（已实现：Cl2 碱性歧化；Ca(ClO)2/CaCl2 可溶，母液场景）", "Y"),
    ]),
    ("Hydrogen", "氢气 H2", [], [
        (None, "2 H2O --电解--> 2 H2↑ + O2↑（电解水；需电解扩展）", "N"),
        (None, "2 NaCl + 2 H2O --电解--> 2 NaOH + H2↑ + Cl2↑（氯碱副产）", "N"),
        (None, "Zn + 2 HCl -> ZnCl2 + H2↑；Fe + H2SO4(稀) -> FeSO4 + H2↑（金属+酸：金属溶解成自由电子的 Ksp 模型与电子总量守恒冲突，数学上不可行，见 docs/known_limitations.md §7）", "N"),
        (None, "C + H2O --高温--> CO + H2（水煤气）", "N"),
    ]),
    ("Oxygen", "氧气 O2", ["oxygen", "hydrogen_peroxide"], [
        (None, "2 H2O --电解--> 2 H2↑ + O2↑（电解水副产）", "N"),
        ("2 chemicaladdon:hydrogen_peroxide = 2 water + chemicaladdon:oxygen",
         "2 H2O2 -> 2 H2O + O2↑（已实现：hydrogen_peroxide 分解平衡，logK 20.9）", "Y"),
        (None, "2 KClO3 --MnO2/△--> 2 KCl + 3 O2↑（需 KClO3 数据）", "P"),
        (None, "空气液化分馏（物理法，工业主路径）", "N"),
    ]),
    ("Chlorine", "氯气 Cl2", ["chlorine"], [
        (None, "2 NaCl + 2 H2O --电解--> 2 NaOH + H2↑ + Cl2↑（氯碱阳极；需电解扩展）", "N"),
        (None, "MnO2 + 4 HCl(浓) --△--> MnCl2 + Cl2↑ + 2 H2O（需 MnO2 数据）", "P"),
    ]),
    ("CarbonDioxide", "二氧化碳 CO2", ["carbon_dioxide"], [
        (None, "CaCO3 --高温--> CaO + CO2↑（石灰窑；煅烧步）", "P"),
        ("chemicaladdon:limestone(s) + 2 H+1 = Ca+2 + chemicaladdon:carbon_dioxide + water",
         "CaCO3 + 2 HCl -> CaCl2 + CO2↑ + H2O（酸溶法，已实现；与氯化钙类重复，保留）", "Y"),
        ("CO3-2 + 2 H+1 = chemicaladdon:carbon_dioxide + water",
         "CO3²- + 2 H+ -> CO2↑ + H2O（酸+碳酸盐，已实现 hclPlusSodaAsh）", "Y"),
    ]),
    ("SulfurDioxide", "二氧化硫 SO2", ["sulfur_dioxide"], [
        (None, "S + O2 --点燃--> SO2（硫燃烧）", "N"),
        (None, "4 FeS2 + 11 O2 --高温--> 2 Fe2O3 + 8 SO2（黄铁矿焙烧）", "N"),
        ("SO3-2 + 2 H+1 = chemicaladdon:sulfur_dioxide + water",
         "Na2SO3 + 2 HCl -> 2 NaCl + SO2↑ + H2O（实验室，已实现：SO2 水合 pKa1/pKa2 组合 logK 8.99）", "Y"),
    ]),
    ("SulfurTrioxide", "三氧化硫 SO3", ["sulfur_trioxide"], [
        (None, "2 SO2 + O2 ⇌ 2 SO3（V2O5 催化，450°C；接触法中间步）", "N"),
        ("chemicaladdon:sulfur_trioxide + water = 2 H+1 + SO4-2",
         "SO3 + H2O -> H2SO4（吸收，已实现；与硫酸类重复，保留）", "Y"),
    ]),
    ("NitricOxide", "一氧化氮 NO", ["nitric_oxide"], [
        (None, "4 NH3 + 5 O2 --Pt/800°C--> 4 NO + 6 H2O（氨氧化；硝酸工艺中间体）", "N"),
        (None, "3 Cu + 8 HNO3(稀) -> 3 Cu(NO3)2 + 2 NO↑ + 4 H2O（需 Cu(s)）", "P"),
        (None, "N2 + O2 --放电/高温--> 2 NO（雷雨固氮）", "N"),
    ]),
    ("NitrogenDioxide", "二氧化氮 NO2", ["nitrogen_dioxide"], [
        (None, "2 NO + O2 -> 2 NO2（NO 氧化）", "N"),
        (None, "Cu + 4 HNO3(浓) -> Cu(NO3)2 + 2 NO2↑ + 2 H2O（需 Cu(s)）", "P"),
        ("3 chemicaladdon:nitrogen_dioxide + water = 2 H+1 + 2 NO3-1 + chemicaladdon:nitric_oxide",
         "3 NO2 + H2O -> 2 HNO3 + NO（吸收，已实现；与硝酸类重复，保留）", "Y"),
    ]),
    ("Acetylene", "乙炔 C2H2", [], [
        (None, "CaC2 + 2 H2O -> Ca(OH)2 + C2H2↑（电石水解；缺有机物种）", "N"),
    ]),
    ("CalciumCarbide", "电石 CaC2", [], [
        (None, "CaO + 3 C --电炉 2200°C--> CaC2 + CO↑（电石炉）", "N"),
    ]),
    ("IronSmelting", "铁 Fe（炼铁）", ["iron_metal"], [
        (None, "C + O2 --点燃--> CO2（焦炭燃烧）", "N"),
        (None, "CO2 + C --高温--> 2 CO（CO 再生）", "N"),
        (None, "Fe2O3 + 3 CO --高温--> 2 Fe + 3 CO2（高炉还原）", "N"),
    ]),
    ("Aluminium", "铝 Al", [], [
        (None, "Al2O3 + 2 NaOH -> 2 NaAlO2 + H2O（拜耳法碱溶）", "P"),
        ("2 [Al(OH)4]-1 + chemicaladdon:carbon_dioxide = 2 chemicaladdon:aluminium_hydroxide(s) + CO3-2 + water",
         "2 NaAlO2 + CO2 + 3 H2O -> 2 Al(OH)3↓ + Na2CO3（分解析出，引擎可表达）", "Y"),
        (None, "2 Al(OH)3 --煅烧--> Al2O3 + 3 H2O；2 Al2O3 --冰晶石/电解--> 4 Al + 3 O2↑（电解铝）", "N"),
    ]),
    ("Copper", "铜 Cu", ["copper_metal"], [
        (None, "Fe + CuSO4 -> FeSO4 + Cu（湿法炼铜/置换）", "P"),
        (None, "2 Cu2S + 3 O2 --高温--> 2 Cu2O + 2 SO2；Cu2S + 2 Cu2O --高温--> 6 Cu + SO2（火法炼铜）", "N"),
    ]),
    ("IronHydroxide", "氢氧化铁 Fe(OH)3", ["iron_hydroxide"], [
        ("Fe+3 + 3 OH-1 = chemicaladdon:iron_hydroxide(s)",
         "FeCl3 + 3 NaOH -> Fe(OH)3↓ + 3 NaCl（已实现 Ksp 竞争）", "Y"),
        ("Fe+3 + 3 OH-1 = chemicaladdon:iron_hydroxide(s)",
         "Fe2(SO4)3 + 6 NaOH -> 2 Fe(OH)3 + 3 Na2SO4（同式，流程完整性保留）", "Y"),
    ]),
    ("CopperHydroxide", "氢氧化铜 Cu(OH)2", ["copper_hydroxide"], [
        ("Cu+2 + 2 OH-1 = chemicaladdon:copper_hydroxide(s)",
         "CuSO4 + 2 NaOH -> Cu(OH)2↓ + Na2SO4（已实现）", "Y"),
    ]),
    ("ZincHydroxide", "氢氧化锌 Zn(OH)2", ["zinc_hydroxide"], [
        ("Zn+2 + 2 OH-1 = chemicaladdon:zinc_hydroxide(s)",
         "ZnSO4 + 2 NaOH -> Zn(OH)2↓ + Na2SO4（已实现）", "Y"),
        (None, "Zn(OH)2 + 2 NaOH -> Na2ZnO2 + 2 H2O（两性，过量碱溶解；缺锌酸盐物种，可仿 [Al(OH)4]- 补数据）", "P"),
    ]),
    ("AluminiumHydroxide", "氢氧化铝 Al(OH)3", ["aluminium_hydroxide"], [
        ("Al+3 + 3 OH-1 = chemicaladdon:aluminium_hydroxide(s)",
         "AlCl3 + 3 NaOH -> Al(OH)3↓ + 3 NaCl（已实现）", "Y"),
        ("chemicaladdon:aluminium_hydroxide(s) + OH-1 = [Al(OH)4]-1",
         "Al(OH)3 + OH- -> [Al(OH)4]-（两性溶解，已实现 logK 1.3）", "Y"),
    ]),
    ("MagnesiumHydroxide", "氢氧化镁 Mg(OH)2", ["magnesium_hydroxide"], [
        ("Mg+2 + 2 OH-1 = chemicaladdon:magnesium_hydroxide(s)",
         "MgCl2 + Ca(OH)2 -> Mg(OH)2↓ + CaCl2（海水提镁；已实现）", "Y"),
        ("Mg+2 + 2 OH-1 = chemicaladdon:magnesium_hydroxide(s)",
         "MgCl2 + 2 NaOH -> Mg(OH)2↓ + 2 NaCl（同式，流程完整性保留）", "Y"),
    ]),
    ("BariumCarbonate", "碳酸钡 BaCO3", ["barium_carbonate"], [
        ("Ba+2 + CO3-2 = chemicaladdon:barium_carbonate(s)",
         "BaCl2 + Na2CO3 -> BaCO3↓ + 2 NaCl（已实现）", "Y"),
    ]),
    ("BariumSulfate", "硫酸钡 BaSO4", ["barium_sulfate"], [
        ("Ba+2 + SO4-2 = chemicaladdon:barium_sulfate(s)",
         "BaCl2 + Na2SO4 -> BaSO4↓ + 2 NaCl（已实现，粗盐精炼）", "Y"),
    ]),
    ("SilverChloride", "氯化银 AgCl", ["silver_chloride"], [
        ("Ag+1 + Cl-1 = chemicaladdon:silver_chloride(s)",
         "AgNO3 + NaCl -> AgCl↓ + NaNO3（已实现）", "Y"),
    ]),
    ("SilverCarbonate", "碳酸银 Ag2CO3", ["silver_carbonate"], [
        ("2 Ag+1 + CO3-2 = chemicaladdon:silver_carbonate(s)",
         "2 AgNO3 + Na2CO3 -> Ag2CO3↓ + 2 NaNO3（已实现）", "Y"),
    ]),
    ("Malachite", "碱式碳酸铜 Cu2(OH)2CO3", ["copper_carbonate"], [
        ("2 Cu+2 + 2 CO3-2 + water = chemicaladdon:copper_carbonate(s) + chemicaladdon:carbon_dioxide",
         "2 CuSO4 + 2 Na2CO3 + H2O -> Cu2(OH)2CO3↓ + 2 Na2SO4 + CO2↑（已实现 malachite）", "Y"),
        (None, "2 Cu + O2 + CO2 + H2O --自然--> Cu2(OH)2CO3（铜绿自然生成）", "N"),
    ]),
    ("Gypsum", "石膏 CaSO4·2H2O", ["gypsum", "gypsum_slurry"], [
        ("Ca+2 + SO4-2 = chemicaladdon:gypsum(s)",
         "Ca(OH)2 + H2SO4 -> CaSO4 + 2 H2O（已实现：gypsum Ksp phreeqc -4.58）", "Y"),
        (None, "CaCO3 + H2SO4 -> CaSO4 + CO2↑ + H2O（酸解）", "P"),
        (None, "2 CaSO3 + O2 -> 2 CaSO4（烟气脱硫氧化）", "P"),
        ("Ca+2 + SO4-2 = chemicaladdon:gypsum(s)",
         "石膏沉淀/结晶（已实现：gypsum Ksp -4.58）", "Y"),
    ]),
    ("CalciumSulfite", "亚硫酸钙 CaSO3", ["calcium_sulfite_slurry", "calcium_sulfite"], [
        ("Ca+2 + SO3-2 = chemicaladdon:calcium_sulfite(s)",
         "Ca(OH)2 + SO2 -> CaSO3↓ + H2O（烟气脱硫，已实现：SO2 水合 + CaSO3 Ksp）", "Y"),
    ]),
    ("FerricChloride", "三氯化铁 FeCl3", ["ferric_chloride_solution"], [
        ("2 Fe+2 + chemicaladdon:chlorine = 2 Fe+3 + 2 Cl-1", "2 FeCl2 + Cl2 -> 2 FeCl3（e- 电对模型；§8 已根治：Solver 无条件激活 e- + 投影钳零；落地需 FeCl2 species 与 Cl2 e- 电对条目）", "P"),
        (None, "Fe2O3 + 6 HCl -> 2 FeCl3 + 3 H2O（氧化铁酸溶；需 Fe2O3 物种；氧化物相会颠覆氢氧化物竞争，见 docs/known_limitations.md §9）", "P"),
        (None, "FeCl3 溶液配制（ferric_chloride_solution，物理过程）", "Y"),
    ]),
    ("Urea", "尿素 CO(NH2)2", [], [
        (None, "2 NH3 + CO2 --20 MPa/180°C--> NH2COONH4 --脱水--> CO(NH2)2 + H2O（氨基甲酸铵中间体）", "N"),
    ]),
    ("Superphosphate", "过磷酸钙（磷肥）", ["calcium_phosphate", "phosphoric_acid"], [
        ("chemicaladdon:calcium_phosphate(s) + 2 H+1 = 3 Ca+2 + 2 H2PO4-1",
         "Ca3(PO4)2 + 2 H2SO4 -> Ca(H2PO4)2 + 2 CaSO4（已实现：磷灰石酸溶 + 磷酸质子化 + 石膏沉淀）", "Y"),
    ]),
    ("PotassiumPermanganate", "高锰酸钾 KMnO4", [], [
        (None, "2 MnO2 + 4 KOH + O2 --熔融--> 2 K2MnO4 + 2 H2O；3 K2MnO4 + 2 H2SO4 -> 2 KMnO4 + MnO2↓ + 2 K2SO4 + 2 H2O（缺锰物种）", "N"),
    ]),
    ("PotassiumChloride", "氯化钾 KCl", ["potassium_chloride_solution"], [
        (None, "光卤石 KCl·MgCl2·6H2O 溶解分离（溶解度差；物理过程）", "Y"),
        ("K+1 + Cl-1 = chemicaladdon:potassium_chloride(s)",
         "KCl 结晶（溶解度曲线）", "Y"),
    ]),
    ("ZincSulfate", "硫酸锌 ZnSO4", ["zinc_sulfate_solution"], [
        (None, "Zn + H2SO4(稀) -> ZnSO4 + H2↑（金属+e- 模型不可行，见 docs/known_limitations.md §7）", "N"),
        (None, "ZnO + H2SO4 -> ZnSO4 + H2O（需 ZnO）", "P"),
        (None, "ZnSO4 溶液配制（zinc_sulfate_solution，物理过程）", "Y"),
    ]),
]

TEMPLATE = '''package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * {cn} 生产流程（Track C 蓝图，见 PLAN.md）。
 *
 * <p>步骤在原料类之间不去重：为保持每条流程的完整性，与其它类重复的步骤原样保留。
 * 引擎反应式（reaction）遵循 {{@link com.yu1745.chemengine.Equilibrium#parse}} 语法；
 * 传统化学式与工艺条件见各步 note。
 */
public final class {name} {{

    /** 中文名/化学式。 */
    public static final String NAME = "{cn}";

    /** 对应的插件 species id（无对应物种时为空列表）。 */
    public static final List<String> PLUGIN_IDS = List.of({ids});

    /** 完整生产流程（步骤顺序即工艺顺序）。 */
    public static final List<ProcessStep> STEPS = List.of(
{steps}
    );

    private {name}() {{}}
}}
'''

STEP_TEMPLATE = '        {ctor}({rx}, "{note}")'

# Steps that are deliberately left BLANK (reaction = null) and kept as comments for
# later implementation: electrolysis, pyrometallurgy/calcination (high-temperature
# processes outside the engine's aqueous form), and the organic/specialty chemicals
# (urea, superphosphate, KMnO4). Flag N steps are always blank; flag P steps are
# blank too when they are electrolysis/calcination (vs. plain missing-data steps,
# which stay as actionable PARTIAL items).
PENDING_PREFIX = "【留空·待实现】"
PENDING_KEYWORDS = ("电解", "煅烧", "高温", "电炉", "熔融")

def annotate_pending(note: str, flag: str) -> str:
    if flag == "N" or (flag == "P" and any(k in note for k in PENDING_KEYWORDS)):
        return PENDING_PREFIX + " " + note
    return note


def java_escape(s: str) -> str:
    return s.replace("\\", "\\\\").replace('"', '\\"')


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    names = set()
    for suffix, cn, ids, steps in MATERIALS:
        name = suffix + "Process"
        if name in names:
            raise SystemExit(f"duplicate class name {name}")
        names.add(name)
        if not steps:
            raise SystemExit(f"{name} has no steps")
        id_list = ", ".join(f'"{i}"' for i in ids)
        step_lines = []
        for rx, note, flag in steps:
            ctor = {"Y": "ProcessStep.yes", "P": "ProcessStep.partial", "N": "ProcessStep.no"}[flag]
            note = annotate_pending(note, flag)
            if flag == "N":
                step_lines.append(f'        {ctor}("{java_escape(note)}")')
            else:
                rx_java = "null" if rx is None else f'"{java_escape(rx)}"'
                step_lines.append(STEP_TEMPLATE.format(ctor=ctor, rx=rx_java, note=java_escape(note)))
        src = TEMPLATE.format(cn=cn, name=name, ids=id_list, steps=",\n".join(step_lines))
        (OUT / f"{name}.java").write_text(src, encoding="utf-8")
    print(f"generated {len(MATERIALS)} process classes in {OUT}")


if __name__ == "__main__":
    main()
