package com.yu1745.chemicaladdon.recipe;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

/**
 * A recipe reference to a solution <b>species</b> (a "mode", plans/03 §4) rather
 * than a registered fluid: it matches against the vessel's dissolved ions, not a
 * fluid registry entry.
 *
 * <p>Concentration is a <b>continuous</b> ratio ({@code ion mB / water mB}), not a
 * fixed "dilute vs concentrated" identity. {@code amount} counts the <b>solute
 * ions</b> (mole-equivalents) only; the water is a separate, orthogonal axis:
 * <ul>
 *   <li><b>input</b> — require &ge; {@code amount} ion mB <i>and</i> a vessel
 *       concentration within {@code [minConcentration, maxConcentration]};</li>
 *   <li><b>output</b> — pack {@code amount} ion mB at an exact concentration
 *       ({@code concentration} sets both bounds; min==max).</li>
 * </ul>
 */
public record SolutionIngredient(ResourceLocation speciesId, int amount, double minConcentration,
	double maxConcentration) {

	public SolutionIngredient(ResourceLocation speciesId, int amount) {
		this(speciesId, amount, 0, Double.POSITIVE_INFINITY);
	}

	public static SolutionIngredient fromJson(JsonObject json) {
		ResourceLocation speciesId = new ResourceLocation(GsonHelper.getAsString(json, "species"));
		int amount = GsonHelper.getAsInt(json, "amount");
		double minC = 0;
		double maxC = Double.POSITIVE_INFINITY;
		if (GsonHelper.isValidNode(json, "concentration")) {
			minC = maxC = GsonHelper.getAsDouble(json, "concentration");
		}
		if (GsonHelper.isValidNode(json, "minConcentration")) {
			minC = GsonHelper.getAsDouble(json, "minConcentration");
		}
		if (GsonHelper.isValidNode(json, "maxConcentration")) {
			maxC = GsonHelper.getAsDouble(json, "maxConcentration");
		}
		return new SolutionIngredient(speciesId, amount, minC, maxC);
	}

	/** True when the vessel concentration must be within this range (input). */
	public boolean hasConcentrationRange() {
		return minConcentration > 0 || Double.isFinite(maxConcentration);
	}

	/** Exact pack concentration for an output (min == max); NaN when not a point. */
	public double targetConcentration() {
		return minConcentration == maxConcentration ? minConcentration : Double.NaN;
	}

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("species", speciesId.toString());
		o.addProperty("amount", amount);
		if (minConcentration == maxConcentration) {
			o.addProperty("concentration", minConcentration);
		} else {
			if (minConcentration > 0) {
				o.addProperty("minConcentration", minConcentration);
			}
			if (Double.isFinite(maxConcentration)) {
				o.addProperty("maxConcentration", maxConcentration);
			}
		}
		return o;
	}

	public static List<SolutionIngredient> listFromJson(JsonArray array) {
		List<SolutionIngredient> out = new ArrayList<>();
		for (JsonElement e : array) {
			out.add(fromJson(e.getAsJsonObject()));
		}
		return out;
	}

	public static JsonArray listToJson(List<SolutionIngredient> list) {
		JsonArray array = new JsonArray();
		for (SolutionIngredient s : list) {
			array.add(s.toJson());
		}
		return array;
	}
}
