package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import com.yu1745.chemicaladdon.registry.AllBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Filter press (M2): single-block machine. Slurry fluid in -> filtrate fluid
 * out + cake item out. Driven by the shared FilteringLogic; a Create pipe
 * feeds the input, funnels pull the cake. (Kinetic drive requirement comes
 * in a later milestone.)
 */
public class FilterPressBlockEntity extends BlockEntity {

	public static final int INPUT_CAPACITY = 4000;
	public static final int OUTPUT_CAPACITY = 4000;

	private final ReactorTank input = new ReactorTank(INPUT_CAPACITY, this::onChanged);
	private final ReactorTank output = new ReactorTank(OUTPUT_CAPACITY, this::onChanged);
	private final ItemStackHandler items = new ItemStackHandler(1) {
		@Override
		protected void onContentsChanged(int slot) {
			onChanged();
		}
	};
	private final FilteringLogic logic = new FilteringLogic();
	private final LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(() -> new IFluidHandler() {
		@Override
		public int getTanks() {
			return input.getTanks() + output.getTanks();
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			return tank < input.getTanks() ? input.getFluidInTank(tank) : output.getFluidInTank(tank - input.getTanks());
		}

		@Override
		public int getTankCapacity(int tank) {
			return tank < input.getTanks() ? INPUT_CAPACITY : OUTPUT_CAPACITY;
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return true;
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			return input.fill(resource, action);
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			return output.drain(resource, action);
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			return output.drain(maxDrain, action);
		}
	});
	private final LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> items);

	private int tickCounter = 0;

	public FilterPressBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.FILTER_PRESS.get(), pos, state);
	}

	public void serverTick() {
		if (level == null || level.isClientSide) {
			return;
		}
		if (++tickCounter % FilteringLogic.TICK_INTERVAL == 0) {
			logic.tick(level, input, output, items, worldPosition, 1.0f);
		}
	}

	private void onChanged() {
		setChanged();
		if (level != null && !level.isClientSide) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
			if (level instanceof ServerLevel serverLevel) {
				ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(this);
				serverLevel.getServer().getPlayerList()
					.broadcast(null, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), 64.0,
						serverLevel.dimension(), packet);
			}
		}
	}

	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag tag = super.getUpdateTag();
		saveAdditional(tag);
		return tag;
	}

	@Nullable
	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
		handleUpdateTag(pkt.getTag());
	}

	public ReactorTank getInput() {
		return input;
	}

	public ReactorTank getOutput() {
		return output;
	}

	public ItemStackHandler getItems() {
		return items;
	}

	public float getProgress() {
		return logic.getProgress();
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
		tag.put("input", input.serializeNBT());
		tag.put("output", output.serializeNBT());
		tag.put("items", items.serializeNBT());
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		input.deserializeNBT(tag.getCompound("input"));
		output.deserializeNBT(tag.getCompound("output"));
		items.deserializeNBT(tag.getCompound("items"));
	}

}
