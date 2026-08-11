package com.yu1745.chemicaladdon;

import com.yu1745.chemicaladdon.reactor.ReactorScreen;
import com.yu1745.chemicaladdon.registry.AllMenuTypes;

import net.minecraft.client.gui.screens.MenuScreens;

/** Client-only initialisation. */
public class ChemicalAddonClient {

	public static void init() {
		MenuScreens.register(AllMenuTypes.REACTOR.get(), ReactorScreen::new);
	}
}
