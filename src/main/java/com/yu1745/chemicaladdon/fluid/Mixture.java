package com.yu1745.chemicaladdon.fluid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.composition.Ion;
import com.yu1745.chemicaladdon.composition.Solution;
import com.yu1745.chemicaladdon.registry.AllFluids;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

/**
 * Data model for the generic mixture fluid (chemicaladdon:mixture).
 *
 * <p>A mixture is identified by its <b>composition ratio</b>, not by absolute
 * amounts: the FluidStack tag carries integer <i>ratio parts</i> split across
 * two namespaces — <b>molecules</b> (water, dissolved molecular solutes, pure
 * gases; keyed by species id) and <b>ions</b> (charge-neutral ion multiset,
 * keyed by canonical ion id) — plus a cached blended colour. The stack's own
 * {@link FluidStack#getAmount() amount} is the total mB. Per-component absolute
 * amounts are <b>derived on demand</b> ({@link #deriveAmounts} /
 * {@link #deriveIonAmounts}) by distributing the total across the joint parts
 * in proportion, handing the integer remainder to the largest components so the
 * derived amounts always sum exactly to the total.
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
 *   Molecules: { "chemicaladdon:water": 10, ... }  // neutral molecular species → GCD-reduced parts
 *   Ions:      { "H+1": 2, "SO4-2": 1, ... }       // charge-neutral ion multiset → parts
 *   Suspended: { "chemicaladdon:gypsum": 5, ... }  // suspended solid species → parts (slurries)
 *   Color:  &lt;int ARGB&gt;          // cached blend of all namespaces, recomputed on write
 * </pre>
 */
public final class Mixture {

	public static final String KEY_MOLECULES = "Molecules";
	public static final String KEY_IONS = "Ions";
	public static final String KEY_SUSPENDED = "Suspended";
	public static final String KEY_COLOR = "Color";

	/** The aqueous solvent (water) species id — contributes no colour (see {@link #blendColor}). */
	public static final ResourceLocation SOLVENT = Solution.WATER;

	private static final String MOLECULE_PREFIX = "M:";
	private static final String ION_PREFIX = "I:";
	private static final String SUSPENDED_PREFIX = "S:";

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

	// ---------------------------------------------------------- molecules domain

	/** Read the molecular composition (species id → part). Empty map if none. */
	public static Map<ResourceLocation, Integer> getMolecules(FluidStack stack) {
		Map<ResourceLocation, Integer> molecules = new LinkedHashMap<>();
		CompoundTag tag = stack.getTag();
		if (tag == null) {
			return molecules;
		}
		CompoundTag c = tag.getCompound(KEY_MOLECULES);
		for (String key : c.getAllKeys()) {
			molecules.put(new ResourceLocation(key), c.getInt(key));
		}
		return molecules;
	}

	/** Write the molecular composition and refresh the blended colour. Does not touch the amount. */
	public static void setMolecules(FluidStack stack, Map<ResourceLocation, Integer> molecules) {
		CompoundTag c = new CompoundTag();
		for (Map.Entry<ResourceLocation, Integer> e : molecules.entrySet()) {
			if (e.getValue() > 0) {
				c.putInt(e.getKey().toString(), e.getValue());
			}
		}
		stack.getOrCreateTag().put(KEY_MOLECULES, c);
		recolor(stack);
	}

	// --------------------------------------------------------------- ions domain

	/** Read the ion multiset (ion id → part). Empty map if none. */
	public static Map<String, Integer> getIons(FluidStack stack) {
		Map<String, Integer> ions = new LinkedHashMap<>();
		CompoundTag tag = stack.getTag();
		if (tag == null) {
			return ions;
		}
		CompoundTag c = tag.getCompound(KEY_IONS);
		for (String key : c.getAllKeys()) {
			ions.put(key, c.getInt(key));
		}
		return ions;
	}

	/**
	 * Write the ion multiset, <b>rejecting non-charge-neutral sets</b>
	 * (Σ charge × part must be 0). On rejection nothing is written and false is
	 * returned (charge neutrality is an invariant — violating it is a bug).
	 */
	public static boolean setIons(FluidStack stack, Map<String, Integer> ions) {
		if (!isChargeNeutral(ions)) {
			ChemicalAddon.LOGGER.error("Rejected non-charge-neutral ion set: {}", ions);
			return false;
		}
		CompoundTag c = new CompoundTag();
		for (Map.Entry<String, Integer> e : ions.entrySet()) {
			if (e.getValue() > 0) {
				c.putInt(e.getKey(), e.getValue());
			}
		}
		stack.getOrCreateTag().put(KEY_IONS, c);
		recolor(stack);
		return true;
	}

	/** True when Σ(charge × part) == 0 (the charge-neutrality invariant). */
	public static boolean isChargeNeutral(Map<String, Integer> ions) {
		long sum = 0;
		for (Map.Entry<String, Integer> e : ions.entrySet()) {
			sum += (long) Ion.chargeOf(e.getKey()) * e.getValue();
		}
		return sum == 0;
	}

	// ----------------------------------------------------- suspended solids domain

	/** Read the suspended-solid composition (solid species id → part). Empty map if none. */
	public static Map<ResourceLocation, Integer> getSuspended(FluidStack stack) {
		Map<ResourceLocation, Integer> suspended = new LinkedHashMap<>();
		CompoundTag tag = stack.getTag();
		if (tag == null) {
			return suspended;
		}
		CompoundTag c = tag.getCompound(KEY_SUSPENDED);
		for (String key : c.getAllKeys()) {
			suspended.put(new ResourceLocation(key), c.getInt(key));
		}
		return suspended;
	}

	/**
	 * Write the suspended-solid composition (uncharged solid species → part).
	 * No charge-neutrality check — suspended solids are electrically neutral.
	 */
	public static void setSuspended(FluidStack stack, Map<ResourceLocation, Integer> suspended) {
		CompoundTag c = new CompoundTag();
		for (Map.Entry<ResourceLocation, Integer> e : suspended.entrySet()) {
			if (e.getValue() > 0) {
				c.putInt(e.getKey().toString(), e.getValue());
			}
		}
		stack.getOrCreateTag().put(KEY_SUSPENDED, c);
		recolor(stack);
	}

	// ----------------------------------------------------- derived absolute amounts

	/**
	 * Derive absolute per-component mB of the <b>molecular</b> domain, from the
	 * joint (molecules + ions) ratio. This is the only place absolute amounts
	 * exist — they are computed, never stored, which keeps the ratio tag stable.
	 */
	public static Map<ResourceLocation, Integer> deriveAmounts(FluidStack stack) {
		Map<ResourceLocation, Integer> molecules = new LinkedHashMap<>();
		Map<String, Integer> ions = new LinkedHashMap<>();
		Map<ResourceLocation, Integer> suspended = new LinkedHashMap<>();
		deriveJoint(stack, molecules, ions, suspended);
		return molecules;
	}

	/** Derive absolute per-component mB of the <b>ion</b> domain (see {@link #deriveAmounts}). */
	public static Map<String, Integer> deriveIonAmounts(FluidStack stack) {
		Map<ResourceLocation, Integer> molecules = new LinkedHashMap<>();
		Map<String, Integer> ions = new LinkedHashMap<>();
		Map<ResourceLocation, Integer> suspended = new LinkedHashMap<>();
		deriveJoint(stack, molecules, ions, suspended);
		return ions;
	}

	/** Derive absolute per-component mB of the <b>suspended-solid</b> domain (see {@link #deriveAmounts}). */
	public static Map<ResourceLocation, Integer> deriveSuspendedAmounts(FluidStack stack) {
		Map<ResourceLocation, Integer> molecules = new LinkedHashMap<>();
		Map<String, Integer> ions = new LinkedHashMap<>();
		Map<ResourceLocation, Integer> suspended = new LinkedHashMap<>();
		deriveJoint(stack, molecules, ions, suspended);
		return suspended;
	}

	/**
	 * Distribute the stack amount across the joint parts exactly once, so the
	 * molecular + ionic + suspended amounts sum to the total with no double-counted
	 * remainder. All three domains share one ratio space: the per-component share is
	 * {@code total × part / Σ(all parts)}.
	 */
	private static void deriveJoint(FluidStack stack, Map<ResourceLocation, Integer> molOut,
		Map<String, Integer> ionOut, Map<ResourceLocation, Integer> suspOut) {
		Map<ResourceLocation, Integer> molParts = getMolecules(stack);
		Map<String, Integer> ionParts = getIons(stack);
		Map<ResourceLocation, Integer> suspParts = getSuspended(stack);
		int total = stack.getAmount();
		int sum = sumParts(molParts) + sumParts(ionParts) + sumParts(suspParts);
		if (total <= 0 || sum <= 0) {
			return;
		}

		// joint parts with a namespace prefix (molecule vs ion vs suspended), inserted
		// molecule-first
		Map<String, Integer> joint = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Integer> e : molParts.entrySet()) {
			joint.put(MOLECULE_PREFIX + e.getKey(), e.getValue());
		}
		for (Map.Entry<String, Integer> e : ionParts.entrySet()) {
			joint.put(ION_PREFIX + e.getKey(), e.getValue());
		}
		for (Map.Entry<ResourceLocation, Integer> e : suspParts.entrySet()) {
			joint.put(SUSPENDED_PREFIX + e.getKey(), e.getValue());
		}

		Map<String, Integer> amounts = distribute(total, joint, sum);
		for (Map.Entry<String, Integer> e : amounts.entrySet()) {
			if (e.getKey().startsWith(MOLECULE_PREFIX)) {
				molOut.put(new ResourceLocation(e.getKey().substring(MOLECULE_PREFIX.length())), e.getValue());
			} else if (e.getKey().startsWith(ION_PREFIX)) {
				ionOut.put(e.getKey().substring(ION_PREFIX.length()), e.getValue());
			} else {
				suspOut.put(new ResourceLocation(e.getKey().substring(SUSPENDED_PREFIX.length())), e.getValue());
			}
		}
	}

	/**
	 * Integer-proportional distribution of {@code total} across {@code parts}
	 * (mole-equivalents); the rounding remainder goes round-robin to the largest
	 * parts so the result always sums exactly to {@code total}.
	 */
	public static Map<String, Integer> distribute(int total, Map<String, Integer> parts) {
		return distribute(total, parts, sumParts(parts));
	}

	/** Integer-proportional distribution; remainder goes round-robin to the largest parts. */
	private static Map<String, Integer> distribute(int total, Map<String, Integer> parts, int sum) {
		Map<String, Integer> amounts = new LinkedHashMap<>();
		if (parts.isEmpty() || total <= 0 || sum <= 0) {
			return amounts;
		}
		List<Map.Entry<String, Integer>> order = new ArrayList<>(parts.entrySet());
		order.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		int assigned = 0;
		for (Map.Entry<String, Integer> e : order) {
			int a = (int) ((long) total * e.getValue() / sum);
			amounts.put(e.getKey(), a);
			assigned += a;
		}
		int idx = 0;
		while (assigned < total && idx < order.size() * 4) {
			Map.Entry<String, Integer> e = order.get(idx % order.size());
			amounts.put(e.getKey(), amounts.get(e.getKey()) + 1);
			assigned++;
			idx++;
		}
		return amounts;
	}

	private static int sumParts(Map<?, Integer> parts) {
		int total = 0;
		for (int v : parts.values()) {
			total += v;
		}
		return total;
	}

	// -------------------------------------------------------------------- colour

	/** The cached blended colour, recomputing + caching it if missing. */
	public static int getColor(FluidStack stack) {
		CompoundTag tag = stack.getOrCreateTag();
		if (tag.contains(KEY_COLOR)) {
			return tag.getInt(KEY_COLOR);
		}
		int color = blendColor(getMolecules(stack), getIons(stack), getSuspended(stack));
		tag.putInt(KEY_COLOR, color);
		return color;
	}

	/** Recompute + cache the blended colour from all three namespaces. */
	private static void recolor(FluidStack stack) {
		stack.getOrCreateTag().putInt(KEY_COLOR, blendColor(getMolecules(stack), getIons(stack), getSuspended(stack)));
	}

	/** Weight-blended ARGB of the three namespaces (per-channel average weighted by part).
	 *  The solvent ({@link #SOLVENT} = water) contributes <b>no</b> colour: it is the
	 *  bulk of every aqueous mixture and would otherwise dominate the blend and wash
	 *  out solute (ion / molecular-solute / suspended-solid) colours. */
	public static int blendColor(Map<ResourceLocation, Integer> molecules, Map<String, Integer> ions,
		Map<ResourceLocation, Integer> suspended) {
		long[] acc = new long[4]; // a, r, g, b
		int total = 0;
		for (Map.Entry<ResourceLocation, Integer> e : molecules.entrySet()) {
			if (SOLVENT.equals(e.getKey())) {
				continue; // colourless solvent
			}
			total += e.getValue();
			accumulate(acc, FluidColors.of(e.getKey()), e.getValue());
		}
		for (Map.Entry<String, Integer> e : ions.entrySet()) {
			total += e.getValue();
			accumulate(acc, IonColors.of(e.getKey()), e.getValue());
		}
		for (Map.Entry<ResourceLocation, Integer> e : suspended.entrySet()) {
			total += e.getValue();
			accumulate(acc, SolidColors.of(e.getKey()), e.getValue());
		}
		if (total <= 0) {
			return 0xFFFFFFFF; // white = no tint (pure solvent / no coloured contents)
		}
		return ((int) (acc[0] / total) << 24)
			| ((int) (acc[1] / total) << 16)
			| ((int) (acc[2] / total) << 8)
			| (int) (acc[3] / total);
	}

	/** Two-domain convenience (suspended empty). */
	public static int blendColor(Map<ResourceLocation, Integer> molecules, Map<String, Integer> ions) {
		return blendColor(molecules, ions, Map.of());
	}

	/** Single-domain convenience (ions + suspended empty). */
	public static int blendColor(Map<ResourceLocation, Integer> molecules) {
		return blendColor(molecules, Map.of(), Map.of());
	}

	private static void accumulate(long[] acc, int c, int amount) {
		acc[0] += (long) ((c >> 24) & 0xFF) * amount;
		acc[1] += (long) ((c >> 16) & 0xFF) * amount;
		acc[2] += (long) ((c >> 8) & 0xFF) * amount;
		acc[3] += (long) (c & 0xFF) * amount;
	}

	// ------------------------------------------------------------------ construct

	/** Build a new mixture FluidStack from a molecular ratio map and a total mB (no ions/suspended). */
	public static FluidStack create(Map<ResourceLocation, Integer> molecules, int total) {
		return create(molecules, Map.of(), Map.of(), total);
	}

	/** Build a new mixture FluidStack from joint (molecules + ions) ratios and a total mB. */
	public static FluidStack create(Map<ResourceLocation, Integer> molecules, Map<String, Integer> ions, int total) {
		return create(molecules, ions, Map.of(), total);
	}

	/** Build a new mixture FluidStack from joint (molecules + ions + suspended) ratios and a total mB. */
	public static FluidStack create(Map<ResourceLocation, Integer> molecules, Map<String, Integer> ions,
		Map<ResourceLocation, Integer> suspended, int total) {
		int g = 0;
		for (int v : molecules.values()) {
			g = gcd(g, v);
		}
		for (int v : ions.values()) {
			g = gcd(g, v);
		}
		for (int v : suspended.values()) {
			g = gcd(g, v);
		}
		FluidStack stack = new FluidStack(fluid(), total);
		setMolecules(stack, g > 1 ? divideMolecules(molecules, g) : molecules);
		if (!setIons(stack, g > 1 ? divideIons(ions, g) : ions)) {
			ChemicalAddon.LOGGER.error("Mixture.create dropped a non-charge-neutral ion set: {}", ions);
		}
		setSuspended(stack, g > 1 ? divideMolecules(suspended, g) : suspended);
		return stack;
	}

	/** GCD-reduce a molecular ratio map to its canonical smallest equivalent. */
	public static Map<ResourceLocation, Integer> reduce(Map<ResourceLocation, Integer> molecules) {
		int g = 0;
		for (int v : molecules.values()) {
			g = gcd(g, v);
		}
		if (g <= 1) {
			return molecules;
		}
		Map<ResourceLocation, Integer> reduced = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Integer> e : molecules.entrySet()) {
			reduced.put(e.getKey(), e.getValue() / g);
		}
		return reduced;
	}

	private static Map<ResourceLocation, Integer> divideMolecules(Map<ResourceLocation, Integer> m, int g) {
		Map<ResourceLocation, Integer> out = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Integer> e : m.entrySet()) {
			out.put(e.getKey(), e.getValue() / g);
		}
		return out;
	}

	private static Map<String, Integer> divideIons(Map<String, Integer> i, int g) {
		Map<String, Integer> out = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> e : i.entrySet()) {
			out.put(e.getKey(), e.getValue() / g);
		}
		return out;
	}

	private static int gcd(int a, int b) {
		return b == 0 ? a : gcd(b, a % b);
	}
}
