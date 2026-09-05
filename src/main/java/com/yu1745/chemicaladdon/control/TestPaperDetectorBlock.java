package com.yu1745.chemicaladdon.control;

import javax.annotation.Nullable;

import com.yu1745.chemicaladdon.item.TestPaperItem;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class TestPaperDetectorBlock extends Block implements EntityBlock {
	public TestPaperDetectorBlock(Properties p){super(p);}
	@Nullable @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new TestPaperDetectorBlockEntity(pos,state);}
	@Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return type==AllBlockEntities.TEST_PAPER_DETECTOR.get()?(l,p,s,b)->((TestPaperDetectorBlockEntity)b).tick():null;}
	@Override public InteractionResult use(BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){if(!(level.getBlockEntity(pos) instanceof TestPaperDetectorBlockEntity box))return InteractionResult.PASS;ItemStack held=player.getItemInHand(hand);if(!level.isClientSide){if(held.getItem() instanceof TestPaperItem){ItemStack old=box.removePaper();box.setPaper(held);if(!old.isEmpty()&&!player.addItem(old))player.drop(old,false);}else if(held.isEmpty()){ItemStack old=box.removePaper();if(!old.isEmpty()&&!player.addItem(old))player.drop(old,false);}}return InteractionResult.sidedSuccess(level.isClientSide);}
	@Override public boolean isSignalSource(BlockState state){return true;}
	@Override public int getSignal(BlockState state,BlockGetter level,BlockPos pos,Direction side){return level.getBlockEntity(pos)instanceof TestPaperDetectorBlockEntity box?box.signal():0;}
	@Override public boolean hasAnalogOutputSignal(BlockState state){return true;}
	@Override public int getAnalogOutputSignal(BlockState state,Level level,BlockPos pos){return level.getBlockEntity(pos)instanceof TestPaperDetectorBlockEntity box?box.signal():0;}
}
