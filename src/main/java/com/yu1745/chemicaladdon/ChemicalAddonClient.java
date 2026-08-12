package com.yu1745.chemicaladdon;

import com.yu1745.chemicaladdon.reactor.ReactorControllerRenderer;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/** Client-only initialisation. */
public class ChemicalAddonClient {

	public static void init() {
		// render the vessel's item buffer + fluid surface inside the hollow interior
		BlockEntityRenderers.register(AllBlockEntities.REACTOR_CONTROLLER.get(), ReactorControllerRenderer::new);
	}
}
