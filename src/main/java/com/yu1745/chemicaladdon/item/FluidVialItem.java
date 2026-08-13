package com.yu1745.chemicaladdon.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack;

/**
 * Generic fluid sample container: a single item that stores one {@code FluidStack}
 * (fluid <b>plus full NBT</b> — temperature, mixture composition, MixDegree) in the
 * item's own NBT.
 *
 * <p>This exists because Forge's per-species {@code BucketItem} keeps a fluid tag-less
 * (its {@code FluidBucketWrapper.getFluid()} returns {@code new FluidStack(fluid, 1000)}
 * with no NBT), so a standard bucket cannot carry temperature or a mixture's
 * composition. {@link FluidHandlerItemStack} round-trips the whole FluidStack via
 * {@code writeToNBT}/{@code loadFluidStackFromNBT}, so a hot/cold or mixed fluid
 * survives being carried between vessels.
 *
 * <p>Filled/emptied by right-clicking a vessel (see {@code ReactorControllerBlock.use}
 * → Create's {@code FluidHelper.tryFillItemFromBE}/{@code tryEmptyItemIntoBE}).
 */
public class FluidVialItem extends Item {

	public static final int CAPACITY = 1000;

	public FluidVialItem(Properties properties) {
		super(properties);
	}

	@Override
	public ICapabilityProvider initCapabilities(ItemStack stack, CompoundTag nbt) {
		return new FluidHandlerItemStack(stack, CAPACITY);
	}
}
