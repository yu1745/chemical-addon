package com.yu1745.chemicaladdon.reactor;

import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.foundation.fluid.FluidIngredient;
import com.yu1745.chemicaladdon.recipe.AllRecipeTypes;
import com.yu1745.chemicaladdon.recipe.FilteringRecipe;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Shared separation engine for the filter press and the settling basin:
 * matches FILTERING recipes against an input tank, drains inputs, fills an
 * output tank (may be the same tank) and inserts the cake into an item
 * buffer. Speed multiplier distinguishes fast press vs slow settling.
 */
public class FilteringLogic {

	public static final int TICK_INTERVAL = 10;

	private float progress = 0;

	public float getProgress() {
		return progress;
	}

	public void tick(Level level, ReactorTank input, ReactorTank output, ItemStackHandler items, BlockPos pos,
		float speed) {
		if (level == null || level.isClientSide) {
			return;
		}
		FilteringRecipe recipe = findRecipe(level, input);
		if (recipe == null || !canFitOutputs(recipe, output, items)) {
			progress = 0;
			return;
		}
		progress += (float) TICK_INTERVAL * speed / recipe.getProcessingDuration();
		if (progress >= 1.0f) {
			complete(level, recipe, input, output, items, pos);
			progress = 0;
		}
	}

	private static FilteringRecipe findRecipe(Level level, ReactorTank input) {
		for (FilteringRecipe recipe : level.getRecipeManager().getAllRecipesFor(AllRecipeTypes.filteringType())) {
			if (matches(recipe, input)) {
				return recipe;
			}
		}
		return null;
	}

	private static boolean matches(FilteringRecipe recipe, ReactorTank input) {
		for (FluidIngredient fluid : recipe.getFluidIngredients()) {
			int total = 0;
			for (FluidStack stack : input.getFluids()) {
				if (fluid.test(stack)) {
					total += stack.getAmount();
				}
			}
			if (total < fluid.getRequiredAmount()) {
				return false;
			}
		}
		return true;
	}

	private static boolean canFitOutputs(FilteringRecipe recipe, ReactorTank output, ItemStackHandler items) {
		int fluidOut = 0;
		for (FluidStack out : recipe.getFluidResults()) {
			fluidOut += out.getAmount();
		}
		if (fluidOut > output.getTankCapacity(0) - output.getTotalAmount()) {
			return false;
		}
		for (ProcessingOutput out : recipe.getRollableResults()) {
			ItemStack stack = out.getStack();
			if (!stack.isEmpty() && !ItemHandlerHelper.insertItemStacked(items, stack.copy(), true).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private static void complete(Level level, FilteringRecipe recipe, ReactorTank input, ReactorTank output,
		ItemStackHandler items, BlockPos pos) {
		// drain fluid inputs
		for (FluidIngredient fluid : recipe.getFluidIngredients()) {
			int remaining = fluid.getRequiredAmount();
			for (int i = 0; i < input.getTanks() && remaining > 0; i++) {
				FluidStack stack = input.getFluidInTank(i);
				if (fluid.test(stack)) {
					remaining -= input.drain(new FluidStack(stack.getFluid(), remaining), FluidAction.EXECUTE).getAmount();
				}
			}
		}
		// fluid output
		for (FluidStack out : recipe.getFluidResults()) {
			output.fill(out.copy(), FluidAction.EXECUTE);
		}
		// cake output (chance-based)
		for (ProcessingOutput out : recipe.getRollableResults()) {
			ItemStack stack = out.getStack();
			if (stack.isEmpty() || (out.getChance() < 1 && level.random.nextFloat() >= out.getChance())) {
				continue;
			}
			ItemStack remainder = ItemHandlerHelper.insertItemStacked(items, stack.copy(), false);
			if (!remainder.isEmpty()) {
				Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), remainder);
			}
		}
	}
}
