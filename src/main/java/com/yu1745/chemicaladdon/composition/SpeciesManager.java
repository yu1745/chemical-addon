package com.yu1745.chemicaladdon.composition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

	/**
	 * Built-in species files (mod resources, preloaded at startup so the creative
	 * tab / JEI can resolve them before a world's datapack reload). Mirrors the
	 * files in {@code data/chemicaladdon/chemistry/species/*.json}; the reload
	 * listener re-reads the datapack (which can override them) at world load.
	 */
	private static final String[] BUILTIN_SPECIES = {
		"ammonia", "ammoniated_brine", "rock_salt", "water", "limestone", "gypsum",
		"sulfuric_acid", "hydrochloric_acid", "nitric_acid",
		"caustic_soda_solution", "soda_ash_solution", "ammonium_chloride_solution",
		"calcium_chloride_solution", "ammonia_water", "milk_of_lime",
		"ammonium_sulfate_solution", "ammonium_nitrate_solution", "brine",
		"gypsum_slurry", "sodium_bicarbonate_slurry", "calcium_sulfite_slurry"
	};

	/** Eagerly load the built-in species so startup consumers (creative tab, JEI) see them. */
	public static void loadBuiltin() {
		int loaded = 0;
		for (String name : BUILTIN_SPECIES) {
			ResourceLocation key = new ResourceLocation(ChemicalAddon.MODID, name);
			if (REGISTRY.containsKey(key)) {
				continue;
			}
			String path = "/data/" + ChemicalAddon.MODID + "/chemistry/species/" + name + ".json";
			try (InputStream in = SpeciesManager.class.getResourceAsStream(path)) {
				if (in == null) {
					continue;
				}
				String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
				Species species = Species.parse(key, GSON.fromJson(content, JsonElement.class));
				if (species != null) {
					REGISTRY.put(key, species);
					loaded++;
				}
			} catch (IOException e) {
				ChemicalAddon.LOGGER.error("Failed to preload builtin species {}", name, e);
			}
		}
		if (loaded > 0) {
			ChemicalAddon.LOGGER.info("Chemical Addon: preloaded {} builtin species", loaded);
		}
	}

	private SpeciesManager() {
	}
}
