package com.yu1745.chemicaladdon.registry;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.FluidEntry;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.fluid.ChemFluidType;
import com.yu1745.chemicaladdon.fluid.MixtureFluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;

public class AllFluids {
	public static final CreateRegistrate REGISTRATE = ChemicalAddon.registrate();


	public static final FluidEntry<ForgeFlowingFluid.Flowing> AIR = REGISTRATE.standardFluid("air",
			(props, still, flow) -> new ChemFluidType(props, still, flow, true))
		.lang("Air")
		.properties(b -> b.density(-500)
			.viscosity(200)
			.temperature(293))
		.source(ForgeFlowingFluid.Source::new)
		.block()
		.lang("Air")
		.build()
		.bucket().lang("Air Bucket").model((ctx, prov) -> {}).build()
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> HYDROGEN = REGISTRATE.standardFluid("hydrogen",
			(props, still, flow) -> new ChemFluidType(props, still, flow, true))
		.lang("Hydrogen")
		.properties(b -> b.density(-100)
			.viscosity(200)
			.temperature(293))
		.source(ForgeFlowingFluid.Source::new)
		.block()
		.lang("Hydrogen")
		.build()
		.bucket().lang("Hydrogen Bucket").model((ctx, prov) -> {}).build()
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> OXYGEN = REGISTRATE.standardFluid("oxygen",
			(props, still, flow) -> new ChemFluidType(props, still, flow, true))
		.lang("Oxygen")
		.properties(b -> b.density(-200)
			.viscosity(200)
			.temperature(293))
		.source(ForgeFlowingFluid.Source::new)
		.block()
		.lang("Oxygen")
		.build()
		.bucket().lang("Oxygen Bucket").model((ctx, prov) -> {}).build()
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> NITROGEN = REGISTRATE.standardFluid("nitrogen",
			(props, still, flow) -> new ChemFluidType(props, still, flow, true))
		.lang("Nitrogen")
		.properties(b -> b.density(-200)
			.viscosity(200)
			.temperature(293))
		.source(ForgeFlowingFluid.Source::new)
		.block()
		.lang("Nitrogen")
		.build()
		.bucket().lang("Nitrogen Bucket").model((ctx, prov) -> {}).build()
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> CHLORINE = REGISTRATE.standardFluid("chlorine",
			(props, still, flow) -> new ChemFluidType(props, still, flow, true))
		.lang("Chlorine")
		.properties(b -> b.density(-400)
			.viscosity(200)
			.temperature(293))
		.source(ForgeFlowingFluid.Source::new)
		.block()
		.lang("Chlorine")
		.build()
		.bucket().lang("Chlorine Bucket").model((ctx, prov) -> {}).build()
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> CARBON_DIOXIDE = REGISTRATE.standardFluid("carbon_dioxide",
			(props, still, flow) -> new ChemFluidType(props, still, flow, true))
		.lang("Carbon Dioxide")
		.properties(b -> b.density(-300)
			.viscosity(200)
			.temperature(293))
		.source(ForgeFlowingFluid.Source::new)
		.block()
		.lang("Carbon Dioxide")
		.build()
		.bucket().lang("Carbon Dioxide Bucket").model((ctx, prov) -> {}).build()
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> CARBON_MONOXIDE = REGISTRATE.standardFluid("carbon_monoxide",
			(props, still, flow) -> new ChemFluidType(props, still, flow, true))
		.lang("Carbon Monoxide")
		.properties(b -> b.density(-250)
			.viscosity(200)
			.temperature(293))
		.source(ForgeFlowingFluid.Source::new)
		.block()
		.lang("Carbon Monoxide")
		.build()
		.bucket().lang("Carbon Monoxide Bucket").model((ctx, prov) -> {}).build()
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> SULFUR_DIOXIDE = REGISTRATE.standardFluid("sulfur_dioxide",
			(props, still, flow) -> new ChemFluidType(props, still, flow, true))
		.lang("Sulfur Dioxide")
		.properties(b -> b.density(-400)
			.viscosity(200)
			.temperature(293))
		.source(ForgeFlowingFluid.Source::new)
		.block()
		.lang("Sulfur Dioxide")
		.build()
		.bucket().lang("Sulfur Dioxide Bucket").model((ctx, prov) -> {}).build()
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> SULFUR_TRIOXIDE = REGISTRATE.standardFluid("sulfur_trioxide",
			(props, still, flow) -> new ChemFluidType(props, still, flow, true))
		.lang("Sulfur Trioxide")
		.properties(b -> b.density(-500)
			.viscosity(200)
			.temperature(293))
		.source(ForgeFlowingFluid.Source::new)
		.block()
		.lang("Sulfur Trioxide")
		.build()
		.bucket().lang("Sulfur Trioxide Bucket").model((ctx, prov) -> {}).build()
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> NITRIC_OXIDE = REGISTRATE.standardFluid("nitric_oxide",
			(props, still, flow) -> new ChemFluidType(props, still, flow, true))
		.lang("Nitric Oxide")
		.properties(b -> b.density(-350)
			.viscosity(200)
			.temperature(293))
		.source(ForgeFlowingFluid.Source::new)
		.block()
		.lang("Nitric Oxide")
		.build()
		.bucket().lang("Nitric Oxide Bucket").model((ctx, prov) -> {}).build()
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> NITROGEN_DIOXIDE = REGISTRATE.standardFluid("nitrogen_dioxide",
			(props, still, flow) -> new ChemFluidType(props, still, flow, true))
		.lang("Nitrogen Dioxide")
		.properties(b -> b.density(-400)
			.viscosity(200)
			.temperature(293))
		.source(ForgeFlowingFluid.Source::new)
		.block()
		.lang("Nitrogen Dioxide")
		.build()
		.bucket().lang("Nitrogen Dioxide Bucket").model((ctx, prov) -> {}).build()
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> AMMONIA = REGISTRATE.standardFluid("ammonia",
			(props, still, flow) -> new ChemFluidType(props, still, flow, true))
		.lang("Ammonia")
		.properties(b -> b.density(-350)
			.viscosity(200)
			.temperature(293))
		.source(ForgeFlowingFluid.Source::new)
		.block()
		.lang("Ammonia")
		.build()
		.bucket().lang("Ammonia Bucket").model((ctx, prov) -> {}).build()
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> HYDROGEN_CHLORIDE = REGISTRATE.standardFluid("hydrogen_chloride",
			(props, still, flow) -> new ChemFluidType(props, still, flow, true))
		.lang("Hydrogen Chloride")
		.properties(b -> b.density(-400)
			.viscosity(200)
			.temperature(293))
		.source(ForgeFlowingFluid.Source::new)
		.block()
		.lang("Hydrogen Chloride")
		.build()
		.bucket().lang("Hydrogen Chloride Bucket").model((ctx, prov) -> {}).build()
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> THERMAL_OIL = REGISTRATE.standardFluid("thermal_oil",
			(props, still, flow) -> new ChemFluidType(props, still, flow, false))
		.lang("Thermal Oil")
		.properties(b -> b.density(900)
			.viscosity(1500)
			.temperature(400))
		.source(ForgeFlowingFluid.Source::new)
		.block()
		.lang("Thermal Oil")
		.build()
		.bucket().lang("Thermal Oil Bucket").model((ctx, prov) -> {}).build()
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> MIXTURE = REGISTRATE.standardFluid("mixture",
			(props, still, flow) -> new MixtureFluidType(props, still, flow))
		.lang("Mixture")
		.properties(b -> b.density(1000)
			.viscosity(1000)
			.temperature(300))
		.source(ForgeFlowingFluid.Source::new)
		.bucket().lang("Mixture Bucket").model((ctx, prov) -> {}).build()
		.register();

	public static void register() {
	}
}
