package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.vessel.IMasterBound;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The thin wall-mounted thermometer plate (薄板): a face-mounted gauge that reads
 * the reactor directly behind its mounting face. Not a structural block — it
 * attaches to the OUTSIDE of the vessel and reads through whatever shell block
 * (brick / glass / decant port / wall thermometer / the controller itself) it is
 * mounted on, via {@link IMasterBound}.
 */
public class ThermometerPanelBlockEntity extends AbstractThermometerBlockEntity {

	public ThermometerPanelBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.THERMOMETER_PANEL.get(), pos, state);
	}

	@Override
	protected boolean isValueBoxSide(BlockState state, Direction side) {
		return side == state.getValue(ThermometerPanelBlock.FACING);
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
		Direction facing = getBlockState().getValue(ThermometerPanelBlock.FACING);
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
