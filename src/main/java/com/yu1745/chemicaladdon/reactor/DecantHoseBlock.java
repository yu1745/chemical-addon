package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The decant hose (分液软管): a transient conversion of Create's Hose Pulley.
 * When the pulley is placed above an open-topped vessel it becomes this block
 * (see the {@code BlockEvent.EntityPlaceEvent} hook); breaking / pick-blocking it
 * returns the original Create hose pulley, so the player never loses it and JEI
 * only ever shows one pulley item.
 */
public class DecantHoseBlock extends Block implements EntityBlock {

	public DecantHoseBlock(Properties properties) {
		super(properties);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new DecantHoseBlockEntity(pos, state);
	}

	/** Client-side ticker: eases the hose toward the liquid surface (rendering animation). */
	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return (lvl, pos, st, be) -> {
			if (be instanceof DecantHoseBlockEntity hose) {
				hose.tick();
			}
		};
	}

	/** Pick-block returns the source Create hose pulley (this block is a conversion, not an item). */
	@Override
	public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
		return new ItemStack(com.simibubi.create.AllBlocks.HOSE_PULLEY.get().asItem());
	}

	/** Wrench toggles between "only top layer" (latch) and "drain all". */
	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		if (player.getItemInHand(hand).getItem() instanceof com.simibubi.create.content.equipment.wrench.WrenchItem) {
			if (level.getBlockEntity(pos) instanceof DecantHoseBlockEntity hose) {
				hose.toggleMode();
				player.displayClientMessage(Component.literal("Decant hose: " + (hose.isOnlyTop() ? "only top layer" : "drain all")), true);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}
}
