package com.yu1745.chemicaladdon.recipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeParams;
import com.yu1745.chemicaladdon.vessel.ProcessCapability;
import com.yu1745.chemicaladdon.vessel.ProcessReadings;
import com.yu1745.chemicaladdon.vessel.StructureAccess;
import com.yu1745.chemicaladdon.vessel.StructureCapabilities;

import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
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
	private final List<SolutionIngredient> solutions = new ArrayList<>();
	private final List<SolutionIngredient> solutionOutputs = new ArrayList<>();
	/* Legacy recipes implicitly target the existing mixed-volume vessel. */
	private final Set<ProcessCapability> requiredCapabilities = EnumSet.of(ProcessCapability.MIXED_VOLUME);
	private final Set<ResourceLocation> requiredParts = new LinkedHashSet<>();
	private ReactionConditions conditions = ReactionConditions.none();
	private boolean capabilitiesSpecified;
	private boolean partsSpecified;
	private boolean conditionsSpecified;

	public ChemicalReactionRecipe(ProcessingRecipeParams params) {
		super(AllRecipeTypes.CHEMICAL_REACTION, params);
	}

	public int getDeltaHeat() {
		return deltaHeat;
	}

	/** Solution-species inputs (matched against the vessel's dissolved ions). */
	public List<SolutionIngredient> getSolutions() {
		return solutions;
	}

	/** Solution-species outputs (expanded into ions + water, no fluid entry needed). */
	public List<SolutionIngredient> getSolutionOutputs() {
		return solutionOutputs;
	}

	/** Structural capabilities required by this recipe (legacy default: mixed volume). */
	public Set<ProcessCapability> getRequiredCapabilities() {
		return Collections.unmodifiableSet(requiredCapabilities);
	}

	/** Required internal parts. Parsed and synchronized, but not enforced until a part snapshot exists. */
	public Set<ResourceLocation> getRequiredParts() {
		return Collections.unmodifiableSet(requiredParts);
	}

	public ReactionConditions getConditions() {
		return conditions;
	}

	/**
	 * Check the currently executable subset of structure requirements. Parts are
	 * intentionally excluded: the current structure snapshot cannot identify
	 * internal parts reliably. Agitation bounds are likewise data-only because
	 * ProcessReadings has no agitation measurement yet.
	 */
	public boolean matchesStructureRequirements(@javax.annotation.Nullable StructureAccess structure,
		@javax.annotation.Nullable ProcessReadings readings) {
		if (structure == null) {
			return false;
		}
		StructureCapabilities snapshot = structure.getStructureCapabilities();
		for (ProcessCapability capability : requiredCapabilities) {
			if (!snapshot.has(capability)) {
				return false;
			}
		}
		if (conditions.hasTemperature()
			&& (readings == null || !conditions.matchesTemperature(readings.getTemperature()))) {
			return false;
		}
		if (conditions.hasPressureKpa()
			&& (readings == null || !conditions.matchesPressureKpa(readings.getPressure()))) {
			return false;
		}
		return true;
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
		deltaHeat = 0;
		solutions.clear();
		solutionOutputs.clear();
		requiredCapabilities.clear();
		requiredCapabilities.add(ProcessCapability.MIXED_VOLUME);
		requiredParts.clear();
		capabilitiesSpecified = false;
		partsSpecified = false;
		conditionsSpecified = false;
		conditions = ReactionConditions.none();
		if (GsonHelper.isValidNode(json, "deltaHeat")) {
			deltaHeat = GsonHelper.getAsInt(json, "deltaHeat");
		}
		if (GsonHelper.isValidNode(json, "solutions")) {
			solutions.addAll(SolutionIngredient.listFromJson(json.getAsJsonArray("solutions")));
		}
		if (GsonHelper.isValidNode(json, "solutionOutputs")) {
			solutionOutputs.addAll(SolutionIngredient.listFromJson(json.getAsJsonArray("solutionOutputs")));
		}
		if (GsonHelper.isValidNode(json, "requiredCapabilities")) {
			capabilitiesSpecified = true;
			requiredCapabilities.clear();
			JsonArray array = GsonHelper.getAsJsonArray(json, "requiredCapabilities");
			for (JsonElement element : array) {
				if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
					throw new JsonSyntaxException("requiredCapabilities entries must be strings");
				}
				requiredCapabilities.add(ProcessCapability.fromJsonName(element.getAsString()));
			}
		}
		if (GsonHelper.isValidNode(json, "requiredParts")) {
			partsSpecified = true;
			JsonArray array = GsonHelper.getAsJsonArray(json, "requiredParts");
			for (JsonElement element : array) {
				if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
					throw new JsonSyntaxException("requiredParts entries must be resource locations");
				}
				requiredParts.add(parseResourceLocation(element.getAsString(), "requiredParts"));
			}
		}
		if (GsonHelper.isValidNode(json, "conditions")) {
			conditionsSpecified = true;
			conditions = ReactionConditions.fromJson(GsonHelper.getAsJsonObject(json, "conditions"));
		}
	}

	@Override
	public void writeAdditional(JsonObject json) {
		super.writeAdditional(json);
		if (deltaHeat != 0) {
			json.addProperty("deltaHeat", deltaHeat);
		}
		if (!solutions.isEmpty()) {
			json.add("solutions", SolutionIngredient.listToJson(solutions));
		}
		if (!solutionOutputs.isEmpty()) {
			json.add("solutionOutputs", SolutionIngredient.listToJson(solutionOutputs));
		}
		if (capabilitiesSpecified || !requiredCapabilities.equals(EnumSet.of(ProcessCapability.MIXED_VOLUME))) {
			JsonArray array = new JsonArray();
			for (ProcessCapability capability : requiredCapabilities) {
				array.add(capability.jsonName());
			}
			json.add("requiredCapabilities", array);
		}
		if (partsSpecified || !requiredParts.isEmpty()) {
			JsonArray array = new JsonArray();
			for (ResourceLocation part : requiredParts) {
				array.add(part.toString());
			}
			json.add("requiredParts", array);
		}
		if (conditionsSpecified || conditions.hasTemperature() || conditions.hasPressureKpa() || conditions.hasAgitation()) {
			json.add("conditions", conditions.toJson());
		}
	}

	@Override
	public void writeAdditional(FriendlyByteBuf buffer) {
		super.writeAdditional(buffer);
		buffer.writeInt(deltaHeat);
		writeSolutionList(buffer, solutions);
		writeSolutionList(buffer, solutionOutputs);
		buffer.writeBoolean(capabilitiesSpecified || !requiredCapabilities.equals(EnumSet.of(ProcessCapability.MIXED_VOLUME)));
		if (capabilitiesSpecified || !requiredCapabilities.equals(EnumSet.of(ProcessCapability.MIXED_VOLUME))) {
			buffer.writeVarInt(requiredCapabilities.size());
			for (ProcessCapability capability : requiredCapabilities) {
				buffer.writeUtf(capability.jsonName());
			}
		}
		buffer.writeBoolean(partsSpecified || !requiredParts.isEmpty());
		if (partsSpecified || !requiredParts.isEmpty()) {
			buffer.writeVarInt(requiredParts.size());
			for (ResourceLocation part : requiredParts) {
				buffer.writeResourceLocation(part);
			}
		}
		boolean writeConditions = conditionsSpecified || conditions.hasTemperature() || conditions.hasPressureKpa()
			|| conditions.hasAgitation();
		buffer.writeBoolean(writeConditions);
		if (writeConditions) {
			writeBound(buffer, conditions.temperatureMin());
			writeBound(buffer, conditions.temperatureMax());
			writeBound(buffer, conditions.pressureKpaMin());
			writeBound(buffer, conditions.pressureKpaMax());
			writeBound(buffer, conditions.agitationMin());
			writeBound(buffer, conditions.agitationMax());
		}
	}

	@Override
	public void readAdditional(FriendlyByteBuf buffer) {
		super.readAdditional(buffer);
		deltaHeat = buffer.readInt();
		solutions.clear();
		solutionOutputs.clear();
		requiredCapabilities.clear();
		requiredCapabilities.add(ProcessCapability.MIXED_VOLUME);
		requiredParts.clear();
		conditions = ReactionConditions.none();
		capabilitiesSpecified = false;
		partsSpecified = false;
		conditionsSpecified = false;
		readSolutionList(buffer, solutions);
		readSolutionList(buffer, solutionOutputs);
		capabilitiesSpecified = buffer.readBoolean();
		if (capabilitiesSpecified) {
			requiredCapabilities.clear();
			int count = buffer.readVarInt();
			for (int i = 0; i < count; i++) {
				requiredCapabilities.add(ProcessCapability.fromJsonName(buffer.readUtf(64)));
			}
		}
		partsSpecified = buffer.readBoolean();
		if (partsSpecified) {
			int count = buffer.readVarInt();
			for (int i = 0; i < count; i++) {
				requiredParts.add(buffer.readResourceLocation());
			}
		}
		conditionsSpecified = buffer.readBoolean();
		if (conditionsSpecified) {
			conditions = ReactionConditions.of(readBound(buffer), readBound(buffer), readBound(buffer),
				readBound(buffer), readBound(buffer), readBound(buffer));
		} else {
			conditions = ReactionConditions.none();
		}
	}

	private static void writeSolutionList(FriendlyByteBuf buffer, List<SolutionIngredient> list) {
		buffer.writeVarInt(list.size());
		for (SolutionIngredient solution : list) {
			buffer.writeResourceLocation(solution.speciesId());
			buffer.writeInt(solution.amount());
			buffer.writeDouble(solution.minConcentration());
			buffer.writeDouble(solution.maxConcentration());
		}
	}

	private static void readSolutionList(FriendlyByteBuf buffer, List<SolutionIngredient> target) {
		int count = buffer.readVarInt();
		if (count < 0 || count > 1024) {
			throw new IllegalArgumentException("Invalid chemical reaction solution count: " + count);
		}
		for (int i = 0; i < count; i++) {
			target.add(new SolutionIngredient(buffer.readResourceLocation(), buffer.readInt(), buffer.readDouble(),
				buffer.readDouble()));
		}
	}

	private static void writeBound(FriendlyByteBuf buffer, Double value) {
		buffer.writeBoolean(value != null);
		if (value != null) {
			buffer.writeDouble(value);
		}
	}

	private static Double readBound(FriendlyByteBuf buffer) {
		return buffer.readBoolean() ? buffer.readDouble() : null;
	}

	private static ResourceLocation parseResourceLocation(String value, String field) {
		try {
			return new ResourceLocation(value);
		} catch (RuntimeException ex) {
			throw new JsonSyntaxException(field + " contains an invalid resource location '" + value + "'", ex);
		}
	}
}
