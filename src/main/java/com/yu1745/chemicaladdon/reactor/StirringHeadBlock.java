package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.vessel.IMasterBound;
import com.yu1745.chemicaladdon.vessel.VesselBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

/**
 * The stirring head (搅拌头, construction package B1): a roof-penetration shell
 * component driven by Create kinetics. It occupies a ceiling position of the
 * vessel (it is in the {@code chemicaladdon:vessel_walls} tag, so a sealed roof
 * stays sealed with the head in it) and takes rotation from a vertical shaft
 * on its UP face ({@code KineticBlock}/{@code KineticBlockEntity} pattern, like
 * Create's mechanical mixer). Its effective |RPM| maps onto the vessel's
 * stirring coefficient (see {@link com.yu1745.chemicaladdon.vessel.Agitation}).
 *
 * <p>Shell participation mirrors {@link ChemicalBrickBlock}: placing one
 * re-forms / grows nearby vessels, breaking one notifies the bound master
 * (the vessel degrades to open-topped rather than exploding), and the BE
 * proxies fluid/item capabilities to the assembled master. Placement rule
 * (B1): it only <i>installs</i> as a part on the roof plane — a head placed
 * in a wall/floor cell is a bound, proxying shell block, but never an
 * installed/effective part (see {@link StirringHeadBlockEntity}).</p>
 */
public class StirringHeadBlock extends KineticBlock implements IBE<StirringHeadBlockEntity> {

	private static final int SEARCH_RADIUS = 7; // n up to 7 -> corner brick ~5.2 blocks from controller

	/** Mixer-class stress impact at 1 RPM (registered through Create's stress registry). */
	private static final double STRESS_IMPACT = 4.0;

	public StirringHeadBlock(Properties properties) {
		super(properties);
		BlockStressValues.IMPACTS.register(this, () -> STRESS_IMPACT);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return Axis.Y;
	}

	/** Vertical shaft input from above: the shaft penetrates the roof down into the head. */
	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face == Direction.UP;
	}

	@Override
	public Class<StirringHeadBlockEntity> getBlockEntityClass() {
		return StirringHeadBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends StirringHeadBlockEntity> getBlockEntityType() {
		return AllBlockEntities.STIRRING_HEAD.get();
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
		// kinetic bookkeeping first (KineticBlock: prevent redundant re-propagation)
		super.onPlace(state, level, pos, oldState, moved);
		// then the ChemicalBrickBlock shell contract: placing a head may complete a
		// roof (sealing a vessel) or a whole shell — re-validate nearby controllers
		// (Create FluidTank-style state recovery, no right-click needed)
		if (oldState.getBlock() == state.getBlock() || moved || level.isClientSide) {
			return;
		}
		for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
			for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
				for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
					BlockEntity be = level.getBlockEntity(pos.offset(dx, dy, dz));
					if (be instanceof VesselBlockEntity vessel) {
						if (vessel.isAssembled()) {
							vessel.tryExtend(pos);
						} else {
							vessel.tryAssemble();
						}
					}
				}
			}
		}
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		// the head is a structural shell block: removing it must notify the master it
		// was bound to (shrink / open-top / tear down), exactly like a brick. A stray
		// (unbound) head is a no-op. The BE is still alive here — KineticBlock's
		// super.onRemove (IBE.onRemove) tears it down below.
		if (!state.is(newState.getBlock()) && !level.isClientSide) {
			if (level.getBlockEntity(pos) instanceof IMasterBound bound) {
				BlockPos masterPos = bound.getMasterPos();
				if (masterPos != null && level.getBlockEntity(masterPos) instanceof VesselBlockEntity vessel) {
					vessel.handleStructuralBlockRemoved(pos);
				}
				// masterPos == null → stray/unbound head: no-op
			}
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}
}
