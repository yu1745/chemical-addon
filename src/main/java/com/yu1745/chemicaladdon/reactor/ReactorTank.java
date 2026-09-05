package com.yu1745.chemicaladdon.reactor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.simibubi.create.foundation.fluid.FluidIngredient;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.composition.Chemistry;
import com.yu1745.chemicaladdon.composition.Solution;
import com.yu1745.chemicaladdon.composition.Species;
import com.yu1745.chemicaladdon.composition.SpeciesManager;
import com.yu1745.chemicaladdon.composition.parity.EngineBridge;
import com.yu1745.chemicaladdon.composition.parity.Kernel;
import com.yu1745.chemicaladdon.composition.parity.KernelSolutionState;
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
import com.yu1745.chemengine.kernel.IPhreeqc;

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

	/** Removes contained gas without producing a world-fluid spill. */
	public int ventGases() {
		int before = getTotalAmount();
		fluids.removeIf(Miscibility::isGas);
		int removed = before - getTotalAmount();
		if (removed > 0) {
			onChanged.run();
		}
		return removed;
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
		// A reactor never stores a bare water phase: ordinary Forge water is an
		// explicit external declaration at this boundary and becomes a native
		// aqueous batch before it can meet any other feed.
		if (!Mixture.isMixture(resource) && sourceOf(resource.getFluid()) == Fluids.WATER) {
			try {
				resource = Mixture.fromDeclaredComposition(resource.getAmount() / 1000d, Map.of(),
					resource.getAmount(), Temperature.get(resource), java.util.List.of());
			} catch (RuntimeException ex) {
				ChemicalAddon.LOGGER.warn("Rejected external water declaration: {}", ex.getMessage());
				return 0;
			}
		}
		if (Mixture.isMixture(resource) && Mixture.engineSolution(resource) == null) {
			ChemicalAddon.LOGGER.warn("Rejected mixture fill without EngineSolutionRaw");
			return 0;
		}
		if (!Mixture.isMixture(resource) && Miscibility.AQUEOUS.equals(Miscibility.groupOf(resource))) {
			ResourceLocation id = ForgeRegistries.FLUIDS.getKey(sourceOf(resource.getFluid()));
			if (id == null || (!Solution.WATER.equals(id)
				&& (SpeciesManager.get(id) == null || !SpeciesManager.get(id).isElectrolyte()))) {
				ChemicalAddon.LOGGER.warn("Rejected external aqueous feed without a declared engine model: {}", id);
				return 0;
			}
		}
		int amount = Math.min(resource.getAmount(), capacity - getTotalAmount());
		if (amount <= 0) {
			return 0;
		}
		if (action.execute()) {
			// a mixture's cached colour is display state, not chemistry: a creative
			// bucket packs an opaque identity tint that must not ride into the vessel.
			// Normalise on a copy so same-composition stacks merge (isFluidEqual
			// compares the whole NBT tag, colour included) and the caller's stack
			// (e.g. the bucket item's own) keeps its display tint.
			if (Mixture.isMixture(resource)) {
				resource = resource.copy();
				Mixture.recolor(resource);
			}
			Fluid fluid = sourceOf(resource);
			boolean incomingMix = Mixture.isMixture(resource);
			for (FluidStack f : fluids) {
				// same mixture composition (identical ratio tag): stack it
				if (incomingMix && Mixture.isMixture(f) && f.isFluidEqual(resource)) {
					f.grow(amount);
					onChanged.run();
					return amount;
				}
				// Independently declared aqueous inputs have distinct RAW tags even
				// when both are water. Merge them through MIX_SOLUTION immediately so
				// Forge's ordinary same-fluid stacking behaviour is retained.
				if (incomingMix && Mixture.isMixture(f)
					&& Miscibility.AQUEOUS.equals(Miscibility.groupOf(f))
					&& Miscibility.AQUEOUS.equals(Miscibility.groupOf(resource))) {
					FluidStack incoming = resource.copy();
					incoming.setAmount(amount);
					FluidStack merged = mergeGroup(List.of(f, incoming));
					if (!merged.isEmpty()) {
						int index = fluids.indexOf(f);
						fluids.set(index, merged);
						onChanged.run();
						return amount;
					}
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
			// The registry can hand recipes/pipes a flowing variant. Store the source
			// variant promised by sourceOf so later gas/product checks do not split a
			// single physical phase by Forge's still/flowing implementation detail.
			FluidStack copy = incomingMix ? resource.copy() : new FluidStack(fluid, amount);
			copy.setAmount(amount);
			if (!incomingMix) Temperature.set(copy, Temperature.get(resource));
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
	 * Decant drain (U16.5): like {@link #drainLightest} but the settled bed's
	 * pore liquor is protected — when the drawn phase is the mixture, only its
	 * LIQUID (molecules + ions, solids untouched) is drawn and never below the
	 * bed's pore level. A hose floating at the surface cannot suck a sludge bed
	 * dry; reslurry washing (decant → refill clean water → decant) decays the
	 * retained liquor geometrically because of exactly this floor.
	 */
	public FluidStack drainLightestClear(int maxDrain, FluidAction action) {
		if (fluids.isEmpty() || maxDrain <= 0) {
			return FluidStack.EMPTY;
		}
		List<FluidStack> order = new ArrayList<>(fluids);
		order.sort((a, b) -> Integer.compare(Miscibility.densityOf(a), Miscibility.densityOf(b)));
		if (Mixture.isMixture(order.get(0))) {
			return decantClear(maxDrain, action);
		}
		return drainSorted(maxDrain, action, true);
	}

	/**
	 * U16.5 settled-bed retention: a settled/suspended solid mass holds its
	 * pore liquor ({@link RulesEngine#CAKE_LIQUOR_FRACTION} of its volume) —
	 * clear-liquid ports (decant spout, hose) may draw the free liquid but
	 * never the pore fraction.
	 *
	 * @return liquid mB (molecules + ions, solids excluded) a clear-liquid port
	 *         may still draw (never negative)
	 */
	public int clearLiquidAvailable() {
		if (containsEngineBackedMixture()) {
			long liquid = 0;
			for (FluidStack stack : fluids) if (Mixture.isMixture(stack)) liquid += stack.getAmount();
			long pore = Math.round((engineSolidUnits(KernelSolutionState.SolidLocation.SEDIMENT)
					+ engineSolidUnits(KernelSolutionState.SolidLocation.SUSPENDED))
					* RulesEngine.CAKE_LIQUOR_FRACTION);
			return (int) Math.max(0, liquid - pore / Chemistry.UNIT_PER_MB);
		}
		// Separation is defined only for an engine-owned aqueous ledger. Oil/gas
		// and malformed display-only mixtures must not enter the retired domain
		// reconstruction path.
		return 0;
	}

	/**
	 * U16.5 decant: draw up to {@code maxDrainMb} of the mixture's <b>liquid</b>
	 * (water + solutes + ions, a proportional sample of all of it) while every
	 * solid domain stays in the pot — and never below the settled bed's pore
	 * level ({@link #clearLiquidAvailable()}). This is the "skim the clear
	 * liquor off the sludge" primitive reslurry washing is built from; a plain
	 * pump ({@link #drain}) is the opposite (slurry pumping: it takes
	 * proportional solids too).
	 */
	public FluidStack decantClear(int maxDrainMb, FluidAction action) {
		if (containsEngineBackedMixture()) return engineDraw(maxDrainMb, action, false, false, true, Double.NaN);
		return FluidStack.EMPTY;
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
					// The raw reference can contain trace solutes below the display
					// projection. It must remain a mixture even if the four legacy
					// domains presently look like pure water.
					if (Mixture.engineSolution(only) != null) {
						return;
					}
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
			} else if (members.stream().anyMatch(Mixture::isMixture)) {
				// A kernel transaction failed.  Retain the original states verbatim;
				// dropping a group here would turn an unsupported feed into material
				// loss while still looking like a normal successful collapse.
				settled.addAll(members);
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
		// Aqueous inventory is never rebuilt from the display domains.  Every
		// member is materialised at its actual amount and combined through
		// PHREEQC MIX_SOLUTION, preserving the complete archived pools.
		if (Miscibility.AQUEOUS.equals(Miscibility.groupOf(members.get(0)))) {
			try {
				IPhreeqc q = Kernel.get();
				synchronized (q) {
				List<KernelSolutionState> states = new ArrayList<>();
				long weightedTemp = 0;
				for (FluidStack member : members) {
					states.add(engineStateFor(q, member));
					weightedTemp += (long) Temperature.get(member) * member.getAmount();
				}
				KernelSolutionState merged = KernelSolutionState.merge(q, states);
				FluidStack target = Mixture.create(Map.of(Solution.WATER, merged.referenceMb()), merged.referenceMb());
				Mixture.setEngineSolution(target, merged);
				Temperature.set(target, Temperature.fromWeightedSum(weightedTemp, merged.referenceMb()));
				return target;
				}
			} catch (RuntimeException ex) {
				ChemicalAddon.LOGGER.warn("Rejected unsupported aqueous merge: {}", ex.getMessage());
				return FluidStack.EMPTY;
			}
		}
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

	/** Convert only a newly-declared external aqueous material to engine state. */
	private static KernelSolutionState engineStateFor(IPhreeqc q, FluidStack stack) {
		if (Mixture.isMixture(stack)) {
			KernelSolutionState state = Mixture.engineSolution(stack);
			if (state == null) throw new IllegalArgumentException("mixture has no engine state");
			return state.scale(q, stack.getAmount());
		}
		ResourceLocation id = ForgeRegistries.FLUIDS.getKey(sourceOf(stack.getFluid()));
		if (id == null) throw new IllegalArgumentException("unregistered external fluid");
		if (Solution.WATER.equals(id)) {
			return KernelSolutionState.fromDeclaredFeed(q, stack.getAmount() / 1000d, Map.of(), stack.getAmount());
		}
		Species species = SpeciesManager.get(id);
		if (species == null || !species.isElectrolyte()) {
			throw new IllegalArgumentException("no declared aqueous model for " + id);
		}
		return KernelSolutionState.fromDeclaredFeed(q, stack.getAmount() / 1000d,
			EngineBridge.declaredFeedForSpecies(id, stack.getAmount() / 1000d), stack.getAmount());
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
		String nativeFormula = nativeFormulaForIngredient(ingredient);
		Species nativeSpecies = nativeSpeciesForIngredient(ingredient);
		for (FluidStack stack : fluids) {
			if (Mixture.isMixture(stack)) {
				KernelSolutionState state = Mixture.engineSolution(stack);
				if (state != null) {
					if ("H2O".equals(nativeFormula)) {
						total += nativeWaterMb(state, stack.getAmount());
					} else if (nativeSpecies != null) {
						total += nativeFormulaMb(nativeSpecies, state, stack.getAmount());
					}
					continue;
				}
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
		// A native REACTION is an all-or-nothing inventory transaction.  Check the
		// whole vessel before touching a pure stack so failed mixed feeds are net-zero.
		if (containsEngineBackedMixture() && countIngredient(ingredient) < required) return 0;
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
		// Pure gas/oil has already been withdrawn above.  Do not let the old
		// display-domain component loop mutate an engine-backed aqueous batch.
		if (remaining > 0 && containsEngineBackedMixture()) {
			String formula = nativeFormulaForIngredient(ingredient);
			if (formula != null) {
				List<FluidStack> aqueous = fluids.stream().filter(Mixture::isMixture)
					.filter(stack -> Mixture.engineSolution(stack) != null).toList();
				if (aqueous.isEmpty()) return required - remaining;
				try {
					IPhreeqc q = Kernel.get(); synchronized (q) {
						List<KernelSolutionState> actualStates = new ArrayList<>();
						int aqueousMb = 0;
						for (FluidStack stack : aqueous) {
							actualStates.add(Mixture.engineSolution(stack).scale(q, stack.getAmount()));
							aqueousMb += stack.getAmount();
						}
						KernelSolutionState actual = KernelSolutionState.merge(q, actualStates);
						KernelSolutionState next = "H2O".equals(formula)
							? actual.evaporateWater(q, remaining / 1000d)
							: actual.removeDeclaredFeed(q, Map.of(formula, remaining / 1000d));
						if (action.execute()) {
							FluidStack target = aqueous.get(0);
							if ("H2O".equals(formula)) {
								int left = aqueousMb - remaining;
								if (left <= 0) throw new IllegalArgumentException("water drain emptied aqueous batch");
								target.setAmount(left);
								next = new KernelSolutionState(next.raw(), left, next.solids());
							}
							Mixture.setEngineSolution(target, next);
							for (int i = 1; i < aqueous.size(); i++) aqueous.get(i).setAmount(0);
							removeEmpty();
							onChanged.run();
						}
					}
					return required;
				} catch (RuntimeException ex) { return required - remaining; }
			}
			if (action.execute()) {
				removeEmpty();
				onChanged.run();
			}
			return required - remaining;
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

	@Nullable
	private static String nativeFormulaForIngredient(FluidIngredient ingredient) {
		if (ingredient.test(new FluidStack(Fluids.WATER, 1))) return "H2O";
		Species species = nativeSpeciesForIngredient(ingredient);
		return species == null ? null : species.engineFormula();
	}

	@Nullable
	private static Species nativeSpeciesForIngredient(FluidIngredient ingredient) {
		for (Species species : SpeciesManager.all()) {
			Fluid fluid = ForgeRegistries.FLUIDS.getValue(species.id());
			if (fluid != null && fluid != Fluids.EMPTY && ingredient.test(new FluidStack(fluid, 1)))
				return species.engineFormula() == null ? null : species;
		}
		return null;
	}

	private static int nativeWaterMb(KernelSolutionState state, int amountMb) {
		try {
			IPhreeqc q = Kernel.get(); synchronized (q) {
				return (int) Math.min(Integer.MAX_VALUE, Math.floor(EngineBridge.derive(q,
					state.scale(q, amountMb), List.of(), List.of()).waterKg() * 1000d + 1e-9));
			}
		} catch (RuntimeException ignored) { return 0; }
	}

	private static int nativeFormulaMb(Species species, KernelSolutionState state, int amountMb) {
		try {
			IPhreeqc q = Kernel.get(); synchronized (q) {
				return (int) Math.min(Integer.MAX_VALUE, Math.floor(nativeFormulaMol(species,
					state.scale(q, amountMb), q) * 1000d + 1e-9));
			}
		} catch (RuntimeException ignored) { return 0; }
	}

	/** mB of a solution species' solute ions (mole-equivalents) formable from the vessel's dissolved ions. */
	public int countSolution(ResourceLocation speciesId) {
		Species species = SpeciesManager.get(speciesId);
		if (species == null || !species.isSolution()) {
			return 0;
		}
		return containsEngineBackedMixture()
			? (int) Math.min(Integer.MAX_VALUE, Math.round(nativeFormulaMol(species) * species.ionCount() * 1000d))
			: 0;
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
		if (!containsEngineBackedMixture()) return 0;
		double waterKg = nativeWaterKg();
		return waterKg > 0 ? nativeFormulaMol(species) * species.ionCount() / waterKg : 0;
	}

	/** Native inventory view for recipe matching; never consults display NBT. */
	private double nativeFormulaMol(Species species) {
		try {
			IPhreeqc q = Kernel.get();
			synchronized (q) {
				double total = 0;
				for (FluidStack stack : fluids) if (Mixture.isMixture(stack)) {
					KernelSolutionState state = Mixture.engineSolution(stack);
					if (state == null) return 0;
					total += nativeFormulaMol(species, state.scale(q, stack.getAmount()), q);
				}
				return total;
			}
		} catch (RuntimeException ex) {
			ChemicalAddon.LOGGER.warn("Native solution view failed for {}: {}", species.solute(), ex.getMessage());
			return 0;
		}
	}

	/**
	 * Formula units in one already materialised native state. This uses conserved
	 * non-H/O component totals plus PHREEQC's native alkalinity inventory for
	 * acids/bases: free H+/OH- cannot measure a feed after protonation or
	 * hydrolysis, while component totals alone would make Na2SO4 look like H2SO4.
	 */
	private static double nativeFormulaMol(Species species, KernelSolutionState state, IPhreeqc q) {
		java.util.Set<String> components = new java.util.LinkedHashSet<>();
		int acidEquivalents = 0;
		int baseEquivalents = 0;
		for (Species.IonComponent ion : species.ions()) {
			if ("H".equals(ion.ion().symbol())) { acidEquivalents += ion.count(); continue; }
			if ("OH".equals(ion.ion().symbol())) { baseEquivalents += ion.count(); continue; }
			String component = EngineBridge.phreeqcComponent(ion.ion().symbol());
			if (component == null) return 0;
			components.add(component);
		}
		if (components.isEmpty()) return 0;
		var view = EngineBridge.derive(q, state, components, java.util.List.of());
		java.util.Map<String, Double> totals = view.totalMol();
		double available = Double.POSITIVE_INFINITY;
		for (Species.IonComponent ion : species.ions()) {
			if ("H".equals(ion.ion().symbol()) || "OH".equals(ion.ion().symbol())) continue;
			String key = EngineBridge.phreeqcComponent(ion.ion().symbol());
			available = Math.min(available, totals.getOrDefault(key, 0d) / ion.count());
		}
		if (acidEquivalents > 0) available = Math.min(available,
			Math.max(0d, -view.alkalinityEq() / acidEquivalents));
		if (baseEquivalents > 0) available = Math.min(available,
			Math.max(0d, view.alkalinityEq() / baseEquivalents));
		return Double.isFinite(available) ? Math.max(0d, available) : 0d;
	}

	private double nativeWaterKg() {
		try {
			IPhreeqc q = Kernel.get();
			synchronized (q) {
				double water = 0;
				for (FluidStack stack : fluids) if (Mixture.isMixture(stack)) {
					KernelSolutionState state = Mixture.engineSolution(stack);
					if (state == null) return 0;
					water += EngineBridge.derive(q, state.scale(q, stack.getAmount()), java.util.List.of(), java.util.List.of()).waterKg();
				}
				return water;
			}
		} catch (RuntimeException ex) { return 0; }
	}

	/** Actual water inventory, derived from native states rather than their
	 * display cache. Pure water phases remain supported for non-reactor users. */
	public int waterInventoryMb() {
		long total = 0;
		for (FluidStack stack : fluids) {
			if (Mixture.isMixture(stack) && Mixture.engineSolution(stack) != null)
				total += nativeWaterMb(Mixture.engineSolution(stack), stack.getAmount());
			else if (sourceOf(stack.getFluid()) == Fluids.WATER) total += stack.getAmount();
		}
		return (int) Math.min(Integer.MAX_VALUE, total);
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
		if (containsEngineBackedMixture()) {
			// Neutral formula removal is a native signed REACTION.  We first obtain
			// the immutable candidate; only EXECUTE installs it, so an undersupplied
			// or unsupported formula is net-zero.
			List<FluidStack> aqueous = fluids.stream().filter(Mixture::isMixture)
				.filter(stack -> Mixture.engineSolution(stack) != null).toList();
			// Gas/oil bystanders are separate phases and must survive an aqueous
			// recipe withdrawal. More than one aqueous state needs an explicit
			// merge transaction instead of choosing one arbitrarily.
			if (aqueous.size() != 1) return 0;
			FluidStack liquid = aqueous.get(0);
			KernelSolutionState state = Mixture.engineSolution(liquid);
			try {
				IPhreeqc q = Kernel.get();
				synchronized (q) {
					KernelSolutionState actual = state.scale(q, liquid.getAmount());
					double formulaMol = required / (species.ionCount() * 1000d);
					KernelSolutionState changed;
					changed = actual.removeDeclaredFeed(q,
						EngineBridge.declaredFeedForSpecies(speciesId, formulaMol));
					if (action.execute()) Mixture.setEngineSolution(liquid, changed);
				}
				return required;
			} catch (RuntimeException ex) {
				ChemicalAddon.LOGGER.warn("Rejected native solution drain {}: {}", speciesId, ex.getMessage());
				return 0;
			}
		}
		return 0;
	}

	private void removeEmpty() {
		fluids.removeIf(f -> f.getAmount() <= 0);
	}

	/** True when legacy display-domain mutations are forbidden for this tank. */
	public boolean containsEngineBackedMixture() {
		return fluids.stream().anyMatch(stack -> Mixture.isMixture(stack) && Mixture.engineSolution(stack) != null);
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
		return extractSolids(sink, sedimentDomain, null, null);
	}

	/**
	 * Exact native solid extraction with a wet-cake mother-liquor payload. When
	 * an engine-backed rinse tank and filtrate are both available, one pore
	 * volume is fully mixed with the cake liquor, the original pore volume stays
	 * on the cake, and the displaced volume becomes native filtrate.
	 *
	 * @param washTank optional native, solid-free rinse liquor; null = no wash
	 * @param filtrate required receiver for a rinse transaction; null leaves the cake unwashed
	 */
	public long extractSolids(Consumer<ItemStack> sink, boolean sedimentDomain,
		@Nullable ReactorTank washTank, @Nullable ReactorTank filtrate) {
		if (containsEngineBackedMixture()) return extractEngineSolids(sink, sedimentDomain, washTank, filtrate);
		return 0;
	}

	/** Engine-ledger solid extraction. Aqueous raw state is deliberately left in
	 * the vessel here; cake-liquor persistence is handled by the filter adapter,
	 * never reconstructed from display ions. */
	private long extractEngineSolids(Consumer<ItemStack> sink, boolean sedimentDomain,
			@Nullable ReactorTank washTank, @Nullable ReactorTank filtrate) {
		KernelSolutionState.SolidLocation location = sedimentDomain
			? KernelSolutionState.SolidLocation.SEDIMENT : KernelSolutionState.SolidLocation.SUSPENDED;
		long units = engineSolidUnits(location);
		long itemUnits = (long) RulesEngine.MB_PER_ITEM * Chemistry.UNIT_PER_MB;
		long items = units / itemUnits;
		if (items <= 0) return 0;
		double fraction = (double) (items * itemUnits) / units;
		long extractedUnits = 0;
		boolean changed = false;
		for (FluidStack stack : fluids) if (Mixture.isMixture(stack)) {
			KernelSolutionState stored = Mixture.engineSolution(stack);
			if (stored == null || stack.getAmount() <= 0) continue;
			try {
				IPhreeqc q = Kernel.get();
				KernelSolutionState next, liquor, washRemainder = null, washCakeLiquor = null, washEffluent = null;
				FluidStack washSource = null;
				int washMb = 0;
				List<KernelSolutionState.SolidPhase> extracted = new ArrayList<>();
				int liquorMb;
				synchronized (q) {
					// A Forge stack can be a proportional copy whose stored reference is
					// larger than its current amount.  All stock arithmetic must begin
					// from that actual amount; otherwise the cake and remainder have
					// incompatible RAW reference volumes.
					KernelSolutionState actual = stored.scale(q, stack.getAmount());
					List<KernelSolutionState.SolidPhase> remain = new ArrayList<>();
					for (var solid : actual.solids()) {
						if (solid.location() != location) { remain.add(solid); continue; }
						double taken = solid.mol() * fraction;
						if (taken <= 0) { remain.add(solid); continue; }
						extracted.add(new KernelSolutionState.SolidPhase(solid.speciesId(), taken, location));
						remain.add(new KernelSolutionState.SolidPhase(solid.speciesId(), solid.mol() - taken, location));
					}
					if (extracted.isEmpty()) continue;
					liquorMb = Math.min(stack.getAmount() - 1, Math.max(0,
						(int) Math.round(stack.getAmount() * fraction * RulesEngine.CAKE_LIQUOR_FRACTION)));
					if (liquorMb > 0) {
						KernelSolutionState.ProportionalRemoval split = actual.removeProportionally(q, liquorMb);
						liquor = split.removed().withSolids(List.of());
						next = split.remainder().withSolids(remain);
					} else {
						liquor = null;
						next = actual.withSolids(remain);
					}
					// One fully-mixed native wash: mix the retained pore liquor with
					// fresh rinse, keep the original pore volume on the cake, and send
					// exactly the displaced rinse volume to filtrate.  No display ions
					// participate in this transaction.
					if (liquor != null && washTank != null && filtrate != null) for (FluidStack candidate : washTank.fluids) {
						if (!Mixture.isMixture(candidate) || candidate.getAmount() <= 1) continue;
						KernelSolutionState storedWash = Mixture.engineSolution(candidate);
						if (storedWash == null || !storedWash.solids().isEmpty()) continue;
						washMb = Math.min(liquorMb, candidate.getAmount() - 1);
						if (washMb <= 0) continue;
						KernelSolutionState actualWash = storedWash.scale(q, candidate.getAmount());
						KernelSolutionState washTaken = actualWash.removeProportionally(q, washMb).removed();
						washRemainder = actualWash.removeProportionally(q, washMb).remainder();
						KernelSolutionState mixed = KernelSolutionState.merge(q, List.of(liquor, washTaken));
						KernelSolutionState.ProportionalRemoval rinsed = mixed.removeProportionally(q, liquorMb);
						washCakeLiquor = rinsed.removed();
						washEffluent = rinsed.remainder();
						washSource = candidate;
						break;
					}
				}
				FluidStack effluent = FluidStack.EMPTY;
				if (washEffluent != null) {
					effluent = new FluidStack(Mixture.fluid(), washMb);
					Mixture.setEngineSolution(effluent, washEffluent);
					// A backed-up or absent filtrate disables the optional rinse before
					// any input is committed; it never consumes wash water invisibly.
					if (filtrate.fill(effluent.copy(), FluidAction.SIMULATE) != washMb) {
						washSource = null; washRemainder = null; washCakeLiquor = null; washEffluent = null;
						effluent = FluidStack.EMPTY;
					}
				}
				if (washCakeLiquor != null) liquor = washCakeLiquor;
				// Construct and hand off the output before mutating the tank. A sink
				// exception therefore leaves its input inventory intact and is not
				// reported as extracted material.
				ItemStack cake = new ItemStack(com.yu1745.chemicaladdon.registry.AllItems.MIXED_RESIDUE.get());
				MixedResidueItem.withEngineSolids(cake, extracted);
				if (liquor != null) MixedResidueItem.withEngineLiquor(cake, liquor);
				sink.accept(cake);
				if (liquorMb > 0) stack.shrink(liquorMb);
				Mixture.setEngineSolution(stack, next);
				if (washSource != null) {
					washSource.shrink(washMb);
					Mixture.setEngineSolution(washSource, washRemainder);
					if (filtrate.fill(effluent, FluidAction.EXECUTE) != washMb)
						throw new IllegalStateException("filtrate changed after native wash preflight");
				}
				for (var solid : extracted) extractedUnits += Math.round(solid.mol() * SOLID_UNITS_PER_MOL);
				changed = true;
			} catch (RuntimeException ex) {
				ChemicalAddon.LOGGER.warn("Engine cake extraction rejected: {}", ex.getMessage());
			}
		}
		if (changed) onChanged.run();
		return extractedUnits;
	}

		// ------------------------------------------------- basin domain transfers (C)

	/** Replace the whole fluid list for an atomic process write-back. */
	public void setFluids(List<FluidStack> stacks) {
		fluids.clear();
		fluids.addAll(stacks);
		removeEmpty();
		collapseIfNeeded();
		onChanged.run();
	}

	/** Total unit-grid mass of the suspended (slurry) domain across all mixtures. */
	public long suspendedUnits() {
		if (containsEngineBackedMixture()) return engineSolidUnits(KernelSolutionState.SolidLocation.SUSPENDED);
		return 0;
	}

	/** Total settled native solid inventory; rawless mixtures are not separable. */
	public long sedimentUnits() {
		return containsEngineBackedMixture() ? engineSolidUnits(KernelSolutionState.SolidLocation.SEDIMENT) : 0;
	}

	/** Native-only solid-location transfer. */
	private long transferDomain(boolean suspendedToSediment, long maxUnits, FluidAction action) {
		return containsEngineBackedMixture() ? transferEngineSolids(suspendedToSediment, maxUnits, action) : 0;
	}

	/** Settle suspended native solids into the sediment ledger. */
	public long settleSuspended(long maxUnits, FluidAction action) {
		return transferDomain(true, maxUnits, action);
	}

	/** Resuspend settled native solids for an overdrawn basin or underflow. */
	public long resuspendSediment(long maxUnits, FluidAction action) {
		return transferDomain(false, maxUnits, action);
	}

	/* Engine-owned solid ledger. One mol maps to the historical 1000 part basis,
	 * then to the unit grid; this conversion is used only for throughput/item
	 * denomination, never to reconstruct aqueous chemistry. */
	private static final double SOLID_UNITS_PER_MOL = 1000d * Chemistry.UNIT_PER_MB;
	private long engineSolidUnits(KernelSolutionState.SolidLocation location) {
		long total = 0;
		for (FluidStack stack : fluids) if (Mixture.isMixture(stack)) {
			KernelSolutionState state = Mixture.engineSolution(stack);
			if (state == null) continue;
			double factor = (double) stack.getAmount() / state.referenceMb();
			for (var solid : state.solids())
				if (solid.location() == location) total += Math.round(solid.mol() * factor * SOLID_UNITS_PER_MOL);
		}
		return total;
	}

	private long transferEngineSolids(boolean suspendedToSediment, long maxUnits, FluidAction action) {
		KernelSolutionState.SolidLocation from = suspendedToSediment
			? KernelSolutionState.SolidLocation.SUSPENDED : KernelSolutionState.SolidLocation.SEDIMENT;
		KernelSolutionState.SolidLocation to = suspendedToSediment
			? KernelSolutionState.SolidLocation.SEDIMENT : KernelSolutionState.SolidLocation.SUSPENDED;
		long available = engineSolidUnits(from), take = Math.min(maxUnits, available);
		if (take <= 0 || action.simulate()) return take;
		double fraction = (double) take / available;
		for (FluidStack stack : fluids) if (Mixture.isMixture(stack)) {
			KernelSolutionState state = Mixture.engineSolution(stack);
			if (state == null) continue;
			IPhreeqc q = Kernel.get();
			synchronized (q) { state = state.scale(q, stack.getAmount()); }
			List<KernelSolutionState.SolidPhase> changed = new ArrayList<>();
			for (var solid : state.solids()) {
				if (solid.location() != from) { changed.add(solid); continue; }
				double moved = solid.mol() * fraction;
				changed.add(new KernelSolutionState.SolidPhase(solid.speciesId(), solid.mol() - moved, from));
				changed.add(new KernelSolutionState.SolidPhase(solid.speciesId(), moved, to));
			}
			Mixture.setEngineSolution(stack, state.withSolids(changed));
		}
		onChanged.run();
		return take;
	}

				/**
	 * 施工包 C slurry-zone draw: a proportional sample of the liquid (molecules +
	 * ions) AND the suspended solids — what a surface lift that punches through
	 * the supernatant actually pulls. The settled bed (Sediment) stays put.
	 */
	public FluidStack drainSlurryZone(int maxDrainMb, FluidAction action) {
		if (containsEngineBackedMixture()) return engineDraw(maxDrainMb, action, true, false, false, Double.NaN);
		return FluidStack.EMPTY;
	}

	/**
	 * 施工包 C thickener underflow: pull the settled bed back into suspension at
	 * the target solids fraction — a pumpable concentrated sludge ({@code ~50% v/v})
	 * that the filter press splits again downstream. No bed → EMPTY (the
	 * underflow port runs dry until sludge accumulates).
	 */
	public FluidStack drainThickenedUnderflow(int maxDrainMb, double solidsFraction, FluidAction action) {
		if (containsEngineBackedMixture()) return engineDraw(maxDrainMb, action, false, true, false, solidsFraction);
		return FluidStack.EMPTY;
	}

	/** Native aqueous split plus an optional independent solid-location transfer. */
	private FluidStack engineDraw(int requestedMb, FluidAction action, boolean suspended, boolean sediment,
			boolean protectPore, double targetSolidsFraction) {
		FluidStack source = null;
		for (FluidStack stack : fluids) if (Mixture.isMixture(stack)) { source = stack; break; }
		if (source == null) return FluidStack.EMPTY;
		KernelSolutionState state = Mixture.engineSolution(source);
		if (state == null || source.getAmount() <= 0) return FluidStack.EMPTY;
		boolean hasAnySolid = !state.solids().isEmpty();
		int maxDraw = hasAnySolid ? source.getAmount() - 1 : source.getAmount();
		int totalVolume = Math.min(requestedMb, maxDraw);
		if (totalVolume <= 0) return FluidStack.EMPTY;
		KernelSolutionState.SolidLocation location = sediment ? KernelSolutionState.SolidLocation.SEDIMENT
			: KernelSolutionState.SolidLocation.SUSPENDED;
		long localSolidUnits = 0;
		double materialized = (double) source.getAmount() / state.referenceMb();
		if (suspended || sediment) for (var solid : state.solids()) if (solid.location() == location)
			localSolidUnits += Math.round(solid.mol() * materialized * SOLID_UNITS_PER_MOL);
		long solidMoveUnits = 0;
		if (suspended || sediment) {
			if (sediment && Double.isFinite(targetSolidsFraction)) {
				double f = Math.max(0d, Math.min(0.999999d, targetSolidsFraction));
				solidMoveUnits = Math.min(localSolidUnits, Math.round(totalVolume * Chemistry.UNIT_PER_MB * f));
				// The bed may not contain enough solids for the requested volume. Keep
				// the requested concentration by shortening the tail batch instead of
				// diluting it with arbitrary mother liquor.
				if (f > 0 && solidMoveUnits < Math.round(totalVolume * Chemistry.UNIT_PER_MB * f))
					totalVolume = Math.min(totalVolume, (int) Math.floor(solidMoveUnits / (f * Chemistry.UNIT_PER_MB)));
			} else {
				solidMoveUnits = Math.min(localSolidUnits,
					Math.round((double) localSolidUnits * totalVolume / source.getAmount()));
			}
		}
		// Suspended slurry is a liquid carrier with an independent solid ledger:
		// transporting half its solids does not consume half its Forge fluid volume.
		// Only a deliberately thickened sediment underflow allocates its requested
		// volume between liquor and solids.
		int solidVolume = sediment && Double.isFinite(targetSolidsFraction)
			? (int) Math.min(totalVolume, Math.round((double) solidMoveUnits / Chemistry.UNIT_PER_MB)) : 0;
		int liquorVolume = totalVolume - solidVolume;
		if (protectPore) {
			int clear = clearLiquidAvailable();
			if (liquorVolume > clear) {
				// Keep the requested slurry ratio while respecting the retained pore liquor.
				if (totalVolume == 0) return FluidStack.EMPTY;
				totalVolume = (int) Math.floor((double) totalVolume * clear / liquorVolume);
				solidVolume = (int) Math.min(totalVolume, Math.round((double) solidMoveUnits * totalVolume / Math.max(1, requestedMb) / Chemistry.UNIT_PER_MB));
				liquorVolume = totalVolume - solidVolume;
			}
		}
		// A mixture FluidStack must never carry a copied full RAW state with zero
		// mother liquor. Pure solids leave through extractEngineSolids instead.
		if (totalVolume <= 0 || liquorVolume <= 0) return FluidStack.EMPTY;
		try {
			IPhreeqc q = Kernel.get();
			synchronized (q) {
				// First materialise any Create-style proportional copy. Subsequent
				// liquor scales are relative to this actual vessel inventory, never
				// to a stale NBT reference amount.
				KernelSolutionState actual = state.scale(q, source.getAmount());
				// RAW tracks aqueous mother liquor only; referenceMb tracks the physical
				// transport volume including its accompanying solid domain.
				KernelSolutionState liquorRaw = liquorVolume > 0 ? actual.scale(q, liquorVolume) : null;
				KernelSolutionState remainderRaw = source.getAmount() > totalVolume
					? actual.scale(q, source.getAmount() - liquorVolume) : null;
				List<KernelSolutionState.SolidPhase> outSolids = new ArrayList<>();
				List<KernelSolutionState.SolidPhase> remainSolids = new ArrayList<>();
				double solidFraction = localSolidUnits == 0 ? 0 : (double) solidMoveUnits / localSolidUnits;
				for (var solid : actual.solids()) {
					boolean moves = suspended && solid.location() == KernelSolutionState.SolidLocation.SUSPENDED
							|| sediment && solid.location() == KernelSolutionState.SolidLocation.SEDIMENT;
					double actualMol = solid.mol();
					if (moves) {
						double moved = actualMol * solidFraction;
						// A thickener underflow physically reslurries settled material so
						// the downstream filter press sees a suspended sludge domain.
						KernelSolutionState.SolidLocation outputLocation = sediment
							? KernelSolutionState.SolidLocation.SUSPENDED : solid.location();
						outSolids.add(new KernelSolutionState.SolidPhase(solid.speciesId(), moved, outputLocation));
						remainSolids.add(new KernelSolutionState.SolidPhase(solid.speciesId(), actualMol - moved, solid.location()));
					} else remainSolids.add(new KernelSolutionState.SolidPhase(solid.speciesId(), actualMol, solid.location()));
				}
				KernelSolutionState liquor = new KernelSolutionState(liquorRaw.raw(), totalVolume, outSolids);
				KernelSolutionState remainder = remainderRaw == null ? null : new KernelSolutionState(remainderRaw.raw(),
					source.getAmount() - totalVolume, remainSolids);
				FluidStack out = new FluidStack(Mixture.fluid(), totalVolume);
				Mixture.setMolecules(out, Map.of(Solution.WATER, (long) liquorVolume));
				Mixture.setEngineSolution(out, liquor);
				Temperature.set(out, Temperature.get(source));
				if (action.execute()) {
					if (remainder == null) fluids.remove(source);
					else {
						source.setAmount(source.getAmount() - totalVolume);
						Mixture.setEngineSolution(source, remainder);
					}
					onChanged.run();
				}
				return out;
			}
		} catch (RuntimeException ex) {
			ChemicalAddon.LOGGER.warn("Rejected engine liquid split: {}", ex.getMessage());
			return FluidStack.EMPTY;
		}
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
	/**
	 * Fine-grid write-back (U18, the rules engine's path): same contract as the
	 * unit-aware {@link #setContents} overload but on the quanta scale (mB ×
	 * {@link Chemistry#QUANTA_PER_MB}) in long maps — sub-unit equilibrium
	 * residuals survive the round trip in the ratio tag instead of being
	 * truncated at every solve.
	 */
	public void setContentsLong(Map<ResourceLocation, Long> molecules, Map<String, Long> ions,
		Map<ResourceLocation, Long> suspended, Map<ResourceLocation, Long> sediment, int temperature, long scale) {
		fluids.clear();
		molecules.values().removeIf(v -> v <= 0);
		ions.values().removeIf(v -> v <= 0);
		suspended.values().removeIf(v -> v <= 0);
		sediment.values().removeIf(v -> v <= 0);
		repairTraceChargeImbalanceLong(ions);
		if (molecules.isEmpty() && ions.isEmpty() && suspended.isEmpty() && sediment.isEmpty()) {
			onChanged.run();
			return;
		}
		FluidStack target;
		if (molecules.size() == 1 && ions.isEmpty() && suspended.isEmpty() && sediment.isEmpty()) {
			Map.Entry<ResourceLocation, Long> only = molecules.entrySet().iterator().next();
			Fluid pf = ForgeRegistries.FLUIDS.getValue(only.getKey());
			if (pf == null || pf == Fluids.EMPTY) {
				onChanged.run();
				return;
			}
			target = new FluidStack(pf, (int) Math.max(1, only.getValue() / scale));
		} else {
			long totalUnits = 0;
			for (long v : molecules.values()) totalUnits += v;
			for (long v : ions.values()) totalUnits += v;
			for (long v : suspended.values()) totalUnits += v;
			for (long v : sediment.values()) totalUnits += v;
			int totalMb = (int) Math.round((double) totalUnits / scale);
			target = Mixture.createLong(molecules, ions, suspended, sediment, Math.max(1, totalMb));
		}
		Temperature.set(target, temperature);
		fluids.add(target);
		onChanged.run();
	}

	/** Long-domain twin of {@link #repairTraceChargeImbalance} (same contract). */
	private static void repairTraceChargeImbalanceLong(Map<String, Long> ions) {
		long imbalance = 0;
		for (Map.Entry<String, Long> e : ions.entrySet()) {
			imbalance += (long) com.yu1745.chemicaladdon.composition.Ion.chargeOf(e.getKey()) * e.getValue();
		}
		int guard = 0;
		while (imbalance != 0 && guard++ < 64) {
			String pick = null;
			for (Map.Entry<String, Long> e : ions.entrySet()) {
				int q = com.yu1745.chemicaladdon.composition.Ion.chargeOf(e.getKey());
				if ((imbalance > 0 && q > 0) || (imbalance < 0 && q < 0)
					&& (pick == null || e.getValue() > ions.get(pick))) {
					pick = e.getKey();
				}
			}
			if (pick == null) {
				break;
			}
			if (ions.merge(pick, -1L, Long::sum) <= 0) {
				ions.remove(pick);
			}
			imbalance -= com.yu1745.chemicaladdon.composition.Ion.chargeOf(pick);
		}
	}

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

	public CompoundTag serializeNBT() {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		for (FluidStack f : fluids) {
			// Forge FluidStack.writeToNBT retains its tag by reference. Candidate
			// transactions mutate EngineSolutionRaw, so persistence needs its own
			// deep snapshot rather than an alias of the source stack's NBT.
			list.add(f.writeToNBT(new CompoundTag()).copy());
		}
		tag.put("fluids", list);
		return tag;
	}

	public void deserializeNBT(CompoundTag tag) {
		fluids.clear();
		ListTag list = tag.getList("fluids", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			// Forge loadFluidStackFromNBT retains the nested Tag as well; callers'
			// persistence tags must never become mutable tank state.
			FluidStack f = FluidStack.loadFluidStackFromNBT(list.getCompound(i).copy());
			// There is no legacy reconstruction path.  The four display domains
			// deliberately cannot recreate a PHREEQC state (redox, complexes and
			// charge pools would be invented).  A pre-release save carrying an old
			// mixture is rejected visibly instead of being accepted and silently
			// changing chemistry on its first tick.
			if (Mixture.isMixture(f) && Mixture.engineSolution(f) == null) {
				ChemicalAddon.LOGGER.error("Rejected legacy mixture without EngineSolutionRaw while loading reactor tank");
				continue;
			}
			if (!f.isEmpty()) {
				fluids.add(f);
			}
		}
	}
}
