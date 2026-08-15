package com.yu1745.chemicaladdon.reactor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.yu1745.chemicaladdon.composition.Chemistry;
import com.yu1745.chemicaladdon.composition.Solution;
import com.yu1745.chemicaladdon.composition.Species;
import com.yu1745.chemicaladdon.composition.SpeciesManager;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.fluid.Miscibility;
import com.yu1745.chemicaladdon.fluid.Temperature;
import com.yu1745.chemicaladdon.item.MixedResidueItem;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Emergent chemistry rules engine (plans/03 §8, mass-action v2). Runs on a
 * vessel's contents each reaction tick, <b>before</b> the whitelist recipe
 * engine: it derives what happens from the mixture's ions + molecules directly
 * — equilibrium precipitation/dissolution, complexation, neutralisation,
 * curve crystallisation/redissolution, solid-item dissolution and open-vessel
 * evaporation — with no dissociation/recombination round-trip.
 *
 * <p>One pass: settle the tank (which expands any solution species into its
 * ions + water), dissolve solid items up to saturation, read the four domains,
 * solve, evaporate if boiling in an open vessel, then write the final domains
 * back. Precipitated solids live in the mixture's {@code Suspended} domain (a
 * turbid slurry; a filter press separates them into items); crystallised
 * solids settle into {@code Sediment}. If nothing changed the tank is left
 * untouched. The solved {@link Solution} (with its speciation report) is
 * returned for diagnostics regardless.
 */
public final class RulesEngine {

	/** mB of a solid per item (filter press output, solid-item dissolution input). */
	public static final int MB_PER_ITEM = 1000;

	/** {@link #MB_PER_ITEM} in solver units (1 item = one bucket of formula units). */
	public static final long ITEM_UNITS = (long) MB_PER_ITEM * Chemistry.UNIT_PER_MB;

	/**
	 * Grains per item — the intermediate denomination across the item↔fluid
	 * boundary (plans/03 §5 "中间面额" invariant): 1 grain = 62.5 mB, so seeding
	 * a metastable solution and small-batch dosing never hit the "either 0 or a
	 * whole bucket" cliff. Produced 1 item → 16 grains via Create crushing.
	 */
	public static final int GRAINS_PER_ITEM = 16;

	/** One grain in solver units (exactly representable on the 10⁴ grid). */
	public static final long GRAIN_UNITS = ITEM_UNITS / GRAINS_PER_ITEM;

	/**
	 * Wet-cake mother-liquor holdup (U16.5, plans/03 §12): the pore fraction of
	 * a settled/filtered solid mass that stays full of liquor — extraction is
	 * mechanically imperfect, and the entrained liquor (a proportional share of
	 * the vessel's liquid at extraction time) travels with the cake into the
	 * residue item. One declared constant, two manifestations: cake
	 * entrainment here, and the decant ports' inability to suck a settled bed
	 * dry (reslurry washing's driving force).
	 */
	public static final double CAKE_LIQUOR_FRACTION = 0.3;

	/**
	 * Displacement-wash efficiency (U16.5): each cake-volume of clean wash
	 * water pushed through the cake displaces this fraction of the entrained
	 * mother liquor — remaining liquor scales by (1−ε)^(wash/pore), so ~8 pore
	 * volumes wash to below the unit grid (integer rounding does the honest
	 * cutoff; the machine never judges concentration, only composition).
	 */
	public static final double WASH_DISPLACEMENT = 0.75;

	/**
	 * Useful maximum of displacement washing in pore volumes: 13 volumes at
	 * ε=0.75 scales even a bucket-scale mother liquor to below half a unit
	 * (integer rounding then zeroes it — the honest cutoff). More wash water
	 * than this is not consumed; it cannot make the cake any cleaner.
	 */
	public static final double MAX_WASH_PORE_VOLUMES = 13;

	/** Water's boiling point (°C) — the open-vessel evaporation threshold. */
	private static final int WATER_BOILING_C = 100;

	/** mB of water vented per reaction tick in a boiling open vessel. */
	private static final int EVAPORATION_RATE_MB = 50;

	private RulesEngine() {}

	/** Sealed-vessel solve with no item input (see {@link #apply(ReactorTank, boolean, IItemHandler, double)}). */
	@Nullable
	public static Solution apply(ReactorTank tank) {
		return apply(tank, false, null, 1.0);
	}

	/** @see #apply(ReactorTank, boolean, IItemHandler, double) */
	@Nullable
	public static Solution apply(ReactorTank tank, boolean openVessel, @Nullable IItemHandler items) {
		return apply(tank, openVessel, items, 1.0);
	}

	/**
	 * @param tank the vessel's fluid contents (read + rewritten in place)
	 * @param openVessel true for an open-topped vessel: boiling water vents
	 *        (evaporative concentration); sealed vessels keep their solvent
	 *        (pressure suppresses boiling — the linear U1 pressure model's domain)
	 * @param items the vessel's item inventory: solid items whose species has a
	 *        solubility curve dissolve up to saturation (1 item = a bucket of
	 *        formula units per tick); nullable
	 * @param stirring mass-transfer coefficient (0.3–1.0; scales all kinetic rates)
	 * @return the solved snapshot (speciation report for goggles/diagnostics);
	 *         null only for an empty vessel
	 */
	@Nullable
	public static Solution apply(ReactorTank tank, boolean openVessel, @Nullable IItemHandler items, double stirring) {
		return apply(tank, openVessel, items, stirring, null);
	}

	/**
	 * Full form with a vented-steam accumulator (M08 crystalliser condensate):
	 * {@code ventedWater[0]} receives the units of water this solve vented from
	 * a boiling open vessel — exactly the distillate the condenser port
	 * recovers as its product. Nullable to keep the reactor path allocation-free.
	 */
	@Nullable
	public static Solution apply(ReactorTank tank, boolean openVessel, @Nullable IItemHandler items, double stirring,
			@Nullable long[] ventedWater) {
		// 1. settle phases (gases stay separate, liquids merge per miscibility group)
		tank.collapseIfNeeded();

		// 2. read only the aqueous phase, in SOLVER UNITS (mB × UNIT_PER_MB —
		//    the finer grid on which sub-mB equilibrium residuals live; the mB
		//    view stays the transport/display granularity). Gases and nonpolar
		//    liquids are inert bystanders — ions don't cross phase boundaries
		//    (plans/06 §9.6) — so they are neither fed to the solver nor merged
		//    back into the mixture.
		List<FluidStack> bystanders = new ArrayList<>();
		Map<ResourceLocation, Long> beforeMol = new LinkedHashMap<>();
		Map<String, Long> beforeIons = new LinkedHashMap<>();
		Map<ResourceLocation, Long> beforeSuspended = new LinkedHashMap<>();
		Map<ResourceLocation, Long> beforeSediment = new LinkedHashMap<>();
		int total = 0;
		long weightedTemp = 0;
		for (FluidStack stack : tank.getFluids()) {
			if (Mixture.isMixture(stack)) {
				total += stack.getAmount();
				weightedTemp += (long) Temperature.get(stack) * stack.getAmount();
				for (Map.Entry<ResourceLocation, Integer> e : Mixture.deriveUnitAmounts(stack).entrySet()) {
					beforeMol.merge(e.getKey(), (long) e.getValue(), Long::sum);
				}
				for (Map.Entry<String, Integer> e : Mixture.deriveUnitIonAmounts(stack).entrySet()) {
					beforeIons.merge(e.getKey(), (long) e.getValue(), Long::sum);
				}
				for (Map.Entry<ResourceLocation, Integer> e : Mixture.deriveUnitSuspendedAmounts(stack).entrySet()) {
					beforeSuspended.merge(e.getKey(), (long) e.getValue(), Long::sum);
				}
				for (Map.Entry<ResourceLocation, Integer> e : Mixture.deriveUnitSedimentAmounts(stack).entrySet()) {
					beforeSediment.merge(e.getKey(), (long) e.getValue(), Long::sum);
				}
			} else if (!Miscibility.isGas(stack) && Miscibility.AQUEOUS.equals(Miscibility.groupOf(stack))) {
				// pure aqueous liquid (water) — part of the aqueous phase
				total += stack.getAmount();
				weightedTemp += (long) Temperature.get(stack) * stack.getAmount();
				ResourceLocation id = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
				if (id != null) {
					beforeMol.merge(id, (long) stack.getAmount() * Chemistry.UNIT_PER_MB, Long::sum);
				}
			} else {
				bystanders.add(stack); // gas or nonpolar liquid: inert
			}
		}
		if (total <= 0) {
			return null;
		}
		int temperature = Temperature.fromWeightedSum(weightedTemp, total);

		// 3. solid items dissolve up to saturation (spontaneous — rules-engine
		//    territory per the plans/03 §8.1 engine boundary). Mutates the
		//    before-maps in place, so it reports whether it consumed anything
		//    (the skip-check below compares against those same maps).
		boolean itemDissolved = items != null
			&& dissolveItems(beforeMol, beforeIons, beforeSuspended, beforeSediment, items, temperature);

		// 4. solve (existing solids participate: undersaturated ones redissolve)
		Solution solution = new Solution(beforeMol, beforeIons, beforeSuspended, beforeSediment, temperature)
			.stirring(stirring);
		solution.solve();

		// 5. open-vessel evaporation: boiling water vents, concentrating the
		// solutes (the next pass's crystallisation sees the higher concentration)
		if (openVessel && temperature >= WATER_BOILING_C) {
			long vented = solution.evaporateWater((long) EVAPORATION_RATE_MB * Chemistry.UNIT_PER_MB);
			if (ventedWater != null) {
				ventedWater[0] += vented;
			}
		}

		// 6. skip the rewrite if nothing happened (avoids sync churn on inert contents)
		if (!itemDissolved && solution.energyJ() == 0
			&& same(beforeMol, solution.molecular()) && same(beforeIons, solution.ions())
			&& same(beforeSuspended, solution.suspended()) && same(beforeSediment, solution.sediment())) {
			return solution;
		}

		// 7. rewrite the aqueous phase (temperature + exotherm). The output maps
		//    are the FINAL domains — replace, don't merge (dissolution must be able
		//    to shrink them).
		Map<ResourceLocation, Integer> molAfter = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Long> e : solution.molecular().entrySet()) {
			molAfter.put(e.getKey(), (int) Math.min(e.getValue(), Integer.MAX_VALUE));
		}
		Map<String, Integer> ionAfter = new LinkedHashMap<>();
		for (Map.Entry<String, Long> e : solution.ions().entrySet()) {
			ionAfter.put(e.getKey(), (int) Math.min(e.getValue(), Integer.MAX_VALUE));
		}
		Map<ResourceLocation, Integer> suspAfter = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Long> e : solution.suspended().entrySet()) {
			suspAfter.put(e.getKey(), (int) Math.min(e.getValue(), Integer.MAX_VALUE));
		}
		Map<ResourceLocation, Integer> sedAfter = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Long> e : solution.sediment().entrySet()) {
			sedAfter.put(e.getKey(), (int) Math.min(e.getValue(), Integer.MAX_VALUE));
		}
		// U16 energy ledger: the solve's ΔT is mass-coupled (ΔT = Q/(feedUnits·c)) —
		// negative energyJ (evaporative latent cooling) cools the body here too
		tank.setContents(molAfter, ionAfter, suspAfter, sedAfter,
			clamp((int) Math.round(temperature + solution.heatRiseC())),
			Chemistry.UNIT_PER_MB);
		// 8. re-append the inert phases untouched (gases / nonpolar liquids)
		for (FluidStack s : bystanders) {
			tank.fill(s.copy(), FluidAction.EXECUTE);
		}
		return solution;
	}

	/**
	 * Dissolve solid items into the aqueous phase up to saturation: a species
	 * whose solubility curve is defined may consume matching items at 1 item =
	 * one bucket of formula units per tick, but only while the solution stays
	 * at/below its curve threshold afterwards (a saturated solution stops
	 * dissolving — drop-in rock salt in water yields exactly saturated brine).
	 * Grains dissolve at their finer 62.5 mB denomination.
	 *
	 * <p>Three U15 extensions:
	 * <ul>
	 *   <li><b>seeding</b> — a solid item dropped into a solution that is
	 *       already supersaturated w.r.t. it cannot dissolve, so it joins the
	 *       {@code Sediment} domain instead (one grain preferred, else the
	 *       whole item). The kinetics then grow crystals on it at the seeded
	 *       rate — one grain collapses a metastable solution (U15②).</li>
	 *   <li><b>mixed residue</b> — a {@link MixedResidueItem} dissolves whole,
	 *       expanding its NBT composition exactly into ions (curve species,
	 *       saturation-checked per species) or into {@code Suspended} (mineral
	 *       species, which the equilibria then take to Ksp). Dissolving it IS
	 *       the assay (plans/03 §12).</li>
	 * </ul>
	 * All amounts in solver units.
	 *
	 * @return true when at least one item was consumed (the caller must then
	 *         rewrite the tank even though the before/after maps look equal)
	 */
	private static boolean dissolveItems(Map<ResourceLocation, Long> mol, Map<String, Long> ions,
		Map<ResourceLocation, Long> suspended, Map<ResourceLocation, Long> sediment, IItemHandler items, int temperature) {
		long water = mol.getOrDefault(Solution.WATER, 0L);
		if (water <= 0) {
			return false; // no solvent: nothing dissolves
		}
		boolean consumed = dissolveResidue(mol, ions, suspended, items, water, temperature);
		for (Species s : SpeciesManager.all()) {
			if (!s.isCrystallisable() || !s.isElectrolyte()) {
				continue;
			}
			Item item = itemFor(s.solute());
			Item grain = grainFor(s.solute());
			if (item == null && grain == null) {
				continue;
			}
			double threshold = Solution.solubilityThreshold(s, temperature);
			long cap = (long) Math.floor(threshold * water);
			long form = Solution.formableUnits(ions, s);
			if (form > cap) {
				// supersaturated: dissolving is impossible, so the item seeds —
				// it becomes settled crystal mass the kinetics grows on
				Item seed = grain != null ? takeOne(items, grain) : null;
				if (seed == null && item != null) {
					seed = takeOne(items, item);
				}
				if (seed != null) {
					long units = seed == grain ? GRAIN_UNITS : ITEM_UNITS;
					sediment.merge(s.solute(), units, Long::sum);
					consumed = true;
				}
				continue;
			}
			long headroom = cap - form;
			if (headroom >= ITEM_UNITS && item != null && takeOne(items, item) != null) {
				for (Species.IonComponent c : s.ions()) {
					ions.merge(c.ion().id(), ITEM_UNITS * c.count(), Long::sum);
				}
				consumed = true; // one item per species per tick (gradual, visible)
			} else if (headroom >= GRAIN_UNITS && grain != null && takeOne(items, grain) != null) {
				for (Species.IonComponent c : s.ions()) {
					ions.merge(c.ion().id(), GRAIN_UNITS * c.count(), Long::sum);
				}
				consumed = true;
			}
		}
		return consumed;
	}

	/**
	 * Dissolve one mixed-residue item: expand its NBT ratio composition into a
	 * full 1000 mB of solids. Curve species go to the ion domain — but only if
	 * every one of them still fits under its saturation cap afterwards (a
	 * residue dropped into an already-saturated brine correctly refuses to
	 * dissolve); mineral species go to {@code Suspended} where the equilibria
	 * dissolve them to Ksp. Whole-item only: partial dissolution would need a
	 * fractional item.
	 */
	/**
	 * Dissolve one mixed-residue (wet-cake) item: expand its NBT ratio
	 * composition — solids <b>and</b> entrained mother liquor (U16.5) — into a
	 * full 1000 mB share, distributed proportionally over all parts. Curve
	 * species go to the ion domain — but only if every one of them (from
	 * solids and liquor alike) still fits under its saturation cap afterwards
	 * (a residue dropped into an already-saturated brine correctly refuses to
	 * dissolve); mineral species go to {@code Suspended} where the equilibria
	 * dissolve them to Ksp; liquor water joins the solvent and liquor solutes
	 * re-enter the molecular domain. Whole-item only: partial dissolution
	 * would need a fractional item.
	 */
	private static boolean dissolveResidue(Map<ResourceLocation, Long> mol, Map<String, Long> ions,
		Map<ResourceLocation, Long> suspended, IItemHandler items, long water, int temperature) {
		for (int slot = 0; slot < items.getSlots(); slot++) {
			ItemStack stack = items.getStackInSlot(slot);
			if (!(stack.getItem() instanceof MixedResidueItem)) {
				continue;
			}
			Map<ResourceLocation, Integer> parts = MixedResidueItem.parts(stack);
			Map<String, Integer> liquor = MixedResidueItem.liquorParts(stack);
			long totalParts = 0;
			for (int v : parts.values()) {
				totalParts += v;
			}
			for (int v : liquor.values()) {
				totalParts += v;
			}
			if (totalParts <= 0) {
				continue;
			}
			// per-entry unit shares summing exactly to ITEM_UNITS, across solids
			// AND liquor (the water in a wet cake rejoins the solvent)
			Map<ResourceLocation, Long> shares = new LinkedHashMap<>();
			Map<String, Long> liquorShares = new LinkedHashMap<>();
			long assigned = 0;
			for (Map.Entry<ResourceLocation, Integer> e : parts.entrySet()) {
				long share = ITEM_UNITS * e.getValue() / totalParts;
				shares.put(e.getKey(), share);
				assigned += share;
			}
			for (Map.Entry<String, Integer> e : liquor.entrySet()) {
				long share = ITEM_UNITS * e.getValue() / totalParts;
				liquorShares.put(e.getKey(), share);
				assigned += share;
			}
			// hand the rounding remainder to the largest entry overall
			String largestLiquor = null;
			for (Map.Entry<String, Integer> e : liquor.entrySet()) {
				if (largestLiquor == null || e.getValue() > liquor.get(largestLiquor)) {
					largestLiquor = e.getKey();
				}
			}
			ResourceLocation largestSolid = null;
			for (Map.Entry<ResourceLocation, Integer> e : parts.entrySet()) {
				if (largestSolid == null || e.getValue() > parts.get(largestSolid)) {
					largestSolid = e.getKey();
				}
			}
			long remainder = ITEM_UNITS - assigned;
			if (largestLiquor != null
				&& (largestSolid == null || liquor.get(largestLiquor) >= parts.get(largestSolid))) {
				liquorShares.merge(largestLiquor, remainder, Long::sum);
			} else if (largestSolid != null) {
				shares.merge(largestSolid, remainder, Long::sum);
			}

			// saturation feasibility: every curve species must fit after the
			// addition — the liquor's direct ions count against the same caps
			Map<String, Long> liquorIons = new LinkedHashMap<>();
			for (Map.Entry<String, Long> e : liquorShares.entrySet()) {
				if (!e.getKey().equals(MixedResidueItem.LIQUOR_WATER)
					&& !e.getKey().startsWith(MixedResidueItem.LIQUOR_SOLUTE_PREFIX)) {
					liquorIons.put(e.getKey(), e.getValue());
				}
			}
			boolean feasible = true;
			for (Map.Entry<ResourceLocation, Long> e : shares.entrySet()) {
				Species sp = SpeciesManager.bySolute(e.getKey());
				if (sp == null || !sp.isElectrolyte()) {
					continue; // mineral / unknown → suspended, equilibria decide
				}
				long cap = (long) Math.floor(Solution.solubilityThreshold(sp, temperature) * water);
				long incoming = e.getValue();
				for (Species.IonComponent c : sp.ions()) {
					// liquor ions of the same species double-count the solute feed:
					// approximate by their equivalent solute fraction
					Long liquorShare = liquorIons.get(c.ion().id());
					if (liquorShare != null) {
						incoming += liquorShare / Math.max(1, c.count());
					}
				}
				if (Solution.formableUnits(ions, sp) + incoming > cap) {
					feasible = false;
					break;
				}
			}
			if (!feasible) {
				continue;
			}
			items.extractItem(slot, 1, false);
			for (Map.Entry<ResourceLocation, Long> e : shares.entrySet()) {
				Species sp = SpeciesManager.bySolute(e.getKey());
				if (sp != null && sp.isElectrolyte()) {
					for (Species.IonComponent c : sp.ions()) {
						ions.merge(c.ion().id(), e.getValue() * c.count(), Long::sum);
					}
				} else {
					suspended.merge(e.getKey(), e.getValue(), Long::sum); // equilibria take it from here
				}
			}
			for (Map.Entry<String, Long> e : liquorShares.entrySet()) {
				if (e.getKey().equals(MixedResidueItem.LIQUOR_WATER)) {
					mol.merge(Solution.WATER, e.getValue(), Long::sum);
				} else if (e.getKey().startsWith(MixedResidueItem.LIQUOR_SOLUTE_PREFIX)) {
					ResourceLocation solute = ResourceLocation.tryParse(
						e.getKey().substring(MixedResidueItem.LIQUOR_SOLUTE_PREFIX.length()));
					if (solute != null) {
						mol.merge(solute, e.getValue(), Long::sum);
					}
				} else {
					ions.merge(e.getKey(), e.getValue(), Long::sum);
				}
			}
			return true; // one residue item per solve (gradual, like every item)
		}
		return false;
	}

	@Nullable
	private static Item itemFor(ResourceLocation solute) {
		Item item = ForgeRegistries.ITEMS.getValue(solute);
		return item == Items.AIR ? null : item;
	}

	/** The grain variant item of a solute ({@code <id>_grain}), if registered. */
	@Nullable
	private static Item grainFor(ResourceLocation solute) {
		Item grain = ForgeRegistries.ITEMS.getValue(
			new ResourceLocation(solute.getNamespace(), solute.getPath() + "_grain"));
		return grain == Items.AIR ? null : grain;
	}

	/** Extract one item from the first slot holding it; null (nothing consumed) when absent. */
	@Nullable
	private static Item takeOne(IItemHandler items, Item item) {
		for (int slot = 0; slot < items.getSlots(); slot++) {
			if (items.getStackInSlot(slot).getItem() == item && items.getStackInSlot(slot).getCount() > 0) {
				items.extractItem(slot, 1, false);
				return item;
			}
		}
		return null;
	}

	private static boolean same(Map<?, Long> a, Map<?, Long> b) {
		if (a.size() != b.size()) {
			return false;
		}
		for (Map.Entry<?, Long> e : a.entrySet()) {
			if (!b.getOrDefault(e.getKey(), 0L).equals(e.getValue())) {
				return false;
			}
		}
		return true;
	}

	private static int clamp(int t) {
		return Math.max(Temperature.AMBIENT, Math.min(2000, t));
	}
}
