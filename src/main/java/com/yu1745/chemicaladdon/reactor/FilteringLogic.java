package com.yu1745.chemicaladdon.reactor;

import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.foundation.fluid.FluidIngredient;
import com.yu1745.chemicaladdon.fluid.Mixture;
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
 * Shared separation engine for the filter press and the settling basin.
 *
 * <p>Two modes:
 * <ol>
 *   <li><b>generic</b> — any mixture with a {@code Suspended} domain (a slurry) is
 *       separated straight through: the solids become items, the liquid
 *       (molecules + ions) passes to the output tank. This is how a reaction's
 *       precipitate (which the rules engine deposits into {@code Suspended}) is
 *       recovered;</li>
 *   <li><b>recipe-driven</b> — legacy FILTERING recipes (matched by fluid
 *       ingredient).</li>
 * </ol>
 * The speed multiplier distinguishes fast press vs slow settling (recipe path).
 */
public class FilteringLogic {

	public static final int TICK_INTERVAL = 10;
	public static final int SLURRY_PROCESSING_TICKS = 100;

	private float progress = 0;

	public float getProgress() {
		return progress;
	}
	public void setProgress(float progress) { this.progress = Math.max(0, Math.min(1, progress)); }

	public void tick(Level level, ReactorTank input, ReactorTank output, ItemStackHandler items, BlockPos pos,
		float speed) {
		tick(level, input, output, null, items, pos, speed);
	}

	/** @param washTank U16.5 optional rinse line (filter press): plain water displacement-washes each cake. */
	public void tick(Level level, ReactorTank input, ReactorTank output, ReactorTank washTank, ItemStackHandler items,
		BlockPos pos, float speed) {
		if (level == null || level.isClientSide) {
			return;
		}
		// Generic slurry follows a real powered press cycle; the old path separated
		// the entire tank instantly on its first process tick.
		if (hasSuspended(input)) {
			if (!canFitSuspendedOutputs(input, output, washTank, items)) {
				progress = 0;
				return;
			}
			progress += (float) TICK_INTERVAL * speed / SLURRY_PROCESSING_TICKS;
			if (progress >= 1.0f) {
				filterSuspended(level, input, output, washTank, items, pos);
				progress = 0;
			}
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

	/** True when the input tank holds a mixture with suspended solids; separates them if so. */
	private static boolean filterSuspended(Level level, ReactorTank input, ReactorTank output, ReactorTank washTank,
		ItemStackHandler items, BlockPos pos) {
		if (!hasSuspended(input)) return false;
		// whole-lump extraction (plans/03 §12): single species + clean pore
		// liquor = pure item, anything else = mixed salt residue; sub-item
		// remainder stays behind. U16.5: the wet cake drags its pore mother
		// liquor along (entrainment) unless the rinse line displacement-washes it
		input.extractSolids(s -> {
			ItemStack remainder = ItemHandlerHelper.insertItemStacked(items, s, false);
			if (!remainder.isEmpty()) {
				Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), remainder);
			}
		}, false, washTank, output);
		if (input != output) moveLiquid(input, output);
		output.collapseIfNeeded();
		return true;
	}

	private static boolean hasSuspended(ReactorTank input) {
		for (FluidStack stack : input.getFluids()) {
			if (Mixture.isMixture(stack) && !Mixture.getSuspended(stack).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Conservatively reserves all liquid currently in the feed plus all optional
	 * wash water.  The actual filtrate is smaller because the cake retains pore
	 * liquor, but reserving the upper bound guarantees that completion can never
	 * void fluid when the filtrate line is backed up.
	 */
	private static boolean canFitSuspendedOutputs(ReactorTank input, ReactorTank output,
		ReactorTank washTank, ItemStackHandler items) {
		long itemUnits = (long) RulesEngine.MB_PER_ITEM
			* com.yu1745.chemicaladdon.composition.Chemistry.UNIT_PER_MB;
		long cakeCount = input.suspendedUnits() / itemUnits;
		if (cakeCount <= 0) {
			return false;
		}
		// The exact cake item (pure solid or mixed residue) depends on entrained
		// liquor, so an occupied slot cannot be proven compatible before extraction.
		if (!items.getStackInSlot(0).isEmpty()) {
			return false;
		}
		long requiredFluidRoom = input.getTotalAmount() + (washTank == null ? 0 : washTank.getTotalAmount());
		return requiredFluidRoom <= output.getTankCapacity(0) - output.getTotalAmount();
	}

	/** Drain the whole input into the output (the liquid left behind after filtering). */
	private static void moveLiquid(ReactorTank input, ReactorTank output) {
		while (!input.getFluids().isEmpty()) {
			FluidStack drained = input.drain(Integer.MAX_VALUE, FluidAction.EXECUTE);
			if (drained.isEmpty()) {
				break;
			}
			output.fill(drained, FluidAction.EXECUTE);
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
