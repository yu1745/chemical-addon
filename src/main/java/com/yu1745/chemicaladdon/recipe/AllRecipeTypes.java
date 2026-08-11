package com.yu1745.chemicaladdon.recipe;

import java.util.Locale;

import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeFactory;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeSerializer;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;
import com.yu1745.chemicaladdon.ChemicalAddon;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Recipe types of the chemical addon. CHEMICAL_REACTION extends Create's
 * ProcessingRecipe pipeline (JSON via ProcessingRecipeSerializer, heat
 * conditions, duration, fluid ingredients/results) with a custom
 * "deltaHeat" field (see ChemicalReactionRecipe).
 */
public enum AllRecipeTypes implements IRecipeTypeInfo {

	CHEMICAL_REACTION(ChemicalReactionRecipe::new);

	private final ResourceLocation id;
	private final RegistryObject<RecipeSerializer<?>> serializerObject;
	private final RegistryObject<RecipeType<?>> typeObject;

	AllRecipeTypes(ProcessingRecipeFactory<?> factory) {
		String path = name().toLowerCase(Locale.ROOT);
		this.id = new ResourceLocation(ChemicalAddon.MODID, path);
		this.serializerObject = RecipeSerializers.REGISTER.register(path, () -> new ProcessingRecipeSerializer<>(factory));
		this.typeObject = RecipeTypes.REGISTER.register(path, () -> new SimpleRecipeType<>(id));
	}

	@Override
	public ResourceLocation getId() {
		return id;
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends RecipeSerializer<?>> T getSerializer() {
		return (T) serializerObject.get();
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T extends RecipeType<?>> T getType() {
		return (T) typeObject.get();
	}

	public static class RecipeSerializers {
		static final DeferredRegister<RecipeSerializer<?>> REGISTER =
			DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, ChemicalAddon.MODID);

		static void register(IEventBus bus) {
			REGISTER.register(bus);
		}
	}

	public static class RecipeTypes {
		static final DeferredRegister<RecipeType<?>> REGISTER =
			DeferredRegister.create(Registries.RECIPE_TYPE, ChemicalAddon.MODID);

		static void register(IEventBus bus) {
			REGISTER.register(bus);
		}
	}

	public static void register(IEventBus modEventBus) {
		RecipeSerializers.register(modEventBus);
		RecipeTypes.register(modEventBus);
	}
}
