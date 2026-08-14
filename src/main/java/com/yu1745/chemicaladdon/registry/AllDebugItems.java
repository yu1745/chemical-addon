package com.yu1745.chemicaladdon.registry;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.item.TemperatureDebugItem;

/**
 * Creative-only debug/dev items. Registered here (not in the generated
 * {@code AllItems}) so {@code tools/gen_species.py} never overwrites them.
 * Every item in the {@code chemicaladdon} namespace is auto-added to the creative
 * tab by {@link AllCreativeModeTabs}, so these are creative-only by construction.
 */
public class AllDebugItems {

	public static final CreateRegistrate REGISTRATE = ChemicalAddon.registrate();

	/** Right-click a vessel to pin its temperature; sneak-right-click to unpin. */
	public static final ItemEntry<TemperatureDebugItem> TEMPERATURE_DEBUG =
		REGISTRATE.item("temperature_debug", TemperatureDebugItem::new)
			.lang("Temperature Debug Stick")
			.register();

	public static void register() {
	}
}
