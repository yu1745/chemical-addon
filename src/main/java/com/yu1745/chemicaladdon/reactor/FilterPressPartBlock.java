package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import com.simibubi.create.foundation.block.IBE;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** Passive member of the fixed three-block filter press. FACING points drive -> manifold. */
public class FilterPressPartBlock extends HorizontalDirectionalBlock implements IBE<FilterPressPartBlockEntity> {
	public FilterPressPartBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override public Class<FilterPressPartBlockEntity> getBlockEntityClass() { return FilterPressPartBlockEntity.class; }
	@Override public BlockEntityType<? extends FilterPressPartBlockEntity> getBlockEntityType() { return AllBlockEntities.FILTER_PRESS_PART.get(); }
}
