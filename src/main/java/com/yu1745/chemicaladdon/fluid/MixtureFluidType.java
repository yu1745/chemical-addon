package com.yu1745.chemicaladdon.fluid;

import java.util.function.Consumer;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;

/**
 * Fluid type for the generic mixture fluid (chemicaladdon:mixture). Unlike the
 * species fluids (whose colour is baked into their texture), a mixture has no
 * fixed colour — it is a neutral base texture tinted by the per-stack blended
 * colour stored in the FluidStack NBT (see {@link Mixture}). This is the one
 * place the fluid render pipeline offers a per-stack hook
 * ({@code IClientFluidTypeExtensions#getTintColor(FluidStack)}), so the mixture
 * shows its blended colour in pipes, tanks, and the vessel renderer with zero
 * renderer-specific code.
 */
public class MixtureFluidType extends ChemFluidType {

	public MixtureFluidType(Properties properties, ResourceLocation still, ResourceLocation flowing) {
		super(properties, still, flowing, false); // a mixture is a liquid
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

			@Override
			public int getTintColor(FluidStack stack) {
				// the cached weight-blended ARGB from the stack's component composition;
				// falls back to white (no tint) if the stack carries no composition yet
				return Mixture.getColor(stack);
			}
		});
	}

	@Override
	public String getDescriptionId(FluidStack stack) {
		// "Mixture" by default; a richer tooltip (component list) is rendered by the
		// vessel's goggles HUD, not the fluid name itself
		return super.getDescriptionId(stack);
	}
}
