package com.yu1745.chemicaladdon.reactor;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.foundation.fluid.FluidIngredient;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.composition.Chemistry;
import com.yu1745.chemicaladdon.composition.Species;
import com.yu1745.chemicaladdon.composition.SpeciesManager;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.fluid.Temperature;
import com.yu1745.chemicaladdon.recipe.AllRecipeTypes;
import com.yu1745.chemicaladdon.recipe.ChemicalReactionRecipe;
import com.yu1745.chemicaladdon.recipe.SolutionIngredient;
import com.yu1745.chemicaladdon.vessel.ProcessReadings;
import com.yu1745.chemicaladdon.vessel.StructureAccess;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * Recipe matching and completion for the reaction engine (U3 split out of the
 * controller): finds the first whitelisted chemical_reaction recipe whose
 * ingredients (items / fluids / dissolved solution species + concentration
 * window) and heat condition are met, and settles a completed recipe —
 * consuming inputs, rolling item outputs, filling fluid + solution outputs and
 * applying the exothermic/endothermic delta to every phase. The controller
 * keeps only the progress/status orchestration ({@code tickReaction}).
 */
final class ReactionLogic {

	private ReactionLogic() {
	}

	/**
	 * Progress-rate multiplier for a running recipe (U1): temperature-window
	 * bonus × stirring. Running hotter than the recipe's minimum accelerates it
	 * up to 2×; exactly at the threshold the factor is 1.0 — matching
	 * temperature always means full speed, so existing timing behaviour never
	 * slows down (the 64-test baseline is the safety net).
	 */
	static float rateCoefficient(ChemicalReactionRecipe recipe, int temperature, float stirring) {
		int min = switch (recipe.getRequiredHeat()) {
			case HEATED -> 400;
			case SUPERHEATED -> 800;
			default -> ReactorControllerBlockEntity.AMBIENT_TEMP;
		};
		float window = Math.min(1.0f, Math.max(0.0f, (temperature - min) / 400.0f));
		return (1.0f + window) * stirring;
	}

	@Nullable
	static ChemicalReactionRecipe findRecipe(ReactorControllerBlockEntity reactor) {
		if (reactor.getLevel() == null) {
			return null;
		}
		for (ChemicalReactionRecipe recipe : reactor.getLevel().getRecipeManager()
			.getAllRecipesFor(chemicalReactionType())) {
			if (matches(reactor, recipe)) {
				return recipe;
			}
		}
		return null;
	}

	private static boolean matches(ReactorControllerBlockEntity reactor, ChemicalReactionRecipe recipe) {
		// Structure requirements are deliberately checked through the narrow
		// contracts. Required parts and agitation metadata are retained by the
		// recipe but are not enforced until reliable snapshots/readings exist.
		StructureAccess structure = reactor;
		ProcessReadings readings = reactor;
		if (!recipe.matchesStructureRequirements(structure, readings)) {
			return false;
		}
		// heat condition vs current temperature
		int temperature = reactor.getTemperature();
		HeatCondition heat = recipe.getRequiredHeat();
		if (heat == HeatCondition.HEATED && temperature < 400) {
			return false;
		}
		if (heat == HeatCondition.SUPERHEATED && temperature < 800) {
			return false;
		}
		return matchesIngredients(reactor, recipe);
	}

	/** True when all item+fluid ingredients are present (heat ignored). */
	static boolean matchesIngredients(ReactorControllerBlockEntity reactor, ChemicalReactionRecipe recipe) {
		for (Ingredient ingredient : recipe.getIngredients()) {
			if (!hasItem(reactor, ingredient)) {
				return false;
			}
		}
		for (FluidIngredient fluid : recipe.getFluidIngredients()) {
			if (!hasFluid(reactor, fluid)) {
				return false;
			}
		}
		ReactorTank tank = reactor.getTank();
		for (SolutionIngredient sol : recipe.getSolutions()) {
			if (tank.countSolution(sol.speciesId()) < sol.amount()) {
				return false;
			}
			if (sol.hasConcentrationRange()) {
				double c = tank.concentrationOf(sol.speciesId());
				if (c < sol.minConcentration() || c > sol.maxConcentration()) {
					return false;
				}
			}
		}
		return true;
	}

	/** Any recipe whose ingredients are ready but whose heat condition is not met. */
	static boolean matchesIgnoringHeat(ReactorControllerBlockEntity reactor) {
		if (reactor.getLevel() == null) {
			return false;
		}
		for (ChemicalReactionRecipe recipe : reactor.getLevel().getRecipeManager()
			.getAllRecipesFor(chemicalReactionType())) {
			if (matchesIngredients(reactor, recipe) && !matches(reactor, recipe)) {
				return true;
			}
		}
		return false;
	}

	static boolean canFitOutputs(ReactorControllerBlockEntity reactor, ChemicalReactionRecipe recipe) {
		ReactorTank tank = reactor.getTank();
		int fluidOut = 0;
		for (FluidStack out : recipe.getFluidResults()) {
			fluidOut += out.getAmount();
		}
		if (fluidOut > tank.getTankCapacity(0) - tank.getTotalAmount()) {
			return false;
		}
		for (ProcessingOutput out : recipe.getRollableResults()) {
			ItemStack stack = out.getStack();
			if (!stack.isEmpty() && !ItemHandlerHelper.insertItemStacked(reactor.getItems(), stack.copy(), true).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private static boolean hasItem(ReactorControllerBlockEntity reactor, Ingredient ingredient) {
		for (int i = 0; i < reactor.getItems().getSlots(); i++) {
			ItemStack stack = reactor.getItems().getStackInSlot(i);
			if (!stack.isEmpty() && ingredient.test(stack)) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasFluid(ReactorControllerBlockEntity reactor, FluidIngredient ingredient) {
		// countIngredient looks inside mixture components, so a recipe matches a
		// species dissolved in the mix as well as a pure stack
		return reactor.getTank().countIngredient(ingredient) >= ingredient.getRequiredAmount();
	}

	static void completeRecipe(ReactorControllerBlockEntity reactor, ChemicalReactionRecipe recipe) {
		ReactorTank tank = reactor.getTank();
		var items = reactor.getItems();
		// capture the vessel's temperature before consuming inputs, so the products
		// inherit it (they form in the hot/cold vessel, not at ambient)
		int vesselTemp = reactor.getTemperature();
		// consume item inputs (1 per ingredient)
		for (Ingredient ingredient : recipe.getIngredients()) {
			for (int i = 0; i < items.getSlots(); i++) {
				ItemStack stack = items.getStackInSlot(i);
				if (!stack.isEmpty() && ingredient.test(stack)) {
					stack.shrink(1);
					items.setStackInSlot(i, stack);
					break;
				}
			}
		}
		// consume fluid inputs (mixture-aware: draws from pure stacks first,
		// then from mixture components, so a recipe can consume a species that is
		// dissolved in the mix)
		for (FluidIngredient fluid : recipe.getFluidIngredients()) {
			tank.drainIngredient(fluid, fluid.getRequiredAmount(), IFluidHandler.FluidAction.EXECUTE);
		}
		// consume solution-species inputs (matched against the dissolved ions)
		for (SolutionIngredient sol : recipe.getSolutions()) {
			tank.drainSolution(sol.speciesId(), sol.amount(), IFluidHandler.FluidAction.EXECUTE);
		}
		// item outputs (chance-based)
		for (ProcessingOutput output : recipe.getRollableResults()) {
			ItemStack out = output.getStack();
			if (out.isEmpty() || (output.getChance() < 1 && reactor.getLevel().random.nextFloat() >= output.getChance())) {
				continue;
			}
			ItemStack remainder = ItemHandlerHelper.insertItemStacked(items, out.copy(), false);
			if (!remainder.isEmpty() && reactor.getLevel() != null) {
				BlockPos pos = reactor.getBlockPos();
				Containers.dropItemStack(reactor.getLevel(), pos.getX(), pos.getY(), pos.getZ(), remainder);
			}
		}
		// fluid outputs (pure fluids only — solutions go through solutionOutputs)
		for (FluidStack out : recipe.getFluidResults()) {
			Temperature.set(out, vesselTemp);
			tank.fill(out.copy(), IFluidHandler.FluidAction.EXECUTE);
		}
		// solution-species outputs: expand straight into ions + water at the target
		// concentration (ion mB / water mB)
		for (SolutionIngredient out : recipe.getSolutionOutputs()) {
			Species species = SpeciesManager.get(out.speciesId());
			if (species == null || !species.isSolution() || Double.isNaN(out.targetConcentration())) {
				continue;
			}
			Map<ResourceLocation, Integer> molecules = new LinkedHashMap<>();
			Map<String, Integer> ions = new LinkedHashMap<>();
			species.expand(out.amount(), out.targetConcentration(), molecules, ions);
			int total = 0;
			for (int v : molecules.values()) {
				total += v;
			}
			for (int v : ions.values()) {
				total += v;
			}
			FluidStack mix = Mixture.create(molecules, ions, total);
			Temperature.set(mix, vesselTemp);
			tank.fill(mix, IFluidHandler.FluidAction.EXECUTE);
		}
		// heat effect (exothermic raises temperature) — U1/G2: the reaction
		// happens throughout the fluid body, so every phase takes the delta
		// (per-stack +Δ keeps phase temperature differences intact and lifts the
		// amount-weighted vessel average by exactly Δ).
		//
		// U16 energy ledger (plans/03 §12): {@code deltaHeat} declares the °C
		// rise this batch would give a reference one-bucket body; the actual
		// rise is mass-coupled — ΔT = Q/(Σunits·c) with Q = deltaHeat·REF·c,
		// i.e. ΔT = deltaHeat × (one bucket / vessel contents). A fixed batch
		// of reaction heat warms a full big vessel by single digits, the same
		// batch in a nearly-empty one flashes hot — concentrated dilution /
		// neutralisation runaway becomes emergent instead of a flat constant.
		if (recipe.getDeltaHeat() != 0) {
			tank.collapseIfNeeded();
			long totalUnits = 0;
			for (FluidStack stack : tank.getFluids()) {
				totalUnits += (long) stack.getAmount() * Chemistry.UNIT_PER_MB;
			}
			if (totalUnits > 0) {
				int delta = (int) Math
					.round((double) recipe.getDeltaHeat() * RulesEngine.ITEM_UNITS / totalUnits);
				for (FluidStack stack : tank.getFluids()) {
					int t = Math.max(ReactorControllerBlockEntity.AMBIENT_TEMP,
						Math.min(ReactorControllerBlockEntity.MAX_TEMP, Temperature.get(stack) + delta));
					Temperature.set(stack, t);
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	static RecipeType<ChemicalReactionRecipe> chemicalReactionType() {
		return (RecipeType<ChemicalReactionRecipe>) (RecipeType<?>) AllRecipeTypes.CHEMICAL_REACTION.getType();
	}
}
