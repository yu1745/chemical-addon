package com.yu1745.chemicaladdon.composition;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.yu1745.chemicaladdon.ChemicalAddon;

import net.minecraft.resources.ResourceLocation;

/**
 * A chemical species definition, loaded from datapack JSON
 * ({@code data/<mod>/chemistry/species/*.json}).
 *
 * <p>Modeled after Tinkers' JSON modifier architecture: definitions are
 * data-driven, ids are ResourceLocations, and mixtures are expressed as
 * base species + components with concentration caps (composition system).
 */
public final class Species {

	public enum Phase {
		GAS, LIQUID, SOLID
	}

	public static final class Component {
		private final ResourceLocation species;
		private final float maxConcentration;

		public Component(ResourceLocation species, float maxConcentration) {
			this.species = species;
			this.maxConcentration = maxConcentration;
		}

		public ResourceLocation species() {
			return species;
		}

		public float maxConcentration() {
			return maxConcentration;
		}
	}

	private final ResourceLocation id;
	private final String formula;
	private final Phase phase;
	private final int boilingPointC;
	private final int meltingPointC;
	private final List<Component> components;
	private final Set<String> dangers;

	private Species(ResourceLocation id, String formula, Phase phase, int boilingPointC, int meltingPointC,
		List<Component> components, Set<String> dangers) {
		this.id = id;
		this.formula = formula;
		this.phase = phase;
		this.boilingPointC = boilingPointC;
		this.meltingPointC = meltingPointC;
		this.components = components;
		this.dangers = dangers;
	}

	/** Parses a species JSON; returns null (with a logged error) on failure. */
	@Nullable
	public static Species parse(ResourceLocation id, JsonElement json) {
		try {
			JsonObject o = json.getAsJsonObject();
			String formula = getString(o, "formula", id.getPath());
			Phase phase = Phase.valueOf(getString(o, "phase", "LIQUID").toUpperCase(Locale.ROOT));
			int bp = getInt(o, "boilingPointC", 0);
			int mp = getInt(o, "meltingPointC", 0);

			List<Component> components = new ArrayList<>();
			if (o.has("components")) {
				for (JsonElement e : o.getAsJsonArray("components")) {
					JsonObject c = e.getAsJsonObject();
					ResourceLocation sid = ResourceLocation.tryParse(getString(c, "species", ""));
					if (sid == null) {
						continue;
					}
					components.add(new Component(sid, getFloat(c, "maxConcentration", 1.0f)));
				}
			}

			Set<String> dangers = new LinkedHashSet<>();
			if (o.has("dangers")) {
				for (JsonElement e : o.getAsJsonArray("dangers")) {
					dangers.add(e.getAsString());
				}
			}

			return new Species(id, formula, phase, bp, mp, List.copyOf(components), Set.copyOf(dangers));
		} catch (Exception e) {
			ChemicalAddon.LOGGER.error("Failed to parse species {}: {}", id, e.getMessage());
			return null;
		}
	}

	public ResourceLocation id() {
		return id;
	}

	public String formula() {
		return formula;
	}

	public Phase phase() {
		return phase;
	}

	public int boilingPointC() {
		return boilingPointC;
	}

	public int meltingPointC() {
		return meltingPointC;
	}

	public List<Component> components() {
		return components;
	}

	public Set<String> dangers() {
		return dangers;
	}

	/** True if this species is a mixture (has components). */
	public boolean isCompound() {
		return !components.isEmpty();
	}

	private static String getString(JsonObject o, String key, String def) {
		return o.has(key) ? o.get(key).getAsString() : def;
	}

	private static int getInt(JsonObject o, String key, int def) {
		return o.has(key) ? o.get(key).getAsInt() : def;
	}

	private static float getFloat(JsonObject o, String key, float def) {
		return o.has(key) ? o.get(key).getAsFloat() : def;
	}
}
