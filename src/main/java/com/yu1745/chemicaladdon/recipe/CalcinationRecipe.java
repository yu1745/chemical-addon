package com.yu1745.chemicaladdon.recipe;

import com.google.gson.JsonObject;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeParams;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

/**
 * Calcination recipe (施工包 D): solid heat treatment in the furnace — item
 * ingredients in, item results + kiln gas out, driven by a minimum bed
 * temperature ({@code minTempC}). This is the recipe-layer half of the
 * engine boundary (plans/02 §3): calcination needs <i>driving</i> (fuel,
 * forced air) so it never belongs to the spontaneous chemistry kernel —
 * the furnace controller performs its own matching against the solid charge
 * bed and the kiln temperature.
 */
public class CalcinationRecipe extends ProcessingRecipe<Container> {

	private int minTempC = 20;

	public CalcinationRecipe(ProcessingRecipeParams params) {
		super(AllRecipeTypes.CALCINATION, params);
	}

	/** Bed temperature (°C) the charge must reach before the batch runs (欠烧 = 生料). */
	public int getMinTempC() {
		return minTempC;
	}

	/** Overheat diagnostic threshold (°C): a bed this hot is 结瘤 territory (warning status, D1). */
	public int getOverheatC() {
		return minTempC + 300;
	}

	@Override
	protected int getMaxInputCount() {
		return 2;
	}

	@Override
	protected int getMaxOutputCount() {
		return 2;
	}

	@Override
	protected int getMaxFluidOutputCount() {
		return 2;
	}

	@Override
	protected boolean canSpecifyDuration() {
		return true;
	}

	@Override
	public boolean matches(Container container, Level level) {
		return false; // the furnace controller performs its own matching
	}

	@Override
	public ItemStack getResultItem(RegistryAccess registryAccess) {
		return getRollableResults().isEmpty() ? ItemStack.EMPTY : getRollableResults().get(0).getStack();
	}

	/** True when the furnace's charge bed holds every ingredient (single-item granularity per D1 design). */
	public boolean matchesCharge(Iterable<ItemStack> bed) {
		for (Ingredient ingredient : getIngredients()) {
			boolean found = false;
			for (ItemStack stack : bed) {
				if (!stack.isEmpty() && ingredient.test(stack)) {
					found = true;
					break;
				}
			}
			if (!found) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void readAdditional(JsonObject json) {
		super.readAdditional(json);
		minTempC = GsonHelper.isValidNode(json, "minTempC") ? GsonHelper.getAsInt(json, "minTempC") : 20;
	}

	@Override
	public void writeAdditional(JsonObject json) {
		super.writeAdditional(json);
		json.addProperty("minTempC", minTempC);
	}

	@Override
	public void writeAdditional(FriendlyByteBuf buffer) {
		super.writeAdditional(buffer);
		buffer.writeInt(minTempC);
	}

	@Override
	public void readAdditional(FriendlyByteBuf buffer) {
		super.readAdditional(buffer);
		minTempC = buffer.readInt();
	}
}
