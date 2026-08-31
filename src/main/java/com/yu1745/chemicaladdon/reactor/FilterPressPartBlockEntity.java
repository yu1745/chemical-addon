package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.registry.AllBlocks;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;

/** Capability proxy for the plate pack and manifold of a fixed filter press. */
public class FilterPressPartBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
	public FilterPressPartBlockEntity(BlockPos pos, BlockState state) { this(AllBlockEntities.FILTER_PRESS_PART.get(), pos, state); }
	public FilterPressPartBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
	@Override public void addBehaviours(List<BlockEntityBehaviour> behaviours) {}
	@Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
		FilterPressBlockEntity master = getMaster();
		return master != null && master.addToGoggleTooltip(tooltip, sneaking);
	}

	@Nullable public FilterPressBlockEntity getMaster() {
		Direction facing = getBlockState().getValue(FilterPressPartBlock.FACING);
		int distance = getBlockState().is(AllBlocks.FILTER_PRESS_PLATE.get()) ? 1 : 2;
		return level != null && level.getBlockEntity(worldPosition.relative(facing.getOpposite(), distance)) instanceof FilterPressBlockEntity press
			&& press.isStructureValid() ? press : null;
	}

	@Override public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
		FilterPressBlockEntity master = getMaster();
		if (master == null) return super.getCapability(capability, side);
		if (getBlockState().is(AllBlocks.FILTER_PRESS_PLATE.get()) && capability == ForgeCapabilities.ITEM_HANDLER && side == Direction.DOWN)
			return master.getCakeOutputCapability().cast();
		if (getBlockState().is(AllBlocks.FILTER_PRESS_PLATE.get()) && capability == ForgeCapabilities.FLUID_HANDLER)
			return master.getWashCapability().cast();
		if (getBlockState().is(AllBlocks.FILTER_PRESS_MANIFOLD.get()) && capability == ForgeCapabilities.FLUID_HANDLER)
			return master.getFiltrateCapability().cast();
		return super.getCapability(capability, side);
	}
}
