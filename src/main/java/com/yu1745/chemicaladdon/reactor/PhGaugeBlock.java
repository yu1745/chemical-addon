package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
 * The full-cube pH gauge (S16 pH 计, 方块形式): a vessel shell block that
 * doubles as a pH gauge — the S02 dual-form pattern reading H⁺ activity instead
 * of temperature. Right-click with an <b>empty hand</b> toggles the alarm
 * direction (fires below / fires above the setpoint) — the one in-world
 * control this gauge needs beyond the scrollable threshold.
 */
public class PhGaugeBlock extends ChemicalBrickBlock {

	public PhGaugeBlock(Properties properties) {
		super(properties);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PhGaugeBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return (lvl, pos, st, be) -> {
			if (be instanceof PhGaugeBlockEntity gauge) {
				gauge.tick();
			}
		};
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		if (player.getItemInHand(hand).isEmpty() && level.getBlockEntity(pos) instanceof PhGaugeBlockEntity gauge) {
			if (!level.isClientSide) {
				gauge.toggleTriggerDirection();
				player.displayClientMessage(Component.translatable("goggles.chemicaladdon.ph_gauge_threshold",
					gauge.getThreshold(),
					Component.translatable(gauge.triggersBelow()
						? "goggles.chemicaladdon.ph_gauge_below"
						: "goggles.chemicaladdon.ph_gauge_above")), true);
				level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.4f, 1.0f);
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
		return InteractionResult.PASS;
	}

	@Override
	public boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
		AbstractPhGaugeBlockEntity gauge = AbstractPhGaugeBlockEntity.at(level, pos);
		return gauge != null ? gauge.alarmSignal() : 0;
	}

	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		AbstractPhGaugeBlockEntity gauge = AbstractPhGaugeBlockEntity.at(level, pos);
		return gauge != null ? gauge.analogSignal() : 0;
	}
}
