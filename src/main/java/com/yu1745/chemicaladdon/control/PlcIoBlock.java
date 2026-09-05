package com.yu1745.chemicaladdon.control;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class PlcIoBlock extends Block implements EntityBlock {
	public PlcIoBlock(Properties properties){super(properties);}
	@Nullable @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new PlcIoBlockEntity(pos,state);}
	@Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
		if(!(level.getBlockEntity(pos) instanceof PlcIoBlockEntity io))return InteractionResult.PASS;
		Direction face=hit.getDirection();
		if(!level.isClientSide){if(player.isShiftKeyDown())io.nextChannel(face);else io.cycleMode(face);player.displayClientMessage(Component.literal(face.getName()+" "+io.mode(face)+" C"+io.channel(face)),true);}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}
	@Override public boolean isSignalSource(BlockState state){return true;}
	@Override public int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return level.getBlockEntity(pos) instanceof PlcIoBlockEntity io?io.output(side.getOpposite()):0;}
	@Override public int getDirectSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return getSignal(state,level,pos,side);}
}
