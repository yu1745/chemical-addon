package com.yu1745.chemicaladdon.registry;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.yu1745.chemicaladdon.ChemicalAddon;
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
	public static final ItemEntry<Item> FILTER_CAKE =
		REGISTRATE.item("filter_cake", Item::new)
			.lang("Filter Cake")
			.register();

	public static void register() {
	}
}
