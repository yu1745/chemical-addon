package com.yu1745.chemicaladdon.reactor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.simibubi.create.AllSpriteShifts;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;

import net.createmod.catnip.data.Iterate;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelData.Builder;
import net.minecraftforge.client.model.data.ModelProperty;

/**
 * CTM shell model for the chemical brick, mirroring Create's FluidTankModel:
 * drops the interior faces of an assembled multiblock (two bricks of the same
 * vessel do not draw their shared wall) and hands the remaining faces to the
 * connected-texture behaviour for seamless sprite remapping. Reuses Create's
 * FluidTank sprite shifts, so the shell gets Create's tank sheets for free.
 */
public class ChemicalTankModel extends CTModel {

	protected static final ModelProperty<CullData> CULL_PROPERTY = new ModelProperty<>();

	public static ChemicalTankModel standard(BakedModel originalModel) {
		return new ChemicalTankModel(originalModel, AllSpriteShifts.FLUID_TANK, AllSpriteShifts.FLUID_TANK_TOP,
			AllSpriteShifts.FLUID_TANK_INNER);
	}

	private ChemicalTankModel(BakedModel originalModel, CTSpriteShiftEntry side, CTSpriteShiftEntry top,
		CTSpriteShiftEntry inner) {
		super(originalModel, new ChemicalTankCTBehaviour(side, top, inner));
	}

	@Override
	protected ModelData.Builder gatherModelData(Builder builder, BlockAndTintGetter world, BlockPos pos,
		BlockState state, ModelData blockEntityData) {
		super.gatherModelData(builder, world, pos, state, blockEntityData);
		CullData cullData = new CullData();
		for (Direction d : Iterate.horizontalDirections) {
			cullData.setCulled(d, ChemicalBrickBlockEntity.isConnected(world, pos, pos.relative(d)));
		}
		return builder.with(CULL_PROPERTY, cullData);
	}

	@Override
	public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData extraData,
		RenderType renderType) {
		if (side != null) {
			return Collections.emptyList();
		}
		List<BakedQuad> quads = new ArrayList<>();
		for (Direction d : Iterate.directions) {
			if (extraData.has(CULL_PROPERTY) && extraData.get(CULL_PROPERTY).isCulled(d)) {
				continue;
			}
			quads.addAll(super.getQuads(state, d, rand, extraData, renderType));
		}
		quads.addAll(super.getQuads(state, null, rand, extraData, renderType));
		return quads;
	}

	private static class CullData {
		boolean[] culledFaces;

		public CullData() {
			culledFaces = new boolean[4];
			Arrays.fill(culledFaces, false);
		}

		void setCulled(Direction face, boolean cull) {
			if (face.getAxis().isVertical()) {
				return;
			}
			culledFaces[face.get2DDataValue()] = cull;
		}

		boolean isCulled(Direction face) {
			if (face.getAxis().isVertical()) {
				return false;
			}
			return culledFaces[face.get2DDataValue()];
		}
	}
}
