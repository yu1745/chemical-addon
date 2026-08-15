package com.yu1745.chemicaladdon.reactor;

import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The thin pH gauge plate (S16 pH 计, 薄板): a face-mounted 2px panel reading
 * the vessel behind its mounting face — the S02/S03 dual-form pattern. Empty
 * hand right-click toggles the alarm direction, same as the cube form.
 */
public class PhGaugePanelBlock extends DirectionalBlock implements EntityBlock {

	// The plate sits flush against the wall it is mounted on (FACING.getOpposite()).
	private static final Map<Direction, VoxelShape> SHAPES = Map.of(
		Direction.NORTH, box(0, 0, 14, 16, 16, 16), // wall to the south
		Direction.SOUTH, box(0, 0, 0, 16, 16, 2),   // wall to the north
		Direction.WEST, box(14, 0, 0, 16, 16, 16),  // wall to the east
		Direction.EAST, box(0, 0, 0, 2, 16, 16),    // wall to the west
		Direction.DOWN, box(0, 14, 0, 16, 16, 16),  // wall above
		Direction.UP, box(0, 0, 0, 16, 2, 16));     // wall below

	public PhGaugePanelBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getClickedFace());
	}

	@Override
	public BlockState rotate(BlockState state, Rotation rot) {
		return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
	}

	@Override
	public BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.getOrDefault(state.getValue(FACING), SHAPES.get(Direction.NORTH));
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PhGaugePanelBlockEntity(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return (lvl, pos, st, be) -> {
			if (be instanceof PhGaugePanelBlockEntity panel) {
				panel.tick();
			}
		};
	}

	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		if (player.getItemInHand(hand).isEmpty()
			&& level.getBlockEntity(pos) instanceof AbstractPhGaugeBlockEntity gauge) {
			if (!level.isClientSide) {
				gauge.toggleTriggerDirection();
				player.displayClientMessage(Component.translatable("goggles.chemicaladdon.ph_gauge_threshold",
					gauge.getThreshold(),
					Component.translatable(gauge.triggersBelow()
						? "goggles.chemicaladdon.ph_gauge_below"
						: "goggles.chemicaladdon.ph_gauge_above")), true);
				level.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.4f, 1.0f);
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
		return InteractionResult.PASS;
	}

	@Override
	public boolean isSignalSource(BlockState state) {
		return true;
	}

	@Override
	public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
		AbstractPhGaugeBlockEntity gauge = AbstractPhGaugeBlockEntity.at(level, pos);
		return gauge != null ? gauge.alarmSignal() : 0;
	}

	@Override
	public boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
		AbstractPhGaugeBlockEntity gauge = AbstractPhGaugeBlockEntity.at(level, pos);
		return gauge != null ? gauge.analogSignal() : 0;
	}
}
