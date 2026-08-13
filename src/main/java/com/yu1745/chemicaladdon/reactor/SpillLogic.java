package com.yu1745.chemicaladdon.reactor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import com.yu1745.chemicaladdon.fluid.Mixture;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Turns a broken vessel's logical contents back into physical world content
 * (the "spill"): items become item entities that can be washed away; fluids
 * emerge progressively — one source block every few ticks from the breach —
 * so vanilla fluid mechanics can carry them out of the hole. If the physical
 * space cannot take the whole amount, the rest keeps trickling out later
 * instead of vanishing (no flooding/overwriting); anything under one bucket
 * at break time is lost by design (a fluid block is the smallest unit).
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
	 * Moves the tank's contents into a spill queue as whole-bucket pure fluids
	 * (the sub-bucket remainder is lost). The tank is emptied.
	 *
	 * <p>A mixture is DECOMPOSED into its pure components for spilling: a mixture's
	 * composition lives in FluidStack NBT, which Forge fluid source blocks cannot
	 * carry, so the mixture cannot survive a world round-trip as a single fluid.
	 * Its pure components can — they spill as ordinary fluid blocks and
	 * {@code collapseIfNeeded} re-merges them back into a mixture on re-absorb.
	 * This keeps the break/reform cycle physical (fluid pours out) without the
	 * composition loss or the proliferation of component-less mixture stacks.
	 */
	public static List<FluidStack> queueFluids(ReactorTank tank) {
		List<FluidStack> pending = new ArrayList<>();
		var it = tank.getFluids().iterator();
		while (it.hasNext()) {
			FluidStack stack = it.next();
			it.remove();
			if (Mixture.isMixture(stack)) {
				for (Map.Entry<ResourceLocation, Integer> e : Mixture.deriveAmounts(stack).entrySet()) {
					Fluid cf = ForgeRegistries.FLUIDS.getValue(e.getKey());
					if (cf == null || cf == Fluids.EMPTY) {
						continue;
					}
					int wholeBuckets = e.getValue() / 1000;
					if (wholeBuckets > 0) {
						pending.add(new FluidStack(cf, wholeBuckets * 1000));
					}
				}
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
	 * Places one source block from the front of the queue at the first free
	 * spot expanding outward from the breach. Returns false when there is no
	 * free spot right now (call again later — flowing fluid frees space) or
	 * when the queue is empty.
	 */
	public static boolean tryPlaceOne(Level level, BlockPos leakPos, List<FluidStack> pending) {
		if (level == null || level.isClientSide || pending.isEmpty()) {
			return false;
		}
		FluidStack stack = pending.get(0);
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
