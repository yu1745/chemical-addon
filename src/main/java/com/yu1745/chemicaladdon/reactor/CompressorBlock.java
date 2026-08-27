package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The compressor block (施工包 F3): a side-wall shell part of a sealed vessel.
 * Binding is the standard vessel_walls flow — the vessel's assembly binds every
 * wall-tagged block and records shell parts automatically.
 */
public class CompressorBlock extends Block implements EntityBlock {

	public CompressorBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CompressorBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
		BlockEntityType<T> type) {
		if (level.isClientSide) {
			return null;
		}
		return (lvl, pos, st, be) -> {
			if (be instanceof CompressorBlockEntity compressor) {
				compressor.tick();
			}
		};
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof CompressorBlockEntity compressor) {
			player.displayClientMessage(Component.literal(String.format(
				"§7压缩机（%s，FE %d/%d，%d FE/步）—— 装在密闭反应釜壁上维持工艺压力",
				statusText(compressor.getStatus()),
				compressor.getEnergy().getEnergyStored(), CompressorBlockEntity.ENERGY_CAPACITY,
				CompressorBlockEntity.FE_PER_STEP)), false);
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}

	private static String statusText(CompressorBlockEntity.Status status) {
		return switch (status) {
			case UNBOUND -> "未绑定";
			case VESSEL_NOT_SEALED -> "容器未密闭";
			case NO_POWER -> "断电";
			case PRESSURIZING -> "保压中";
		};
	}
}
