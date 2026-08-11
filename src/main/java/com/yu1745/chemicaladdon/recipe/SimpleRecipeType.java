package com.yu1745.chemicaladdon.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;

/** Minimal RecipeType implementation (1.20.1 vanilla has no built-in helper). */
public class SimpleRecipeType<T extends Recipe<?>> implements RecipeType<T> {

	private final ResourceLocation id;

	public SimpleRecipeType(ResourceLocation id) {
		this.id = id;
	}

	public ResourceLocation getId() {
		return id;
	}

	@Override
	public String toString() {
		return id.toString();
	}
}
