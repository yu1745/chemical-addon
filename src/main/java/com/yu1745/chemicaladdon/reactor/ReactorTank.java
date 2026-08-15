package com.yu1745.chemicaladdon.reactor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.simibubi.create.foundation.fluid.FluidIngredient;
import com.yu1745.chemicaladdon.composition.Chemistry;
import com.yu1745.chemicaladdon.composition.Solution;
import com.yu1745.chemicaladdon.composition.Species;
import com.yu1745.chemicaladdon.composition.SpeciesManager;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.fluid.Miscibility;
import com.yu1745.chemicaladdon.fluid.Temperature;
import com.yu1745.chemicaladdon.item.MixedResidueItem;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Multi-fluid tank inside a reaction vessel (smeltery-style: arbitrary fluids
 * coexist, one entry each, sharing one total capacity). Implements Forge
 * {@link IFluidHandler} so Create pipes/pumps can connect directly.
 *
 * <p>Mixture-aware: when 2+ distinct species coexist they {@linkplain #collapseIfNeeded()
 * collapse} into a single {@link Mixture} stack whose composition is stored as a
 * GCD-reduced ratio in the FluidStack NBT (see {@link Mixture}). Reaction
 * matching and draining operate on the derived per-component amounts so a recipe
 * can "see" and consume a species dissolved in the mix.
 *
 * <p>The ratio-in-tag representation is what makes a mixture pumpable: a
 * proportional drain copies the ratio tag verbatim and only shrinks the amount,
 * so Create's {@code FluidStack.isFluidEqual} (fluid + NBT) sees every sample of
 * the same mixture as identical and keeps the flow alive. A 1 mB probe, a
 * full-bucket transfer and the tank's own contents all carry one and the same
 * tag — which is the property Create's probe-then-transfer protocol needs.
 */
public class ReactorTank implements IFluidHandler {

	private int capacity;
	private final Runnable onChanged;
	private final List<FluidStack> fluids = new ArrayList<>();

	public ReactorTank(int capacity, Runnable onChanged) {
		this.capacity = capacity;
		this.onChanged = onChanged;
	}

	/** Fluids are stored as their source instance so recipe matching (Create's
	 * FluidIngredient fixes to the source) works regardless of what instance
	 * the pipes/callers carry. */
	private static Fluid sourceOf(FluidStack stack) {
		return stack.getFluid() instanceof FlowingFluid flowing ? flowing.getSource() : stack.getFluid();
	}

	private static Fluid sourceOf(Fluid fluid) {
		return fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid;
	}

	public void setCapacity(int capacity) {
		this.capacity = capacity;
	}

	public List<FluidStack> getFluids() {
		return fluids;
	}

	public int getTotalAmount() {
		int total = 0;
		for (FluidStack f : fluids) {
			total += f.getAmount();
		}
		return total;
	}

	@Override
	public int getTanks() {
		return fluids.size();
	}

	@Override
	public FluidStack getFluidInTank(int tank) {
		return fluids.get(tank);
	}

	@Override
	public int getTankCapacity(int tank) {
		return capacity;
	}

	@Override
	public boolean isFluidValid(int tank, FluidStack stack) {
		return true;
	}

	@Override
	public int fill(FluidStack resource, FluidAction action) {
		if (resource.isEmpty()) {
			return 0;
		}
		int amount = Math.min(resource.getAmount(), capacity - getTotalAmount());
		if (amount <= 0) {
			return 0;
		}
		if (action.execute()) {
			Fluid fluid = sourceOf(resource);
			boolean incomingMix = Mixture.isMixture(resource);
			for (FluidStack f : fluids) {
				// same mixture composition (identical ratio tag): stack it
				if (incomingMix && Mixture.isMixture(f) && f.isFluidEqual(resource)) {
					f.grow(amount);
					onChanged.run();
					return amount;
				}
				// same pure fluid: stack it and blend the temperature (amount-weighted)
				if (!incomingMix && !Mixture.isMixture(f) && f.getFluid() == fluid) {
					int blended = Temperature.merge(Temperature.get(f), f.getAmount(),
						Temperature.get(resource), amount);
					f.grow(amount);
					Temperature.set(f, blended);
					onChanged.run();
					return amount;
				}
			}
			// new entry — preserve the resource's NBT so a mixture's ratio travels with it
			FluidStack copy = resource.copy();
			copy.setAmount(amount);
			fluids.add(copy);
			onChanged.run();
		}
		return amount;
	}

	@Override
	public FluidStack drain(FluidStack resource, FluidAction action) {
		if (resource.isEmpty()) {
			return FluidStack.EMPTY;
		}
		Fluid target = sourceOf(resource);
		// pulling the mixture itself: proportional sample (ratio tag copied verbatim).
		// Create always builds the resource from getFluidInTank, so its ratio tag matches
		// ours; a plain pump thus keeps the mixture's identity intact through transport.
		if (Mixture.isMixture(target)) {
			for (FluidStack f : fluids) {
				if (Mixture.isMixture(f)) {
					FluidStack out = drainMixtureAmount(f, resource.getAmount(), action);
					if (action.execute()) {
						removeEmpty();
						onChanged.run();
					}
					return out;
				}
			}
			return FluidStack.EMPTY;
		}
		// pulling a pure species: only from pure stacks (you can't separate a
		// dissolved component out of a mixture with a plain pump — that needs
		// distillation / the reaction engine). Use drain(int) to pull the mix.
		for (FluidStack f : fluids) {
			if (Mixture.isMixture(f)) {
				continue;
			}
			if (f.getFluid() == target) {
				int amount = Math.min(f.getAmount(), resource.getAmount());
				if (amount <= 0) {
					return FluidStack.EMPTY;
				}
				// copy the live stack BEFORE shrinking (a fully-drained stack copies to
				// EMPTY and setAmount would throw) — its NBT (frozen temperature) travels
				// with the sample; a stripped tag would break Create's isFluidEqual
				// against getFluidInTank and stall the pump
				FluidStack out = f.copy();
				out.setAmount(amount);
				if (action.execute()) {
					f.shrink(amount);
					removeEmpty();
					onChanged.run();
				}
				return out;
			}
		}
		return FluidStack.EMPTY;
	}

	@Override
	public FluidStack drain(int maxDrain, FluidAction action) {
		return drainSorted(maxDrain, action, false);
	}

	/** D18.5: drain the LIGHTEST phase first (top port / decantation) — the reverse of {@link #drain(int, FluidAction)}. */
	public FluidStack drainLightest(int maxDrain, FluidAction action) {
		return drainSorted(maxDrain, action, true);
	}

	/**
	 * Drain one phase, ordered by density. {@code lightest=false} takes the densest
	 * phase first (bottom port: heavy sinks); {@code lightest=true} takes the
	 * lightest first (top port: decant the floating layer). Gases (negative density)
	 * are lightest, so they drain last from the bottom and first from the top.
	 */
	private FluidStack drainSorted(int maxDrain, FluidAction action, boolean lightest) {
		if (fluids.isEmpty() || maxDrain <= 0) {
			return FluidStack.EMPTY;
		}
		List<FluidStack> order = new ArrayList<>(fluids);
		if (lightest) {
			order.sort((a, b) -> Integer.compare(Miscibility.densityOf(a), Miscibility.densityOf(b)));
		} else {
			order.sort((a, b) -> Integer.compare(Miscibility.densityOf(b), Miscibility.densityOf(a)));
		}
		FluidStack first = order.get(0);
		int amount = Math.min(first.getAmount(), maxDrain);
		if (amount <= 0) {
			return FluidStack.EMPTY;
		}
		FluidStack out;
		if (Mixture.isMixture(first)) {
			out = drainMixtureAmount(first, amount, action);
		} else {
			out = first.copy();
			out.setAmount(amount);
			if (action.execute()) {
				first.shrink(amount);
			}
		}
		if (action.execute()) {
			removeEmpty();
			onChanged.run();
		}
		return out;
	}

	/**
	 * An {@link IFluidHandler} view that drains the lightest phase first — the
	 * vessel's "top port" for decantation (roof brick). Everything else delegates to
	 * this tank unchanged; filling still re-separates on the next settle.
	 */
	public IFluidHandler lightPhase() {
		return new IFluidHandler() {
			@Override
			public int getTanks() {
				return ReactorTank.this.getTanks();
			}

			@Override
			public FluidStack getFluidInTank(int tank) {
				return ReactorTank.this.getFluidInTank(tank);
			}

			@Override
			public int getTankCapacity(int tank) {
				return ReactorTank.this.getTankCapacity(tank);
			}

			@Override
			public boolean isFluidValid(int tank, FluidStack stack) {
				return ReactorTank.this.isFluidValid(tank, stack);
			}

			@Override
			public int fill(FluidStack resource, FluidAction action) {
				return ReactorTank.this.fill(resource, action);
			}

			@Override
			public FluidStack drain(FluidStack resource, FluidAction action) {
				return ReactorTank.this.drain(resource, action);
			}

			@Override
			public FluidStack drain(int maxDrain, FluidAction action) {
				return ReactorTank.this.drainLightest(maxDrain, action);
			}
		};
	}

	/**
	 * Proportional drain of up to {@code amount} mB from a mixture stack: the
	 * sample carries the source's ratio tag unchanged, and the source simply
	 * loses that much total. Per-component amounts are derived on demand
	 * elsewhere, so there is no integer splitting to do here — the ratio never
	 * moves, which is exactly what keeps Create's flow alive across samples of
	 * different sizes.
	 */
	private FluidStack drainMixtureAmount(FluidStack mixture, int amount, FluidAction action) {
		amount = Math.min(amount, mixture.getAmount());
		if (amount <= 0) {
			return FluidStack.EMPTY;
		}
		FluidStack out = mixture.copy(); // ratio + colour NBT copied verbatim
		out.setAmount(amount);
		if (action.execute()) {
			mixture.shrink(amount);
		}
		return out;
	}

	// ----------------------------------------------------------- mixture support

	/**
	 * Settle the tank's contents into a single canonical stack.
	 *
	 * <p>A single settled entry is left untouched: a valid multi-component
	 * mixture's ratio tag is its transport identity (proportional drains preserve
	 * it), and re-deriving it every tick would rewrite the tag whenever the amount
	 * isn't divisible by the ratio sum — churning the tag and defeating Create's
	 * {@code isFluidEqual} matching. So this only acts when there is real work:
	 * <ul>
	 *   <li>2+ entries -> merge them into one mixture (or one pure fluid);</li>
	 *   <li>a single mixture drained down to one component -> degrade to pure;</li>
	 *   <li>a component-less (corrupt) mixture -> drop.</li>
	 * </ul>
	 * Called by the controller each tick.
	 */
	public void collapseIfNeeded() {
		if (fluids.size() <= 1) {
			if (fluids.size() == 1) {
				FluidStack only = fluids.get(0);
				if (Mixture.isMixture(only)) {
					int comps = Mixture.getMolecules(only).size() + Mixture.getIons(only).size()
						+ Mixture.getSuspended(only).size() + Mixture.getSediment(only).size();
					if (comps == 0) {
						fluids.clear(); // corrupt component-less mixture
						onChanged.run();
					} else if (Mixture.getIons(only).isEmpty() && Mixture.getSuspended(only).isEmpty()
						&& Mixture.getSediment(only).isEmpty() && Mixture.getMolecules(only).size() == 1) {
						degradeSingleComponentMixture(only);
					}
					// otherwise: settled — leave the ratio tag alone
				}
				// a lone pure fluid is left as-is
			}
			return;
		}

		// D18: settle by phase — gases stay separate phases; liquids merge only
		// within their own miscibility group (cross-group liquids are immiscible).
		List<FluidStack> gases = new ArrayList<>();
		Map<String, List<FluidStack>> liquidGroups = new LinkedHashMap<>();
		for (FluidStack stack : fluids) {
			if (Miscibility.isGas(stack)) {
				gases.add(stack);
			} else {
				liquidGroups.computeIfAbsent(Miscibility.groupOf(stack), k -> new ArrayList<>()).add(stack);
			}
		}

		List<FluidStack> settled = new ArrayList<>();
		for (List<FluidStack> members : liquidGroups.values()) {
			FluidStack merged;
			if (members.size() == 1) {
				// Already settled: keep the stack verbatim. Rebuilding a mixture here
				// (derive amounts -> Mixture.create GCD-reduce) would churn its ratio tag
				// whenever the total isn't divisible by the ratio sum, breaking Create's
				// isFluidEqual flow identity and stalling the pump.
				merged = members.get(0);
			} else {
				merged = mergeGroup(members);
			}
			if (!merged.isEmpty()) {
				settled.add(merged);
			}
		}
		// densest liquid first (it sinks); gases are the lightest phase, kept last
		// (the renderer hangs them from the top)
		settled.sort((a, b) -> Integer.compare(Miscibility.densityOf(b), Miscibility.densityOf(a)));
		settled.addAll(gases);

		// Only rewrite + notify when something actually changed — a settled multi-phase
		// tank would otherwise mark itself dirty and re-sync every tick.
		if (sameContents(settled, fluids)) {
			return;
		}
		fluids.clear();
		fluids.addAll(settled);
		onChanged.run();
	}

	/** True when two phase lists carry the same fluid + amount in the same order. */
	private static boolean sameContents(List<FluidStack> a, List<FluidStack> b) {
		if (a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			FluidStack x = a.get(i);
			FluidStack y = b.get(i);
			if (x.getAmount() != y.getAmount() || !x.isFluidEqual(y)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Merge one miscibility group's members into a single canonical stack: a pure
	 * fluid when only one molecular species survives, otherwise a {@link Mixture}.
	 * Returns {@link FluidStack#EMPTY} when every member was corrupt.
	 */
	private FluidStack mergeGroup(List<FluidStack> members) {
		Map<ResourceLocation, Integer> molecules = new LinkedHashMap<>();
		Map<String, Integer> ions = new LinkedHashMap<>();
		Map<ResourceLocation, Integer> suspended = new LinkedHashMap<>();
		Map<ResourceLocation, Integer> sediment = new LinkedHashMap<>();
		long weightedTemp = 0; // Σ (temperature × amount), °C·mB
		int total = 0;
		for (FluidStack stack : members) {
			total += stack.getAmount();
			weightedTemp += (long) Temperature.get(stack) * stack.getAmount();
			if (Mixture.isMixture(stack)) {
				for (Map.Entry<ResourceLocation, Integer> e : Mixture.deriveAmounts(stack).entrySet()) {
					molecules.merge(e.getKey(), e.getValue(), Integer::sum);
				}
				for (Map.Entry<String, Integer> e : Mixture.deriveIonAmounts(stack).entrySet()) {
					ions.merge(e.getKey(), e.getValue(), Integer::sum);
				}
				for (Map.Entry<ResourceLocation, Integer> e : Mixture.deriveSuspendedAmounts(stack).entrySet()) {
					suspended.merge(e.getKey(), e.getValue(), Integer::sum);
				}
				for (Map.Entry<ResourceLocation, Integer> e : Mixture.deriveSedimentAmounts(stack).entrySet()) {
					sediment.merge(e.getKey(), e.getValue(), Integer::sum);
				}
			} else {
				ResourceLocation id = ForgeRegistries.FLUIDS.getKey(sourceOf(stack.getFluid()));
				if (id != null) {
					molecules.merge(id, stack.getAmount(), Integer::sum);
				}
			}
		}
		molecules.values().removeIf(v -> v <= 0);
		ions.values().removeIf(v -> v <= 0);
		suspended.values().removeIf(v -> v <= 0);
		sediment.values().removeIf(v -> v <= 0);

		if (molecules.isEmpty() && ions.isEmpty() && suspended.isEmpty() && sediment.isEmpty()) {
			return FluidStack.EMPTY; // every entry was corrupt
		}
		FluidStack target;
		if (molecules.size() == 1 && ions.isEmpty() && suspended.isEmpty() && sediment.isEmpty()) {
			// a single pure molecular species → a pure fluid
			Map.Entry<ResourceLocation, Integer> only = molecules.entrySet().iterator().next();
			Fluid pf = ForgeRegistries.FLUIDS.getValue(only.getKey());
			if (pf == null || pf == Fluids.EMPTY) {
				return FluidStack.EMPTY;
			}
			target = new FluidStack(pf, total);
		} else {
			target = Mixture.create(molecules, ions, suspended, sediment, total);
		}
		Temperature.set(target, Temperature.fromWeightedSum(weightedTemp, total));
		return target;
	}

	/** Replace a single-component mixture with the equivalent pure fluid stack. */
	private void degradeSingleComponentMixture(FluidStack mixture) {
		Map.Entry<ResourceLocation, Integer> only =
			Mixture.deriveAmounts(mixture).entrySet().iterator().next();
		Fluid pf = ForgeRegistries.FLUIDS.getValue(only.getKey());
		if (pf != null && pf != Fluids.EMPTY) {
			FluidStack pure = new FluidStack(pf, only.getValue());
			Temperature.set(pure, Temperature.get(mixture));
			fluids.clear();
			fluids.add(pure);
			onChanged.run();
		}
	}

	/** Total mB matching the ingredient, looking inside mixture components too. */
	public int countIngredient(FluidIngredient ingredient) {
		int total = 0;
		for (FluidStack stack : fluids) {
			if (Mixture.isMixture(stack)) {
				for (Map.Entry<ResourceLocation, Integer> e : Mixture.deriveAmounts(stack).entrySet()) {
					Fluid cf = ForgeRegistries.FLUIDS.getValue(e.getKey());
					if (cf != null && cf != Fluids.EMPTY && ingredient.test(new FluidStack(cf, e.getValue()))) {
						total += e.getValue();
					}
				}
			} else if (ingredient.test(stack)) {
				total += stack.getAmount();
			}
		}
		return total;
	}

	/**
	 * Drain up to {@code required} mB matching the ingredient, drawing first from
	 * pure stacks then from mixture components. Consuming a component is a real
	 * composition change, so the mixture's ratio tag is re-stamped from the
	 * surviving amounts and the total rebalanced. Returns the amount actually
	 * drained.
	 */
	public int drainIngredient(FluidIngredient ingredient, int required, FluidAction action) {
		if (required <= 0) {
			return 0;
		}
		int remaining = required;
		// pure entries first (prefer not to dissolve a mixture if a pure stack suffices)
		for (FluidStack stack : fluids) {
			if (Mixture.isMixture(stack) || remaining <= 0) {
				continue;
			}
			if (ingredient.test(stack)) {
				int take = Math.min(stack.getAmount(), remaining);
				if (action.execute()) {
					stack.shrink(take);
				}
				remaining -= take;
			}
		}
		// then mixture components
		for (FluidStack stack : fluids) {
			if (!Mixture.isMixture(stack) || remaining <= 0) {
				continue;
			}
			Map<ResourceLocation, Integer> amounts = Mixture.deriveAmounts(stack);
			boolean changed = false;
			for (Map.Entry<ResourceLocation, Integer> e : new ArrayList<>(amounts.entrySet())) {
				Fluid cf = ForgeRegistries.FLUIDS.getValue(e.getKey());
				if (cf == null || cf == Fluids.EMPTY) {
					continue;
				}
				if (ingredient.test(new FluidStack(cf, e.getValue()))) {
					int take = Math.min(e.getValue(), remaining);
					amounts.put(e.getKey(), e.getValue() - take);
					remaining -= take;
					changed = true;
					if (remaining <= 0) {
						break;
					}
				}
			}
			if (changed && action.execute()) {
				amounts.values().removeIf(v -> v <= 0);
				Map<String, Integer> ions = Mixture.deriveIonAmounts(stack);
				Map<ResourceLocation, Integer> susp = Mixture.deriveSuspendedAmounts(stack);
				Map<ResourceLocation, Integer> sed = Mixture.deriveSedimentAmounts(stack);
				int newTotal = 0;
				for (int v : amounts.values()) {
					newTotal += v;
				}
				for (int v : ions.values()) {
					newTotal += v;
				}
				for (int v : susp.values()) {
					newTotal += v;
				}
				for (int v : sed.values()) {
					newTotal += v;
				}
				int idx = fluids.indexOf(stack);
				if (newTotal <= 0 || (amounts.isEmpty() && ions.isEmpty() && susp.isEmpty() && sed.isEmpty())) {
					fluids.set(idx, FluidStack.EMPTY);
				} else {
					// rebuild with ALL domains preserved (ions / suspended / sediment survive
					// a molecular drain); collapseIfNeeded degrades a 1-component remainder
					FluidStack replacement = Mixture.create(amounts, ions, susp, sed, newTotal);
					Temperature.set(replacement, Temperature.get(stack));
					fluids.set(idx, replacement);
				}
			}
		}
		if (action.execute()) {
			removeEmpty();
			onChanged.run();
		}
		return required - remaining;
	}

	/** mB of a solution species' solute ions (mole-equivalents) formable from the vessel's dissolved ions. */
	public int countSolution(ResourceLocation speciesId) {
		Species species = SpeciesManager.get(speciesId);
		if (species == null || !species.isSolution()) {
			return 0;
		}
		int total = 0;
		for (FluidStack stack : fluids) {
			if (Mixture.isMixture(stack)) {
				total += species.equivalentIonMb(Mixture.deriveIonAmounts(stack));
			}
		}
		return total;
	}

	/**
	 * Continuous concentration of a solution species across the vessel's mixture
	 * stacks: solute ion mB / water mB. 0 when there is no water (or no species).
	 */
	public double concentrationOf(ResourceLocation speciesId) {
		Species species = SpeciesManager.get(speciesId);
		if (species == null || !species.isSolution()) {
			return 0;
		}
		int ionMb = 0;
		int waterMb = 0;
		for (FluidStack stack : fluids) {
			if (Mixture.isMixture(stack)) {
				ionMb += species.equivalentIonMb(Mixture.deriveIonAmounts(stack));
				waterMb += Mixture.deriveAmounts(stack).getOrDefault(Solution.WATER, 0);
			}
		}
		return waterMb > 0 ? (double) ionMb / waterMb : 0.0;
	}

	/**
	 * Drain up to {@code required} mB of a solution species' solute ions (whole
	 * formula units, so the ion multiset stays charge-neutral). Water is the
	 * solvent and is <b>not</b> consumed — a reaction eats the solute, not the
	 * water it is dissolved in.
	 */
	public int drainSolution(ResourceLocation speciesId, int required, FluidAction action) {
		Species species = SpeciesManager.get(speciesId);
		if (species == null || !species.isSolution() || required <= 0) {
			return 0;
		}
		int remaining = required;
		int ic = species.ionCount();
		for (FluidStack stack : fluids) {
			if (!Mixture.isMixture(stack) || remaining <= 0) {
				continue;
			}
			Map<String, Integer> ionAmounts = Mixture.deriveIonAmounts(stack);
			long formulaUnits = species.formulaUnits(ionAmounts);
			if (formulaUnits <= 0 || formulaUnits == Long.MAX_VALUE) {
				continue;
			}
			long need = (remaining + ic - 1L) / ic;
			long take = Math.min(formulaUnits, need);
			int takeMb = (int) (take * ic);
			if (action.execute()) {
				Map<ResourceLocation, Integer> mol = Mixture.deriveAmounts(stack);
				Map<ResourceLocation, Integer> susp = Mixture.deriveSuspendedAmounts(stack);
				Map<ResourceLocation, Integer> sed = Mixture.deriveSedimentAmounts(stack);
				for (Species.IonComponent c : species.ions()) {
					ionAmounts.merge(c.ion().id(), (int) (-take * c.count()), Integer::sum);
				}
				ionAmounts.values().removeIf(v -> v <= 0);
				mol.values().removeIf(v -> v <= 0);
				susp.values().removeIf(v -> v <= 0);
				sed.values().removeIf(v -> v <= 0);
				int newTotal = stack.getAmount() - takeMb;
				int idx = fluids.indexOf(stack);
				if (newTotal <= 0 || (mol.isEmpty() && ionAmounts.isEmpty() && susp.isEmpty() && sed.isEmpty())) {
					fluids.set(idx, FluidStack.EMPTY);
				} else {
					// preserve suspended + sediment alongside the surviving molecules + ions
					FluidStack replacement = Mixture.create(mol, ionAmounts, susp, sed, newTotal);
					Temperature.set(replacement, Temperature.get(stack));
					fluids.set(idx, replacement);
				}
			}
			remaining -= takeMb;
		}
		if (action.execute()) {
			removeEmpty();
			onChanged.run();
		}
		return required - remaining;
	}

	private void removeEmpty() {
		fluids.removeIf(f -> f.getAmount() <= 0);
	}

	/** Drops stacks that were drained down to zero (public: structure-break paths
	 * shrink stacks in place and need to clean up without a full tank round-trip). */
	public void pruneEmpty() {
		removeEmpty();
	}

	/** Empties the tank entirely (e.g. after contents were spilled into the world). */
	public void clear() {
		fluids.clear();
	}

	/**
	 * Remove all suspended solids from the tank, emitting them as items via
	 * {@code sink} (1 item per {@code mbPerItem} mB, rounded, min 1). The liquid
	 * (molecules + ions) and any settled solids (sediment) stay in the tank.
	 * Returns the total mB of suspended solids removed.
	 */
	/**
	 * Whole-lump extraction of one solid domain (plans/03 §12, U15): the
	 * per-species ledger is <b>not</b> a separation — physically the solids are
	 * one muddled mass, so the extraction may never pick species.
	 * <ul>
	 *   <li><b>strict single-species = pure</b> — a domain holding exactly one
	 *       species with a registered item extracts that many plain items;</li>
	 *   <li><b>any second species = mixed residue</b> — the domain extracts as
	 *       {@link MixedResidueItem} stacks whose NBT carries the GCD-reduced
	 *       composition (equal tags stack; the deterministic engine makes the
	 *       same feed yield the same residue);</li>
	 *   <li>denomination is whole items only ({@link RulesEngine#MB_PER_ITEM}
	 *       per item) — the sub-item remainder <b>stays in the domain</b>, which
	 *       is exactly the heirloom seed: a pot that keeps &lt;1000 mB of
	 *       settled crystal re-seeds every later supersaturation for free.</li>
	 * </ul>
	 *
	 * @param sedimentDomain true for the settled (Sediment) domain, false for
	 *        the suspended (Suspended) slurry domain
	 * @return total units extracted (0 when nothing forms a whole item)
	 */
	public long extractSolids(Consumer<ItemStack> sink, boolean sedimentDomain) {
		// aggregate the domain across the vessel's mixtures (one physical pot)
		Map<ResourceLocation, Long> units = new LinkedHashMap<>();
		for (FluidStack stack : fluids) {
			if (!Mixture.isMixture(stack)) {
				continue;
			}
			Map<ResourceLocation, Integer> domain = sedimentDomain
				? Mixture.deriveUnitSedimentAmounts(stack)
				: Mixture.deriveUnitSuspendedAmounts(stack);
			for (Map.Entry<ResourceLocation, Integer> e : domain.entrySet()) {
				units.merge(e.getKey(), (long) e.getValue(), Long::sum);
			}
		}
		long total = 0;
		for (long v : units.values()) {
			total += v;
		}
		long items = total / RulesEngine.ITEM_UNITS;
		if (items <= 0) {
			return 0; // sub-item remainder only: the heirloom seed stays put
		}
		Map<ResourceLocation, Long> take = new LinkedHashMap<>();
		if (units.size() == 1) {
			Map.Entry<ResourceLocation, Long> only = units.entrySet().iterator().next();
			Item item = ForgeRegistries.ITEMS.getValue(only.getKey());
			if (item == null || item == Items.AIR) {
				return 0; // single species without a registered item is not extractable
			}
			emitStacks(sink, new ItemStack(item), items);
			take.put(only.getKey(), items * RulesEngine.ITEM_UNITS);
		} else {
			emitStacks(sink, MixedResidueItem.of(units), items);
			// proportional shares of the extracted lump, summing exactly
			long extracted = items * RulesEngine.ITEM_UNITS;
			long assigned = 0;
			Map.Entry<ResourceLocation, Long> largest = null;
			for (Map.Entry<ResourceLocation, Long> e : units.entrySet()) {
				long share = extracted * e.getValue() / total;
				take.put(e.getKey(), share);
				assigned += share;
				if (largest == null || e.getValue() > largest.getValue()) {
					largest = e;
				}
			}
			take.merge(largest.getKey(), extracted - assigned, Long::sum);
		}

		// subtract the taken units from each mixture stack (greedy: exact
		// integers), rebuilding the stack list wholesale — setContents replaces
		// the whole tank, so a per-stack write must not run inside the loop
		List<FluidStack> rebuilt = new ArrayList<>();
		boolean changed = false;
		for (FluidStack stack : fluids) {
			if (!Mixture.isMixture(stack)) {
				rebuilt.add(stack);
				continue;
			}
			Map<ResourceLocation, Integer> domain = new LinkedHashMap<>(sedimentDomain
				? Mixture.deriveUnitSedimentAmounts(stack)
				: Mixture.deriveUnitSuspendedAmounts(stack));
			boolean touched = false;
			for (Map.Entry<ResourceLocation, Integer> e : domain.entrySet()) {
				long need = take.getOrDefault(e.getKey(), 0L);
				if (need <= 0) {
					continue;
				}
				long remove = Math.min(need, e.getValue());
				take.merge(e.getKey(), -remove, Long::sum);
				domain.put(e.getKey(), (int) (e.getValue() - remove));
				touched = true;
			}
			if (!touched) {
				rebuilt.add(stack);
				continue;
			}
			Map<ResourceLocation, Integer> susp = sedimentDomain
				? Mixture.deriveUnitSuspendedAmounts(stack) : domain;
			Map<ResourceLocation, Integer> sed = sedimentDomain ? domain : Mixture.deriveUnitSedimentAmounts(stack);
			replaceWith(rebuilt, Mixture.deriveUnitAmounts(stack), Mixture.deriveUnitIonAmounts(stack), susp, sed,
				Temperature.get(stack));
			changed = true;
		}
		if (changed) {
			fluids.clear();
			fluids.addAll(rebuilt);
			removeEmpty();
			collapseIfNeeded(); // degrade a single-component remainder to a pure fluid
			onChanged.run();
		}
		return items * RulesEngine.ITEM_UNITS;
	}

	/** Emit {@code count} copies of {@code proto} to the sink in legal stack sizes. */
	private static void emitStacks(Consumer<ItemStack> sink, ItemStack proto, long count) {
		while (count > 0) {
			int n = (int) Math.min(count, proto.getMaxStackSize());
			ItemStack stack = proto.copy();
			stack.setCount(n);
			sink.accept(stack);
			count -= n;
		}
	}

	/**
	 * Append a mixture rebuilt from unit-domain maps to {@code out} (single pure
	 * molecular species degrades to a plain stack, mirroring
	 * {@link #setContents}); an all-empty result appends nothing.
	 */
	private static void replaceWith(List<FluidStack> out, Map<ResourceLocation, Integer> molecules,
		Map<String, Integer> ions, Map<ResourceLocation, Integer> suspended, Map<ResourceLocation, Integer> sediment,
		int temperature) {
		molecules.values().removeIf(v -> v <= 0);
		ions.values().removeIf(v -> v <= 0);
		suspended.values().removeIf(v -> v <= 0);
		sediment.values().removeIf(v -> v <= 0);
		if (molecules.isEmpty() && ions.isEmpty() && suspended.isEmpty() && sediment.isEmpty()) {
			return;
		}
		if (molecules.size() == 1 && ions.isEmpty() && suspended.isEmpty() && sediment.isEmpty()) {
			Map.Entry<ResourceLocation, Integer> only = molecules.entrySet().iterator().next();
			Fluid pf = ForgeRegistries.FLUIDS.getValue(only.getKey());
			if (pf != null && pf != Fluids.EMPTY) {
				FluidStack pure = new FluidStack(pf, only.getValue() / Chemistry.UNIT_PER_MB);
				Temperature.set(pure, temperature);
				out.add(pure);
				return;
			}
		}
		long totalUnits = 0;
		for (int v : molecules.values()) totalUnits += v;
		for (int v : ions.values()) totalUnits += v;
		for (int v : suspended.values()) totalUnits += v;
		for (int v : sediment.values()) totalUnits += v;
		FluidStack rebuilt = Mixture.create(molecules, ions, suspended, sediment,
			Math.max(1, (int) Math.round((double) totalUnits / Chemistry.UNIT_PER_MB)));
		Temperature.set(rebuilt, temperature);
		out.add(rebuilt);
	}

	/**
	 * Replace the entire contents with the given molecular + ionic amounts
	 * (mole-equivalents). A single pure molecular species becomes a pure stack;
	 * anything else becomes one {@link Mixture}. Used by the rules engine to write
	 * its solved composition back in one canonical step.
	 */
	public void setContents(Map<ResourceLocation, Integer> molecules, Map<String, Integer> ions, int temperature) {
		setContents(molecules, ions, Map.of(), Map.of(), temperature);
	}

	/**
	 * Replace the entire contents with the given molecular + ionic + suspended
	 * amounts (mole-equivalents). A single pure molecular species becomes a pure
	 * stack; anything else becomes one {@link Mixture}. Used by the rules engine to
	 * write its solved composition back in one canonical step.
	 */
	public void setContents(Map<ResourceLocation, Integer> molecules, Map<String, Integer> ions,
		Map<ResourceLocation, Integer> suspended, int temperature) {
		setContents(molecules, ions, suspended, Map.of(), temperature);
	}

	/**
	 * Replace the entire contents with the given molecular + ionic + suspended +
	 * sediment amounts (mole-equivalents). A single pure molecular species becomes
	 * a pure stack; anything else becomes one {@link Mixture}. Used by the rules
	 * engine to write its solved composition back in one canonical step.
	 */
	public void setContents(Map<ResourceLocation, Integer> molecules, Map<String, Integer> ions,
		Map<ResourceLocation, Integer> suspended, Map<ResourceLocation, Integer> sediment, int temperature) {
		setContents(molecules, ions, suspended, sediment, temperature, 1);
	}

	/**
	 * Unit-aware write-back (the rules engine's path): {@code scale = 1} means
	 * the maps are in mB; {@code Chemistry#UNIT_PER_MB} means the solver's
	 * 1/10000 mB grid. The GCD-reduced ratio parts are scale-free either way —
	 * only the stack's mB amount is the scaled-down sum (rounded to the
	 * nearest mB; the <1 sub-unit error is invisible and does not accumulate,
	 * because the next tick re-derives from the ratio tag).
	 */
	public void setContents(Map<ResourceLocation, Integer> molecules, Map<String, Integer> ions,
		Map<ResourceLocation, Integer> suspended, Map<ResourceLocation, Integer> sediment, int temperature, int scale) {
		fluids.clear();
		molecules.values().removeIf(v -> v <= 0);
		ions.values().removeIf(v -> v <= 0);
		suspended.values().removeIf(v -> v <= 0);
		sediment.values().removeIf(v -> v <= 0);
		repairTraceChargeImbalance(ions);
		if (molecules.isEmpty() && ions.isEmpty() && suspended.isEmpty() && sediment.isEmpty()) {
			onChanged.run();
			return;
		}
		FluidStack target;
		if (scale == 1 && molecules.size() == 1 && ions.isEmpty() && suspended.isEmpty() && sediment.isEmpty()) {
			Map.Entry<ResourceLocation, Integer> only = molecules.entrySet().iterator().next();
			Fluid pf = ForgeRegistries.FLUIDS.getValue(only.getKey());
			if (pf == null || pf == Fluids.EMPTY) {
				onChanged.run();
				return;
			}
			target = new FluidStack(pf, only.getValue());
		} else {
			long totalUnits = 0;
			for (int v : molecules.values()) {
				totalUnits += v;
			}
			for (int v : ions.values()) {
				totalUnits += v;
			}
			for (int v : suspended.values()) {
				totalUnits += v;
			}
			for (int v : sediment.values()) {
				totalUnits += v;
			}
			int totalMb = (int) Math.round((double) totalUnits / scale);
			target = Mixture.create(molecules, ions, suspended, sediment, Math.max(1, totalMb));
		}
		Temperature.set(target, temperature);
		fluids.add(target);
		onChanged.run();
	}

	/**
	 * The unit-grid write-back can arrive a few units off neutral: the mB→unit
	 * re-derivation's integer remainder distribution is not charge-balanced
	 * (equal Na⁺/Cl⁻ parts can differ by one unit). The mixture's neutrality
	 * invariant is exact and would REJECT the whole ion set — silently
	 * dropping every ion — so shave the trace imbalance off the most abundant
	 * ion of the excess sign (a few units in millions: invisible). A real
	 * solver bug (large imbalance) is still left for the invariant to reject
	 * loudly.
	 */
	private static void repairTraceChargeImbalance(Map<String, Integer> ions) {
		long imbalance = 0;
		for (Map.Entry<String, Integer> e : ions.entrySet()) {
			imbalance += (long) com.yu1745.chemicaladdon.composition.Ion.chargeOf(e.getKey()) * e.getValue();
		}
		int guard = 0;
		while (imbalance != 0 && guard++ < 64) {
			String pick = null;
			for (Map.Entry<String, Integer> e : ions.entrySet()) {
				int q = com.yu1745.chemicaladdon.composition.Ion.chargeOf(e.getKey());
				if ((imbalance > 0 && q > 0) || (imbalance < 0 && q < 0)
					&& (pick == null || e.getValue() > ions.get(pick))) {
					pick = e.getKey();
				}
			}
			if (pick == null) {
				break;
			}
			if (ions.merge(pick, -1, Integer::sum) <= 0) {
				ions.remove(pick);
			}
			imbalance -= com.yu1745.chemicaladdon.composition.Ion.chargeOf(pick);
		}
	}

	public CompoundTag serializeNBT() {		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		for (FluidStack f : fluids) {
			list.add(f.writeToNBT(new CompoundTag()));
		}
		tag.put("fluids", list);
		return tag;
	}

	public void deserializeNBT(CompoundTag tag) {
		fluids.clear();
		ListTag list = tag.getList("fluids", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			FluidStack f = FluidStack.loadFluidStackFromNBT(list.getCompound(i));
			if (!f.isEmpty()) {
				fluids.add(f);
			}
		}
	}
}
