package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The full-cube thermometer (方块形式): a vessel shell block that doubles as a
 * temperature gauge. It behaves exactly like {@link ChemicalBrickBlock} (auto
 * re-form on place, tear-down on break, capability proxy, in the vessel_walls
 * tag) and additionally drives thermometer redstone / goggles. The reading logic
 * lives in {@link ThermometerBlockEntity}; redstone is shared with the panel form
 * via {@link AbstractThermometerBlockEntity#at}.
 */
public class ThermometerBlock extends ChemicalBrickBlock {

	public ThermometerBlock(Properties properties) {
		super(properties);
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
		return t != null ? t.analogSignal() : 0;
	}
}
