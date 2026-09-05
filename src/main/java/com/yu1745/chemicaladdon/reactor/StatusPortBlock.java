package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The fixed-function reactor status port (wall form): a {@code vessel_walls}
 * shell brick that publishes the master's process status — right-click reads it
 * out, redstone encodes it (see {@link StatusPortBlockEntity}). It inherits the
 * full brick lifecycle (auto re-assembly on placement, master-notified removal)
 * and the capability proxy from {@link ChemicalBrickBlock}/{@link ChemicalBrickBlockEntity}.
 */
public class StatusPortBlock extends ChemicalBrickBlock implements EntityBlock {

	public StatusPortBlock(Properties properties) {
		super(properties);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new StatusPortBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return (lvl, pos, st, be) -> {
			if (be instanceof StatusPortBlockEntity port) {
				port.tick();
			}
		};
	}

	/** Right-click: read the current status out loud (world-in display, no GUI). */
	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		StatusPortBlockEntity port = level.getBlockEntity(pos) instanceof StatusPortBlockEntity p ? p : null;
		if (port == null) {
			return InteractionResult.PASS;
		}
		if (!level.isClientSide) {
			if(player.isShiftKeyDown())port.acknowledge();
			player.displayClientMessage(Component.translatable("message.chemicaladdon.process_state_transmitter", port.statusComponent()),
				true);
			level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.4f, 1.0f);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	@Override
	public boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
		StatusPortBlockEntity port = StatusPortBlockEntity.at(level, pos);
		return port != null ? port.strongSignal() : 0;
	}

	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		StatusPortBlockEntity port = StatusPortBlockEntity.at(level, pos);
		return port != null ? port.comparatorSignal() : 0;
	}
}
