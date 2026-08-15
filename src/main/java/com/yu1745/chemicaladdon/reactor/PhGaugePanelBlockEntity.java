package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.vessel.IMasterBound;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The thin wall-mounted pH plate (薄板): a face-mounted gauge reading the
 * reactor directly behind its mounting face — the S02/S03 panel pattern,
 * reading H⁺ activity (S16).
 */
public class PhGaugePanelBlockEntity extends AbstractPhGaugeBlockEntity {

	public PhGaugePanelBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.PH_GAUGE_PANEL.get(), pos, state);
	}

	@Override
	protected boolean isValueBoxSide(BlockState state, Direction side) {
		return side == state.getValue(PhGaugePanelBlock.FACING);
	}

	@Override
	protected float dialOffset() {
		return -3f / 8f; // thin panel: the dial hangs 2px inside the cell
	}

	@Override
	@Nullable
	protected ReactorControllerBlockEntity findReactor() {
		if (level == null) {
			return null;
		}
		Direction facing = getBlockState().getValue(PhGaugePanelBlock.FACING);
		BlockPos behind = worldPosition.relative(facing.getOpposite());
		BlockEntity be = level.getBlockEntity(behind);
		if (be instanceof ReactorControllerBlockEntity reactor) {
			return reactor;
		}
		if (be instanceof IMasterBound bound) {
			BlockEntity master = bound.getValidMaster();
			if (master instanceof ReactorControllerBlockEntity reactor) {
				return reactor;
			}
		}
		return null;
	}
}
