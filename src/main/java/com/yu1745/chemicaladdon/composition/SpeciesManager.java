package com.yu1745.chemicaladdon.composition;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.yu1745.chemicaladdon.ChemicalAddon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Loads species definitions from datapacks (folder {@code chemistry/species}).
 * Reloadable via /reload. Modeled after Tinkers' ModifierManager
 * (SimpleJsonResourceReloadListener + ResourceLocation ids).
 */
public class SpeciesManager {

	private static final String FOLDER = "chemistry/species";
	private static final Gson GSON = new GsonBuilder().create();

	private static final Map<ResourceLocation, Species> REGISTRY = new HashMap<>();

	public static final SimpleJsonResourceReloadListener RELOADER = new SimpleJsonResourceReloadListener(GSON, FOLDER) {
		@Override
		protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager resourceManager,
			ProfilerFiller profilerFiller) {
			Map<ResourceLocation, Species> loaded = new HashMap<>();
			for (Map.Entry<ResourceLocation, JsonElement> entry : map.entrySet()) {
				Species species = Species.parse(entry.getKey(), entry.getValue());
				if (species != null) {
					loaded.put(entry.getKey(), species);
				}
			}
			REGISTRY.clear();
			REGISTRY.putAll(loaded);
			ChemicalAddon.LOGGER.info("Chemical Addon: loaded {} species definitions", REGISTRY.size());
		}
	};

	@Nullable
	public static Species get(ResourceLocation id) {
		return REGISTRY.get(id);
	}

	public static Collection<Species> all() {
		return List.copyOf(REGISTRY.values());
	}

	private SpeciesManager() {
	}
}
