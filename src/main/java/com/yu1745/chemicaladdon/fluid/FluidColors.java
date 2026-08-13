package com.yu1745.chemicaladdon.fluid;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import com.yu1745.chemicaladdon.ChemicalAddon;

/** Species colour table (single source of truth: tools/gen_species.py FLUIDS).
 *  Used to weight-blend mixture colours at runtime. ARGB, fully opaque. */
public final class FluidColors {
	private FluidColors() {}
	private static final Map<ResourceLocation, Integer> COLORS = new HashMap<>();
	static {
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "air"), 0xFFC8D8E8);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "hydrogen"), 0xFFD8E0F0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "oxygen"), 0xFFB0C8E8);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "nitrogen"), 0xFFC0D0E0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "chlorine"), 0xFF9ED44D);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "carbon_dioxide"), 0xFFB8B8B0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "carbon_monoxide"), 0xFFC0C0C8);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "sulfur_dioxide"), 0xFFE0E0B0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "sulfur_trioxide"), 0xFFE8D8C8);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "nitric_oxide"), 0xFFC8A8A8);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "nitrogen_dioxide"), 0xFFB84A2A);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "ammonia"), 0xFFC8E0C8);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "hydrogen_chloride"), 0xFFD8E8D0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "water"), 0xFF3F76E4);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "brine"), 0xFF8FB4E8);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "ammoniated_brine"), 0xFFA8C8E8);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "dilute_hydrochloric_acid"), 0xFFD8F0D8);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "concentrated_hydrochloric_acid"), 0xFFC8E8C0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "dilute_sulfuric_acid"), 0xFFE8E8D0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "concentrated_sulfuric_acid"), 0xFFF0E0B0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "oleum"), 0xFFF0C890);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "dilute_nitric_acid"), 0xFFE8E8F0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "concentrated_nitric_acid"), 0xFFE8D8A0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "caustic_soda_solution"), 0xFFD8E0F0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "soda_ash_solution"), 0xFFE0E8E0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "ammonium_chloride_solution"), 0xFFD0E0D0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "calcium_chloride_solution"), 0xFFE0E8F0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "ammonia_water"), 0xFFC8E0D0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "milk_of_lime"), 0xFFE8E8E0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "bleach_solution"), 0xFFC8F0E8);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "phosphoric_acid"), 0xFFE8E0D8);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "ammonium_sulfate_solution"), 0xFFE0E8D8);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "ammonium_nitrate_solution"), 0xFFE0E8E8);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "sodium_aluminate_solution"), 0xFFD8E0E8);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "sodium_bicarbonate_slurry"), 0xFFE0E8E0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "gypsum_slurry"), 0xFFE0E0D0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "calcium_sulfite_slurry"), 0xFFD8E8D8);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "thermal_oil"), 0xFFC89030);
	}

	/** @return the species' ARGB colour, or -1 (white) if unknown. */
	public static int of(ResourceLocation id) {
		return COLORS.getOrDefault(id, 0xFFFFFFFF);
	}
}
