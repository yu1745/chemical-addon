package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import com.yu1745.chemicaladdon.fluid.Miscibility;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.vessel.VesselBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

/**
 * A structural wall block that is also a one-way drain port (分液口, "decant
 * spout"). It is part of the vessel shell — bound to the master like a brick and
 * in the {@code vessel_walls} tag — but its FLUID_HANDLER only exposes the
 * DENSEST (bottom) phase. Like a separatory funnel's stopcock it latches onto the
 * heaviest phase on first flow and runs dry when that phase is exhausted: the
 * lighter phase above never reaches the spout (an interface float valve).
 *
 * <p>This is the bottom-port counterpart of {@code ReactorTank#drainLightest} (the
 * top port the future hose pulley will use): bottom = heaviest-only, top =
 * lightest-only.
 */
public class DecantPortBlockEntity extends ChemicalBrickBlockEntity {

	/** The phase this port is latched onto (identity via isFluidEqual); empty = not latched. */
	private FluidStack latched = FluidStack.EMPTY;
	private final LazyOptional<IFluidHandler> decantCap = LazyOptional.of(() -> new DecantHandler());

	public DecantPortBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.DECANT_PORT.get(), pos, state);
	}

	@Override
	public void setMaster(@Nullable BlockPos masterPos) {
		super.setMaster(masterPos);
		latched = FluidStack.EMPTY; // re-latch onto the new contents after re-assembly
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
		if (cap == ForgeCapabilities.FLUID_HANDLER) {
			return decantCap.cast();
		}
		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		decantCap.invalidate();
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		if (!latched.isEmpty()) {
			tag.put("latched", latched.writeToNBT(new CompoundTag()));
		}
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		latched = tag.contains("latched")
			? FluidStack.loadFluidStackFromNBT(tag.getCompound("latched"))
			: FluidStack.EMPTY;
	}

	@Nullable
	private ReactorTank vesselTank() {
		BlockEntity master = getValidMaster();
		return master instanceof VesselBlockEntity vessel ? vessel.getTank() : null;
	}

	private void ensureLatched() {
		if (!latched.isEmpty()) {
			return;
		}
		ReactorTank tank = vesselTank();
		if (tank == null) {
			return;
		}
		FluidStack heaviest = heaviest(tank);
		if (!heaviest.isEmpty()) {
			latched = heaviest;
		}
	}

	/** The densest phase (bottom layer) — a copy, never the live stack. */
	private static FluidStack heaviest(ReactorTank tank) {
		FluidStack best = FluidStack.EMPTY;
		for (FluidStack f : tank.getFluids()) {
			if (best.isEmpty() || Miscibility.densityOf(f) > Miscibility.densityOf(best)) {
				best = f;
			}
		}
		return best.copy();
	}

	private final class DecantHandler implements IFluidHandler {

		@Override
		public int getTanks() {
			return 1;
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			ReactorTank t = vesselTank();
			if (t == null) {
				return FluidStack.EMPTY;
			}
			if (!latched.isEmpty()) {
				for (FluidStack f : t.getFluids()) {
					if (f.isFluidEqual(latched)) {
						return f.copy();
					}
				}
				return FluidStack.EMPTY; // latched phase drained out -> port closed
			}
			return heaviest(t);
		}

		@Override
		public int getTankCapacity(int tank) {
			ReactorTank t = vesselTank();
			return t == null ? 0 : t.getTankCapacity(0);
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return true;
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			return 0; // one-way drain port — cannot be filled through
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			ensureLatched();
			if (latched.isEmpty() || !latched.isFluidEqual(resource)) {
				return FluidStack.EMPTY;
			}
			ReactorTank t = vesselTank();
			if (t == null) {
				return FluidStack.EMPTY;
			}
			// U16.5: a clear-liquid spout skims the liquid only, never the
			// settled bed or its pore liquor (the reslurry-washing primitive)
			if (Mixture.isMixture(latched)) {
				return t.decantClear(resource.getAmount(), action);
			}
			return t.drain(resource, action);
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			ensureLatched();
			if (latched.isEmpty()) {
				return FluidStack.EMPTY;
			}
			ReactorTank t = vesselTank();
			if (t == null) {
				return FluidStack.EMPTY;
			}
			if (Mixture.isMixture(latched)) {
				return t.decantClear(maxDrain, action);
			}
			FluidStack request = latched.copy();
			request.setAmount(maxDrain);
			return t.drain(request, action);
		}
	}
}
