package com.yu1745.chemicaladdon.reactor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Structural block of the reaction vessel shell. When broken, any nearby
 * reactor controller is notified so the structure de-assembles.
 */
public class ChemicalBrickBlock extends Block {

	private static final int SEARCH_RADIUS = 3;

	public ChemicalBrickBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (!state.is(newState.getBlock())) {
			for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
				for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
					for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
						BlockEntity be = level.getBlockEntity(pos.offset(dx, dy, dz));
						if (be instanceof ReactorControllerBlockEntity controller) {
							controller.invalidateStructure();
						}
					}
				}
			}
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}
}
