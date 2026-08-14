package com.yu1745.chemicaladdon.reactor;

import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
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
 * The thin thermometer plate (薄板): a face-mounted 2px-thick panel. {@code FACING}
 * points away from the vessel (toward the player); the vessel is the block at
 * {@code FACING.getOpposite()}. Redstone matches the wall form (see
 * {@link AbstractThermometerBlockEntity#at}).
 */
public class ThermometerPanelBlock extends DirectionalBlock implements EntityBlock {

	// The plate sits flush against the wall it is mounted on (FACING.getOpposite()).
	private static final Map<Direction, VoxelShape> SHAPES = Map.of(
		Direction.NORTH, box(0, 0, 14, 16, 16, 16), // wall to the south
		Direction.SOUTH, box(0, 0, 0, 16, 16, 2),   // wall to the north
		Direction.WEST, box(14, 0, 0, 16, 16, 16),  // wall to the east
		Direction.EAST, box(0, 0, 0, 2, 16, 16),    // wall to the west
		Direction.DOWN, box(0, 14, 0, 16, 16, 16),  // wall above
		Direction.UP, box(0, 0, 0, 16, 2, 16));     // wall below

	public ThermometerPanelBlock(Properties properties) {
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
		return new ThermometerPanelBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return (lvl, pos, st, be) -> {
			if (be instanceof ThermometerPanelBlockEntity panel) {
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
		AbstractThermometerBlockEntity t = AbstractThermometerBlockEntity.at(level, pos);
		return t != null && t.isAlarm() ? 15 : 0;
	}

	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		AbstractThermometerBlockEntity t = AbstractThermometerBlockEntity.at(level, pos);
		return t != null ? Mth.clamp(t.getTemperature() * 15 / 1000, 0, 15) : 0;
	}
}
