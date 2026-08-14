/*
 * Vendored from Mantle (https://github.com/SlimeKnights/Mantle, 1.20 branch),
 * slimeknights.mantle.client.model.connected.ConnectedModelRegistry, trimmed to the
 * types we use ("block" predicate, "cornerless_full" texture mapping).
 * Copyright (c) SlimeKnights — MIT License. Attribution notice in THIRD_PARTY.md.
 */
package com.yu1745.chemicaladdon.client.connected;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import net.minecraft.util.GsonHelper;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/** Place to register data related to connected block models. */
public class ConnectedModelRegistry {

	/** Default state predicate, compares the blocks for equality */
	private static final BiPredicate<BlockState, BlockState> BLOCK_CONNECTION_PREDICATE =
		(s1, s2) -> s1.getBlock() == s2.getBlock();
	private static final Map<String, BiPredicate<BlockState, BlockState>> CONNECTION_PREDICATES = new HashMap<>();

	public static void registerPredicate(String name, BiPredicate<BlockState, BlockState> predicate) {
		CONNECTION_PREDICATES.putIfAbsent(name, predicate);
	}

	public static BiPredicate<BlockState, BlockState> deserializePredicate(JsonObject json, String key) {
		String name = GsonHelper.getAsString(json, key, "block");
		if (!CONNECTION_PREDICATES.containsKey(name)) {
			throw new JsonSyntaxException("Unknown connection predicate " + name);
		}
		return CONNECTION_PREDICATES.get(name);
	}

	public static BiPredicate<BlockState, BlockState> getPredicate(String name) {
		return CONNECTION_PREDICATES.getOrDefault(name, BLOCK_CONNECTION_PREDICATE);
	}

	static {
		registerPredicate("block", BLOCK_CONNECTION_PREDICATE);
	}

	/* Texture mapping */

	private static final Map<String, String[]> CONNECTION_TYPES = new HashMap<>();

	/**
	 * Registers a connection type
	 * @param name    Type name
	 * @param mapper  Function of predicate to texture name. Predicate will match NSWE, signifying the texture connects UDLR
	 */
	public static void registerType(String name, Function<Predicate<Direction>, String> mapper) {
		if (!CONNECTION_TYPES.containsKey(name)) {
			String[] suffixes = new String[16];
			for (int i = 0; i < 16; i++) {
				final int index = i;
				suffixes[i] = mapper.apply((dir) -> {
					int flag = 1 << dir.get2DDataValue();
					return (index & flag) == flag;
				});
			}
			CONNECTION_TYPES.put(name, suffixes);
		}
	}

	public static String[] deserializeType(JsonElement json, String key) {
		String name = GsonHelper.convertToString(json, key);
		if (!CONNECTION_TYPES.containsKey(name)) {
			throw new JsonSyntaxException("Unknown connection type " + name);
		}
		return CONNECTION_TYPES.get(name);
	}

	static {
		// connects on all four sides: suffix = which of up/down/left/right edges connect
		registerType("cornerless_full", predicate -> {
			String name = "";
			if (predicate.test(Direction.NORTH)) name += "u";
			if (predicate.test(Direction.SOUTH)) name += "d";
			if (predicate.test(Direction.WEST)) name += "l";
			if (predicate.test(Direction.EAST)) name += "r";
			return name;
		});
	}
}
