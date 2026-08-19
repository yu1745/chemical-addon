package com.yu1745.chemengine.industrial;

import java.util.List;

/**
 * Track C 聚合清单：插件全部化工原料（及常见工业原料）的生产流程蓝图类。
 *
 * <p>每个原料一个 {@code XxxProcess} 类（由 tools/gen_process_blueprints.py 生成，
 * 手工改动请改生成器并重新生成）。契约（由 IndustrialProcessBlueprintTest 校验）：
 * 每个类必须提供 {@code NAME}（中文名）、{@code PLUGIN_IDS}（对应 species id 列表）、
 * {@code STEPS}（非空流程步骤）；所有插件物种 id（除 thermal_oil）必须被至少一个类覆盖。
 */
public final class IndustrialProcesses {

    /** 全部流程蓝图类（新增原料时在此登记，并同步改生成器数据表）。 */
    public static final List<Class<?>> ALL = List.of(
        HydrochloricAcidProcess.class,
        SulfuricAcidProcess.class,
        NitricAcidProcess.class,
        CausticSodaProcess.class,
        SodaAshProcess.class,
        SodiumBicarbonateProcess.class,
        LimestoneProcess.class,
        SlakedLimeProcess.class,
        QuicklimeProcess.class,
        AmmoniaWaterProcess.class,
        AmmoniaProcess.class,
        AmmoniumChlorideProcess.class,
        AmmoniumSulfateProcess.class,
        AmmoniumNitrateProcess.class,
        AmmoniumBicarbonateProcess.class,
        RefinedSaltProcess.class,
        PotassiumNitrateProcess.class,
        CalciumChlorideProcess.class,
        MagnesiumChlorideProcess.class,
        CopperSulfateProcess.class,
        FerrousSulfateProcess.class,
        PotassiumAlumProcess.class,
        PotassiumThiocyanateProcess.class,
        SilverNitrateProcess.class,
        SodiumHypochloriteProcess.class,
        BleachingPowderProcess.class,
        HydrogenProcess.class,
        OxygenProcess.class,
        ChlorineProcess.class,
        CarbonDioxideProcess.class,
        SulfurDioxideProcess.class,
        SulfurTrioxideProcess.class,
        NitricOxideProcess.class,
        NitrogenDioxideProcess.class,
        AcetyleneProcess.class,
        CalciumCarbideProcess.class,
        IronSmeltingProcess.class,
        AluminiumProcess.class,
        CopperProcess.class,
        IronHydroxideProcess.class,
        CopperHydroxideProcess.class,
        ZincHydroxideProcess.class,
        AluminiumHydroxideProcess.class,
        MagnesiumHydroxideProcess.class,
        BariumCarbonateProcess.class,
        BariumSulfateProcess.class,
        SilverChlorideProcess.class,
        SilverCarbonateProcess.class,
        MalachiteProcess.class,
        GypsumProcess.class,
        CalciumSulfiteProcess.class,
        FerricChlorideProcess.class,
        UreaProcess.class,
        SuperphosphateProcess.class,
        PotassiumPermanganateProcess.class,
        PotassiumChlorideProcess.class,
        ZincSulfateProcess.class
    );

    /** 非化学/纯物理中间体：不要求蓝图覆盖的 species id。 */
    public static final List<String> NOT_REQUIRED = List.of("thermal_oil");

    private IndustrialProcesses() {}
}
