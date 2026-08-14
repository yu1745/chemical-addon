package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.vessel.VesselBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Settling basin (M2): pool-shaped instance of the vessel template (U3: a
 * {@link VesselBlockEntity}, no more hand-rolled structure copy) — a fixed
 * 3x3 roofless pool (bottom layer + one wall ring) that settles slurries
 * slowly (1/4 of the filter press speed) into clear liquid + cake. Wall
 * validation now uses the shared {@code vessel_walls} tag, so glass/gauge
 * wall blocks count for the pool exactly as they do for the reactor.
 */
public class SettlingBasinBlockEntity extends VesselBlockEntity {

	public static final int TANK_CAPACITY = 8000;

	private final FilteringLogic logic = new FilteringLogic();
	private int tickCounter = 0;

	public SettlingBasinBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.SETTLING_BASIN.get(), pos, state, TANK_CAPACITY, 1);
	}

	// ------------------------------------------------------------ shape hooks

	@Override
	protected int minSize() {
		return 3;
	}

	@Override
	protected int maxSize() {
		return 3;
	}

	@Override
	protected int minRings() {
		return 1;
	}

	@Override
	protected int maxRings() {
		return 1;
	}

	@Override
	protected RoofMode roofMode() {
		return RoofMode.FORBIDDEN; // roofless pool: the layer above the rim is not part of the shape
	}

	@Override
	protected int capacityFor(int w, int rings) {
		return TANK_CAPACITY; // flat 8 buckets — the shallow pool holds more than its 1-block interior
	}

	@Override
	protected void onAssembled() {
		// no process state to set (the pool has no status machine)
	}

	@Override
	protected void onStructureInvalidated() {
		// no process state to reset
	}

	// ------------------------------------------------------------------ tick

	@Override
	protected void vesselTick() {
		if (!isAssembled()) {
			return;
		}
		if (++tickCounter % FilteringLogic.TICK_INTERVAL == 0) {
			logic.tick(level, tank, tank, items, worldPosition, 0.25f);
		}
	}

	/** Settling progress toward the next cake (0..1). */
	public float getProgress() {
		return logic.getProgress();
	}

	/** Controller block of the settling basin. */
	public static class SettlingBasinBlock extends Block implements EntityBlock {

		public SettlingBasinBlock(Properties properties) {
			super(properties);
		}

		@Override
		public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
			return new SettlingBasinBlockEntity(pos, state);
		}

		@Nullable
		@Override
		public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
			if (level.isClientSide) {
				return null;
			}
			return (lvl, pos, st, be) -> {
				if (be instanceof SettlingBasinBlockEntity basin) {
					basin.tick();
				}
			};
		}

		@Override
		public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
			if (level.isClientSide) {
				return InteractionResult.SUCCESS;
			}
			if (level.getBlockEntity(pos) instanceof SettlingBasinBlockEntity basin) {
				if (!basin.isAssembled()) {
					boolean ok = basin.tryAssemble().ok();
					player.displayClientMessage(Component.literal(ok
						? "§a沉淀池成型！"
						: "§c结构不完整：需要 3×3 化工砖池底 + 一圈池壁，控制器嵌在壁中"), false);
				} else {
					player.displayClientMessage(Component.literal("§7沉淀池（已成型，静置沉降中）"), false);
				}
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
	}
}
