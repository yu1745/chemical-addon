package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Structural block of the reaction vessel / settling basin shell. Has a light
 * BE that proxies fluid/item capabilities to the assembled master (Create
 * FluidTank pattern). When broken, any nearby controller is notified so the
 * structure de-assembles.
 */
public class ChemicalBrickBlock extends Block implements EntityBlock {

	private static final int SEARCH_RADIUS = 3;

	public ChemicalBrickBlock(Properties properties) {
		super(properties);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ChemicalBrickBlockEntity(pos, state);
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
		// placing (or replacing) a brick re-forms nearby unassembled structures
		// automatically — Create FluidTank-style state recovery (no right-click
		// needed after rebuilding a broken shell)
		if (oldState.getBlock() == state.getBlock() || moved || level.isClientSide) {
			return;
		}
		for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
			for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
				for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
					BlockEntity be = level.getBlockEntity(pos.offset(dx, dy, dz));
					if (be instanceof ReactorControllerBlockEntity controller && !controller.isAssembled()) {
						controller.tryAssemble();
					} else if (be instanceof SettlingBasinBlockEntity basin && !basin.isAssembled()) {
						basin.tryAssemble();
					}
				}
			}
		}
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (!state.is(newState.getBlock())) {
			for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
				for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
					for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
						BlockEntity be = level.getBlockEntity(pos.offset(dx, dy, dz));
						if (be instanceof ReactorControllerBlockEntity controller) {
							controller.invalidateStructure(pos);
						}
						if (be instanceof SettlingBasinBlockEntity basin) {
							basin.invalidateStructure(pos);
						}
					}
				}
			}
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}
}
