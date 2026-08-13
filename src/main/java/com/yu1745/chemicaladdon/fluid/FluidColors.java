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
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "thermal_oil"), 0xFFC89030);
	}

	/** @return the species' ARGB colour, or -1 (white) if unknown. */
	public static int of(ResourceLocation id) {
		return COLORS.getOrDefault(id, 0xFFFFFFFF);
	}
}
