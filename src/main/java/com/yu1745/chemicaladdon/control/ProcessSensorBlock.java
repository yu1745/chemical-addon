package com.yu1745.chemicaladdon.control;

import javax.annotation.Nullable;

import com.yu1745.chemicaladdon.registry.AllBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;

public class ProcessSensorBlock extends Block implements EntityBlock {
	public ProcessSensorBlock(Properties p){super(p);}
	@Nullable @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ProcessSensorBlockEntity(pos,state);}
	@Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return type==AllBlockEntities.PROCESS_SENSOR.get()?(l,p,s,b)->((ProcessSensorBlockEntity)b).tick():null;}
	@Override public boolean isSignalSource(BlockState state){return true;}
	@Override public int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return level.getBlockEntity(pos)instanceof ProcessSensorBlockEntity s?s.signal():0;}
	@Override public boolean hasAnalogOutputSignal(BlockState state){return true;}
	@Override public int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos){return level.getBlockEntity(pos)instanceof ProcessSensorBlockEntity s?s.signal():0;}
}
