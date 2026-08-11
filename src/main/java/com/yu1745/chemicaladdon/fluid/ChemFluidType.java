package com.yu1745.chemicaladdon.fluid;

import java.util.function.Consumer;

import com.yu1745.chemicaladdon.ChemicalAddon;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidType;

/**
 * Fluid type for all chemical species. Gases are represented as fluids with
 * negative density (Create 6.0.8 has no gas system); their textures are
 * translucent and banded (see tools/gen_species.py).
 */
public class ChemFluidType extends FluidType {

	private final ResourceLocation still;
	private final ResourceLocation flowing;
	private final boolean gas;

	public ChemFluidType(Properties properties, ResourceLocation still, ResourceLocation flowing, boolean gas) {
		super(properties);
		this.still = still;
		this.flowing = flowing;
		this.gas = gas;
	}

	public boolean isGas() {
		return gas;
	}

	@Override
	public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
		consumer.accept(new IClientFluidTypeExtensions() {
			@Override
			public ResourceLocation getStillTexture() {
				return still;
			}

			@Override
			public ResourceLocation getFlowingTexture() {
				return flowing;
			}
		});
	}
}
