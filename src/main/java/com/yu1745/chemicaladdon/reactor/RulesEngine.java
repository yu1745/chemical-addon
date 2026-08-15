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

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
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
	private static final long ITEM_UNITS = (long) MB_PER_ITEM * Chemistry.UNIT_PER_MB;

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
		boolean itemDissolved = items != null && dissolveItems(beforeMol, beforeIons, items, temperature);

		// 4. solve (existing solids participate: undersaturated ones redissolve)
		Solution solution = new Solution(beforeMol, beforeIons, beforeSuspended, beforeSediment, temperature)
			.stirring(stirring);
		solution.solve();

		// 5. open-vessel evaporation: boiling water vents, concentrating the
		//    solutes (the next pass's crystallisation sees the higher concentration)
		if (openVessel && temperature >= WATER_BOILING_C) {
			solution.evaporateWater((long) EVAPORATION_RATE_MB * Chemistry.UNIT_PER_MB);
		}

		// 6. skip the rewrite if nothing happened (avoids sync churn on inert contents)
		if (!itemDissolved && solution.heat() == 0
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
		tank.setContents(molAfter, ionAfter, suspAfter, sedAfter, clamp(temperature + solution.heat()),
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
	 * All amounts in solver units.
	 *
	 * @return true when at least one item was consumed (the caller must then
	 *         rewrite the tank even though the before/after maps look equal)
	 */
	private static boolean dissolveItems(Map<ResourceLocation, Long> mol, Map<String, Long> ions,
		IItemHandler items, int temperature) {
		long water = mol.getOrDefault(Solution.WATER, 0L);
		if (water <= 0) {
			return false; // no solvent: nothing dissolves
		}
		boolean consumed = false;
		for (Species s : SpeciesManager.all()) {
			if (!s.isCrystallisable() || !s.isElectrolyte()) {
				continue;
			}
			Item item = ForgeRegistries.ITEMS.getValue(s.solute());
			if (item == null || item == Items.AIR) {
				continue;
			}
			double threshold = Solution.solubilityThreshold(s, temperature);
			long cap = (long) Math.floor(threshold * water);
			long headroom = cap - Solution.formableUnits(ions, s);
			if (headroom < ITEM_UNITS) {
				continue; // at/near saturation (or no matching item below)
			}
			for (int slot = 0; slot < items.getSlots(); slot++) {
				if (items.getStackInSlot(slot).getItem() == item && items.getStackInSlot(slot).getCount() > 0) {
					items.extractItem(slot, 1, false);
					for (Species.IonComponent c : s.ions()) {
						ions.merge(c.ion().id(), ITEM_UNITS * c.count(), Long::sum);
					}
					consumed = true;
					break; // one item per species per tick (gradual, visible)
				}
			}
		}
		return consumed;
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
