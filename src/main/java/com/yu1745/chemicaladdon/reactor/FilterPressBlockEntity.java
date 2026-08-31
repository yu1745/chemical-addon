package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.composition.Chemistry;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.registry.AllBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Three-block powered filter press controller.  The drive, plate pack and
 * manifold form one horizontal line; only the two process blocks expose the
 * deliberately separated feed, wash, filtrate and cake capabilities.
 */
public class FilterPressBlockEntity extends KineticBlockEntity implements IHaveGoggleInformation {

	public static final int INPUT_CAPACITY = 4000;
	public static final int OUTPUT_CAPACITY = 4000;
	public static final int WASH_CAPACITY = 4000;

	private final ReactorTank input = new ReactorTank(INPUT_CAPACITY, this::onChanged);
	private final ReactorTank output = new ReactorTank(OUTPUT_CAPACITY, this::onChanged);
	/** U16.5 rinse line: plain water piped into the press displacement-washes the cake. */
	private final ReactorTank wash = new ReactorTank(WASH_CAPACITY, this::onChanged);
	private final ItemStackHandler items = new ItemStackHandler(1) {
		@Override
		protected void onContentsChanged(int slot) {
			onChanged();
		}
	};
	private final FilteringLogic logic = new FilteringLogic();
	private LazyOptional<IItemHandler> cakeCap = LazyOptional.of(() -> new IItemHandler() {
		@Override public int getSlots() { return items.getSlots(); }
		@Override public ItemStack getStackInSlot(int slot) { return items.getStackInSlot(slot); }
		@Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
		@Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return isStructureValid() ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY; }
		@Override public int getSlotLimit(int slot) { return items.getSlotLimit(slot); }
		@Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
	});
	private LazyOptional<IFluidHandler> feedCap = LazyOptional.of(() -> port(input, 0));
	private LazyOptional<IFluidHandler> washCap = LazyOptional.of(() -> port(wash, 1));
	private LazyOptional<IFluidHandler> filtrateCap = LazyOptional.of(() -> port(output, 2));

	private int tickCounter = 0;
	private float pinnedSpeed = -1;

	public FilterPressBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.FILTER_PRESS.get(), pos, state);
	}

	public void serverTick() {
		super.tick();
		if (level == null || level.isClientSide) {
			return;
		}
		float speed = pinnedSpeed >= 0 ? pinnedSpeed : Math.abs(getSpeed());
		if (++tickCounter % FilteringLogic.TICK_INTERVAL == 0 && isStructureValid()
			&& speed > 0 && !isOverStressed()) {
			logic.tick(level, input, output, wash, items, worldPosition,
				Math.min(4.0f, speed / 32.0f));
			onChanged();
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

	public ReactorTank getWash() {
		return wash;
	}

	public ItemStackHandler getItems() {
		return items;
	}

	public float getProgress() {
		return logic.getProgress();
	}
	public void pinSpeedForTest(float speed) { pinnedSpeed = Math.max(0, speed); }

	@Override public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
		String s = " ";
		tooltip.add(Component.literal(s).append(Component.translatable("block.chemicaladdon.filter_press")));
		if (!isStructureValid()) {
			tooltip.add(Component.literal(s).append(Component.translatable("goggles.chemicaladdon.filter_press_incomplete"))
				.withStyle(ChatFormatting.RED));
			return true;
		}
		float speed = pinnedSpeed >= 0 ? pinnedSpeed : Math.abs(getSpeed());
		tooltip.add(Component.literal(s).append(Component.translatable("goggles.chemicaladdon.filter_press_speed", (int) speed))
			.withStyle(speed > 0 && !isOverStressed() ? ChatFormatting.AQUA : ChatFormatting.RED));
		tooltip.add(Component.literal(s).append(Component.translatable("goggles.chemicaladdon.filter_press_feed",
			input.getTotalAmount(), INPUT_CAPACITY)).withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.literal(s).append(Component.translatable("goggles.chemicaladdon.filter_press_wash",
			wash.getTotalAmount(), WASH_CAPACITY)).withStyle(ChatFormatting.BLUE));
		tooltip.add(Component.literal(s).append(Component.translatable("goggles.chemicaladdon.filter_press_filtrate",
			output.getTotalAmount(), OUTPUT_CAPACITY)).withStyle(ChatFormatting.GREEN));
		tooltip.add(Component.literal(s).append(Component.translatable("goggles.chemicaladdon.filter_press_progress",
			Math.round(logic.getProgress() * 100))).withStyle(ChatFormatting.AQUA));
		Component blocked = blockageMessage();
		if (blocked != null) tooltip.add(Component.literal(s).append(blocked).withStyle(ChatFormatting.RED));
		return true;
	}

	@Nullable
	private Component blockageMessage() {
		float speed = pinnedSpeed >= 0 ? pinnedSpeed : Math.abs(getSpeed());
		if (speed <= 0) return Component.translatable("goggles.chemicaladdon.filter_press_no_power");
		if (isOverStressed()) return Component.translatable("goggles.chemicaladdon.filter_press_overstressed");
		long suspendedMb = input.suspendedUnits() / Chemistry.UNIT_PER_MB;
		if (suspendedMb <= 0) return Component.translatable("goggles.chemicaladdon.filter_press_no_slurry");
		if (suspendedMb < RulesEngine.MB_PER_ITEM)
			return Component.translatable("goggles.chemicaladdon.filter_press_need_solids",
				suspendedMb, RulesEngine.MB_PER_ITEM);
		if (!items.getStackInSlot(0).isEmpty())
			return Component.translatable("goggles.chemicaladdon.filter_press_cake_blocked");
		long fluidRoom = output.getTankCapacity(0) - output.getTotalAmount();
		long required = input.getTotalAmount() + wash.getTotalAmount();
		if (fluidRoom < required)
			return Component.translatable("goggles.chemicaladdon.filter_press_filtrate_blocked",
				required, fluidRoom);
		return null;
	}

	public boolean isStructureValid() {
		if (level == null) return false;
		Direction facing = getBlockState().getValue(FilterPressBlock.HORIZONTAL_FACING);
		BlockState plate = level.getBlockState(worldPosition.relative(facing));
		BlockState manifold = level.getBlockState(worldPosition.relative(facing, 2));
		return plate.is(AllBlocks.FILTER_PRESS_PLATE.get()) && manifold.is(AllBlocks.FILTER_PRESS_MANIFOLD.get())
			&& plate.getValue(FilterPressPartBlock.FACING) == facing
			&& manifold.getValue(FilterPressPartBlock.FACING) == facing;
	}

	public LazyOptional<IItemHandler> getCakeOutputCapability() { return cakeCap; }
	public LazyOptional<IFluidHandler> getWashCapability() { return washCap; }
	public LazyOptional<IFluidHandler> getFiltrateCapability() { return filtrateCap; }

	private IFluidHandler port(ReactorTank tank, int mode) {
		return new IFluidHandler() {
			@Override public int getTanks() { return tank.getTanks(); }
			@Override public FluidStack getFluidInTank(int i) { return tank.getFluidInTank(i); }
			@Override public int getTankCapacity(int i) { return tank.getTankCapacity(i); }
			@Override public boolean isFluidValid(int i, FluidStack stack) {
				if (!isStructureValid()) return false;
				return mode == 0 ? Mixture.isMixture(stack) && !Mixture.getSuspended(stack).isEmpty()
					: mode == 1 && !Mixture.isMixture(stack) && stack.getFluid() == Fluids.WATER;
			}
			@Override public int fill(FluidStack stack, FluidAction action) { return isFluidValid(0, stack) ? tank.fill(stack, action) : 0; }
			@Override public FluidStack drain(FluidStack stack, FluidAction action) { return mode == 2 && isStructureValid() ? tank.drain(stack, action) : FluidStack.EMPTY; }
			@Override public FluidStack drain(int amount, FluidAction action) { return mode == 2 && isStructureValid() ? tank.drain(amount, action) : FluidStack.EMPTY; }
		};
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
		if (cap == ForgeCapabilities.FLUID_HANDLER)
			return isStructureValid() ? feedCap.cast() : LazyOptional.empty();
		if (cap == ForgeCapabilities.ITEM_HANDLER)
			return LazyOptional.empty();
		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		cakeCap.invalidate();
		feedCap.invalidate();
		washCap.invalidate();
		filtrateCap.invalidate();
	}

	@Override public void reviveCaps() {
		super.reviveCaps();
		cakeCap = LazyOptional.of(() -> new IItemHandler() {
			@Override public int getSlots() { return items.getSlots(); }
			@Override public ItemStack getStackInSlot(int slot) { return items.getStackInSlot(slot); }
			@Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
			@Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return isStructureValid() ? items.extractItem(slot, amount, simulate) : ItemStack.EMPTY; }
			@Override public int getSlotLimit(int slot) { return items.getSlotLimit(slot); }
			@Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
		});
		feedCap = LazyOptional.of(() -> port(input, 0));
		washCap = LazyOptional.of(() -> port(wash, 1));
		filtrateCap = LazyOptional.of(() -> port(output, 2));
	}

	@Override
	protected void write(CompoundTag tag, boolean clientPacket) {
		super.write(tag, clientPacket);
		tag.put("input", input.serializeNBT());
		tag.put("output", output.serializeNBT());
		tag.put("wash", wash.serializeNBT());
		tag.put("items", items.serializeNBT());
		tag.putFloat("progress", logic.getProgress());
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		super.read(tag, clientPacket);
		input.deserializeNBT(tag.getCompound("input"));
		output.deserializeNBT(tag.getCompound("output"));
		wash.deserializeNBT(tag.getCompound("wash"));
		items.deserializeNBT(tag.getCompound("items"));
		logic.setProgress(tag.getFloat("progress"));
	}

}
