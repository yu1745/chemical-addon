package com.yu1745.chemicaladdon.fluid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.composition.Chemistry;
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
 *   Sediment:  { "chemicaladdon:ammonium_nitrate": 3, ... } // settled solid species → parts (crystals)
 *   Color:  &lt;int ARGB&gt;          // cached blend of the fluid tint (molecules+ions+suspended), recomputed on write
 * </pre>
 */
public final class Mixture {

	public static final String KEY_MOLECULES = "Molecules";
	public static final String KEY_IONS = "Ions";
	public static final String KEY_SUSPENDED = "Suspended";
	public static final String KEY_SEDIMENT = "Sediment";
	public static final String KEY_COLOR = "Color";

	/** The aqueous solvent (water) species id — contributes no colour (see {@link #blendColor}). */
	public static final ResourceLocation SOLVENT = Solution.WATER;

	private static final String MOLECULE_PREFIX = "M:";
	private static final String ION_PREFIX = "I:";
	private static final String SUSPENDED_PREFIX = "S:";
	private static final String SEDIMENT_PREFIX = "D:";

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

	// -------------------------------------------------------- settled solids domain

	/** Read the settled-solid composition (solid species id → part). Empty map if none. */
	public static Map<ResourceLocation, Integer> getSediment(FluidStack stack) {
		Map<ResourceLocation, Integer> sediment = new LinkedHashMap<>();
		CompoundTag tag = stack.getTag();
		if (tag == null) {
			return sediment;
		}
		CompoundTag c = tag.getCompound(KEY_SEDIMENT);
		for (String key : c.getAllKeys()) {
			sediment.put(new ResourceLocation(key), c.getInt(key));
		}
		return sediment;
	}

	/**
	 * Write the settled-solid composition (uncharged solid species → part). No
	 * charge-neutrality check — settled solids are electrically neutral. Unlike
	 * {@link #setSuspended}, this does <b>not</b> recolor: sediment is a separate
	 * bottom layer rendered with its own tint, not part of the fluid colour.
	 */
	public static void setSediment(FluidStack stack, Map<ResourceLocation, Integer> sediment) {
		CompoundTag c = new CompoundTag();
		for (Map.Entry<ResourceLocation, Integer> e : sediment.entrySet()) {
			if (e.getValue() > 0) {
				c.putInt(e.getKey().toString(), e.getValue());
			}
		}
		stack.getOrCreateTag().put(KEY_SEDIMENT, c);
	}

	// ----------------------------------------------------- derived absolute amounts

	/**
	 * Derive absolute per-component mB of the <b>molecular</b> domain, from the
	 * joint (molecules + ions) ratio. This is the only place absolute amounts
	 * exist — they are computed, never stored, which keeps the ratio tag stable.
	 */
	public static Map<ResourceLocation, Integer> deriveAmounts(FluidStack stack) {
		return deriveAmounts(stack, 1);
	}

	/** The solver-resolution view: amounts in internal units (mB × {@link Chemistry#UNIT_PER_MB}). */
	public static Map<ResourceLocation, Integer> deriveUnitAmounts(FluidStack stack) {
		return deriveAmounts(stack, Chemistry.UNIT_PER_MB);
	}

	private static Map<ResourceLocation, Integer> deriveAmounts(FluidStack stack, int scale) {
		Map<ResourceLocation, Integer> molecules = new LinkedHashMap<>();
		Map<String, Integer> ions = new LinkedHashMap<>();
		Map<ResourceLocation, Integer> suspended = new LinkedHashMap<>();
		Map<ResourceLocation, Integer> sediment = new LinkedHashMap<>();
		deriveJoint(stack, molecules, ions, suspended, sediment, scale);
		return molecules;
	}

	/** Derive absolute per-component mB of the <b>ion</b> domain (see {@link #deriveAmounts}). */
	public static Map<String, Integer> deriveIonAmounts(FluidStack stack) {
		return deriveIonAmounts(stack, 1);
	}

	/** The solver-resolution view: amounts in internal units (mB × {@link Chemistry#UNIT_PER_MB}). */
	public static Map<String, Integer> deriveUnitIonAmounts(FluidStack stack) {
		return deriveIonAmounts(stack, Chemistry.UNIT_PER_MB);
	}

	private static Map<String, Integer> deriveIonAmounts(FluidStack stack, int scale) {
		Map<ResourceLocation, Integer> molecules = new LinkedHashMap<>();
		Map<String, Integer> ions = new LinkedHashMap<>();
		Map<ResourceLocation, Integer> suspended = new LinkedHashMap<>();
		Map<ResourceLocation, Integer> sediment = new LinkedHashMap<>();
		deriveJoint(stack, molecules, ions, suspended, sediment, scale);
		return ions;
	}

	/** Derive absolute per-component mB of the <b>suspended-solid</b> domain (see {@link #deriveAmounts}). */
	public static Map<ResourceLocation, Integer> deriveSuspendedAmounts(FluidStack stack) {
		return deriveSuspendedAmounts(stack, 1);
	}

	/** The solver-resolution view: amounts in internal units (mB × {@link Chemistry#UNIT_PER_MB}). */
	public static Map<ResourceLocation, Integer> deriveUnitSuspendedAmounts(FluidStack stack) {
		return deriveSuspendedAmounts(stack, Chemistry.UNIT_PER_MB);
	}

	private static Map<ResourceLocation, Integer> deriveSuspendedAmounts(FluidStack stack, int scale) {
		Map<ResourceLocation, Integer> molecules = new LinkedHashMap<>();
		Map<String, Integer> ions = new LinkedHashMap<>();
		Map<ResourceLocation, Integer> suspended = new LinkedHashMap<>();
		Map<ResourceLocation, Integer> sediment = new LinkedHashMap<>();
		deriveJoint(stack, molecules, ions, suspended, sediment, scale);
		return suspended;
	}

	/** Derive absolute per-component mB of the <b>settled-solid</b> domain (see {@link #deriveAmounts}). */
	public static Map<ResourceLocation, Integer> deriveSedimentAmounts(FluidStack stack) {
		return deriveSedimentAmounts(stack, 1);
	}

	/** The solver-resolution view: amounts in internal units (mB × {@link Chemistry#UNIT_PER_MB}). */
	public static Map<ResourceLocation, Integer> deriveUnitSedimentAmounts(FluidStack stack) {
		return deriveSedimentAmounts(stack, Chemistry.UNIT_PER_MB);
	}

	private static Map<ResourceLocation, Integer> deriveSedimentAmounts(FluidStack stack, int scale) {
		Map<ResourceLocation, Integer> molecules = new LinkedHashMap<>();
		Map<String, Integer> ions = new LinkedHashMap<>();
		Map<ResourceLocation, Integer> suspended = new LinkedHashMap<>();
		Map<ResourceLocation, Integer> sediment = new LinkedHashMap<>();
		deriveJoint(stack, molecules, ions, suspended, sediment, scale);
		return sediment;
	}

	/**
	 * Distribute the stack amount across the joint parts exactly once, so the
	 * molecular + ionic + suspended + sediment amounts sum to the total with no
	 * double-counted remainder. All four domains share one ratio space: the
	 * per-component share is {@code total × part / Σ(all parts)}. {@code scale}
	 * selects the unit (1 = mB transport view; {@link Chemistry#UNIT_PER_MB} =
	 * the solver's 1/1000 mB grid, on which sub-mB equilibrium residuals are
	 * representable).
	 */
	private static void deriveJoint(FluidStack stack, Map<ResourceLocation, Integer> molOut,
		Map<String, Integer> ionOut, Map<ResourceLocation, Integer> suspOut,
		Map<ResourceLocation, Integer> sedOut, int scale) {
		Map<ResourceLocation, Integer> molParts = getMolecules(stack);
		Map<String, Integer> ionParts = getIons(stack);
		Map<ResourceLocation, Integer> suspParts = getSuspended(stack);
		Map<ResourceLocation, Integer> sedParts = getSediment(stack);
		// soft cap: unit-view totals must fit int (the solver's Long maps and the
		// int distribution below both stay safe even for absurd external stacks)
		long total = Math.min((long) stack.getAmount() * scale, Integer.MAX_VALUE - 8L);
		int sum = sumParts(molParts) + sumParts(ionParts) + sumParts(suspParts) + sumParts(sedParts);
		if (total <= 0 || sum <= 0) {
			return;
		}

		// joint parts with a namespace prefix (molecule vs ion vs suspended vs
		// sediment), inserted molecule-first
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
		for (Map.Entry<ResourceLocation, Integer> e : sedParts.entrySet()) {
			joint.put(SEDIMENT_PREFIX + e.getKey(), e.getValue());
		}

		Map<String, Integer> amounts = distribute(total, joint, sum);
		for (Map.Entry<String, Integer> e : amounts.entrySet()) {
			if (e.getKey().startsWith(MOLECULE_PREFIX)) {
				molOut.put(new ResourceLocation(e.getKey().substring(MOLECULE_PREFIX.length())), e.getValue());
			} else if (e.getKey().startsWith(ION_PREFIX)) {
				ionOut.put(e.getKey().substring(ION_PREFIX.length()), e.getValue());
			} else if (e.getKey().startsWith(SUSPENDED_PREFIX)) {
				suspOut.put(new ResourceLocation(e.getKey().substring(SUSPENDED_PREFIX.length())), e.getValue());
			} else {
				sedOut.put(new ResourceLocation(e.getKey().substring(SEDIMENT_PREFIX.length())), e.getValue());
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
	private static Map<String, Integer> distribute(long total, Map<String, Integer> parts, int sum) {
		Map<String, Integer> amounts = new LinkedHashMap<>();
		if (parts.isEmpty() || total <= 0 || sum <= 0) {
			return amounts;
		}
		List<Map.Entry<String, Integer>> order = new ArrayList<>(parts.entrySet());
		order.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
		long assigned = 0;
		for (Map.Entry<String, Integer> e : order) {
			int a = (int) (total * e.getValue() / sum);
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

	/**
	 * The solute fraction (coloured parts ÷ all liquid parts) at which the blend
	 * reaches full opacity. Anchored to the canonical species {@code solventRatio}
	 * of 10 water : 1 solute → 1/11, so a "standard" recipe concentration renders
	 * full strength and further dilution fades toward {@link IonColors#CLEAR_TINT}.
	 */
	private static final float SATURATED_SOLUTE_FRACTION = 1f / 11;

	/** Weight-blended ARGB of the three namespaces (per-channel average weighted by part).
	 *  The solvent ({@link #SOLVENT} = water) contributes <b>no</b> hue: it is the
	 *  bulk of every aqueous mixture and would otherwise dominate the blend and wash
	 *  out solute (ion / molecular-solute / suspended-solid) colours. It does,
	 *  however, set the <b>opacity</b>: the final alpha is scaled by the coloured
	 *  solute fraction (see {@link #SATURATED_SOLUTE_FRACTION}), so a dilute solution
	 *  renders faint and a concentrated one renders deep — same hue, varying depth.
	 *  Colourless ions are excluded from both the hue average and the concentration
	 *  (they must neither wash out a coloured solute like Cu+2 nor make a colourless
	 *  solution read as anything but {@link IonColors#CLEAR_TINT}). */
	public static int blendColor(Map<ResourceLocation, Integer> molecules, Map<String, Integer> ions,
			Map<ResourceLocation, Integer> suspended) {
		long[] acc = new long[4]; // a, r, g, b
		int total = 0;
		int solvent = 0;
		for (Map.Entry<ResourceLocation, Integer> e : molecules.entrySet()) {
			if (SOLVENT.equals(e.getKey())) {
				solvent += e.getValue();
				continue; // colourless solvent
			}
			total += e.getValue();
			accumulate(acc, FluidColors.of(e.getKey()), e.getValue());
		}
		for (Map.Entry<String, Integer> e : ions.entrySet()) {
			int c = IonColors.of(e.getKey());
			if (c == IonColors.CLEAR_TINT) {
				continue; // colourless ion: no hue, no visible concentration
			}
			total += e.getValue();
			accumulate(acc, c, e.getValue());
		}
		for (Map.Entry<ResourceLocation, Integer> e : suspended.entrySet()) {
			total += e.getValue();
			accumulate(acc, SolidColors.of(e.getKey()), e.getValue());
		}
		if (total <= 0) {
			return IonColors.CLEAR_TINT; // faint white = no tint (pure solvent / colourless contents)
		}
		int blended = ((int) (acc[0] / total) << 24)
			| ((int) (acc[1] / total) << 16)
			| ((int) (acc[2] / total) << 8)
			| (int) (acc[3] / total);
		return scaleAlphaByConcentration(blended, total, solvent);
	}

	/**
	 * Rescale {@code color}'s alpha by the coloured-solute concentration: linear
	 * interpolation from {@link IonColors#CLEAR_TINT}'s alpha (trace solute) to the
	 * blend's own alpha (at or above {@link #SATURATED_SOLUTE_FRACTION}). Non-aqueous
	 * mixes (no solvent) have fraction 1 and keep full opacity.
	 */
	private static int scaleAlphaByConcentration(int color, int soluteParts, int solventParts) {
		float fraction = soluteParts / (float) (soluteParts + solventParts);
		float t = Math.min(1f, fraction / SATURATED_SOLUTE_FRACTION);
		int clearA = (IonColors.CLEAR_TINT >>> 24) & 0xFF;
		int alpha = Math.round(clearA + (((color >>> 24) & 0xFF) - clearA) * t);
		return (alpha << 24) | (color & 0x00FFFFFF);
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

	/** Build a new mixture FluidStack from a molecular ratio map and a total mB (no ions/suspended/sediment). */
	public static FluidStack create(Map<ResourceLocation, Integer> molecules, int total) {
		return create(molecules, Map.of(), Map.of(), Map.of(), total);
	}

	/** Build a new mixture FluidStack from joint (molecules + ions) ratios and a total mB. */
	public static FluidStack create(Map<ResourceLocation, Integer> molecules, Map<String, Integer> ions, int total) {
		return create(molecules, ions, Map.of(), Map.of(), total);
	}

	/** Build a new mixture FluidStack from joint (molecules + ions + suspended) ratios and a total mB. */
	public static FluidStack create(Map<ResourceLocation, Integer> molecules, Map<String, Integer> ions,
		Map<ResourceLocation, Integer> suspended, int total) {
		return create(molecules, ions, suspended, Map.of(), total);
	}

	/** Build a new mixture FluidStack from joint (molecules + ions + suspended + sediment) ratios and a total mB. */
	public static FluidStack create(Map<ResourceLocation, Integer> molecules, Map<String, Integer> ions,
		Map<ResourceLocation, Integer> suspended, Map<ResourceLocation, Integer> sediment, int total) {
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
		for (int v : sediment.values()) {
			g = gcd(g, v);
		}
		FluidStack stack = new FluidStack(fluid(), total);
		setMolecules(stack, g > 1 ? divideMolecules(molecules, g) : molecules);
		if (!setIons(stack, g > 1 ? divideIons(ions, g) : ions)) {
			ChemicalAddon.LOGGER.error("Mixture.create dropped a non-charge-neutral ion set: {}", ions);
		}
		setSuspended(stack, g > 1 ? divideMolecules(suspended, g) : suspended);
		setSediment(stack, g > 1 ? divideMolecules(sediment, g) : sediment);
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
