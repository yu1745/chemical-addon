package com.yu1745.chemicaladdon.recipe;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import net.minecraft.util.GsonHelper;

/**
 * Optional operating window attached to a chemical reaction recipe.
 *
 * <p>Temperature is in °C and pressure is in the same gauge kPa scale exposed
 * by {@code ProcessReadings}. Agitation is deliberately data-only for now:
 * there is no reliable agitation reading in the current vessel, so its bounds
 * round-trip but are not used to accept or reject a recipe.</p>
 */
public final class ReactionConditions {
	private final Double temperatureMin;
	private final Double temperatureMax;
	private final Double pressureKpaMin;
	private final Double pressureKpaMax;
	private final Double agitationMin;
	private final Double agitationMax;

	private ReactionConditions(Double temperatureMin, Double temperatureMax, Double pressureKpaMin,
		Double pressureKpaMax, Double agitationMin, Double agitationMax) {
		validateRange("temperature", temperatureMin, temperatureMax);
		validateRange("pressureKpa", pressureKpaMin, pressureKpaMax);
		validateRange("agitation", agitationMin, agitationMax);
		this.temperatureMin = temperatureMin;
		this.temperatureMax = temperatureMax;
		this.pressureKpaMin = pressureKpaMin;
		this.pressureKpaMax = pressureKpaMax;
		this.agitationMin = agitationMin;
		this.agitationMax = agitationMax;
	}

	public static ReactionConditions none() {
		return new ReactionConditions(null, null, null, null, null, null);
	}

	public static ReactionConditions of(Double temperatureMin, Double temperatureMax, Double pressureKpaMin,
		Double pressureKpaMax, Double agitationMin, Double agitationMax) {
		return new ReactionConditions(temperatureMin, temperatureMax, pressureKpaMin, pressureKpaMax, agitationMin,
			agitationMax);
	}

	public static ReactionConditions fromJson(JsonObject json) {
		if (json == null) {
			throw new JsonSyntaxException("conditions must be an object");
		}
		return of(readBound(json, "temperature", "min"), readBound(json, "temperature", "max"),
			readBound(json, "pressureKpa", "min"), readBound(json, "pressureKpa", "max"),
			readBound(json, "agitation", "min"), readBound(json, "agitation", "max"));
	}

	private static Double readBound(JsonObject parent, String rangeName, String boundName) {
		if (!GsonHelper.isValidNode(parent, rangeName)) {
			return null;
		}
		JsonObject range = GsonHelper.getAsJsonObject(parent, rangeName);
		if (!GsonHelper.isValidNode(range, boundName)) {
			return null;
		}
		double value = GsonHelper.getAsDouble(range, boundName);
		if (!Double.isFinite(value)) {
			throw new JsonSyntaxException(rangeName + "." + boundName + " must be finite");
		}
		return value;
	}

	private static void validateRange(String name, Double min, Double max) {
		if (min != null && !Double.isFinite(min) || max != null && !Double.isFinite(max)) {
			throw new JsonSyntaxException(name + " bounds must be finite");
		}
		if (min != null && max != null && min > max) {
			throw new JsonSyntaxException(name + ".min must not exceed " + name + ".max");
		}
	}

	public Double temperatureMin() {
		return temperatureMin;
	}

	public Double temperatureMax() {
		return temperatureMax;
	}

	public Double pressureKpaMin() {
		return pressureKpaMin;
	}

	public Double pressureKpaMax() {
		return pressureKpaMax;
	}

	public Double agitationMin() {
		return agitationMin;
	}

	public Double agitationMax() {
		return agitationMax;
	}

	public boolean hasTemperature() {
		return temperatureMin != null || temperatureMax != null;
	}

	public boolean hasPressureKpa() {
		return pressureKpaMin != null || pressureKpaMax != null;
	}

	/** True when the recipe carries agitation metadata; it is not enforced yet. */
	public boolean hasAgitation() {
		return agitationMin != null || agitationMax != null;
	}

	public boolean matchesTemperature(double value) {
		return (temperatureMin == null || value >= temperatureMin)
			&& (temperatureMax == null || value <= temperatureMax);
	}

	public boolean matchesPressureKpa(double value) {
		return (pressureKpaMin == null || value >= pressureKpaMin)
			&& (pressureKpaMax == null || value <= pressureKpaMax);
	}

	/** Serialize only fields that were specified; an empty object is valid. */
	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		writeRange(json, "temperature", temperatureMin, temperatureMax);
		writeRange(json, "pressureKpa", pressureKpaMin, pressureKpaMax);
		writeRange(json, "agitation", agitationMin, agitationMax);
		return json;
	}

	private static void writeRange(JsonObject parent, String name, Double min, Double max) {
		if (min == null && max == null) {
			return;
		}
		JsonObject range = new JsonObject();
		if (min != null) {
			range.addProperty("min", min);
		}
		if (max != null) {
			range.addProperty("max", max);
		}
		parent.add(name, range);
	}
}
