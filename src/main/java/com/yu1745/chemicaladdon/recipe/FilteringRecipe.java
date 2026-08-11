package com.yu1745.chemicaladdon.recipe;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeParams;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Physical separation recipe (filtering/settling): fluid input -> fluid
 * output + item output (cake). No heat, no delta heat — a mechanical process.
 */
public class FilteringRecipe extends ProcessingRecipe<Container> {

	public FilteringRecipe(ProcessingRecipeParams params) {
		super(AllRecipeTypes.FILTERING, params);
	}

	@Override
	protected int getMaxInputCount() {
		return 0;
	}

	@Override
	protected int getMaxOutputCount() {
		return 1;
	}

	@Override
	protected boolean canRequireHeat() {
		return false;
	}

	@Override
	protected boolean canSpecifyDuration() {
		return true;
	}

	@Override
	protected int getMaxFluidInputCount() {
		return 1;
	}

	@Override
	protected int getMaxFluidOutputCount() {
		return 1;
	}

	@Override
	public boolean matches(Container container, Level level) {
		return false; // the filter press / settling basin performs its own matching
	}

	@Override
	public ItemStack getResultItem(RegistryAccess registryAccess) {
		return ItemStack.EMPTY;
	}
}
