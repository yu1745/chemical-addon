package com.yu1745.chemicaladdon.fluid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yu1745.chemicaladdon.registry.AllFluids;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

/**
 * Data model for the generic mixture fluid (chemicaladdon:mixture).
 *
 * <p>A mixture is identified by its <b>composition ratio</b>, not by absolute
 * amounts: the FluidStack tag carries integer <i>ratio parts</i> (one per
 * species, GCD-reduced) plus a cached blended colour. The stack's own
 * {@link FluidStack#getAmount() amount} is the total mB. Per-component absolute
 * amounts are <b>derived on demand</b> ({@link #deriveAmounts}) by distributing
 * the total across the parts in proportion, handing the integer remainder to
 * the largest components so the derived amounts always sum exactly to the total.
 *
 * <p>The ratio-in-tag representation is what lets a mixture flow through
 * Create's pipes. Create tracks a fluid by {@code FluidStack.isFluidEqual}
 * (fluid identity <b>+</b> NBT tag equality), so a 1 mB sample, a 1000 mB sample
 * and the tank's full contents of the <i>same</i> mixture must all carry an
 * identical tag. Storing absolute component mB in the tag (the first attempt)
 * made every sample's tag differ and silently blocked extraction: Create's probe
 * saw the mixture but its transfer stage could never match its own snapshot.
 *
 * <p>Consequences of the design:
 * <ul>
 *   <li>Proportional drain (pipe/pump) copies the tag verbatim and shrinks the
 *       amount — the ratio never moves, so the mixture keeps its identity
 *       through transport and between vessels.</li>
 *   <li>Filling a mixture of the same ratio stacks; a different ratio becomes a
 *       new entry that {@code collapseIfNeeded} merges (a genuine composition
 *       change, which legitimately re-stamps the tag).</li>
 *   <li>Reaction consumption of one component re-derives all amounts, removes
 *       the consumed share, and re-stamps the ratio from the survivors. Integer
 *       rounding can shift a derived amount by &le;1 mB per component per such
 *       event; this is bounded and intentional — the alternative (exact absolute
 *       amounts in the tag) is what broke transport.</li>
 * </ul>
 *
 * <p>NBT schema on the mixture FluidStack:
 * <pre>
 *   Ratios: { "chemicaladdon:water": 5, "chemicaladdon:ammonia": 3, ... }  // GCD-reduced parts
 *   Color:  &lt;int ARGB&gt;          // cached blend of the ratio, recomputed when Ratios change
 *   MixDegree:  &lt;float 0..1&gt;     // homogenisation progress (0 = just mixed, 1 = blended)
 * </pre>
 */
public final class Mixture {

	public static final String KEY_RATIOS = "Ratios";
	public static final String KEY_COLOR = "Color";
	public static final String KEY_MIX_DEGREE = "MixDegree";

	private Mixture() {}

	/** The registered mixture source fluid (deferred lookup — safe after registration). */
	public static Fluid fluid() {
		return AllFluids.MIXTURE.get().getSource();
	}

	public static boolean isMixture(Fluid fluid) {
		return fluid == fluid();
	}

	public static boolean isMixture(FluidStack stack) {
		return !stack.isEmpty() && isMixture(stack.getFluid());
	}

	/** Read the composition ratio (species id → part). Empty map if none. */
	public static Map<ResourceLocation, Integer> getRatios(FluidStack stack) {
		Map<ResourceLocation, Integer> ratios = new LinkedHashMap<>();
		CompoundTag tag = stack.getTag();
		if (tag == null) {
			return ratios;
		}
		CompoundTag c = tag.getCompound(KEY_RATIOS);
		for (String key : c.getAllKeys()) {
			ratios.put(new ResourceLocation(key), c.getInt(key));
		}
		return ratios;
	}

	/** Write the composition ratio and refresh the cached blended colour. Does not touch the stack amount. */
	public static void setRatios(FluidStack stack, Map<ResourceLocation, Integer> ratios) {
		CompoundTag tag = stack.getOrCreateTag();
		CompoundTag c = new CompoundTag();
		for (Map.Entry<ResourceLocation, Integer> e : ratios.entrySet()) {
			if (e.getValue() > 0) {
				c.putInt(e.getKey().toString(), e.getValue());
			}
		}
		tag.put(KEY_RATIOS, c);
		tag.putInt(KEY_COLOR, blendColor(ratios));
	}

	/** Sum of the ratio parts. */
	public static int totalParts(Map<ResourceLocation, Integer> ratios) {
		int total = 0;
		for (int amt : ratios.values()) {
			total += amt;
		}
		return total;
	}

	/**
	 * Derive absolute per-component mB from (total, ratio), distributing the
	 * total across the parts in proportion and handing the integer remainder to
	 * the largest components so the result always sums exactly to {@code total}.
	 * This is the only place absolute amounts exist — they are computed, never
	 * stored, which keeps the ratio tag stable.
	 */
	public static Map<ResourceLocation, Integer> deriveAmounts(int total, Map<ResourceLocation, Integer> ratios) {
		Map<ResourceLocation, Integer> amounts = new LinkedHashMap<>();
		if (ratios.isEmpty() || total <= 0) {
			return amounts;
		}
		int sum = totalParts(ratios);
		if (sum <= 0) {
			return amounts;
		}
		List<Map.Entry<ResourceLocation, Integer>> order = new ArrayList<>(ratios.entrySet());
		order.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		int assigned = 0;
		for (Map.Entry<ResourceLocation, Integer> e : order) {
			int a = (int) ((long) total * e.getValue() / sum);
			amounts.put(e.getKey(), a);
			assigned += a;
		}
		// distribute the rounding remainder (always < #components * a few) to the largest
		int idx = 0;
		while (assigned < total && idx < order.size() * 4) {
			Map.Entry<ResourceLocation, Integer> e = order.get(idx % order.size());
			amounts.put(e.getKey(), amounts.get(e.getKey()) + 1);
			assigned++;
			idx++;
		}
		return amounts;
	}

	/** Convenience: derive the absolute component amounts for a mixture stack. */
	public static Map<ResourceLocation, Integer> deriveAmounts(FluidStack stack) {
		return deriveAmounts(stack.getAmount(), getRatios(stack));
	}

	/** GCD-reduce the ratio parts to their smallest equivalent (keeps the identity canonical across scales). */
	public static Map<ResourceLocation, Integer> reduce(Map<ResourceLocation, Integer> ratios) {
		int g = 0;
		for (int v : ratios.values()) {
			g = gcd(g, v);
		}
		if (g <= 1) {
			return ratios;
		}
		Map<ResourceLocation, Integer> reduced = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Integer> e : ratios.entrySet()) {
			reduced.put(e.getKey(), e.getValue() / g);
		}
		return reduced;
	}

	private static int gcd(int a, int b) {
		return b == 0 ? a : gcd(b, a % b);
	}

	/** The cached blended colour, recomputing + caching it if missing. */
	public static int getColor(FluidStack stack) {
		CompoundTag tag = stack.getOrCreateTag();
		if (tag.contains(KEY_COLOR)) {
			return tag.getInt(KEY_COLOR);
		}
		int color = blendColor(getRatios(stack));
		tag.putInt(KEY_COLOR, color);
		return color;
	}

	/** Weight-blended ARGB of the ratio (per-channel average weighted by part). */
	public static int blendColor(Map<ResourceLocation, Integer> ratios) {
		int total = totalParts(ratios);
		if (total <= 0) {
			return 0xFFFFFFFF; // white = no tint
		}
		long r = 0, g = 0, b = 0, a = 0;
		for (Map.Entry<ResourceLocation, Integer> e : ratios.entrySet()) {
			int c = FluidColors.of(e.getKey());
			int amt = e.getValue();
			a += ((c >> 24) & 0xFF) * amt;
			r += ((c >> 16) & 0xFF) * amt;
			g += ((c >> 8) & 0xFF) * amt;
			b += (c & 0xFF) * amt;
		}
		int ai = (int) (a / total);
		int ri = (int) (r / total);
		int gi = (int) (g / total);
		int bi = (int) (b / total);
		return (ai << 24) | (ri << 16) | (gi << 8) | bi;
	}

	public static float getMixDegree(FluidStack stack) {
		CompoundTag tag = stack.getTag();
		return tag != null && tag.contains(KEY_MIX_DEGREE) ? tag.getFloat(KEY_MIX_DEGREE) : 0f;
	}

	public static void setMixDegree(FluidStack stack, float degree) {
		stack.getOrCreateTag().putFloat(KEY_MIX_DEGREE, Math.max(0f, Math.min(1f, degree)));
	}

	/** Build a new mixture FluidStack from a ratio map and a total mB. */
	public static FluidStack create(Map<ResourceLocation, Integer> ratios, int total) {
		FluidStack stack = new FluidStack(fluid(), total);
		setRatios(stack, ratios);
		return stack;
	}
}
