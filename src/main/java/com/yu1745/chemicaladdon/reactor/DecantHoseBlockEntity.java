package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import com.yu1745.chemicaladdon.fluid.Miscibility;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;

import net.createmod.catnip.animation.LerpedFloat;
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
 * The decant hose's logic: find the open-topped vessel directly below, then expose
 * a one-way FLUID_HANDLER that draws the LIGHTEST (top) phase. Two wrench-toggled
 * modes (like a separatory funnel used from the top):
 * <ul>
 *   <li><b>only top</b> (default) — latches onto the lightest phase and runs dry
 *       when it is exhausted, so the denser phase below is left untouched;</li>
 *   <li><b>drain all</b> — {@code ReactorTank#drainLightest}, empties light-first.</li>
 * </ul>
 */
public class DecantHoseBlockEntity extends BlockEntity {

	/** true = only the top (lightest) phase, latched; false = drain everything light-first. */
	private boolean onlyTop = true;
	private FluidStack latched = FluidStack.EMPTY;
	private final LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(() -> new HoseHandler());

	/** Client-side hose length: eases toward the liquid surface rather than snapping. */
	private final LerpedFloat offset = LerpedFloat.linear().startWithValue(0);
	/** Exponential chase speed for the hose offset (higher = faster descent, tighter follow). */
	private static final float OFFSET_CHASE_SPEED = 0.25f;

	public DecantHoseBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.DECANT_HOSE.get(), pos, state);
	}

	public void toggleMode() {
		onlyTop = !onlyTop;
		latched = FluidStack.EMPTY;
		setChanged();
	}

	public boolean isOnlyTop() {
		return onlyTop;
	}

	/** The open-topped reactor directly below {@code pos} (within reach), or null. */
	@Nullable
	public static ReactorControllerBlockEntity findReactorBelow(net.minecraft.world.level.Level level, BlockPos pos) {
		for (int dy = 1; dy <= 32; dy++) {
			BlockPos p = pos.below(dy);
			BlockEntity be = level.getBlockEntity(p);
			if (be instanceof ReactorControllerBlockEntity r && r.isOpen()) {
				return r;
			}
			if (be instanceof ChemicalBrickBlockEntity brick) {
				BlockEntity master = brick.getValidMaster();
				if (master instanceof ReactorControllerBlockEntity r && r.isOpen()) {
					return r;
				}
			}
		}
		return null;
	}

	@Nullable
	private ReactorControllerBlockEntity reactor() {
		return level == null ? null : findReactorBelow(level, worldPosition);
	}

	/** Client tick: ease the hose toward the liquid surface (or retract when there is none). */
	public void tick() {
		if (level == null || !level.isClientSide) {
			return;
		}
		ReactorControllerBlockEntity r = reactor();
		float target = 0;
		if (r != null && r.isAssembled()) {
			target = worldPosition.getY() - r.getLiquidSurfaceY(1.0f);
		}
		offset.chase(target, OFFSET_CHASE_SPEED, LerpedFloat.Chaser.EXP);
		offset.tickChaser();
	}

	/** Interpolated hose length for the renderer (min 3/16 — the pulley never fully retracts). */
	public float getInterpolatedOffset(float partialTicks) {
		return Math.max(offset.getValue(partialTicks), 3 / 16f);
	}

	private void ensureLatched() {
		if (!latched.isEmpty()) {
			return;
		}
		ReactorControllerBlockEntity r = reactor();
		if (r == null) {
			return;
		}
		FluidStack lightest = lightest(r.getTank());
		if (!lightest.isEmpty()) {
			latched = lightest;
		}
	}

	/** The lightest phase (top layer) — a copy, never the live stack. */
	private static FluidStack lightest(ReactorTank tank) {
		FluidStack best = FluidStack.EMPTY;
		for (FluidStack f : tank.getFluids()) {
			if (best.isEmpty() || Miscibility.densityOf(f) < Miscibility.densityOf(best)) {
				best = f;
			}
		}
		return best.copy();
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
		if (cap == ForgeCapabilities.FLUID_HANDLER) {
			return fluidCap.cast();
		}
		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		fluidCap.invalidate();
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putBoolean("onlyTop", onlyTop);
		if (!latched.isEmpty()) {
			tag.put("latched", latched.writeToNBT(new CompoundTag()));
		}
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		onlyTop = tag.contains("onlyTop") ? tag.getBoolean("onlyTop") : true;
		latched = tag.contains("latched")
			? FluidStack.loadFluidStackFromNBT(tag.getCompound("latched"))
			: FluidStack.EMPTY;
	}

	private final class HoseHandler implements IFluidHandler {

		@Override
		public int getTanks() {
			return 1;
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			ReactorControllerBlockEntity r = reactor();
			if (r == null) {
				return FluidStack.EMPTY;
			}
			if (onlyTop && !latched.isEmpty()) {
				for (FluidStack f : r.getTank().getFluids()) {
					if (f.isFluidEqual(latched)) {
						return f.copy();
					}
				}
				return FluidStack.EMPTY; // latched phase drained out -> hose dry
			}
			return lightest(r.getTank());
		}

		@Override
		public int getTankCapacity(int tank) {
			ReactorControllerBlockEntity r = reactor();
			return r == null ? 0 : r.getTank().getTankCapacity(0);
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return true;
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			// A hose is bidirectional: a pump can also push fluid back into the vessel
			// (it re-separates into phases on the next settle).
			ReactorControllerBlockEntity r = reactor();
			return r == null ? 0 : r.getTank().fill(resource, action);
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			if (onlyTop) {
				ensureLatched();
				if (latched.isEmpty() || !latched.isFluidEqual(resource)) {
					return FluidStack.EMPTY;
				}
			}
			ReactorControllerBlockEntity r = reactor();
			return r == null ? FluidStack.EMPTY : r.getTank().drain(resource, action);
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			ReactorControllerBlockEntity r = reactor();
			if (r == null) {
				return FluidStack.EMPTY;
			}
			if (!onlyTop) {
				return r.getTank().drainLightest(maxDrain, action);
			}
			ensureLatched();
			if (latched.isEmpty()) {
				return FluidStack.EMPTY;
			}
			FluidStack request = latched.copy();
			request.setAmount(maxDrain);
			return r.getTank().drain(request, action);
		}
	}
}
