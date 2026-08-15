package com.yu1745.chemicaladdon.registry;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.item.MixedResidueItem;
import com.yu1745.chemicaladdon.item.TestPaperItem;
import net.minecraft.world.item.Item;

public class AllItems {
	public static final CreateRegistrate REGISTRATE = ChemicalAddon.registrate();

	public static final ItemEntry<Item> ROCK_SALT =
		REGISTRATE.item("rock_salt", Item::new)
			.lang("Rock Salt")
			.register();
	public static final ItemEntry<Item> LIMESTONE =
		REGISTRATE.item("limestone", Item::new)
			.lang("Limestone")
			.register();
	public static final ItemEntry<Item> QUICKLIME =
		REGISTRATE.item("quicklime", Item::new)
			.lang("Quicklime")
			.register();
	public static final ItemEntry<Item> SLAKED_LIME =
		REGISTRATE.item("slaked_lime", Item::new)
			.lang("Slaked Lime")
			.register();
	public static final ItemEntry<Item> SODIUM_BICARBONATE =
		REGISTRATE.item("sodium_bicarbonate", Item::new)
			.lang("Sodium Bicarbonate")
			.register();
	public static final ItemEntry<Item> SODA_ASH =
		REGISTRATE.item("soda_ash", Item::new)
			.lang("Soda Ash")
			.register();
	public static final ItemEntry<Item> GYPSUM =
		REGISTRATE.item("gypsum", Item::new)
			.lang("Gypsum")
			.register();
	public static final ItemEntry<Item> SULFUR =
		REGISTRATE.item("sulfur", Item::new)
			.lang("Sulfur")
			.register();
	public static final ItemEntry<Item> BAUXITE =
		REGISTRATE.item("bauxite", Item::new)
			.lang("Bauxite")
			.register();
	public static final ItemEntry<Item> ALUMINIUM_HYDROXIDE =
		REGISTRATE.item("aluminium_hydroxide", Item::new)
			.lang("Aluminium Hydroxide")
			.register();
	public static final ItemEntry<Item> ALUMINA =
		REGISTRATE.item("alumina", Item::new)
			.lang("Alumina")
			.register();
	public static final ItemEntry<Item> PHOSPHATE_ROCK =
		REGISTRATE.item("phosphate_rock", Item::new)
			.lang("Phosphate Rock")
			.register();
	public static final ItemEntry<Item> PHOSPHOGYPSUM =
		REGISTRATE.item("phosphogypsum", Item::new)
			.lang("Phosphogypsum")
			.register();
	public static final ItemEntry<Item> AMMONIUM_SULFATE =
		REGISTRATE.item("ammonium_sulfate", Item::new)
			.lang("Ammonium Sulfate")
			.register();
	public static final ItemEntry<Item> AMMONIUM_NITRATE =
		REGISTRATE.item("ammonium_nitrate", Item::new)
			.lang("Ammonium Nitrate")
			.register();
	public static final ItemEntry<Item> UREA =
		REGISTRATE.item("urea", Item::new)
			.lang("Urea")
			.register();
	public static final ItemEntry<Item> CALCIUM_CHLORIDE =
		REGISTRATE.item("calcium_chloride", Item::new)
			.lang("Calcium Chloride")
			.register();
	public static final ItemEntry<Item> CALCIUM_SULFITE =
		REGISTRATE.item("calcium_sulfite", Item::new)
			.lang("Calcium Sulfite")
			.register();
	public static final ItemEntry<Item> COPPER_SULFATE =
		REGISTRATE.item("copper_sulfate", Item::new)
			.lang("Copper Sulfate")
			.register();
	public static final ItemEntry<Item> COPPER_CARBONATE =
		REGISTRATE.item("copper_carbonate", Item::new)
			.lang("Basic Copper Carbonate")
			.register();
	public static final ItemEntry<Item> POTASSIUM_NITRATE =
		REGISTRATE.item("potassium_nitrate", Item::new)
			.lang("Potassium Nitrate")
			.register();
	public static final ItemEntry<Item> POTASSIUM_CHLORIDE =
		REGISTRATE.item("potassium_chloride", Item::new)
			.lang("Potassium Chloride")
			.register();
	public static final ItemEntry<Item> AMMONIUM_CHLORIDE =
		REGISTRATE.item("ammonium_chloride", Item::new)
			.lang("Ammonium Chloride")
			.register();
	public static final ItemEntry<Item> MAGNESIUM_CHLORIDE =
		REGISTRATE.item("magnesium_chloride", Item::new)
			.lang("Magnesium Chloride")
			.register();
	public static final ItemEntry<Item> POTASSIUM_ALUM =
		REGISTRATE.item("potassium_alum", Item::new)
			.lang("Potassium Alum")
			.register();
	public static final ItemEntry<Item> FILTER_CAKE =
		REGISTRATE.item("filter_cake", Item::new)
			.lang("Filter Cake")
			.register();
	public static final ItemEntry<Item> ROCK_SALT_GRAIN =
		REGISTRATE.item("rock_salt_grain", Item::new)
			.lang("Rock Salt Grains")
			.register();
	public static final ItemEntry<Item> POTASSIUM_NITRATE_GRAIN =
		REGISTRATE.item("potassium_nitrate_grain", Item::new)
			.lang("Potassium Nitrate Grains")
			.register();
	public static final ItemEntry<Item> POTASSIUM_CHLORIDE_GRAIN =
		REGISTRATE.item("potassium_chloride_grain", Item::new)
			.lang("Potassium Chloride Grains")
			.register();
	public static final ItemEntry<Item> AMMONIUM_CHLORIDE_GRAIN =
		REGISTRATE.item("ammonium_chloride_grain", Item::new)
			.lang("Ammonium Chloride Grains")
			.register();
	public static final ItemEntry<Item> COPPER_SULFATE_GRAIN =
		REGISTRATE.item("copper_sulfate_grain", Item::new)
			.lang("Copper Sulfate Grains")
			.register();
	public static final ItemEntry<Item> CALCIUM_CHLORIDE_GRAIN =
		REGISTRATE.item("calcium_chloride_grain", Item::new)
			.lang("Calcium Chloride Grains")
			.register();
	public static final ItemEntry<Item> MAGNESIUM_CHLORIDE_GRAIN =
		REGISTRATE.item("magnesium_chloride_grain", Item::new)
			.lang("Magnesium Chloride Grains")
			.register();
	public static final ItemEntry<Item> POTASSIUM_ALUM_GRAIN =
		REGISTRATE.item("potassium_alum_grain", Item::new)
			.lang("Potassium Alum Grains")
			.register();
	public static final ItemEntry<MixedResidueItem> MIXED_RESIDUE =
		REGISTRATE.item("mixed_residue", MixedResidueItem::new)
			.lang("Mixed Salt Residue")
			.register();
	public static final ItemEntry<TestPaperItem> LITMUS =
		REGISTRATE.item("litmus_paper", p -> new TestPaperItem(p, TestPaperItem.Kind.LITMUS))
			.lang("Litmus Paper")
			.register();
	public static final ItemEntry<TestPaperItem> PHENOLPHTHALEIN =
		REGISTRATE.item("phenolphthalein_paper", p -> new TestPaperItem(p, TestPaperItem.Kind.PHENOLPHTHALEIN))
			.lang("Phenolphthalein Paper")
			.register();
	public static final ItemEntry<TestPaperItem> WIDE_PH =
		REGISTRATE.item("wide_ph_paper", p -> new TestPaperItem(p, TestPaperItem.Kind.WIDE_PH))
			.lang("Wide-Range pH Paper")
			.register();
	public static final ItemEntry<TestPaperItem> SILVER_NITRATE =
		REGISTRATE.item("silver_nitrate_paper", p -> new TestPaperItem(p, TestPaperItem.Kind.SILVER_NITRATE))
			.lang("Silver Nitrate Paper")
			.register();
	public static final ItemEntry<TestPaperItem> BARIUM_CHLORIDE =
		REGISTRATE.item("barium_chloride_paper", p -> new TestPaperItem(p, TestPaperItem.Kind.BARIUM_CHLORIDE))
			.lang("Barium Chloride Paper")
			.register();
	public static final ItemEntry<TestPaperItem> POTASSIUM_THIOCYANATE =
		REGISTRATE.item("potassium_thiocyanate_paper", p -> new TestPaperItem(p, TestPaperItem.Kind.POTASSIUM_THIOCYANATE))
			.lang("Potassium Thiocyanate Paper")
			.register();
	public static final ItemEntry<TestPaperItem> COBALT_GLASS =
		REGISTRATE.item("cobalt_glass", p -> new TestPaperItem(p, TestPaperItem.Kind.COBALT_GLASS))
			.lang("Cobalt-Glass Flame Scope")
			.register();

	public static void register() {
	}
}
