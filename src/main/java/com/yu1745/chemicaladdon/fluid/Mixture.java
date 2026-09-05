package com.yu1745.chemicaladdon.fluid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.composition.Chemistry;
import com.yu1745.chemicaladdon.composition.Ion;
import com.yu1745.chemicaladdon.composition.Solution;
import com.yu1745.chemicaladdon.composition.parity.KernelSolutionState;
import com.yu1745.chemicaladdon.composition.parity.Kernel;
import com.yu1745.chemicaladdon.composition.parity.EngineBridge;
import com.yu1745.chemengine.kernel.ChemicalBasis;
import com.yu1745.chemengine.kernel.IPhreeqc;
import com.yu1745.chemicaladdon.registry.AllFluids;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
	/**
	 * A canonical PHREEQC {@code SOLUTION_RAW} reference state.  The text describes
	 * {@link #KEY_ENGINE_REFERENCE_MB} mB, rather than this stack's current amount:
	 * Forge copies an NBT tag unchanged for proportional drains, so this is the only
	 * representation that can survive a 1 mB pipe sample without copying an entire
	 * vessel's chemical inventory into the sample.
	 */
	public static final String KEY_ENGINE_SOLUTION = "EngineSolutionRaw";
	public static final String KEY_ENGINE_REFERENCE_MB = "EngineSolutionReferenceMb";
	public static final String KEY_ENGINE_SOLUTION_VERSION = "EngineSolutionVersion";
	public static final String KEY_ENGINE_SOLIDS = "EngineSolutionSolids";
	public static final String KEY_DISPLAY_REFERENCE_MB = "EngineDisplayReferenceMb";

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

	/** Read one long ratio part from the current display cache format. */
	/** Integer-domain view of the charge-neutrality check (display/test maps). */
	public static boolean isChargeNeutral(Map<String, Integer> ions) {
		long sum = 0;
		for (Map.Entry<String, Integer> e : ions.entrySet()) {
			sum += (long) Ion.chargeOf(e.getKey()) * e.getValue();
		}
		return sum == 0;
	}

	private static long part(CompoundTag c, String key) {
		return c.getLong(key);
	}

	/** Read the molecular composition (species id → part). Empty map if none. */
	public static Map<ResourceLocation, Long> getMolecules(FluidStack stack) {
		Map<ResourceLocation, Long> molecules = new LinkedHashMap<>();
		CompoundTag tag = stack.getTag();
		if (tag == null) {
			return molecules;
		}
		CompoundTag c = tag.getCompound(KEY_MOLECULES);
		for (String key : c.getAllKeys()) {
			molecules.put(new ResourceLocation(key), part(c, key));
		}
		return molecules;
	}

	/** Write the molecular composition and refresh the blended colour. Does not touch the amount. */
	public static void setMolecules(FluidStack stack, Map<ResourceLocation, Long> molecules) {
		CompoundTag c = new CompoundTag();
		for (Map.Entry<ResourceLocation, Long> e : molecules.entrySet()) {
			if (e.getValue() > 0) {
				c.putLong(e.getKey().toString(), e.getValue());
			}
		}
		stack.getOrCreateTag().put(KEY_MOLECULES, c);
		recolor(stack);
	}

	// --------------------------------------------------------------- ions domain

	/** Read the ion multiset (ion id → part). Empty map if none. */
	public static Map<String, Long> getIons(FluidStack stack) {
		Map<String, Long> ions = new LinkedHashMap<>();
		CompoundTag tag = stack.getTag();
		if (tag == null) {
			return ions;
		}
		CompoundTag c = tag.getCompound(KEY_IONS);
		for (String key : c.getAllKeys()) {
			ions.put(key, part(c, key));
		}
		return ions;
	}

	/**
	 * Write the ion multiset, <b>rejecting non-charge-neutral sets</b>
	 * (Σ charge × part must be 0). On rejection nothing is written and false is
	 * returned (charge neutrality is an invariant — violating it is a bug).
	 */
	public static boolean setIons(FluidStack stack, Map<String, Long> ions) {
		if (!isChargeNeutralLong(ions)) {
			ChemicalAddon.LOGGER.error("Rejected non-charge-neutral ion set: {}", ions, ions);
			return false;
		}
		CompoundTag c = new CompoundTag();
		for (Map.Entry<String, Long> e : ions.entrySet()) {
			if (e.getValue() > 0) {
				c.putLong(e.getKey(), e.getValue());
			}
		}
		stack.getOrCreateTag().put(KEY_IONS, c);
		recolor(stack);
		return true;
	}

	/** True when Σ(charge × part) == 0 (the charge-neutrality invariant). */
	public static boolean isChargeNeutralLong(Map<String, Long> ions) {
		long sum = 0;
		for (Map.Entry<String, Long> e : ions.entrySet()) {
			sum += Ion.chargeOf(e.getKey()) * e.getValue();
		}
		return sum == 0;
	}

	// ----------------------------------------------------- suspended solids domain

	/** Read the suspended-solid composition (solid species id → part). Empty map if none. */
	public static Map<ResourceLocation, Long> getSuspended(FluidStack stack) {
		Map<ResourceLocation, Long> suspended = new LinkedHashMap<>();
		CompoundTag tag = stack.getTag();
		if (tag == null) {
			return suspended;
		}
		CompoundTag c = tag.getCompound(KEY_SUSPENDED);
		for (String key : c.getAllKeys()) {
			suspended.put(new ResourceLocation(key), part(c, key));
		}
		return suspended;
	}

	/**
	 * Write the suspended-solid composition (uncharged solid species → part).
	 * No charge-neutrality check — suspended solids are electrically neutral.
	 */
	public static void setSuspended(FluidStack stack, Map<ResourceLocation, Long> suspended) {
		CompoundTag c = new CompoundTag();
		for (Map.Entry<ResourceLocation, Long> e : suspended.entrySet()) {
			if (e.getValue() > 0) {
				c.putLong(e.getKey().toString(), e.getValue());
			}
		}
		stack.getOrCreateTag().put(KEY_SUSPENDED, c);
		recolor(stack);
	}

	// -------------------------------------------------------- settled solids domain

	/** Read the settled-solid composition (solid species id → part). Empty map if none. */
	public static Map<ResourceLocation, Long> getSediment(FluidStack stack) {
		Map<ResourceLocation, Long> sediment = new LinkedHashMap<>();
		CompoundTag tag = stack.getTag();
		if (tag == null) {
			return sediment;
		}
		CompoundTag c = tag.getCompound(KEY_SEDIMENT);
		for (String key : c.getAllKeys()) {
			sediment.put(new ResourceLocation(key), part(c, key));
		}
		return sediment;
	}

	/**
	 * Write the settled-solid composition (uncharged solid species → part). No
	 * charge-neutrality check — settled solids are electrically neutral. Unlike
	 * {@link #setSuspended}, this does <b>not</b> recolor: sediment is a separate
	 * bottom layer rendered with its own tint, not part of the fluid colour.
	 */
	public static void setSediment(FluidStack stack, Map<ResourceLocation, Long> sediment) {
		CompoundTag c = new CompoundTag();
		for (Map.Entry<ResourceLocation, Long> e : sediment.entrySet()) {
			if (e.getValue() > 0) {
				c.putLong(e.getKey().toString(), e.getValue());
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
		return narrowMol(deriveLongView(stack, 1).molecules);
	}

	/** The solver-resolution view: amounts in internal units (mB × {@link Chemistry#UNIT_PER_MB}). */
	public static Map<ResourceLocation, Integer> deriveUnitAmounts(FluidStack stack) {
		return narrowMol(deriveLongView(stack, Chemistry.UNIT_PER_MB).molecules);
	}

	/** The fixed-point fraction view (U18): amounts in quanta (mB × {@link Chemistry#QUANTA_PER_MB}).
	 * The rules engine's round trip runs on this view so sub-unit equilibrium
	 * residuals persist in the ratio tag instead of being truncated each solve. */
	public static Map<ResourceLocation, Long> deriveQuantaAmounts(FluidStack stack) {
		return deriveLongView(stack, Chemistry.QUANTA_PER_MB).molecules;
	}

	/** Derive absolute per-component mB of the <b>ion</b> domain (see {@link #deriveAmounts}). */
	public static Map<String, Integer> deriveIonAmounts(FluidStack stack) {
		return narrowIon(deriveLongView(stack, 1).ions);
	}

	/** The solver-resolution view: amounts in internal units (mB × {@link Chemistry#UNIT_PER_MB}). */
	public static Map<String, Integer> deriveUnitIonAmounts(FluidStack stack) {
		return narrowIon(deriveLongView(stack, Chemistry.UNIT_PER_MB).ions);
	}

	/** The fixed-point fraction view (U18, see {@link #deriveQuantaAmounts}). */
	public static Map<String, Long> deriveQuantaIonAmounts(FluidStack stack) {
		return deriveLongView(stack, Chemistry.QUANTA_PER_MB).ions;
	}

	/** Derive absolute per-component mB of the <b>suspended-solid</b> domain (see {@link #deriveAmounts}). */
	public static Map<ResourceLocation, Integer> deriveSuspendedAmounts(FluidStack stack) {
		return narrowMol(deriveLongView(stack, 1).suspended);
	}

	/** The solver-resolution view: amounts in internal units (mB × {@link Chemistry#UNIT_PER_MB}). */
	public static Map<ResourceLocation, Integer> deriveUnitSuspendedAmounts(FluidStack stack) {
		return narrowMol(deriveLongView(stack, Chemistry.UNIT_PER_MB).suspended);
	}

	/** The fixed-point fraction view (U18, see {@link #deriveQuantaAmounts}). */
	public static Map<ResourceLocation, Long> deriveQuantaSuspendedAmounts(FluidStack stack) {
		return deriveLongView(stack, Chemistry.QUANTA_PER_MB).suspended;
	}

	/** Derive absolute per-component mB of the <b>settled-solid</b> domain (see {@link #deriveAmounts}). */
	public static Map<ResourceLocation, Integer> deriveSedimentAmounts(FluidStack stack) {
		return narrowMol(deriveLongView(stack, 1).sediment);
	}

	/** The solver-resolution view: amounts in internal units (mB × {@link Chemistry#UNIT_PER_MB}). */
	public static Map<ResourceLocation, Integer> deriveUnitSedimentAmounts(FluidStack stack) {
		return narrowMol(deriveLongView(stack, Chemistry.UNIT_PER_MB).sediment);
	}

	/** The fixed-point fraction view (U18, see {@link #deriveQuantaAmounts}). */
	public static Map<ResourceLocation, Long> deriveQuantaSedimentAmounts(FluidStack stack) {
		return deriveLongView(stack, Chemistry.QUANTA_PER_MB).sediment;
	}

	/** The four long-domain views of one derive pass (typed holder). */
	private static final class Views {
		final Map<ResourceLocation, Long> molecules = new LinkedHashMap<>();
		final Map<String, Long> ions = new LinkedHashMap<>();
		final Map<ResourceLocation, Long> suspended = new LinkedHashMap<>();
		final Map<ResourceLocation, Long> sediment = new LinkedHashMap<>();
	}

	/**
	 * Construct a new external aqueous batch from authored neutral formulae.
	 * This is deliberately the sole state-creating entry point: it cannot be
	 * used to reinterpret a stored display view, while every result has a raw
	 * engine state before it can enter a tank.
	 */
	public static FluidStack fromDeclaredComposition(double waterKg, Map<String, Double> formulaMol,
		int amountMb, int temperatureC, java.util.Collection<KernelSolutionState.SolidPhase> solids) {
		if (amountMb <= 0) throw new IllegalArgumentException("mixture amount must be positive");
		IPhreeqc q = Kernel.get();
		synchronized (q) {
			KernelSolutionState state = KernelSolutionState.fromDeclaredFeed(q, waterKg, formulaMol, amountMb,
				temperatureC, solids == null ? java.util.List.of() : solids);
			FluidStack stack = new FluidStack(fluid(), amountMb);
			// Cache only a harmless display placeholder.  All gameplay reads the
			// raw state through the bridge; no ratio can feed chemistry back.
			setMolecules(stack, Map.of(SOLVENT, (long) amountMb));
			Map<ResourceLocation, Long> suspended = new LinkedHashMap<>();
			Map<ResourceLocation, Long> sediment = new LinkedHashMap<>();
			for (KernelSolutionState.SolidPhase solid : state.solids()) {
				ResourceLocation id = new ResourceLocation(solid.speciesId());
				Map<ResourceLocation, Long> target = solid.location() == KernelSolutionState.SolidLocation.SUSPENDED
					? suspended : sediment;
				target.merge(id, Math.max(1L, Math.round(solid.mol() * 1000d)), Long::sum);
			}
			setSuspended(stack, suspended);
			setSediment(stack, sediment);
			setEngineSolution(stack, state);
			Temperature.set(stack, temperatureC);
			return stack;
		}
	}

	/** Immutable raw-kernel state attached to a transportable mixture. */

	/** Read the current versioned kernel state; missing or malformed state is invalid. */
	@javax.annotation.Nullable
	public static KernelSolutionState engineSolution(FluidStack stack) {
		CompoundTag tag = stack.getTag();
		if (tag == null || tag.getInt(KEY_ENGINE_SOLUTION_VERSION) != KernelSolutionState.VERSION
				|| !tag.contains(KEY_ENGINE_SOLUTION) || !tag.contains(KEY_ENGINE_REFERENCE_MB, 99)) {
			return null;
		}
		String raw = tag.getString(KEY_ENGINE_SOLUTION);
		int referenceMb = tag.getInt(KEY_ENGINE_REFERENCE_MB);
		if (raw.isBlank() || referenceMb <= 0) return null;
		List<KernelSolutionState.SolidPhase> solids = new ArrayList<>();
		ListTag stored = tag.getList(KEY_ENGINE_SOLIDS, net.minecraft.nbt.Tag.TAG_COMPOUND);
		for (int i = 0; i < stored.size(); i++) {
			CompoundTag solid = stored.getCompound(i);
			try {
				solids.add(new KernelSolutionState.SolidPhase(solid.getString("Species"), solid.getDouble("Mol"),
					KernelSolutionState.SolidLocation.valueOf(solid.getString("Location"))));
			} catch (IllegalArgumentException ignored) {
				return null; // malformed state is rejected with the whole mixture
			}
		}
		return new KernelSolutionState(raw, referenceMb, solids);
	}

	/** Store a reference solution. Callers must use a reference amount independent of later proportional drains. */
	public static void setEngineSolution(FluidStack stack, String raw, int referenceMb) {
		setEngineSolution(stack, new KernelSolutionState(raw, referenceMb));
	}

	/** Store the complete engine state; display tags remain an expendable cache. */
	public static void setEngineSolution(FluidStack stack, KernelSolutionState state) {
		if (!isMixture(stack)) {
			throw new IllegalArgumentException("kernel state belongs only on mixture fluid");
		}
		CompoundTag tag = stack.getOrCreateTag();
		tag.putString(KEY_ENGINE_SOLUTION, state.raw());
		tag.putInt(KEY_ENGINE_REFERENCE_MB, state.referenceMb());
		tag.putInt(KEY_ENGINE_SOLUTION_VERSION, KernelSolutionState.VERSION);
		ListTag solids = new ListTag();
		for (KernelSolutionState.SolidPhase solid : state.solids()) {
			CompoundTag encoded = new CompoundTag();
			encoded.putString("Species", solid.speciesId());
			encoded.putDouble("Mol", solid.mol());
			encoded.putString("Location", solid.location().name());
			solids.add(encoded);
		}
		tag.put(KEY_ENGINE_SOLIDS, solids);
		tag.putInt(KEY_DISPLAY_REFERENCE_MB, state.referenceMb());
		refreshEngineDisplay(stack, state);
	}

	/* Native state -> cosmetic cache. This is deliberately one-way: no getter or
	 * gameplay transaction ever turns these rounded labels back into chemistry. */
	private static final Map<String, String> DISPLAY_IONS = ChemicalBasis.loadDefault().displayIons();

	private static void refreshEngineDisplay(FluidStack stack, KernelSolutionState state) {
		try {
			IPhreeqc q = Kernel.get();
			synchronized (q) {
				var view = EngineBridge.derive(q, state, DISPLAY_IONS.keySet());
				Map<ResourceLocation, Long> molecules = Map.of(SOLVENT,
						Math.max(1L, Math.round(view.waterKg() * 1000 * Chemistry.UNIT_PER_MB)));
				Map<String, Long> ions = new LinkedHashMap<>();
				for (var entry : DISPLAY_IONS.entrySet()) {
					long part = Math.round(view.aqueousMol().getOrDefault(entry.getKey(), 0d) * 1000 * Chemistry.UNIT_PER_MB);
					if (part > 0) ions.merge(entry.getValue(), part, Long::sum);
				}
				writeDisplayIons(stack, ions); // speciation may be non-neutral; no fake H/OH balancing
				setMolecules(stack, molecules);
				Map<ResourceLocation, Long> suspended = new LinkedHashMap<>(), sediment = new LinkedHashMap<>();
				for (var solid : state.solids()) {
					ResourceLocation id = new ResourceLocation(solid.speciesId());
					(solid.location() == KernelSolutionState.SolidLocation.SUSPENDED ? suspended : sediment)
						.merge(id, Math.max(1L, Math.round(solid.mol() * 1000 * Chemistry.UNIT_PER_MB)), Long::sum);
				}
				setSuspended(stack, suspended);
				setSediment(stack, sediment);
			}
		} catch (RuntimeException ex) {
			ChemicalAddon.LOGGER.warn("Native display projection failed: {}", ex.getMessage());
			writeDisplayIons(stack, Map.of());
			setMolecules(stack, Map.of());
			setSuspended(stack, Map.of());
			setSediment(stack, Map.of());
		}
	}

	private static void writeDisplayIons(FluidStack stack, Map<String, Long> ions) {
		CompoundTag c = new CompoundTag();
		for (var e : ions.entrySet()) if (e.getValue() > 0) c.putLong(e.getKey(), e.getValue());
		stack.getOrCreateTag().put(KEY_IONS, c);
		recolor(stack);
	}

	/**
	 * Explicitly invalidate the native payload on a rejected or deliberately
	 * destructive path. A stack without this payload is rejected at the tank
	 * boundary; this method is never a display-domain conversion mechanism.
	 */
	public static void clearEngineSolution(FluidStack stack) {
		CompoundTag tag = stack.getTag();
		if (tag != null) {
			tag.remove(KEY_ENGINE_SOLUTION);
			tag.remove(KEY_ENGINE_REFERENCE_MB);
			tag.remove(KEY_ENGINE_SOLUTION_VERSION);
			tag.remove(KEY_ENGINE_SOLIDS);
		}
	}

	private static Views deriveLongView(FluidStack stack, long scale) {
		Views v = new Views();
		KernelSolutionState engine = engineSolution(stack);
		if (engine != null) {
			deriveEngineDisplay(stack, engine.referenceMb(), scale, v);
			return v;
		}
		deriveJointLong(stack, v.molecules, v.ions, v.suspended, v.sediment, scale);
		return v;
	}

	/** Engine display cache stores extensive reference quantities, never ratio parts. */
	private static void deriveEngineDisplay(FluidStack stack, int referenceMb, long scale, Views out) {
		double factor = (double) stack.getAmount() * scale / referenceMb / Chemistry.UNIT_PER_MB;
		for (var e : getMolecules(stack).entrySet()) out.molecules.put(e.getKey(), Math.round(e.getValue() * factor));
		for (var e : getIons(stack).entrySet()) out.ions.put(e.getKey(), Math.round(e.getValue() * factor));
		for (var e : getSuspended(stack).entrySet()) out.suspended.put(e.getKey(), Math.round(e.getValue() * factor));
		for (var e : getSediment(stack).entrySet()) out.sediment.put(e.getKey(), Math.round(e.getValue() * factor));
	}

	private static Map<ResourceLocation, Integer> narrowMol(Map<ResourceLocation, Long> m) {
		Map<ResourceLocation, Integer> out = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Long> e : m.entrySet()) {
			out.put(e.getKey(), (int) Math.min(e.getValue(), Integer.MAX_VALUE));
		}
		return out;
	}

	private static Map<String, Integer> narrowIon(Map<String, Long> m) {
		Map<String, Integer> out = new LinkedHashMap<>();
		for (Map.Entry<String, Long> e : m.entrySet()) {
			out.put(e.getKey(), (int) Math.min(e.getValue(), Integer.MAX_VALUE));
		}
		return out;
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
	private static void deriveJointLong(FluidStack stack, Map<ResourceLocation, Long> molOut,
		Map<String, Long> ionOut, Map<ResourceLocation, Long> suspOut,
		Map<ResourceLocation, Long> sedOut, long scale) {
		Map<ResourceLocation, Long> molParts = getMolecules(stack);
		Map<String, Long> ionParts = getIons(stack);
		Map<ResourceLocation, Long> suspParts = getSuspended(stack);
		Map<ResourceLocation, Long> sedParts = getSediment(stack);
		long total = (long) stack.getAmount() * scale;
		long sum = sumPartsLong(molParts) + sumPartsLong(ionParts) + sumPartsLong(suspParts) + sumPartsLong(sedParts);
		if (total <= 0 || sum <= 0) {
			return;
		}

		// joint parts with a namespace prefix (molecule vs ion vs suspended vs
		// sediment), inserted molecule-first
		Map<String, Long> joint = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Long> e : molParts.entrySet()) {
			joint.put(MOLECULE_PREFIX + e.getKey(), e.getValue());
		}
		for (Map.Entry<String, Long> e : ionParts.entrySet()) {
			joint.put(ION_PREFIX + e.getKey(), e.getValue());
		}
		for (Map.Entry<ResourceLocation, Long> e : suspParts.entrySet()) {
			joint.put(SUSPENDED_PREFIX + e.getKey(), e.getValue());
		}
		for (Map.Entry<ResourceLocation, Long> e : sedParts.entrySet()) {
			joint.put(SEDIMENT_PREFIX + e.getKey(), e.getValue());
		}

		Map<String, Long> amounts = distributeLong(total, joint, sum);
		for (Map.Entry<String, Long> e : amounts.entrySet()) {
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
	 * parts so the result always sums exactly to {@code total}. Long-domain
	 * (the fine-grid quanta view): total×part stays far inside long range for
	 * any realistic vessel.
	 */
	public static Map<String, Long> distributeLong(long total, Map<String, Long> parts) {
		return distributeLong(total, parts, sumPartsLong(parts));
	}

	private static Map<String, Long> distributeLong(long total, Map<String, Long> parts, long sum) {
		Map<String, Long> amounts = new LinkedHashMap<>();
		if (parts.isEmpty() || total <= 0 || sum <= 0) {
			return amounts;
		}
		List<Map.Entry<String, Long>> order = new ArrayList<>(parts.entrySet());
		order.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
		long assigned = 0;
		for (Map.Entry<String, Long> e : order) {
			// 128-bit exact share: on the quanta grid total × part reaches ~1e19
			// and overflows long (first seen as a negative sediment share) —
			// floor via BigInteger, the round-robin below absorbs the remainders
			long a = java.math.BigInteger.valueOf(total)
				.multiply(java.math.BigInteger.valueOf(e.getValue()))
				.divide(java.math.BigInteger.valueOf(sum)).longValue();
			amounts.put(e.getKey(), a);
			assigned += a;
		}
		int idx = 0;
		while (assigned < total && idx < order.size() * 4) {
			Map.Entry<String, Long> e = order.get(idx % order.size());
			amounts.put(e.getKey(), amounts.get(e.getKey()) + 1);
			assigned++;
			idx++;
		}
		return amounts;
	}

	private static long sumPartsLong(Map<?, Long> parts) {
		long total = 0;
		for (long v : parts.values()) {
			total += v;
		}
		return total;
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
		int color = blendColorLong(getMolecules(stack), getIons(stack), getSuspended(stack));
		tag.putInt(KEY_COLOR, color);
		return color;
	}

	/**
	 * Recompute + cache the blended colour from all three namespaces. Public so
	 * vessel tanks can call it on fill: a creative bucket packs an opaque
	 * display tint ({@code 0xFF000000 | species.color}) into its FluidStack so
	 * the item renders distinctly — once that stack enters a vessel, the colour
	 * must re-derive from the actual contents (colourless → CLEAR_TINT), not
	 * keep the bucket's identity tint.
	 */
	public static void recolor(FluidStack stack) {
		stack.getOrCreateTag().putInt(KEY_COLOR,
			blendColorLong(getMolecules(stack), getIons(stack), getSuspended(stack)));
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
	public static int blendColorLong(Map<ResourceLocation, Long> molecules, Map<String, Long> ions,
			Map<ResourceLocation, Long> suspended) {
		long[] acc = new long[4]; // a, r, g, b
		long total = 0;
		long solvent = 0;
		for (Map.Entry<ResourceLocation, Long> e : molecules.entrySet()) {
			if (SOLVENT.equals(e.getKey())) {
				solvent += e.getValue();
				continue; // colourless solvent
			}
			total += e.getValue();
			accumulate(acc, FluidColors.of(e.getKey()), e.getValue());
		}
		for (Map.Entry<String, Long> e : ions.entrySet()) {
			int c = IonColors.of(e.getKey());
			if (c == IonColors.CLEAR_TINT) {
				continue; // colourless ion: no hue, no visible concentration
			}
			total += e.getValue();
			accumulate(acc, c, e.getValue());
		}
		for (Map.Entry<ResourceLocation, Long> e : suspended.entrySet()) {
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

	public static int blendColor(Map<ResourceLocation, Integer> molecules, Map<String, Integer> ions,
			Map<ResourceLocation, Integer> suspended) {
		return blendColorLong(widenMol(molecules), widenIon(ions), widenMol(suspended));
	}

	private static Map<ResourceLocation, Long> widenMol(Map<ResourceLocation, Integer> m) {
		Map<ResourceLocation, Long> out = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Integer> e : m.entrySet()) {
			out.put(e.getKey(), e.getValue().longValue());
		}
		return out;
	}

	private static Map<String, Long> widenIon(Map<String, Integer> m) {
		Map<String, Long> out = new LinkedHashMap<>();
		for (Map.Entry<String, Integer> e : m.entrySet()) {
			out.put(e.getKey(), e.getValue().longValue());
		}
		return out;
	}

	/**
	 * Rescale {@code color}'s alpha by the coloured-solute concentration: linear
	 * interpolation from {@link IonColors#CLEAR_TINT}'s alpha (trace solute) to the
	 * blend's own alpha (at or above {@link #SATURATED_SOLUTE_FRACTION}). Non-aqueous
	 * mixes (no solvent) have fraction 1 and keep full opacity.
	 */
	private static int scaleAlphaByConcentration(int color, long soluteParts, long solventParts) {
		float fraction = soluteParts / (float) (soluteParts + solventParts);
		float t = Math.min(1f, fraction / SATURATED_SOLUTE_FRACTION);
		int clearA = (IonColors.CLEAR_TINT >>> 24) & 0xFF;
		int alpha = Math.round(clearA + (((color >>> 24) & 0xFF) - clearA) * t);
		return (alpha << 24) | (color & 0x00FFFFFF);
	}

	/** Two-domain convenience (suspended empty). */
	public static int blendColorLong(Map<ResourceLocation, Long> molecules, Map<String, Long> ions) {
		return blendColorLong(molecules, ions, Map.of());
	}

	/** Single-domain convenience (ions + suspended empty). */
	public static int blendColorLong(Map<ResourceLocation, Long> molecules) {
		return blendColorLong(molecules, Map.of(), Map.of());
	}

	private static void accumulate(long[] acc, int c, long amount) {
		acc[0] += (long) ((c >> 24) & 0xFF) * amount;
		acc[1] += (long) ((c >> 16) & 0xFF) * amount;
		acc[2] += (long) ((c >> 8) & 0xFF) * amount;
		acc[3] += (long) (c & 0xFF) * amount;
	}

	// ------------------------------------------------------------------ construct

	public static FluidStack create(Map<ResourceLocation, Integer> molecules, int total) {
		return createLong(widenMol(molecules), Map.of(), Map.of(), Map.of(), total);
	}

	public static FluidStack create(Map<ResourceLocation, Integer> molecules, Map<String, Integer> ions, int total) {
		return createLong(widenMol(molecules), widenIon(ions), Map.of(), Map.of(), total);
	}

	public static FluidStack create(Map<ResourceLocation, Integer> molecules, Map<String, Integer> ions,
		Map<ResourceLocation, Integer> suspended, int total) {
		return createLong(widenMol(molecules), widenIon(ions), widenMol(suspended), Map.of(), total);
	}

	public static FluidStack create(Map<ResourceLocation, Integer> molecules, Map<String, Integer> ions,
		Map<ResourceLocation, Integer> suspended, Map<ResourceLocation, Integer> sediment, int total) {
		return createLong(widenMol(molecules), widenIon(ions), widenMol(suspended), widenMol(sediment), total);
	}

	/** Build a new mixture FluidStack from joint (molecules + ions + suspended + sediment) ratios and a total mB. */
	public static FluidStack createLong(Map<ResourceLocation, Long> molecules, Map<String, Long> ions,
		Map<ResourceLocation, Long> suspended, Map<ResourceLocation, Long> sediment, int total) {
		long g = 0;
		for (long v : molecules.values()) {
			g = gcd(g, v);
		}
		for (long v : ions.values()) {
			g = gcd(g, v);
		}
		for (long v : suspended.values()) {
			g = gcd(g, v);
		}
		for (long v : sediment.values()) {
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
	public static Map<ResourceLocation, Long> reduce(Map<ResourceLocation, Long> molecules) {
		long g = 0;
		for (long v : molecules.values()) {
			g = gcd(g, v);
		}
		if (g <= 1) {
			return molecules;
		}
		Map<ResourceLocation, Long> reduced = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Long> e : molecules.entrySet()) {
			reduced.put(e.getKey(), e.getValue() / g);
		}
		return reduced;
	}

	private static Map<ResourceLocation, Long> divideMolecules(Map<ResourceLocation, Long> m, long g) {
		Map<ResourceLocation, Long> out = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Long> e : m.entrySet()) {
			out.put(e.getKey(), e.getValue() / g);
		}
		return out;
	}

	private static Map<String, Long> divideIons(Map<String, Long> i, long g) {
		Map<String, Long> out = new LinkedHashMap<>();
		for (Map.Entry<String, Long> e : i.entrySet()) {
			out.put(e.getKey(), e.getValue() / g);
		}
		return out;
	}

	private static long gcd(long a, long b) {
		return b == 0 ? a : gcd(b, a % b);
	}
}
