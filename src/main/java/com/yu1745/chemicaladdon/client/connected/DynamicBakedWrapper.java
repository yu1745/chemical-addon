/*
 * Vendored from Mantle (https://github.com/SlimeKnights/Mantle, 1.20 branch),
 * slimeknights.mantle.client.model.util.DynamicBakedWrapper.
 * Copyright (c) SlimeKnights — MIT License. Attribution notice in THIRD_PARTY.md.
 */
package com.yu1745.chemicaladdon.client.connected;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * Wrapper to create a baked model with a dynamic
 * {@link #getQuads(BlockState, Direction, RandomSource, ModelData, RenderType)} without
 * worrying about overriding the deprecated variant.
 */
public abstract class DynamicBakedWrapper<T extends BakedModel> extends BakedModelWrapper<T> {

	protected DynamicBakedWrapper(T originalModel) {
		super(originalModel);
	}

	@Override
	@Deprecated
	public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
		return this.getQuads(state, side, rand, ModelData.EMPTY, null);
	}

	@Override
	@Nonnull
	public abstract List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType);
}
