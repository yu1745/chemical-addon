package com.yu1745.chemicaladdon.reactor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.simibubi.create.foundation.fluid.FluidIngredient;
import com.yu1745.chemicaladdon.composition.Solution;
import com.yu1745.chemicaladdon.composition.Species;
import com.yu1745.chemicaladdon.composition.SpeciesManager;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.fluid.Miscibility;
import com.yu1745.chemicaladdon.fluid.Temperature;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
				if (action.execute()) {
					f.shrink(amount);
					removeEmpty();
					onChanged.run();
				}
				return new FluidStack(target, amount);
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
						+ Mixture.getSuspended(only).size();
					if (comps == 0) {
						fluids.clear(); // corrupt component-less mixture
						onChanged.run();
					} else if (Mixture.getIons(only).isEmpty() && Mixture.getSuspended(only).isEmpty()
						&& Mixture.getMolecules(only).size() == 1) {
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
			FluidStack merged = mergeGroup(members);
			if (!merged.isEmpty()) {
				settled.add(merged);
			}
		}
		// densest liquid first (it sinks); gases are the lightest phase, kept last
		// (the renderer hangs them from the top)
		settled.sort((a, b) -> Integer.compare(Miscibility.densityOf(b), Miscibility.densityOf(a)));
		settled.addAll(gases);

		fluids.clear();
		fluids.addAll(settled);
		onChanged.run();
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

		if (molecules.isEmpty() && ions.isEmpty() && suspended.isEmpty()) {
			return FluidStack.EMPTY; // every entry was corrupt
		}
		FluidStack target;
		if (molecules.size() == 1 && ions.isEmpty() && suspended.isEmpty()) {
			// a single pure molecular species → a pure fluid
			Map.Entry<ResourceLocation, Integer> only = molecules.entrySet().iterator().next();
			Fluid pf = ForgeRegistries.FLUIDS.getValue(only.getKey());
			if (pf == null || pf == Fluids.EMPTY) {
				return FluidStack.EMPTY;
			}
			target = new FluidStack(pf, total);
		} else {
			target = Mixture.create(molecules, ions, suspended, total);
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
				if (amounts.isEmpty()) {
					stack.setAmount(0); // removeEmpty drops it
				} else {
					// re-stamp the ratio from the surviving absolute amounts and rebalance
					// the total; collapseIfNeeded degrades a 1-component remainder to pure
					int newTotal = 0;
					for (int v : amounts.values()) {
						newTotal += v;
					}
					Mixture.setMolecules(stack, Mixture.reduce(amounts));
					stack.setAmount(newTotal);
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
				for (Species.IonComponent c : species.ions()) {
					ionAmounts.merge(c.ion().id(), (int) (-take * c.count()), Integer::sum);
				}
				ionAmounts.values().removeIf(v -> v <= 0);
				mol.values().removeIf(v -> v <= 0);
				int newTotal = stack.getAmount() - takeMb;
				int idx = fluids.indexOf(stack);
				if (newTotal <= 0 || (ionAmounts.isEmpty() && mol.isEmpty())) {
					fluids.set(idx, FluidStack.EMPTY);
				} else {
					FluidStack replacement = Mixture.create(mol, ionAmounts, newTotal);
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

	/** Empties the tank entirely (e.g. after contents were spilled into the world). */
	public void clear() {
		fluids.clear();
	}

	/**
	 * Remove all suspended solids from the tank, emitting them as items via
	 * {@code sink} (1 item per {@code mbPerItem} mB, rounded, min 1). The liquid
	 * (molecules + ions) stays in the tank. Returns the total mB of solids removed.
	 */
	public int extractSuspended(Consumer<ItemStack> sink, int mbPerItem) {
		int extracted = 0;
		for (FluidStack stack : new ArrayList<>(fluids)) {
			if (!Mixture.isMixture(stack)) {
				continue;
			}
			Map<ResourceLocation, Integer> susp = Mixture.deriveSuspendedAmounts(stack);
			if (susp.isEmpty()) {
				continue;
			}
			Map<ResourceLocation, Integer> mol = Mixture.deriveAmounts(stack);
			Map<String, Integer> ions = Mixture.deriveIonAmounts(stack);
			for (Map.Entry<ResourceLocation, Integer> e : susp.entrySet()) {
				Item item = ForgeRegistries.ITEMS.getValue(e.getKey());
				if (item == null || e.getValue() <= 0) {
					continue;
				}
				int count = Math.max(1, (int) Math.round((double) e.getValue() / mbPerItem));
				sink.accept(new ItemStack(item, count));
				extracted += e.getValue();
			}
			int newTotal = 0;
			for (int v : mol.values()) {
				newTotal += v;
			}
			for (int v : ions.values()) {
				newTotal += v;
			}
			int idx = fluids.indexOf(stack);
			if (newTotal <= 0) {
				fluids.set(idx, FluidStack.EMPTY);
			} else {
				FluidStack replacement = Mixture.create(mol, ions, newTotal);
				Temperature.set(replacement, Temperature.get(stack));
				fluids.set(idx, replacement);
			}
		}
		if (extracted > 0) {
			removeEmpty();
			onChanged.run();
		}
		return extracted;
	}

	/**
	 * Replace the entire contents with the given molecular + ionic amounts
	 * (mole-equivalents). A single pure molecular species becomes a pure stack;
	 * anything else becomes one {@link Mixture}. Used by the rules engine to write
	 * its solved composition back in one canonical step.
	 */
	public void setContents(Map<ResourceLocation, Integer> molecules, Map<String, Integer> ions, int temperature) {
		setContents(molecules, ions, Map.of(), temperature);
	}

	/**
	 * Replace the entire contents with the given molecular + ionic + suspended
	 * amounts (mole-equivalents). A single pure molecular species becomes a pure
	 * stack; anything else becomes one {@link Mixture}. Used by the rules engine to
	 * write its solved composition back in one canonical step.
	 */
	public void setContents(Map<ResourceLocation, Integer> molecules, Map<String, Integer> ions,
		Map<ResourceLocation, Integer> suspended, int temperature) {
		fluids.clear();
		molecules.values().removeIf(v -> v <= 0);
		ions.values().removeIf(v -> v <= 0);
		suspended.values().removeIf(v -> v <= 0);
		if (molecules.isEmpty() && ions.isEmpty() && suspended.isEmpty()) {
			onChanged.run();
			return;
		}
		FluidStack target;
		if (molecules.size() == 1 && ions.isEmpty() && suspended.isEmpty()) {
			Map.Entry<ResourceLocation, Integer> only = molecules.entrySet().iterator().next();
			Fluid pf = ForgeRegistries.FLUIDS.getValue(only.getKey());
			if (pf == null || pf == Fluids.EMPTY) {
				onChanged.run();
				return;
			}
			target = new FluidStack(pf, only.getValue());
		} else {
			int total = 0;
			for (int v : molecules.values()) {
				total += v;
			}
			for (int v : ions.values()) {
				total += v;
			}
			for (int v : suspended.values()) {
				total += v;
			}
			target = Mixture.create(molecules, ions, suspended, total);
		}
		Temperature.set(target, temperature);
		fluids.add(target);
		onChanged.run();
	}

	public CompoundTag serializeNBT() {
		CompoundTag tag = new CompoundTag();
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
