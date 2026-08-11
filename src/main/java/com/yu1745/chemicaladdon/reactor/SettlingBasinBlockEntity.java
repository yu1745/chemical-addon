package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.registry.AllBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Settling basin (M2): pool-shaped instance of the vessel template — a 3x3
 * open pool (bottom layer + one wall layer, no roof). Settles slurries
 * slowly (1/4 of the filter press speed) into clear liquid + cake.
 */
public class SettlingBasinBlockEntity extends BlockEntity {

	public static final int TANK_CAPACITY = 8000;

	private final ReactorTank tank = new ReactorTank(TANK_CAPACITY, this::onChanged);
	private final ItemStackHandler items = new ItemStackHandler(1) {
		@Override
		protected void onContentsChanged(int slot) {
			onChanged();
		}
	};
	private final FilteringLogic logic = new FilteringLogic();
	private final LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(() -> tank);
	private final LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> items);

	private boolean assembled = false;
	private int tickCounter = 0;

	public SettlingBasinBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.SETTLING_BASIN.get(), pos, state);
	}

	public void serverTick() {
		if (level == null || level.isClientSide) {
			return;
		}
		if (!assembled) {
			return;
		}
		if (++tickCounter % FilteringLogic.TICK_INTERVAL == 0) {
			logic.tick(level, tank, tank, items, worldPosition, 0.25f);
		}
	}

	/**
	 * Validates the open pool: bottom 3x3 of bricks at y-1, wall ring of 8
	 * bricks at y=0 (controller replaces one), interior air, no roof.
	 */
	public boolean tryAssemble() {
		if (level == null || level.isClientSide) {
			return false;
		}
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		// bottom layer
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (!level.getBlockState(worldPosition.offset(dx, -1, dz)).is(brick.getBlock())) {
					return false;
				}
			}
		}
		// wall ring at y=0, controller at own position, interior air
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					if (!level.getBlockState(worldPosition).isAir()) {
						return false;
					}
				} else if (!level.getBlockState(worldPosition.offset(dx, 0, dz)).is(brick.getBlock())) {
					return false;
				}
			}
		}
		assembled = true;
		setChanged();
		sync();
		return true;
	}

	public void invalidateStructure() {
		if (assembled) {
			assembled = false;
			setChanged();
			sync();
		}
	}

	public boolean isAssembled() {
		return assembled;
	}

	public ReactorTank getTank() {
		return tank;
	}

	public ItemStackHandler getItems() {
		return items;
	}

	public float getProgress() {
		return logic.getProgress();
	}

	private void onChanged() {
		setChanged();
		if (level != null && !level.isClientSide) {
			sync();
		}
	}

	private void sync() {
		if (level != null && !level.isClientSide) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
		if (cap == ForgeCapabilities.FLUID_HANDLER) {
			return fluidCap.cast();
		}
		if (cap == ForgeCapabilities.ITEM_HANDLER) {
			return itemCap.cast();
		}
		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		fluidCap.invalidate();
		itemCap.invalidate();
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putBoolean("assembled", assembled);
		tag.put("tank", tank.serializeNBT());
		tag.put("items", items.serializeNBT());
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		assembled = tag.getBoolean("assembled");
		tank.deserializeNBT(tag.getCompound("tank"));
		items.deserializeNBT(tag.getCompound("items"));
	}

	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag tag = super.getUpdateTag();
		saveAdditional(tag);
		return tag;
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
					basin.serverTick();
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
					boolean ok = basin.tryAssemble();
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
