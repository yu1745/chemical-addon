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
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * B3 catalyst tray shell block. FACING points from the shell cell toward the
 * vessel interior (a side-wall install); the opposite face is the sole Forge
 * item endpoint. World insert/extract only — no GUI: right-click loads a held
 * catalyst item, right-click with an empty hand retrieves the front item.
 */
public class CatalystTrayBlock extends DirectionalBlock implements EntityBlock {

	private static final int SEARCH_RADIUS = 7;

	public CatalystTrayBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// A tray mounts on a side wall with its bed facing into the vessel. The
		// player stands outside and looks inward, so the horizontal view
		// direction is the inward bed direction (B2 convention). A vertical view
		// (looking down while clicking a wall cell) falls back to the player's
		// horizontal facing — still the inward direction.
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
	public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
		CollisionContext context) {
		return Shapes.block();
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new CatalystTrayBlockEntity(pos, state);
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof CatalystTrayBlockEntity tray) {
			ItemStack held = player.getItemInHand(hand);
			if (!held.isEmpty() && held.is(CatalystTrayBlockEntity.CATALYST_TAG)) {
				// load the held catalyst into the one slot
				ItemStack remainder = ItemHandlerHelper.insertItemStacked(tray.getCatalysts(), held.copy(), false);
				player.setItemInHand(hand, remainder);
			} else {
				// empty hand (or a non-catalyst item): retrieve the front catalyst
				ItemStack stack = tray.getCatalysts().getStackInSlot(0);
				if (!stack.isEmpty()) {
					ItemStack taken = stack.copy();
					taken.setCount(1);
					stack.shrink(1);
					if (stack.isEmpty()) {
						tray.getCatalysts().setStackInSlot(0, ItemStack.EMPTY);
					}
					ItemHandlerHelper.giveItemToPlayer(player, taken);
				}
			}
			CatalystTrayBlockEntity.Status status = tray.refreshDiagnostic();
			player.displayClientMessage(Component.translatable(
				"catalyst_tray.chemicaladdon.status." + status.name().toLowerCase()), false);
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
		if (!state.is(newState.getBlock()) && !level.isClientSide) {
			// the tray's catalyst inventory drops with the block (no GUI, no cache)
			if (level.getBlockEntity(pos) instanceof CatalystTrayBlockEntity tray) {
				SpillLogic.spillItems(level, pos, tray.getCatalysts());
			}
			if (level.getBlockEntity(pos) instanceof IMasterBound bound) {
				BlockPos masterPos = bound.getMasterPos();
				if (masterPos != null && level.getBlockEntity(masterPos) instanceof VesselBlockEntity vessel) {
					vessel.handleStructuralBlockRemoved(pos);
				}
			}
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}
}
