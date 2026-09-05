package com.yu1745.chemicaladdon.reactor;

import java.util.ArrayList;
import java.util.List;

import com.yu1745.chemicaladdon.composition.parity.Kernel;
import com.yu1745.chemicaladdon.composition.parity.KernelSolutionState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.item.MixedResidueItem;
import com.yu1745.chemicaladdon.registry.AllItems;
import com.yu1745.chemengine.kernel.IPhreeqc;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Turns a broken vessel's logical contents back into physical world content
 * (the "spill"): items become item entities that can be washed away; fluids
 * emerge progressively — one source block every few ticks from the breach —
 * so vanilla fluid mechanics can carry them out of the hole. If the physical
 * space cannot take the whole amount, the rest keeps trickling out later
 * instead of vanishing (no flooding/overwriting). Engine-owned mixtures are
 * emitted as recoverable wet-residue entities because a world fluid block
 * cannot retain their RAW solution or solid ledger.
 *
 * Multi-fluid spills are emitted strictly in tank order (one fluid fully
 * before the next); interleaved mixing of two fluids at the breach is a
 * known open question and is intentionally not handled yet.
 */
public final class SpillLogic {

	private static final int MAX_RADIUS = 4;

	private SpillLogic() {
	}

	/** Drops all item-buffer contents as item entities at the breach (immediate). */
	public static void spillItems(Level level, BlockPos leakPos, ItemStackHandler items) {
		if (level == null || level.isClientSide) {
			return;
		}
		for (int i = 0; i < items.getSlots(); i++) {
			ItemStack stack = items.getStackInSlot(i);
			if (stack.isEmpty()) {
				continue;
			}
			ItemEntity entity = new ItemEntity(level,
				leakPos.getX() + 0.5 + level.random.nextGaussian() * 0.3,
				leakPos.getY() + 0.5,
				leakPos.getZ() + 0.5 + level.random.nextGaussian() * 0.3,
				stack.copy());
			entity.setDeltaMovement(new Vec3(level.random.nextGaussian() * 0.15, 0.25,
				level.random.nextGaussian() * 0.15));
			level.addFreshEntity(entity);
			items.setStackInSlot(i, ItemStack.EMPTY);
		}
	}

	/**
	 * Moves all tank contents into a spill queue. Native mixtures are copied
	 * byte-for-byte, including sub-bucket amounts and engine NBT, before the
	 * tank is cleared. A malformed mixture without a kernel state is rejected
	 * before the tank mutates.
	 */
	public static List<FluidStack> queueFluids(ReactorTank tank) {
		List<FluidStack> pending = queueFluids(tank.getFluids());
		tank.clear();
		return pending;
	}

	/**
	 * Copies spillable stacks. Native mixture state is never decomposed into a
	 * display view: it remains attached until {@link #tryPlaceOne} writes an
	 * exact proportional wet-residue payload. Ordinary fluids retain their old
	 * whole-bucket world-fluid behavior.
	 */
	public static List<FluidStack> queueFluids(List<FluidStack> fluids) {
		List<FluidStack> pending = new ArrayList<>();
		for (FluidStack stack : fluids) {
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			if (Mixture.isMixture(stack)) {
				if (Mixture.engineSolution(stack) == null)
					throw new IllegalStateException("mixture spill requires an engine-owned solution state");
				pending.add(stack.copy());
			} else {
				int wholeBuckets = stack.getAmount() / 1000;
				if (wholeBuckets > 0) {
					pending.add(new FluidStack(stack.getFluid(), wholeBuckets * 1000));
				}
			}
		}
		return pending;
	}

	/**
	 * Places one ordinary source block or releases at most 1000 mB of an engine
	 * mixture as a recoverable wet-residue entity. Queue state changes only
	 * after the corresponding world write succeeds.
	 */
	public static boolean tryPlaceOne(Level level, BlockPos leakPos, List<FluidStack> pending) {
		if (level == null || level.isClientSide || pending.isEmpty()) {
			return false;
		}
		FluidStack stack = pending.get(0);
		if (Mixture.isMixture(stack)) {
			return releaseNativeMixture(level, leakPos, pending, stack);
		}
		BlockPos spot = findFreeSpot(level, leakPos);
		if (spot == null) {
			return false;
		}
		BlockState state = stack.getFluid().defaultFluidState().createLegacyBlock();
		level.setBlock(spot, state, 3);
		int remaining = stack.getAmount() - 1000;
		if (remaining <= 0) {
			pending.remove(0);
		} else {
			stack.setAmount(remaining);
		}
		return true;
	}

	private static boolean releaseNativeMixture(Level level, BlockPos leakPos, List<FluidStack> pending,
			FluidStack stack) {
		KernelSolutionState stored = Mixture.engineSolution(stack);
		if (stored == null || stack.getAmount() <= 0) return false;
		int spillMb = Math.min(1000, stack.getAmount());
		KernelSolutionState removed;
		KernelSolutionState remainder = null;
		try {
			IPhreeqc q = Kernel.get();
			synchronized (q) {
				// A pipe copy may carry a reference larger than its current amount.
				// Materialize first so both raw chemistry and solid mol are split from
				// the actual queue inventory, not its original transport reference.
				KernelSolutionState actual = stored.atAmount(q, stack.getAmount());
				if (spillMb == stack.getAmount()) {
					removed = actual;
				} else {
					KernelSolutionState.ProportionalRemoval split = actual.removeProportionally(q, spillMb);
					removed = split.removed();
					remainder = split.remainder();
				}
			}
		} catch (RuntimeException rejected) {
			return false;
		}
		ItemStack residue = new ItemStack(AllItems.MIXED_RESIDUE.get());
		MixedResidueItem.withEngineLiquor(residue, removed);
		MixedResidueItem.withEngineSolids(residue, removed.solids());
		ItemEntity entity = new ItemEntity(level,
			leakPos.getX() + 0.5 + level.random.nextGaussian() * 0.3,
			leakPos.getY() + 0.5,
			leakPos.getZ() + 0.5 + level.random.nextGaussian() * 0.3, residue);
		entity.setDeltaMovement(new Vec3(level.random.nextGaussian() * 0.15, 0.25,
			level.random.nextGaussian() * 0.15));
		// ServerLevel reports insertion failure; retain the untouched queue when
		// the entity cannot enter the world.
		if (!(level instanceof ServerLevel server) || !server.addFreshEntity(entity)) return false;
		if (remainder == null) {
			pending.remove(0);
		} else {
			stack.setAmount(stack.getAmount() - spillMb);
			Mixture.setEngineSolution(stack, remainder);
		}
		return true;
	}

	/** First empty block in hollow shells expanding outward from the breach. */
	private static BlockPos findFreeSpot(Level level, BlockPos leakPos) {
		for (int radius = 0; radius <= MAX_RADIUS; radius++) {
			for (int dx = -radius; dx <= radius; dx++) {
				for (int dy = -radius; dy <= radius; dy++) {
					for (int dz = -radius; dz <= radius; dz++) {
						if (radius > 0 && Math.abs(dx) != radius && Math.abs(dy) != radius && Math.abs(dz) != radius) {
							continue; // shell, not the filled cube
						}
						BlockPos p = leakPos.offset(dx, dy, dz);
						if (level.isEmptyBlock(p)) {
							return p;
						}
					}
				}
			}
		}
		return null;
	}
}
