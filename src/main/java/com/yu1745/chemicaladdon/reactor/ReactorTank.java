package com.yu1745.chemicaladdon.reactor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.simibubi.create.foundation.fluid.FluidIngredient;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.fluid.Temperature;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
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
	/** Set by every drain (including Create's 1 mB SIMULATE probe) since the last
	 *  homogenisation tick. The controller consumes it to freeze MixDegree while the
	 *  mixture is being pumped out, keeping the transport tag stable for Create's
	 *  probe-then-transfer {@code isFluidEqual} matching. */
	private boolean drainedSinceLastMixTick = false;

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

	/** Consumes (and returns) the "drained since last homogenisation tick" flag. */
	public boolean consumeDrainedFlag() {
		boolean drained = drainedSinceLastMixTick;
		drainedSinceLastMixTick = false;
		return drained;
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
		drainedSinceLastMixTick = true;
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
		drainedSinceLastMixTick = true;
		if (fluids.isEmpty() || maxDrain <= 0) {
			return FluidStack.EMPTY;
		}
		FluidStack first = fluids.get(0);
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
					int comps = Mixture.getRatios(only).size();
					if (comps == 0) {
						fluids.clear(); // corrupt component-less mixture
						onChanged.run();
					} else if (comps == 1) {
						degradeSingleComponentMixture(only);
					}
					// comps >= 2: settled — leave the ratio tag alone
				}
			}
			return;
		}

		Map<ResourceLocation, Integer> merged = new LinkedHashMap<>();
		double weightedMix = 0; // Σ (MixDegree × amount); pure fluids contribute 0
		long weightedTemp = 0; // Σ (temperature × amount), °C·mB
		for (FluidStack stack : fluids) {
			weightedTemp += (long) Temperature.get(stack) * stack.getAmount();
			if (Mixture.isMixture(stack)) {
				weightedMix += Mixture.getMixDegree(stack) * (double) stack.getAmount();
				for (Map.Entry<ResourceLocation, Integer> e : Mixture.deriveAmounts(stack).entrySet()) {
					merged.merge(e.getKey(), e.getValue(), Integer::sum);
				}
			} else {
				ResourceLocation id = ForgeRegistries.FLUIDS.getKey(sourceOf(stack.getFluid()));
				if (id != null) {
					merged.merge(id, stack.getAmount(), Integer::sum);
				}
			}
		}
		merged.values().removeIf(v -> v <= 0);
		int distinct = merged.size();

		FluidStack target;
		if (distinct >= 2) {
			int total = 0;
			for (int v : merged.values()) {
				total += v;
			}
			target = Mixture.create(Mixture.reduce(merged), total);
			// amount-weighted homogenisation: freshly added pure fluid (MixDegree 0)
			// drags the blend back down in proportion to how much of it was added
			Mixture.setMixDegree(target, total > 0 ? (float) (weightedMix / total) : 0f);
			Temperature.set(target, Temperature.fromWeightedSum(weightedTemp, total));
		} else if (distinct == 1) {
			Map.Entry<ResourceLocation, Integer> only = merged.entrySet().iterator().next();
			Fluid pf = ForgeRegistries.FLUIDS.getValue(only.getKey());
			if (pf == null || pf == Fluids.EMPTY) {
				return;
			}
			target = new FluidStack(pf, only.getValue());
			Temperature.set(target, Temperature.fromWeightedSum(weightedTemp, only.getValue()));
		} else {
			target = null; // every entry was corrupt
		}

		fluids.clear();
		if (target != null) {
			fluids.add(target);
		}
		onChanged.run();
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
					Mixture.setRatios(stack, Mixture.reduce(amounts));
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

	private void removeEmpty() {
		fluids.removeIf(f -> f.getAmount() <= 0);
	}

	/** Empties the tank entirely (e.g. after contents were spilled into the world). */
	public void clear() {
		fluids.clear();
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
