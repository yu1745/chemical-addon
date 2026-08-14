/*
 * Vendored from Mantle (https://github.com/SlimeKnights/Mantle, 1.20 branch),
 * slimeknights.mantle.block.IMultipartConnectedBlock.
 * Copyright (c) SlimeKnights — MIT License. Attribution notice in THIRD_PARTY.md.
 */
package com.yu1745.chemicaladdon.client.connected;

import com.mojang.datafixers.util.Pair;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.core.Direction;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Used in {@link ConnectedModel} to workaround Forge #6841: mirrors the model's
 * neighbour scan into blockstate properties so the connection survives even
 * without model data (multipart / weighted random contexts).
 */
public interface IMultipartConnectedBlock {
	/** Map of direction to boolean property for that direction */
	Map<Direction, BooleanProperty> CONNECTED_DIRECTIONS = Arrays.stream(Direction.values())
		.map(dir -> Pair.of(dir, BooleanProperty.create("connected_" + dir.getSerializedName())))
		.collect(Collectors.toMap(Pair::getFirst, Pair::getSecond, (u, v) -> u, () -> new EnumMap<>(Direction.class)));

	/** Applies false to all directions in the state, for use in the block constructor. */
	static BlockState defaultConnections(BlockState state) {
		for (BooleanProperty prop : CONNECTED_DIRECTIONS.values()) {
			state = state.setValue(prop, false);
		}
		return state;
	}

	/** Fills a state container, for use in {@code createBlockStateDefinition}. */
	static void fillStateContainer(Builder<Block, BlockState> builder) {
		CONNECTED_DIRECTIONS.values().forEach(builder::add);
	}

	/** Checks if the block connects to the given neighbor */
	default boolean connects(BlockState state, BlockState neighbor) {
		return state.getBlock() == neighbor.getBlock();
	}

	/** Gets the new connected state based on the given block update */
	default BlockState getConnectionUpdate(BlockState state, Direction facing, BlockState neighbor) {
		return state.setValue(CONNECTED_DIRECTIONS.get(facing), connects(state, neighbor));
	}
}
