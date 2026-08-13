package com.yu1745.chemicaladdon.registry;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.item.FluidVialItem;
import com.yu1745.chemicaladdon.item.SolutionBucketItem;

import net.minecraft.resources.ResourceLocation;

public class AllContainers {

	public static final CreateRegistrate REGISTRATE = ChemicalAddon.registrate();

	/** NBT-carrying sample container: temperature + mixture composition survive. */
	public static final ItemEntry<FluidVialItem> FLUID_VIAL =
		REGISTRATE.item("fluid_vial", FluidVialItem::new)
			.lang("Sample Vial")
			// the model is a hand-authored forge:fluid_container JSON (see
			// assets/chemicaladdon/models/item/fluid_vial.json); suppress the
			// default item/generated model Registrate would otherwise datagen.
			.model((ctx, prov) -> {})
			.register();

	/** (species id, en name) — mirrored by tools/gen_species.py SOLUTIONS for models + zh_cn. */
	private static final String[][] SOLUTION_SPECIES = {
		{ "sulfuric_acid", "Sulfuric Acid" },
		{ "hydrochloric_acid", "Hydrochloric Acid" },
		{ "nitric_acid", "Nitric Acid" },
		{ "brine", "Saturated Brine" },
		{ "caustic_soda_solution", "Caustic Soda Solution" },
		{ "soda_ash_solution", "Soda Ash Solution" },
		{ "ammonium_chloride_solution", "Ammonium Chloride Solution" },
		{ "calcium_chloride_solution", "Calcium Chloride Solution" },
		{ "ammonia_water", "Ammonia Water" },
		{ "ammonium_sulfate_solution", "Ammonium Sulfate Solution" },
		{ "ammonium_nitrate_solution", "Ammonium Nitrate Solution" },
	};

	/** (species id, en name) — mirrored by tools/gen_species.py SLURRIES for models + zh_cn. */
	private static final String[][] SLURRY_SPECIES = {
		{ "milk_of_lime", "Milk of Lime" },
		{ "gypsum_slurry", "Gypsum Slurry" },
		{ "sodium_bicarbonate_slurry", "Sodium Bicarbonate Slurry" },
		{ "calcium_sulfite_slurry", "Calcium Sulfite Slurry" },
	};

	/**
	 * Creative "packed mixture" buckets: one per solution <b>and</b> slurry mode.
	 * Each pre-fills a 1000 mB {@code Mixture} (a solution = ion signature + water;
	 * a slurry = suspended solid + water) — neither is a registered fluid, so a
	 * standard bucket cannot carry them.
	 */
	public static final List<ItemEntry<SolutionBucketItem>> SOLUTION_BUCKETS = registerBuckets(SOLUTION_SPECIES);
	public static final List<ItemEntry<SolutionBucketItem>> SLURRY_BUCKETS = registerBuckets(SLURRY_SPECIES);

	private static List<ItemEntry<SolutionBucketItem>> registerBuckets(String[][] species) {
		List<ItemEntry<SolutionBucketItem>> out = new ArrayList<>();
		for (String[] entry : species) {
			ResourceLocation speciesId = new ResourceLocation(ChemicalAddon.MODID, entry[0]);
			ItemEntry<SolutionBucketItem> item = REGISTRATE
				.item(entry[0] + "_bucket", p -> new SolutionBucketItem(p, speciesId))
				.lang(entry[1] + " Bucket")
				// model JSON written by tools/gen_species.py (forge:fluid_container)
				.model((ctx, prov) -> {})
				.register();
			out.add(item);
		}
		return out;
	}

	public static void register() {
	}
}
