package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import com.yu1745.chemicaladdon.client.connected.IMultipartConnectedBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Transparent structural block of the vessel shell — the "seared glass" of the
 * series. Whether the reactor walls are see-through is decided by which
 * material the player builds with: {@link ChemicalBrickBlock} is opaque,
 * this block is transparent (you can watch the fluid from the side). Extends
 * the brick so placement/breakage re-forms and de-assembles the structure
 * exactly like a brick (U3 fix: it previously had no lifecycle hooks, so a
 * broken glass wall block silently left the vessel assembled with a hole).
 *
 * <p>Connected textures (Tinkers clear-glass behaviour, machinery vendored
 * from Mantle — see {@code client.connected} package): the six
 * {@code connected_*} blockstate properties mirror the connected model
 * loader's neighbour scan, and — like vanilla glass — faces between
 * adjacent glass of the same kind are culled: only the merged outer faces
 * render, otherwise the doubled inner faces would show a seam at each joint.
 */
public class ChemicalGlassBlock extends ChemicalBrickBlock implements IMultipartConnectedBlock {

	public ChemicalGlassBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(IMultipartConnectedBlock.defaultConnections(this.defaultBlockState()));
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ChemicalBrickBlockEntity(pos, state);
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		IMultipartConnectedBlock.fillStateContainer(builder);
	}

	@Override
	public BlockState updateShape(BlockState state, Direction facing, BlockState facingState, LevelAccessor level, BlockPos pos, BlockPos facingPos) {
		return getConnectionUpdate(super.updateShape(state, facing, facingState, level, pos, facingPos), facing, facingState);
	}

	/**
	 * Vanilla-glass behaviour ({@code HalfTransparentBlock#skipRendering}): the
	 * face between two adjacent glass blocks of the same kind is culled, so each
	 * joint is covered by the merged outer faces only. Without this the two
	 * coplanar inner faces both render and double-blend their alpha, leaving a
	 * visible seam line at every block boundary (the Tinkers block this mirrors
	 * is a plain {@code GlassBlock}, which skips those faces for the same reason).
	 */
	@Override
	public boolean skipRendering(BlockState state, BlockState adjacentBlockState, Direction side) {
		return adjacentBlockState.is(this) || super.skipRendering(state, adjacentBlockState, side);
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
