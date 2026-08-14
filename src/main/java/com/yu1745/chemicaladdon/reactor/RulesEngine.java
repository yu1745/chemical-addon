package com.yu1745.chemicaladdon.reactor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yu1745.chemicaladdon.composition.Solution;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.fluid.Miscibility;
import com.yu1745.chemicaladdon.fluid.Temperature;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Emergent chemistry rules engine (plans/03 §8). Runs on a vessel's contents each
 * reaction tick, <b>before</b> the whitelist recipe engine: it derives what
 * happens from the mixture's ions + molecules directly — double displacement,
 * precipitation, neutralisation and crystallisation — with no dissociation /
 * recombination round-trip.
 *
 * <p>One pass: settle the tank to a single mixture (which expands any solution
 * species into its ions + water), read the ion/molecule/suspended/sediment
 * domains, solve, then write the final domains back. Precipitated solids are
 * <b>deposited into the mixture's {@code Suspended} domain</b> (a turbid slurry);
 * crystallised solids go to the {@code Sediment} domain (they sink to the bottom).
 * A downstream filter press separates the slurry into items. If nothing changed
 * the tank is left untouched.
 */
public final class RulesEngine {

	/** mB of a solid per item when a filter press separates the slurry (1 item = 1 "bucket"). */
	public static final int MB_PER_ITEM = 1000;

	private RulesEngine() {}

	/**
	 * @param tank the vessel's fluid contents (read + rewritten in place)
	 */
	public static void apply(ReactorTank tank) {
		// 1. settle phases (gases stay separate, liquids merge per miscibility group)
		tank.collapseIfNeeded();

		// 2. read only the aqueous phase. Gases and nonpolar liquids are inert
		//    bystanders — ions don't cross phase boundaries (plans/06 §9.6) — so they
		//    are neither fed to the solver nor merged back into the mixture.
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
				for (Map.Entry<ResourceLocation, Integer> e : Mixture.deriveAmounts(stack).entrySet()) {
					beforeMol.merge(e.getKey(), (long) e.getValue(), Long::sum);
				}
				for (Map.Entry<String, Integer> e : Mixture.deriveIonAmounts(stack).entrySet()) {
					beforeIons.merge(e.getKey(), (long) e.getValue(), Long::sum);
				}
				for (Map.Entry<ResourceLocation, Integer> e : Mixture.deriveSuspendedAmounts(stack).entrySet()) {
					beforeSuspended.merge(e.getKey(), (long) e.getValue(), Long::sum);
				}
				for (Map.Entry<ResourceLocation, Integer> e : Mixture.deriveSedimentAmounts(stack).entrySet()) {
					beforeSediment.merge(e.getKey(), (long) e.getValue(), Long::sum);
				}
			} else if (!Miscibility.isGas(stack) && Miscibility.AQUEOUS.equals(Miscibility.groupOf(stack))) {
				// pure aqueous liquid (water) — part of the aqueous phase
				total += stack.getAmount();
				weightedTemp += (long) Temperature.get(stack) * stack.getAmount();
				ResourceLocation id = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
				if (id != null) {
					beforeMol.merge(id, (long) stack.getAmount(), Long::sum);
				}
			} else {
				bystanders.add(stack); // gas or nonpolar liquid: inert
			}
		}
		if (total <= 0) {
			return;
		}
		int temperature = Temperature.fromWeightedSum(weightedTemp, total);

		// 3. solve
		Solution solution = new Solution(beforeMol, beforeIons, temperature);
		solution.solve();

		// 4. skip the rewrite if nothing happened (avoids sync churn on inert contents)
		if (solution.suspended().isEmpty() && solution.sediment().isEmpty() && solution.heat() == 0
			&& sameMolecules(beforeMol, solution.molecular())
			&& sameIons(beforeIons, solution.ions())) {
			return;
		}

		// 5. rewrite the aqueous phase (temperature + exotherm); precipitated solids
		//    join the existing suspended domain (they stay in the vessel as a slurry),
		//    crystallised solids join the sediment domain (they sink to the bottom)
		Map<ResourceLocation, Integer> molAfter = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Long> e : solution.molecular().entrySet()) {
			molAfter.put(e.getKey(), (int) Math.min(e.getValue(), Integer.MAX_VALUE));
		}
		Map<String, Integer> ionAfter = new LinkedHashMap<>();
		for (Map.Entry<String, Long> e : solution.ions().entrySet()) {
			ionAfter.put(e.getKey(), (int) Math.min(e.getValue(), Integer.MAX_VALUE));
		}
		Map<ResourceLocation, Integer> suspAfter = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Long> e : beforeSuspended.entrySet()) {
			suspAfter.put(e.getKey(), (int) Math.min(e.getValue(), Integer.MAX_VALUE));
		}
		for (Map.Entry<ResourceLocation, Long> e : solution.suspended().entrySet()) {
			suspAfter.merge(e.getKey(), (int) Math.min(e.getValue(), Integer.MAX_VALUE), Integer::sum);
		}
		Map<ResourceLocation, Integer> sedAfter = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Long> e : beforeSediment.entrySet()) {
			sedAfter.put(e.getKey(), (int) Math.min(e.getValue(), Integer.MAX_VALUE));
		}
		for (Map.Entry<ResourceLocation, Long> e : solution.sediment().entrySet()) {
			sedAfter.merge(e.getKey(), (int) Math.min(e.getValue(), Integer.MAX_VALUE), Integer::sum);
		}
		tank.setContents(molAfter, ionAfter, suspAfter, sedAfter, clamp(temperature + solution.heat()));
		// 6. re-append the inert phases untouched (gases / nonpolar liquids)
		for (FluidStack s : bystanders) {
			tank.fill(s.copy(), FluidAction.EXECUTE);
		}
	}

	private static boolean sameMolecules(Map<ResourceLocation, Long> a, Map<ResourceLocation, Long> b) {
		if (a.size() != b.size()) {
			return false;
		}
		for (Map.Entry<ResourceLocation, Long> e : a.entrySet()) {
			if (!b.getOrDefault(e.getKey(), 0L).equals(e.getValue())) {
				return false;
			}
		}
		return true;
	}

	private static boolean sameIons(Map<String, Long> a, Map<String, Long> b) {
		if (a.size() != b.size()) {
			return false;
		}
		for (Map.Entry<String, Long> e : a.entrySet()) {
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
