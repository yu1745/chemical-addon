package com.yu1745.chemicaladdon.registry;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.item.FluidVialItem;

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

	public static void register() {
	}
}
