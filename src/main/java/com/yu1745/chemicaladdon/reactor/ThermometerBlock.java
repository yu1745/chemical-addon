package com.yu1745.chemicaladdon.reactor;

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

/**
 * S02 thermometer (温度计): a face-mounted instrument block. {@code FACING} points away
 * from the vessel it reads (toward the player), so the reactor is the block at
 * {@code FACING.getOpposite()}. Redstone:
 * <ul>
 *   <li><b>comparator</b> — an analog signal proportional to the vessel temperature;</li>
 *   <li><b>strong signal</b> — 15 once the temperature reaches the alarm threshold.</li>
 * </ul>
 */
public class ThermometerBlock extends DirectionalBlock implements EntityBlock {

	public ThermometerBlock(Properties properties) {
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

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ThermometerBlockEntity(pos, state);
	}

	/** Tick both sides: the server updates the alarm/redstone, the client refreshes the goggles reading. */
	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return (lvl, pos, st, be) -> {
			if (be instanceof ThermometerBlockEntity thermometer) {
				thermometer.tick();
			}
		};
	}

	// ---- strong redstone: alarm when the temperature reaches the threshold ----

	@Override
	public boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
		if (level instanceof Level lvl && lvl.getBlockEntity(pos) instanceof ThermometerBlockEntity thermometer
			&& thermometer.isAlarm()) {
			return 15;
		}
		return 0;
	}

	// ---- analog (comparator) redstone: temperature reading ----

	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof ThermometerBlockEntity thermometer) {
			return Mth.clamp(thermometer.getTemperature() * 15 / 1000, 0, 15);
		}
		return 0;
	}
}
