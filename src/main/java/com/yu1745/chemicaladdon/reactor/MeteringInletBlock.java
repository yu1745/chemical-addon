package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.vessel.IMasterBound;
import com.yu1745.chemicaladdon.vessel.VesselBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * B4 metering inlet shell block. FACING points from the shell cell toward the
 * vessel interior (a side-wall install); the opposite outward face is the sole
 * Forge fluid inlet, metered to the world-scroll batch dose. World interaction
 * only — no GUI: empty-hand right click resets the current batch counter, any
 * other right click reports the diagnostic. The dose is scrolled in-world
 * (Create value box on the outward face).
 */
public class MeteringInletBlock extends DirectionalBlock implements EntityBlock {

	private static final int SEARCH_RADIUS = 7;

	public MeteringInletBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// The player stands outside the wall and looks inward, so the horizontal
		// view direction is the inward direction (B2/B3 convention); a vertical
		// view falls back to the horizontal facing.
		Direction looking = context.getNearestLookingDirection();
		Direction facing = looking.getAxis().isVertical() ? context.getHorizontalDirection() : looking;
		return defaultBlockState().setValue(FACING, facing);
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.block();
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MeteringInletBlockEntity(pos, state);
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof MeteringInletBlockEntity inlet) {
			ItemStack held = player.getItemInHand(hand);
			if (held.isEmpty()) {
				// the physical reset: clear the current batch counter
				inlet.resetBatch();
			}
			MeteringInletBlockEntity.Status status = inlet.refreshDiagnostic();
			player.displayClientMessage(Component.translatable(
				"metering_inlet.chemicaladdon.status." + status.name().toLowerCase()), false);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
		if (oldState.getBlock() == state.getBlock() || moved || level.isClientSide) {
			return;
		}
		tryReformNearby(level, pos);
	}

	static void tryReformNearby(Level level, BlockPos pos) {
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
		if (!state.is(newState.getBlock()) && !level.isClientSide
			&& level.getBlockEntity(pos) instanceof IMasterBound bound) {
			BlockPos masterPos = bound.getMasterPos();
			if (masterPos != null && level.getBlockEntity(masterPos) instanceof VesselBlockEntity vessel) {
				vessel.handleStructuralBlockRemoved(pos);
			}
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}

	// -------------------------------------------------------------- redstone

	@Override
	public boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
		return signalAt(level, pos);
	}

	@Override
	public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
		// strong 15 when the batch reached its dose
		return signalAt(level, pos);
	}

	private static int signalAt(BlockGetter level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof MeteringInletBlockEntity inlet) {
			return inlet.doneSignal();
		}
		return 0;
	}

	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof MeteringInletBlockEntity inlet) {
			return inlet.analogSignal();
		}
		return 0;
	}
}
