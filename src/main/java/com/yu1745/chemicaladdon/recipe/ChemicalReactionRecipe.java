package com.yu1745.chemicaladdon.recipe;

import com.google.gson.JsonObject;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeParams;

import net.minecraft.core.RegistryAccess;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Chemical reaction recipe: Create ProcessingRecipe with an extra "deltaHeat"
 * field (°C temperature rise per completed batch; exothermic = positive,
 * endothermic = negative). Matching/execution is performed by the reactor
 * vessel (matches() returns false; the vessel does its own multi-fluid
 * matching against the reactor contents).
 */
public class ChemicalReactionRecipe extends ProcessingRecipe<Container> {

	private int deltaHeat = 0;

	public ChemicalReactionRecipe(ProcessingRecipeParams params) {
		super(AllRecipeTypes.CHEMICAL_REACTION, params);
	}

	public int getDeltaHeat() {
		return deltaHeat;
	}

	@Override
	protected int getMaxInputCount() {
		return 4;
	}

	@Override
	protected int getMaxOutputCount() {
		return 4;
	}

	@Override
	protected boolean canRequireHeat() {
		return true;
	}

	@Override
	protected boolean canSpecifyDuration() {
		return true;
	}

	@Override
	protected int getMaxFluidInputCount() {
		return 4;
	}

	@Override
	protected int getMaxFluidOutputCount() {
		return 4;
	}

	@Override
	public boolean matches(Container container, Level level) {
		return false; // the reactor vessel performs its own matching
	}

	@Override
	public ItemStack getResultItem(RegistryAccess registryAccess) {
		return ItemStack.EMPTY;
	}

	@Override
	public void readAdditional(JsonObject json) {
		super.readAdditional(json);
		if (GsonHelper.isValidNode(json, "deltaHeat")) {
			deltaHeat = GsonHelper.getAsInt(json, "deltaHeat");
		}
	}

	@Override
	public void writeAdditional(JsonObject json) {
		super.writeAdditional(json);
		if (deltaHeat != 0) {
			json.addProperty("deltaHeat", deltaHeat);
		}
	}
}
