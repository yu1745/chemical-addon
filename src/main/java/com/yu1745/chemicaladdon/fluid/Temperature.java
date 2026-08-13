package com.yu1745.chemicaladdon.fluid;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fluids.FluidStack;

/**
 * Per-stack temperature carried in the FluidStack NBT ({@code Temperature}, °C).
 *
 * <p>Temperature travels with the fluid but is <b>frozen during transport</b>:
 * only a vessel (reactor / future multiblocks)
 * changes it, so Create's {@code isFluidEqual} (fluid + full tag) sees a stable
 * tag while the fluid is in a pipe. Amount-weighted averaging is the physical
 * mixing rule for the same species (equal specific heat), so pouring a 40 °C
 * bucket into a 20 °C bucket of the same fluid gives 30 °C.
 *
 * <p>Stored as an {@code int} on purpose: NBT float equality is bit-exact, so a
 * float temperature would make "the same" temperature drift into a different
 * tag and silently break transport.
 */
public final class Temperature {

	public static final String KEY = "Temperature";
	/** Room temperature in °C; the value a tag-less (fresh) fluid is assumed to be at. */
	public static final int AMBIENT = 20;

	private Temperature() {}

	/** The stack's temperature, or {@link #AMBIENT} if it carries none. */
	public static int get(FluidStack stack) {
		CompoundTag tag = stack.getTag();
		return tag != null && tag.contains(KEY) ? tag.getInt(KEY) : AMBIENT;
	}

	public static void set(FluidStack stack, int value) {
		stack.getOrCreateTag().putInt(KEY, value);
	}

	/** Amount-weighted average of two temperatures (°C), rounded half-up. */
	public static int merge(int t1, int amount1, int t2, int amount2) {
		long sum = (long) t1 * amount1 + (long) t2 * amount2;
		int total = amount1 + amount2;
		return total <= 0 ? AMBIENT : (int) Math.round((double) sum / total);
	}

	/** Amount-weighted average from a pre-accumulated Σ(temperature × amount) and a total mB. */
	public static int fromWeightedSum(long weightedSum, int totalAmount) {
		return totalAmount <= 0 ? AMBIENT : (int) Math.round((double) weightedSum / totalAmount);
	}
}
