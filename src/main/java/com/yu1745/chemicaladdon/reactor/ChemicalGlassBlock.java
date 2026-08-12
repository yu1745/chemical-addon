package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Transparent structural block of the vessel shell — the "seared glass" of the
 * series. Whether the reactor walls are see-through is decided by which
 * material the player builds with: {@link ChemicalBrickBlock} is opaque,
 * this block is transparent (you can watch the fluid from the side). Reuses
 * the brick's proxy BE so both count as the same structural series.
 */
public class ChemicalGlassBlock extends Block implements EntityBlock {

	public ChemicalGlassBlock(Properties properties) {
		super(properties);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ChemicalBrickBlockEntity(pos, state);
	}

	@Override
	public boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
		// adjacent glass blocks share the interior face (Tinkers seared glass behaviour)
		return adjacent.is(this) || super.skipRendering(state, adjacent, side);
	}

	@Override
	public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
		return true;
	}

	@Override
	public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
		return 0;
	}
}
