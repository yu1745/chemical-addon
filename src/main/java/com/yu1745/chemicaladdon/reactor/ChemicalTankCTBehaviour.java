package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.HorizontalCTBehaviour;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Connected-texture behaviour for the chemical brick shell (mirrors Create's
 * FluidTankCTBehaviour): two bricks connect their textures only when they are
 * part of the same assembled multiblock (same master position). Uses Create's
 * FluidTank sprite shifts, so the shell reuses the Create tank's seamless
 * connected-texture sheets.
 */
public class ChemicalTankCTBehaviour extends HorizontalCTBehaviour {

	private final CTSpriteShiftEntry innerShift;

	public ChemicalTankCTBehaviour(CTSpriteShiftEntry layerShift, CTSpriteShiftEntry topShift,
		CTSpriteShiftEntry innerShift) {
		super(layerShift, topShift);
		this.innerShift = innerShift;
	}

	@Override
	public CTSpriteShiftEntry getShift(BlockState state, Direction direction, @Nullable TextureAtlasSprite sprite) {
		if (sprite != null && direction.getAxis() == Direction.Axis.Y && innerShift.getOriginal() == sprite) {
			return innerShift;
		}
		return super.getShift(state, direction, sprite);
	}

	@Override
	public boolean buildContextForOccludedDirections() {
		// the shared internal faces of an assembled shell still need CTM data
		return true;
	}

	@Override
	public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter reader, BlockPos pos,
		BlockPos otherPos, Direction face) {
		return state.getBlock() == other.getBlock() && ChemicalBrickBlockEntity.isConnected(reader, pos, otherPos);
	}
}
