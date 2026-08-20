package com.yu1745.chemicaladdon.composition;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;

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
			Chemistry.LOGGER.info("Chemical Addon: loaded {} species definitions", REGISTRY.size());
		}
	};

	@Nullable
	public static Species get(ResourceLocation id) {
		return REGISTRY.get(id);
	}

	/**
	 * The solution species that crystallises into the given solid solute
	 * (reverse lookup of {@link Species#solute()} — the grain/seeding and
	 * mixed-residue paths map an extracted solid back to the ions it dissolves
	 * into). Nullable when no curve species targets this solute.
	 */
	@Nullable
	public static Species bySolute(ResourceLocation solute) {
		for (Species s : REGISTRY.values()) {
			if (s.isCrystallisable() && solute.equals(s.solute())) {
				return s;
			}
		}
		return null;
	}

	public static Collection<Species> all() {
		return List.copyOf(REGISTRY.values());
	}

	/**
	 * All equilibrium entries across every species — the global list the solver
	 * consumes (PHREEQC's PHASES/SOLUTION_SPECIES organisation: the carrier
	 * file is only where the entry was authored). Sorted <b>aqueous entries
	 * (complexation) first, then minerals by log_k ascending</b>: complexation
	 * happens in solution, so a ligand ties up its metal before any mineral can
	 * nucleate from it (ammonia masking copper against carbonate precipitation)
	 * — the relaxation is order-sensitive, and this order encodes that
	 * chemistry.
	 */
	public static List<Equilibrium> allEquilibria() {
		List<Equilibrium> out = new ArrayList<>();
		for (Species s : REGISTRY.values()) {
			out.addAll(s.equilibria());
		}
		out.sort((a, b) -> {
			int byKind = Boolean.compare(!a.isAqueous(), !b.isAqueous()); // aqueous first
			return byKind != 0 ? byKind : Double.compare(a.logK(), b.logK());
		});
		return out;
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
		"gypsum_slurry", "sodium_bicarbonate_slurry", "calcium_sulfite_slurry",
		"copper_sulfate", "copper_sulfate_solution", "copper_carbonate",
		// U14 engine-data roster (species JSON only — no items/fluids registered)
		"silver_chloride", "barium_sulfate", "barium_carbonate", "silver_carbonate",
		"magnesium_hydroxide", "magnesium_carbonate", "copper_hydroxide", "zinc_hydroxide",
		"iron_hydroxide", "aluminium_hydroxide",
		"silver_nitrate_solution", "ferric_chloride_solution", "zinc_sulfate_solution",
		"potassium_nitrate_solution", "potassium_chloride_solution", "potassium_alum_solution",
		"ferrous_sulfate_solution", "potassium_thiocyanate_solution",
		// U15 bittern salts (bittern-salt curves: the dry-out residue of brine)
		"magnesium_chloride_solution",
		// P4b 伪池宿主（species JSON only）：次氯酸钠/亚硫酸钠溶液——
		// EngineBridge 映射 OCl→Hyp、SO3→Sul 介稳池（见 parity 包）
		"sodium_hypochlorite", "sodium_sulphite_solution",
		"sodium_nitrate_solution", "sodium_nitrite"
	};

	/** Register a species programmatically — JUnit kinetics tests injecting rate-bearing entries. */
	static void registerForTest(Species species) {
		REGISTRY.put(species.id(), species);
	}

	/** Eagerly load the built-in species so startup consumers (creative tab, JEI) see them. */
	public static void loadBuiltin() {		int loaded = 0;
		for (String name : BUILTIN_SPECIES) {
			ResourceLocation key = new ResourceLocation(Chemistry.MOD_ID, name);
			if (REGISTRY.containsKey(key)) {
				continue;
			}
			String path = "/data/" + Chemistry.MOD_ID + "/chemistry/species/" + name + ".json";
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
				Chemistry.LOGGER.error("Failed to preload builtin species {}", name, e);
			}
		}
		if (loaded > 0) {
			Chemistry.LOGGER.info("Chemical Addon: preloaded {} builtin species", loaded);
		}
	}

	private SpeciesManager() {
	}
}
