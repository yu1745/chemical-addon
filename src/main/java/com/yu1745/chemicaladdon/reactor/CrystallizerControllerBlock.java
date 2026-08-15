package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The M08 endpoint crystalliser controller block: the reactor controller's
 * multiblock (structure, item panel, bucket interaction all inherited) with
 * the crystalliser BE behind it. Redstone is the endpoint event: strong 15
 * when the liquor's Baumé reaches the setpoint (cut the burner, discharge
 * the slurry); comparator shows °Bé progress until then.
 */
public class CrystallizerControllerBlock extends ReactorControllerBlock {

	public CrystallizerControllerBlock(Properties properties) {
		super(properties);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CrystallizerControllerBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return (lvl, pos, st, be) -> {
			if (be instanceof CrystallizerControllerBlockEntity controller) {
				controller.tick();
			}
		};
	}

	@Override
	public boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
		BlockEntity be = level instanceof Level l ? l.getBlockEntity(pos) : null;
		return be instanceof CrystallizerControllerBlockEntity controller && controller.atEndpoint() ? 15 : 0;
	}

	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof CrystallizerControllerBlockEntity controller) {
			return controller.atEndpoint() ? 15 : Math.min(15, AbstractBaumeGaugeBlockEntity.baumeOf(controller.getTank()));
		}
		return 0;
	}
}
