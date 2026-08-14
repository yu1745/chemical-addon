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
 * Opaque structural block of the vessel shell. Part of the "vessel wall" series
 * (with {@link ChemicalGlassBlock}): the multiblock validates the shell by the
 * {@code chemicaladdon:vessel_walls} block tag, so transparency is decided by
 * which material the player builds with — brick is solid, glass is see-through
 * (Tinkers seared-series pattern). Has a light BE that proxies fluid/item
 * capabilities to the assembled master; when broken, nearby controllers are
 * notified so the structure de-assembles.
 */
public class ChemicalBrickBlock extends Block implements EntityBlock {

	private static final int SEARCH_RADIUS = 7; // n up to 7 -> corner brick ~5.2 blocks from controller

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
		// needed after rebuilding a broken shell). For already-assembled vessels
		// the same placement may complete a LARGER shell: re-validate and grow
		// (strictly larger only, contents preserved — see tryExtend).
		if (oldState.getBlock() == state.getBlock() || moved || level.isClientSide) {
			return;
		}
		for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
			for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
				for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
					BlockEntity be = level.getBlockEntity(pos.offset(dx, dy, dz));
					if (be instanceof ReactorControllerBlockEntity controller) {
						if (controller.isAssembled()) {
							controller.tryExtend(pos);
						} else {
							controller.tryAssemble();
						}
					} else if (be instanceof SettlingBasinBlockEntity basin && !basin.isAssembled()) {
						basin.tryAssemble();
					}
				}
			}
		}
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		// A structural brick carries masterPos == its controller; a stray brick
		// (placed next to, but not part of, an assembled vessel) has masterPos ==
		// null. Only tear down the vessel this brick actually belonged to — a
		// stray brick must NOT invalidate a neighbouring reactor (Create
		// FluidTank-style: the broken block notifies its own master, no radius scan).
		// The BE is still alive here (super.onRemove removes it below).
		// Removing a bound brick first tries to SHRINK the vessel (e.g. a top brick
		// lowers the height one ring); only a shell with no legal remainder fully
		// de-assembles (with the breach-level spill).
		if (!state.is(newState.getBlock()) && !level.isClientSide) {
			if (level.getBlockEntity(pos) instanceof IMasterBound bound) {
				BlockPos masterPos = bound.getMasterPos();
				if (masterPos != null) {
					BlockEntity be = level.getBlockEntity(masterPos);
					if (be instanceof ReactorControllerBlockEntity controller) {
						controller.handleStructuralBlockRemoved(pos);
					} else if (be instanceof SettlingBasinBlockEntity basin) {
						basin.invalidateStructure(pos);
					}
				}
				// masterPos == null → stray/unbound brick: no-op
			}
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}
}
