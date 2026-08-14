package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The full-cube pressure gauge (方块形式): a vessel shell block that doubles as a
 * pressure gauge. It behaves exactly like {@link ChemicalBrickBlock} (auto
 * re-form on place, tear-down on break, capability proxy, in the vessel_walls
 * tag) and additionally drives overpressure redstone / goggles. The reading
 * logic lives in {@link PressureGaugeBlockEntity}; redstone is shared with the
 * panel form via {@link AbstractPressureGaugeBlockEntity#at}.
 */
public class PressureGaugeBlock extends ChemicalBrickBlock {

	public PressureGaugeBlock(Properties properties) {
		super(properties);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PressureGaugeBlockEntity(pos, state);
	}

	/** Tick both sides: the server updates the alarm/redstone, the client refreshes the goggles reading. */
	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return (lvl, pos, st, be) -> {
			if (be instanceof PressureGaugeBlockEntity gauge) {
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
		AbstractPressureGaugeBlockEntity gauge = AbstractPressureGaugeBlockEntity.at(level, pos);
		return gauge != null ? gauge.alarmSignal() : 0;
	}

	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		AbstractPressureGaugeBlockEntity gauge = AbstractPressureGaugeBlockEntity.at(level, pos);
		return gauge != null ? gauge.analogSignal() : 0;
	}
}
