package com.yu1745.chemicaladdon;

import com.yu1745.chemicaladdon.reactor.ReactorControllerRenderer;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.registry.AllBlocks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

/** Client-only initialisation. */
public class ChemicalAddonClient {

	/**
	 * Metal-grey tint applied to the chemical brick shell (multiplies Create's
	 * tank sheets, distinguishing our vessels from the vanilla-blue fluid tank).
	 * Adjust to taste — tint is per-channel multiplication.
	 */
	public static final int BRICK_TINT = 0xB0B0B0;

	public static void init() {
		// render the vessel's item buffer + fluid surface inside the hollow interior
		BlockEntityRenderers.register(AllBlockEntities.REACTOR_CONTROLLER.get(), ReactorControllerRenderer::new);

		// metal-grey tint for the shell model faces (they carry "tintindex": 0)
		Minecraft.getInstance().getBlockColors()
			.register((state, level, pos, tintIndex) -> BRICK_TINT, AllBlocks.CHEMICAL_BRICK.get());
		Minecraft.getInstance().getItemColors()
			.register((stack, tintIndex) -> BRICK_TINT, AllBlocks.CHEMICAL_BRICK.get());
	}
}
