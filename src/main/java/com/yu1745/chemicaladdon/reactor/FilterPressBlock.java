package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Single-block filter press machine (M2). */
public class FilterPressBlock extends HorizontalKineticBlock implements IBE<FilterPressBlockEntity> {
	private static final double STRESS_IMPACT = 8.0;

	public FilterPressBlock(Properties properties) {
		super(properties);
		BlockStressValues.IMPACTS.register(this, () -> STRESS_IMPACT);
	}

	@Override public Axis getRotationAxis(BlockState state) { return state.getValue(HORIZONTAL_FACING).getAxis(); }
	@Override public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
		return face.getAxis() == getRotationAxis(state);
	}
	@Override public Class<FilterPressBlockEntity> getBlockEntityClass() { return FilterPressBlockEntity.class; }
	@Override public BlockEntityType<? extends FilterPressBlockEntity> getBlockEntityType() { return AllBlockEntities.FILTER_PRESS.get(); }

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return (lvl, pos, st, be) -> {
			if (be instanceof FilterPressBlockEntity filter) {
				filter.serverTick();
			}
		};
	}
}
