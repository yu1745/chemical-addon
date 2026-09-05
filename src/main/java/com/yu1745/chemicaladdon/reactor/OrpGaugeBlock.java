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

public class OrpGaugeBlock extends ChemicalBrickBlock {
	public OrpGaugeBlock(Properties p){super(p);}
	@Nullable @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new OrpGaugeBlockEntity(pos,state);}
	@Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return(l,p,s,b)->{if(b instanceof OrpGaugeBlockEntity g)g.tick();};}
	@Override public boolean isSignalSource(BlockState state){return true;}
	@Override public int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return level.getBlockEntity(pos)instanceof OrpGaugeBlockEntity g?g.analogSignal():0;}
	@Override public boolean hasAnalogOutputSignal(BlockState state){return true;}
	@Override public int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos){return level.getBlockEntity(pos)instanceof OrpGaugeBlockEntity g?g.analogSignal():0;}
}
