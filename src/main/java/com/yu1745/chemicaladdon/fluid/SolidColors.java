package com.yu1745.chemicaladdon.fluid;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import com.yu1745.chemicaladdon.ChemicalAddon;

/** Solid species colour table (single source of truth: tools/gen_species.py SOLIDS).
 *  Used to weight-blend suspended-solid tint in a mixture. ARGB, fully opaque. */
public final class SolidColors {
	private SolidColors() {}
	private static final Map<ResourceLocation, Integer> COLORS = new HashMap<>();
	static {
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "rock_salt"), 0xFFE8E0D0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "limestone"), 0xFFD0D0C0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "quicklime"), 0xFFE0E0E0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "slaked_lime"), 0xFFE8E8E0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "sodium_bicarbonate"), 0xFFE0E8E0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "soda_ash"), 0xFFF0F0F0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "gypsum"), 0xFFE0E0D0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "sulfur"), 0xFFD8D838);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "bauxite"), 0xFFB85030);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "aluminium_hydroxide"), 0xFFE8E8E8);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "alumina"), 0xFFF0F0F0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "phosphate_rock"), 0xFFC8B088);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "phosphogypsum"), 0xFFD8D0C0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "ammonium_sulfate"), 0xFFE8E8E0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "ammonium_nitrate"), 0xFFE8E8F0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "urea"), 0xFFF0F0F0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "calcium_chloride"), 0xFFE0E8F0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "calcium_sulfite"), 0xFFE0E8E0);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "copper_sulfate"), 0xFF2285D6);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "copper_carbonate"), 0xFF2FA896);
		COLORS.put(new ResourceLocation(ChemicalAddon.MODID, "filter_cake"), 0xFF908878);
	}

	/** @return the solid's ARGB colour, or -1 (white) if unknown. */
	public static int of(ResourceLocation id) {
		return COLORS.getOrDefault(id, 0xFFFFFFFF);
	}
}
