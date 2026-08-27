package com.yu1745.chemicaladdon.recipe;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.yu1745.chemicaladdon.vessel.ProcessCapability;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Network representation of ChemicalReactionRecipe's custom fields.
 * Create's ProcessingRecipeSerializer only transports its base fields, so
 * this small codec keeps the addon fields in one explicit, version-local
 * sequence. It intentionally contains no recipe matching or execution logic.
 */
public record ReactionRecipeNetworkData(int deltaHeat, List<SolutionIngredient> solutions,
	List<SolutionIngredient> solutionOutputs, Set<ProcessCapability> requiredCapabilities,
	Set<ResourceLocation> requiredParts, ReactionConditions conditions, boolean capabilitiesSpecified,
	boolean partsSpecified, boolean conditionsSpecified) {

	public ReactionRecipeNetworkData {
		solutions = List.copyOf(solutions);
		solutionOutputs = List.copyOf(solutionOutputs);
		Set<ProcessCapability> capabilityCopy = requiredCapabilities.isEmpty()
			? EnumSet.noneOf(ProcessCapability.class)
			: EnumSet.copyOf(requiredCapabilities);
		requiredCapabilities = Collections.unmodifiableSet(capabilityCopy);
		requiredParts = Collections.unmodifiableSet(new LinkedHashSet<>(requiredParts));
		conditions = conditions == null ? ReactionConditions.none() : conditions;
	}

	public static void write(FriendlyByteBuf buffer, ReactionRecipeNetworkData data) {
		buffer.writeInt(data.deltaHeat());
		writeSolutions(buffer, data.solutions());
		writeSolutions(buffer, data.solutionOutputs());
		buffer.writeBoolean(data.capabilitiesSpecified());
		if (data.capabilitiesSpecified()) {
			buffer.writeVarInt(data.requiredCapabilities().size());
			for (ProcessCapability capability : data.requiredCapabilities()) {
				buffer.writeUtf(capability.jsonName());
			}
		}
		buffer.writeBoolean(data.partsSpecified());
		if (data.partsSpecified()) {
			buffer.writeVarInt(data.requiredParts().size());
			for (ResourceLocation part : data.requiredParts()) {
				buffer.writeResourceLocation(part);
			}
		}
		buffer.writeBoolean(data.conditionsSpecified());
		if (data.conditionsSpecified()) {
			writeBound(buffer, data.conditions().temperatureMin());
			writeBound(buffer, data.conditions().temperatureMax());
			writeBound(buffer, data.conditions().pressureKpaMin());
			writeBound(buffer, data.conditions().pressureKpaMax());
			writeBound(buffer, data.conditions().agitationMin());
			writeBound(buffer, data.conditions().agitationMax());
		}
	}

	public static ReactionRecipeNetworkData read(FriendlyByteBuf buffer) {
		int deltaHeat = buffer.readInt();
		List<SolutionIngredient> solutions = readSolutions(buffer);
		List<SolutionIngredient> outputs = readSolutions(buffer);
		boolean capabilitiesSpecified = buffer.readBoolean();
		Set<ProcessCapability> capabilities = EnumSet.noneOf(ProcessCapability.class);
		if (capabilitiesSpecified) {
			int count = boundedCount(buffer.readVarInt(), "capability");
			for (int i = 0; i < count; i++) {
				capabilities.add(ProcessCapability.fromJsonName(buffer.readUtf(64)));
			}
		}
		boolean partsSpecified = buffer.readBoolean();
		Set<ResourceLocation> parts = new LinkedHashSet<>();
		if (partsSpecified) {
			int count = boundedCount(buffer.readVarInt(), "part");
			for (int i = 0; i < count; i++) {
				parts.add(buffer.readResourceLocation());
			}
		}
		boolean conditionsSpecified = buffer.readBoolean();
		ReactionConditions conditions = conditionsSpecified
			? ReactionConditions.of(readBound(buffer), readBound(buffer), readBound(buffer), readBound(buffer),
				readBound(buffer), readBound(buffer))
			: ReactionConditions.none();
		return new ReactionRecipeNetworkData(deltaHeat, solutions, outputs, capabilities, parts, conditions,
			capabilitiesSpecified, partsSpecified, conditionsSpecified);
	}

	private static void writeSolutions(FriendlyByteBuf buffer, List<SolutionIngredient> list) {
		buffer.writeVarInt(list.size());
		for (SolutionIngredient solution : list) {
			buffer.writeResourceLocation(solution.speciesId());
			buffer.writeInt(solution.amount());
			buffer.writeDouble(solution.minConcentration());
			buffer.writeDouble(solution.maxConcentration());
		}
	}

	private static List<SolutionIngredient> readSolutions(FriendlyByteBuf buffer) {
		int count = boundedCount(buffer.readVarInt(), "solution");
		java.util.ArrayList<SolutionIngredient> list = new java.util.ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			list.add(new SolutionIngredient(buffer.readResourceLocation(), buffer.readInt(), buffer.readDouble(),
				buffer.readDouble()));
		}
		return list;
	}

	private static int boundedCount(int count, String kind) {
		if (count < 0 || count > 1024) {
			throw new IllegalArgumentException("Invalid chemical reaction " + kind + " count: " + count);
		}
		return count;
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
}
