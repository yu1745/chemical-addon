package com.yu1745.chemicaladdon.registry;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.FluidEntry;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.fluid.ChemFluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;

public class AllFluids {
	public static final CreateRegistrate REGISTRATE = ChemicalAddon.registrate();


	public static final FluidEntry<ForgeFlowingFluid.Flowing> AIR = REGISTRATE.standardFluid("air",
			(props, still, flow) -> new ChemFluidType(props, still, flow, true))
		.lang("Air")
		.properties(b -> b.density(-500)
			.viscosity(200)
			.temperature(293))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> HYDROGEN = REGISTRATE.standardFluid("hydrogen",
			(props, still, flow) -> new ChemFluidType(props, still, flow, true))
		.lang("Hydrogen")
		.properties(b -> b.density(-100)
			.viscosity(200)
			.temperature(293))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> OXYGEN = REGISTRATE.standardFluid("oxygen",
			(props, still, flow) -> new ChemFluidType(props, still, flow, true))
		.lang("Oxygen")
		.properties(b -> b.density(-200)
			.viscosity(200)
			.temperature(293))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> NITROGEN = REGISTRATE.standardFluid("nitrogen",
			(props, still, flow) -> new ChemFluidType(props, still, flow, true))
		.lang("Nitrogen")
		.properties(b -> b.density(-200)
			.viscosity(200)
			.temperature(293))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> CHLORINE = REGISTRATE.standardFluid("chlorine",
			(props, still, flow) -> new ChemFluidType(props, still, flow, true))
		.lang("Chlorine")
		.properties(b -> b.density(-400)
			.viscosity(200)
			.temperature(293))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> CARBON_DIOXIDE = REGISTRATE.standardFluid("carbon_dioxide",
			(props, still, flow) -> new ChemFluidType(props, still, flow, true))
		.lang("Carbon Dioxide")
		.properties(b -> b.density(-300)
			.viscosity(200)
			.temperature(293))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> CARBON_MONOXIDE = REGISTRATE.standardFluid("carbon_monoxide",
			(props, still, flow) -> new ChemFluidType(props, still, flow, true))
		.lang("Carbon Monoxide")
		.properties(b -> b.density(-250)
			.viscosity(200)
			.temperature(293))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> SULFUR_DIOXIDE = REGISTRATE.standardFluid("sulfur_dioxide",
			(props, still, flow) -> new ChemFluidType(props, still, flow, true))
		.lang("Sulfur Dioxide")
		.properties(b -> b.density(-400)
			.viscosity(200)
			.temperature(293))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> SULFUR_TRIOXIDE = REGISTRATE.standardFluid("sulfur_trioxide",
			(props, still, flow) -> new ChemFluidType(props, still, flow, true))
		.lang("Sulfur Trioxide")
		.properties(b -> b.density(-500)
			.viscosity(200)
			.temperature(293))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> NITRIC_OXIDE = REGISTRATE.standardFluid("nitric_oxide",
			(props, still, flow) -> new ChemFluidType(props, still, flow, true))
		.lang("Nitric Oxide")
		.properties(b -> b.density(-350)
			.viscosity(200)
			.temperature(293))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> NITROGEN_DIOXIDE = REGISTRATE.standardFluid("nitrogen_dioxide",
			(props, still, flow) -> new ChemFluidType(props, still, flow, true))
		.lang("Nitrogen Dioxide")
		.properties(b -> b.density(-400)
			.viscosity(200)
			.temperature(293))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> AMMONIA = REGISTRATE.standardFluid("ammonia",
			(props, still, flow) -> new ChemFluidType(props, still, flow, true))
		.lang("Ammonia")
		.properties(b -> b.density(-350)
			.viscosity(200)
			.temperature(293))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> HYDROGEN_CHLORIDE = REGISTRATE.standardFluid("hydrogen_chloride",
			(props, still, flow) -> new ChemFluidType(props, still, flow, true))
		.lang("Hydrogen Chloride")
		.properties(b -> b.density(-400)
			.viscosity(200)
			.temperature(293))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> WATER = REGISTRATE.standardFluid("water",
			(props, still, flow) -> new ChemFluidType(props, still, flow, false))
		.lang("Water")
		.properties(b -> b.density(1000)
			.viscosity(1000)
			.temperature(300))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> BRINE = REGISTRATE.standardFluid("brine",
			(props, still, flow) -> new ChemFluidType(props, still, flow, false))
		.lang("Saturated Brine")
		.properties(b -> b.density(1200)
			.viscosity(1300)
			.temperature(300))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> AMMONIATED_BRINE = REGISTRATE.standardFluid("ammoniated_brine",
			(props, still, flow) -> new ChemFluidType(props, still, flow, false))
		.lang("Ammoniated Brine")
		.properties(b -> b.density(1150)
			.viscosity(1200)
			.temperature(300))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> DILUTE_HYDROCHLORIC_ACID = REGISTRATE.standardFluid("dilute_hydrochloric_acid",
			(props, still, flow) -> new ChemFluidType(props, still, flow, false))
		.lang("Dilute Hydrochloric Acid")
		.properties(b -> b.density(1050)
			.viscosity(1000)
			.temperature(300))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> CONCENTRATED_HYDROCHLORIC_ACID = REGISTRATE.standardFluid("concentrated_hydrochloric_acid",
			(props, still, flow) -> new ChemFluidType(props, still, flow, false))
		.lang("Concentrated Hydrochloric Acid")
		.properties(b -> b.density(1190)
			.viscosity(1100)
			.temperature(300))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> DILUTE_SULFURIC_ACID = REGISTRATE.standardFluid("dilute_sulfuric_acid",
			(props, still, flow) -> new ChemFluidType(props, still, flow, false))
		.lang("Dilute Sulfuric Acid")
		.properties(b -> b.density(1080)
			.viscosity(1000)
			.temperature(300))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> CONCENTRATED_SULFURIC_ACID = REGISTRATE.standardFluid("concentrated_sulfuric_acid",
			(props, still, flow) -> new ChemFluidType(props, still, flow, false))
		.lang("Concentrated Sulfuric Acid")
		.properties(b -> b.density(1840)
			.viscosity(2000)
			.temperature(320))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> OLEUM = REGISTRATE.standardFluid("oleum",
			(props, still, flow) -> new ChemFluidType(props, still, flow, false))
		.lang("Oleum")
		.properties(b -> b.density(1900)
			.viscosity(2500)
			.temperature(320))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> DILUTE_NITRIC_ACID = REGISTRATE.standardFluid("dilute_nitric_acid",
			(props, still, flow) -> new ChemFluidType(props, still, flow, false))
		.lang("Dilute Nitric Acid")
		.properties(b -> b.density(1060)
			.viscosity(1000)
			.temperature(300))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> CONCENTRATED_NITRIC_ACID = REGISTRATE.standardFluid("concentrated_nitric_acid",
			(props, still, flow) -> new ChemFluidType(props, still, flow, false))
		.lang("Concentrated Nitric Acid")
		.properties(b -> b.density(1400)
			.viscosity(1500)
			.temperature(300))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> CAUSTIC_SODA_SOLUTION = REGISTRATE.standardFluid("caustic_soda_solution",
			(props, still, flow) -> new ChemFluidType(props, still, flow, false))
		.lang("Caustic Soda Solution")
		.properties(b -> b.density(1300)
			.viscosity(1500)
			.temperature(300))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> SODA_ASH_SOLUTION = REGISTRATE.standardFluid("soda_ash_solution",
			(props, still, flow) -> new ChemFluidType(props, still, flow, false))
		.lang("Soda Ash Solution")
		.properties(b -> b.density(1100)
			.viscosity(1000)
			.temperature(300))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> AMMONIUM_CHLORIDE_SOLUTION = REGISTRATE.standardFluid("ammonium_chloride_solution",
			(props, still, flow) -> new ChemFluidType(props, still, flow, false))
		.lang("Ammonium Chloride Solution")
		.properties(b -> b.density(1050)
			.viscosity(1000)
			.temperature(300))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> CALCIUM_CHLORIDE_SOLUTION = REGISTRATE.standardFluid("calcium_chloride_solution",
			(props, still, flow) -> new ChemFluidType(props, still, flow, false))
		.lang("Calcium Chloride Solution")
		.properties(b -> b.density(1200)
			.viscosity(1200)
			.temperature(300))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> AMMONIA_WATER = REGISTRATE.standardFluid("ammonia_water",
			(props, still, flow) -> new ChemFluidType(props, still, flow, false))
		.lang("Ammonia Water")
		.properties(b -> b.density(950)
			.viscosity(1000)
			.temperature(300))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> MILK_OF_LIME = REGISTRATE.standardFluid("milk_of_lime",
			(props, still, flow) -> new ChemFluidType(props, still, flow, false))
		.lang("Milk of Lime")
		.properties(b -> b.density(1150)
			.viscosity(3000)
			.temperature(300))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> BLEACH_SOLUTION = REGISTRATE.standardFluid("bleach_solution",
			(props, still, flow) -> new ChemFluidType(props, still, flow, false))
		.lang("Bleach Solution")
		.properties(b -> b.density(1100)
			.viscosity(1000)
			.temperature(300))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> PHOSPHORIC_ACID = REGISTRATE.standardFluid("phosphoric_acid",
			(props, still, flow) -> new ChemFluidType(props, still, flow, false))
		.lang("Phosphoric Acid")
		.properties(b -> b.density(1700)
			.viscosity(1800)
			.temperature(300))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> AMMONIUM_SULFATE_SOLUTION = REGISTRATE.standardFluid("ammonium_sulfate_solution",
			(props, still, flow) -> new ChemFluidType(props, still, flow, false))
		.lang("Ammonium Sulfate Solution")
		.properties(b -> b.density(1150)
			.viscosity(1100)
			.temperature(300))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> AMMONIUM_NITRATE_SOLUTION = REGISTRATE.standardFluid("ammonium_nitrate_solution",
			(props, still, flow) -> new ChemFluidType(props, still, flow, false))
		.lang("Ammonium Nitrate Solution")
		.properties(b -> b.density(1200)
			.viscosity(1100)
			.temperature(300))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> SODIUM_ALUMINATE_SOLUTION = REGISTRATE.standardFluid("sodium_aluminate_solution",
			(props, still, flow) -> new ChemFluidType(props, still, flow, false))
		.lang("Sodium Aluminate Solution")
		.properties(b -> b.density(1250)
			.viscosity(1400)
			.temperature(330))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> SODIUM_BICARBONATE_SLURRY = REGISTRATE.standardFluid("sodium_bicarbonate_slurry",
			(props, still, flow) -> new ChemFluidType(props, still, flow, false))
		.lang("Sodium Bicarbonate Slurry")
		.properties(b -> b.density(1300)
			.viscosity(3000)
			.temperature(300))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> GYPSUM_SLURRY = REGISTRATE.standardFluid("gypsum_slurry",
			(props, still, flow) -> new ChemFluidType(props, still, flow, false))
		.lang("Gypsum Slurry")
		.properties(b -> b.density(1400)
			.viscosity(4000)
			.temperature(300))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> CALCIUM_SULFITE_SLURRY = REGISTRATE.standardFluid("calcium_sulfite_slurry",
			(props, still, flow) -> new ChemFluidType(props, still, flow, false))
		.lang("Calcium Sulfite Slurry")
		.properties(b -> b.density(1400)
			.viscosity(4000)
			.temperature(300))
		.register();

	public static final FluidEntry<ForgeFlowingFluid.Flowing> THERMAL_OIL = REGISTRATE.standardFluid("thermal_oil",
			(props, still, flow) -> new ChemFluidType(props, still, flow, false))
		.lang("Thermal Oil")
		.properties(b -> b.density(900)
			.viscosity(1500)
			.temperature(400))
		.register();

	public static void register() {
	}
}
