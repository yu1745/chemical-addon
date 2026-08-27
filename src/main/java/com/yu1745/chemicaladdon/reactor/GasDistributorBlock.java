package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.vessel.IMasterBound;
import com.yu1745.chemicaladdon.vessel.VesselBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.BlockHitResult;

/**
 * B2 gas distributor shell block. FACING points from the shell cell toward the
 * vessel interior; the opposite face is the sole Forge fluid input endpoint.
 */
public class GasDistributorBlock extends DirectionalBlock implements EntityBlock {

	private static final int SEARCH_RADIUS = 7;

	public GasDistributorBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// FACING is the nozzle face toward the vessel interior. For a wall-mounted
		// distributor the player stands outside and looks inward, so the horizontal
		// view direction itself is the required nozzle direction (Observer uses the
		// same nearest-looking convention). For a floor-mounted distributor the
		// player looks down while the nozzle must point UP, hence the vertical
		// opposite. clickedFace cannot express this consistently.
		Direction looking = context.getNearestLookingDirection();
		Direction facing = looking.getAxis().isVertical() ? looking.getOpposite() : looking;
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
	public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
		CollisionContext context) {
		return Shapes.block();
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new GasDistributorBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
		BlockEntityType<T> type) {
		return (lvl, pos, blockState, be) -> {
			if (be instanceof GasDistributorBlockEntity distributor) {
				distributor.tick();
			}
		};
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof GasDistributorBlockEntity distributor) {
			GasDistributorBlockEntity.Status status = distributor.refreshDiagnostic();
			player.displayClientMessage(Component.translatable(
				"gas_distributor.chemicaladdon.status." + status.name().toLowerCase()), false);
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
}
