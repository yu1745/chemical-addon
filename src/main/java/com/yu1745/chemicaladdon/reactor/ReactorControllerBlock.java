package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.registry.AllBlocks;

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
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Controller of the reaction vessel multiblock (M1 template: 3x3x3 hollow
 * brick shell, controller embedded in a wall). Right-click while un-assembled
 * attempts structure validation; once assembled it opens the control panel.
 */
public class ReactorControllerBlock extends Block implements EntityBlock {

	public ReactorControllerBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ReactorControllerBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		if (level.isClientSide) {
			return null;
		}
		return (lvl, pos, st, be) -> {
			if (be instanceof ReactorControllerBlockEntity controller) {
				controller.serverTick();
			}
		};
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof ReactorControllerBlockEntity controller) {
			if (controller.isAssembled()) {
				player.openMenu(controller);
			} else {
				boolean ok = controller.tryAssemble();
				player.displayClientMessage(Component.literal(ok
					? "§a反应釜结构成型！右键打开控制面板"
					: "§c结构不完整：需要 3×3×3 化工砖空心壳体，控制器嵌在壁层中间"), false);
			}
		}
		return InteractionResult.sidedSuccess(level.isClientSide);
	}
}
