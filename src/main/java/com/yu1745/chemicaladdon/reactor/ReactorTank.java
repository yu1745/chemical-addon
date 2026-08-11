package com.yu1745.chemicaladdon.reactor;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

/**
 * Multi-fluid tank inside a reaction vessel (smeltery-style: arbitrary fluids
 * coexist, one entry each, sharing one total capacity). Implements Forge
 * {@link IFluidHandler} so Create pipes/pumps can connect directly.
 */
public class ReactorTank implements IFluidHandler {

	private int capacity;
	private final Runnable onChanged;
	private final List<FluidStack> fluids = new ArrayList<>();

	public ReactorTank(int capacity, Runnable onChanged) {
		this.capacity = capacity;
		this.onChanged = onChanged;
	}

	/** Fluids are stored as their source instance so recipe matching (Create's
	 * FluidIngredient fixes to the source) works regardless of what instance
	 * the pipes/callers carry. */
	private static Fluid sourceOf(FluidStack stack) {
		return stack.getFluid() instanceof FlowingFluid flowing ? flowing.getSource() : stack.getFluid();
	}

	public void setCapacity(int capacity) {
		this.capacity = capacity;
	}

	public List<FluidStack> getFluids() {
		return fluids;
	}

	public int getTotalAmount() {
		int total = 0;
		for (FluidStack f : fluids) {
			total += f.getAmount();
		}
		return total;
	}

	@Override
	public int getTanks() {
		return fluids.size();
	}

	@Override
	public FluidStack getFluidInTank(int tank) {
		return fluids.get(tank);
	}

	@Override
	public int getTankCapacity(int tank) {
		return capacity;
	}

	@Override
	public boolean isFluidValid(int tank, FluidStack stack) {
		return true;
	}

	@Override
	public int fill(FluidStack resource, FluidAction action) {
		if (resource.isEmpty()) {
			return 0;
		}
		int amount = Math.min(resource.getAmount(), capacity - getTotalAmount());
		if (amount <= 0) {
			return 0;
		}
		if (action.execute()) {
			Fluid fluid = sourceOf(resource);
			for (FluidStack f : fluids) {
				if (f.getFluid() == fluid) {
					f.grow(amount);
					onChanged.run();
					return amount;
				}
			}
			fluids.add(new FluidStack(fluid, amount));
			onChanged.run();
		}
		return amount;
	}

	@Override
	public FluidStack drain(FluidStack resource, FluidAction action) {
		if (resource.isEmpty()) {
			return FluidStack.EMPTY;
		}
		Fluid target = sourceOf(resource);
		for (FluidStack f : fluids) {
			if (f.getFluid() == target) {
				int amount = Math.min(f.getAmount(), resource.getAmount());
				if (action.execute()) {
					f.shrink(amount);
					removeEmpty();
					onChanged.run();
				}
				return new FluidStack(target, amount);
			}
		}
		return FluidStack.EMPTY;
	}

	@Override
	public FluidStack drain(int maxDrain, FluidAction action) {
		if (fluids.isEmpty()) {
			return FluidStack.EMPTY;
		}
		FluidStack first = fluids.get(0);
		int amount = Math.min(first.getAmount(), maxDrain);
		if (action.execute()) {
			first.shrink(amount);
			removeEmpty();
			onChanged.run();
		}
		return new FluidStack(first.getFluid(), amount);
	}

	private void removeEmpty() {
		fluids.removeIf(f -> f.getAmount() <= 0);
	}

	/** Empties the tank entirely (e.g. after contents were spilled into the world). */
	public void clear() {
		fluids.clear();
	}

	public CompoundTag serializeNBT() {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		for (FluidStack f : fluids) {
			list.add(f.writeToNBT(new CompoundTag()));
		}
		tag.put("fluids", list);
		return tag;
	}

	public void deserializeNBT(CompoundTag tag) {
		fluids.clear();
		ListTag list = tag.getList("fluids", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			FluidStack f = FluidStack.loadFluidStackFromNBT(list.getCompound(i));
			if (!f.isEmpty()) {
				fluids.add(f);
			}
		}
	}
}
