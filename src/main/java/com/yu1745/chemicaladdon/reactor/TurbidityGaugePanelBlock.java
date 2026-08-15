package com.yu1745.chemicaladdon.reactor;

import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The thin turbidity gauge plate (S18 电导率计, 薄板): a face-mounted 2px
 * panel reading the vessel behind its mounting face — the S02/S03 dual-form
 * pattern. {@code FACING} points away from the vessel (toward the player);
 * redstone matches the wall form (see
 * {@link AbstractTurbidityGaugeBlockEntity#at}).
 */
public class TurbidityGaugePanelBlock extends DirectionalBlock implements EntityBlock {

	// The plate sits flush against the wall it is mounted on (FACING.getOpposite()).
	private static final Map<Direction, VoxelShape> SHAPES = Map.of(
		Direction.NORTH, box(0, 0, 14, 16, 16, 16), // wall to the south
		Direction.SOUTH, box(0, 0, 0, 16, 16, 2),   // wall to the north
		Direction.WEST, box(14, 0, 0, 16, 16, 16),  // wall to the east
		Direction.EAST, box(0, 0, 0, 2, 16, 16),    // wall to the west
		Direction.DOWN, box(0, 14, 0, 16, 16, 16),  // wall above
		Direction.UP, box(0, 0, 0, 16, 2, 16));     // wall below

	public TurbidityGaugePanelBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getClickedFace());
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rot) {
		return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.getOrDefault(state.getValue(FACING), SHAPES.get(Direction.NORTH));
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TurbidityGaugePanelBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return (lvl, pos, st, be) -> {
			if (be instanceof TurbidityGaugePanelBlockEntity panel) {
				panel.tick();
			}
		};
	}

	@Override
	public boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
		AbstractTurbidityGaugeBlockEntity gauge = AbstractTurbidityGaugeBlockEntity.at(level, pos);
		return gauge != null ? gauge.alarmSignal() : 0;
	}

	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		AbstractTurbidityGaugeBlockEntity gauge = AbstractTurbidityGaugeBlockEntity.at(level, pos);
		return gauge != null ? gauge.analogSignal() : 0;
	}
}
