package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The full-cube baume gauge (S04 电导率计, 方块形式): a vessel shell block
 * that doubles as a baume gauge — the S03 dual-form pattern reading
 * density instead of pressure. Redstone is shared with the panel form
 * via {@link AbstractBaumeGaugeBlockEntity#at}; the alarm direction is
 * inverted (signal = baume fell to/below the setpoint).
 */
public class BaumeGaugeBlock extends ChemicalBrickBlock {

	public BaumeGaugeBlock(Properties properties) {
		super(properties);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new BaumeGaugeBlockEntity(pos, state);
	}

	/** Tick both sides: the server updates the alarm/redstone, the client refreshes the goggles reading. */
	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return (lvl, pos, st, be) -> {
			if (be instanceof BaumeGaugeBlockEntity gauge) {
				gauge.tick();
			}
		};
	}

	@Override
	public boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
		AbstractBaumeGaugeBlockEntity gauge = AbstractBaumeGaugeBlockEntity.at(level, pos);
		return gauge != null ? gauge.alarmSignal() : 0;
	}

	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		AbstractBaumeGaugeBlockEntity gauge = AbstractBaumeGaugeBlockEntity.at(level, pos);
		return gauge != null ? gauge.analogSignal() : 0;
	}
}
