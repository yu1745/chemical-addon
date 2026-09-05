package com.yu1745.chemicaladdon.gametest;

import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.composition.parity.EngineBridge;
import com.yu1745.chemicaladdon.composition.parity.Kernel;
import com.yu1745.chemicaladdon.composition.parity.KernelSolutionState;
import com.yu1745.chemicaladdon.composition.Solution;
import com.yu1745.chemicaladdon.fluid.FluidColors;
import com.yu1745.chemicaladdon.fluid.IonColors;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.fluid.Temperature;
import com.yu1745.chemicaladdon.reactor.ReactorTank;
import com.yu1745.chemicaladdon.reactor.SpillLogic;
import com.yu1745.chemicaladdon.registry.AllContainers;
import com.yu1745.chemicaladdon.registry.AllFluids;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ChemicalAddon.MODID)
@PrefixGameTestTemplate(false)
public class CompositionGameTests {

	private static final int TICKS = 20;

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void collapseDoesNotChurnMixtureRatio(GameTestHelper helper) {
		// regression: collapseIfNeeded on a multi-phase tank used to rebuild the
		// mixture every tick (derive amounts -> Mixture.create GCD-reduce), churning
		// its ratio tag whenever the total isn't divisible by the ratio sum — which
		// broke Create's isFluidEqual flow identity and stalled the pump. A settled
		// phase must be left verbatim.
		ReactorTank tank = new ReactorTank(10000, () -> {});
		// The native state has an explicit 1601 mB reference. A collapse must not
		// reconstruct it from the derived display projection.
		FluidStack mix = GameTestFixtures.declared(1, Map.of("H2SO4", .2), 1601);
		tank.fill(mix, FluidAction.EXECUTE);
		tank.fill(new FluidStack(AllFluids.THERMAL_OIL.get().getSource(), 500), FluidAction.EXECUTE);
		tank.collapseIfNeeded();

		net.minecraft.nbt.CompoundTag before = tank.getFluids().get(0).getOrCreateTag().copy();
		tank.collapseIfNeeded();
		net.minecraft.nbt.CompoundTag after = tank.getFluids().get(0).getOrCreateTag();

		helper.assertTrue(before.equals(after),
			"collapseIfNeeded must not churn a settled mixture's ratio tag (before=" + before + " after=" + after + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void solventWaterContributesNoColor(GameTestHelper helper) {
		// water is the colourless solvent: it must not tint a mixture. The old
		// saturated blue would dominate any blend and hide the solute's colour.
		ResourceLocation water = Solution.WATER;
		ResourceLocation co2 = new ResourceLocation(ChemicalAddon.MODID, "carbon_dioxide");
		FluidStack mix = Mixture.create(Map.of(water, 900, co2, 100), 1000);
		int color = Mixture.getColor(mix);
		int co2Color = FluidColors.of(co2);
		helper.assertTrue(color == co2Color,
			"solvent water must contribute no colour (got " + Integer.toHexString(color)
				+ ", want " + Integer.toHexString(co2Color) + ")");

		// pure solvent (water only) has nothing coloured -> faint white (no tint):
		// clear water must NOT read as opaque white (or a white precipitate CaCO3
		// would be indistinguishable), but also not fully transparent (the liquid
		// surface must stay visible).
		FluidStack pure = Mixture.create(Map.of(water, 1000), 1000);
		helper.assertTrue(Mixture.getColor(pure) == IonColors.CLEAR_TINT,
			"pure solvent water should render faint white (got " + Integer.toHexString(Mixture.getColor(pure)) + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void dilutionFadesTintDepth(GameTestHelper helper) {
		// concentration must read visually: same coloured solute, more water → same
		// hue but lower alpha (faint when dilute, full at the canonical 10:1
		// solventRatio concentration). Colourless solvent still contributes no hue.
		ResourceLocation water = Solution.WATER;
		ResourceLocation no2 = new ResourceLocation(ChemicalAddon.MODID, "nitrogen_dioxide");
		int full = FluidColors.of(no2);

		// saturated (solute fraction 1/11, the canonical solventRatio): full depth
		FluidStack sat = Mixture.create(Map.of(water, 1000, no2, 100), 1100);
		int satColor = Mixture.getColor(sat);
		helper.assertTrue(satColor == full,
			"saturated solution should render full-depth (got " + Integer.toHexString(satColor)
				+ ", want " + Integer.toHexString(full) + ")");

		// tenfold dilution (solute fraction 1/110): same RGB, strictly fainter alpha
		FluidStack dilute = Mixture.create(Map.of(water, 1000, no2, 10), 1010);
		int dilColor = Mixture.getColor(dilute);
		helper.assertTrue((dilColor & 0x00FFFFFF) == (full & 0x00FFFFFF),
			"dilution must not shift the hue (got " + Integer.toHexString(dilColor)
				+ ", want RGB of " + Integer.toHexString(full) + ")");
		int satA = (satColor >>> 24) & 0xFF, dilA = (dilColor >>> 24) & 0xFF;
		helper.assertTrue(dilA < satA,
			"dilute tint must be fainter than saturated (dilute alpha " + dilA + " vs " + satA + ")");
		helper.assertTrue(dilA > ((IonColors.CLEAR_TINT >>> 24) & 0xFF),
			"trace solute must still be more visible than pure solvent (alpha " + dilA + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void copperSulfateSolutionRendersBlue(GameTestHelper helper) {
		// CuSO4 dissolves into Cu+2 (blue) + SO4-2 (colourless): the mixture must
		// render the ion blue at full depth when saturated, fade when diluted, and
		// the colourless counter-ion must not wash the blue out.
		ResourceLocation water = Solution.WATER;
		int blue = IonColors.of("Cu+2");

		// canonical solventRatio 10:1 — Cu+2 fraction 1/11 → full depth, pure hue
		FluidStack sat = Mixture.create(Map.of(water, 1000), Map.of("Cu+2", 100, "SO4-2", 100), 1200);
		helper.assertTrue(Mixture.getColor(sat) == blue,
			"saturated CuSO4 must render full Cu+2 blue (got " + Integer.toHexString(Mixture.getColor(sat))
				+ ", want " + Integer.toHexString(blue) + ")");

		// tenfold dilution: same blue hue, strictly fainter alpha
		FluidStack dilute = Mixture.create(Map.of(water, 1000), Map.of("Cu+2", 10, "SO4-2", 10), 1020);
		int dilColor = Mixture.getColor(dilute);
		helper.assertTrue((dilColor & 0x00FFFFFF) == (blue & 0x00FFFFFF),
			"dilution must not shift the Cu+2 hue (got " + Integer.toHexString(dilColor) + ")");
		helper.assertTrue(((dilColor >>> 24) & 0xFF) < ((blue >>> 24) & 0xFF),
			"dilute CuSO4 must render fainter than saturated");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void mixtureDegradesToPure(GameTestHelper helper) {
		// Native declared-feed withdrawal leaves an engine-backed aqueous solvent
		// state; the mixture transport identity remains stable after the solute drain.
		ReactorTank tank = new ReactorTank(10000, () -> {});
		// A high-precision native observation is a normal preceding vessel action.
		// Its session settings must not prevent the following exact transaction.
		KernelSolutionState solventObservation = KernelSolutionState.fromDeclaredFeed(Kernel.get(), 1, Map.of(), 1000);
		EngineBridge.derive(Kernel.get(), solventObservation, List.of());
		// Exercise the shared session recovery before this exact ingress chain:
		// malformed external declaration may never poison a later vessel's RAW.
		boolean rejected = false;
		try {
			KernelSolutionState.fromDeclaredFeed(Kernel.get(), .6, Map.of("NotARealFormula", .1), 900);
		} catch (RuntimeException expected) {
			rejected = true;
		}
		helper.assertTrue(rejected, "invalid declared formula must be rejected");
		FluidStack mix = GameTestFixtures.declared(.6, Map.of("H2SO4", .1), 900);
		tank.fill(mix, FluidAction.EXECUTE);
		// consume all the acid ions (the path completeRecipe uses)
		int drained = tank.drainSolution(new ResourceLocation(ChemicalAddon.MODID, "sulfuric_acid"), 300,
			FluidAction.EXECUTE);
		helper.assertTrue(drained == 300, "should drain 300 mB of acid ions (got " + drained + ")");
		tank.collapseIfNeeded();

		helper.assertTrue(tank.getFluids().size() == 1, "one native stack remains (got " + tank.getFluids().size() + ")");
		FluidStack remain = tank.getFluids().get(0);
		helper.assertTrue(Mixture.isMixture(remain) && Mixture.engineSolution(remain) != null,
			"native solvent remainder must retain its raw state");
		helper.assertTrue(tank.countSolution(new ResourceLocation(ChemicalAddon.MODID, "sulfuric_acid")) == 0,
			"the declared acid must be absent after the native removal");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void mixtureSpillsAsPureComponents(GameTestHelper helper) {
		// Native liquor cannot be converted to a world water block without losing
		// complexes and solid ledger. The spill queue retains its exact RAW payload
		// until SpillLogic releases recoverable wet-residue entities.
		ReactorTank tank = new ReactorTank(10000, () -> {});
		FluidStack mix = GameTestFixtures.declared(2, Map.of("H2SO4", .2), 2600);
		tank.fill(mix, FluidAction.EXECUTE);
		helper.assertTrue(Mixture.isMixture(tank.getFluids().get(0)), "baseline: tank holds a mixture");

		List<FluidStack> spilled = SpillLogic.queueFluids(tank);
		helper.assertTrue(tank.getFluids().isEmpty(), "the spill must empty the tank");
		helper.assertTrue(!spilled.isEmpty(), "the mixture must enter the spill queue");
		for (FluidStack s : spilled) {
			helper.assertTrue(Mixture.isMixture(s) && Mixture.engineSolution(s) != null,
				"spill queue must retain the native solution state for residue recovery");
		}
		// Reinsert the queued payload without deriving it through the display domains.
		for (FluidStack s : spilled) {
			tank.fill(s.copy(), FluidAction.EXECUTE);
		}
		tank.collapseIfNeeded();
		helper.assertTrue(tank.getFluids().size() == 1 && Mixture.engineSolution(tank.getFluids().get(0)) != null,
			"recovery must restore the native aqueous state");
		helper.assertTrue(tank.waterInventoryMb() >= 1900,
			"recovery must retain the mother-liquor water inventory");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void temperatureBlendsOnMerge(GameTestHelper helper) {
		// pouring 40 °C and 20 °C water into the same vessel blends to the
		// amount-weighted average: (40×1000 + 20×1000) / 2000 = 30 °C
		ReactorTank tank = new ReactorTank(10000, () -> {});
		FluidStack hot = GameTestFixtures.declared(1, Map.of(), 1000);
		Temperature.set(hot, 40);
		FluidStack cold = GameTestFixtures.declared(1, Map.of(), 1000);
		Temperature.set(cold, 20);

		tank.fill(hot, FluidAction.EXECUTE);
		tank.fill(cold, FluidAction.EXECUTE);
		helper.assertTrue(tank.getFluids().size() == 1, "same-species fluids should stack into one entry");
		helper.assertTrue(tank.getTotalAmount() == 2000, "amounts should sum (got " + tank.getTotalAmount() + ")");
		helper.assertTrue(Temperature.get(tank.getFluids().get(0)) == 30,
			"temperature should blend to 30 °C (got " + Temperature.get(tank.getFluids().get(0)) + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void temperatureSurvivesTransfer(GameTestHelper helper) {
		// a pump-style drain carries the fluid's temperature (frozen) so the next
		// vessel can continue heating/cooling from where the last one left off
		ReactorTank src = new ReactorTank(10000, () -> {});
		FluidStack hot = GameTestFixtures.declared(1, Map.of(), 1000);
		Temperature.set(hot, 40);
		src.fill(hot, FluidAction.EXECUTE);

		FluidStack drained = src.drain(500, FluidAction.EXECUTE);
		helper.assertTrue(Temperature.get(drained) == 40,
			"drained sample must carry its temperature (frozen) (got " + Temperature.get(drained) + ")");

		ReactorTank dest = new ReactorTank(10000, () -> {});
		dest.fill(drained.copy(), FluidAction.EXECUTE);
		helper.assertTrue(Temperature.get(dest.getFluids().get(0)) == 40,
			"temperature must survive the transfer into another vessel");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vialPreservesFluidNbt(GameTestHelper helper) {
		// the sample vial's FluidHandlerItemStack must round-trip the whole FluidStack
		// (temperature + mixture NBT), unlike a standard BucketItem which is tag-less
		ItemStack vial = AllContainers.FLUID_VIAL.asStack();
		IFluidHandlerItem vialHandler = vial.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
		helper.assertTrue(vialHandler != null, "vial must expose FLUID_HANDLER_ITEM");

		FluidStack hot = new FluidStack(Fluids.WATER, 1000);
		Temperature.set(hot, 40);
		int filled = vialHandler.fill(hot, FluidAction.EXECUTE);
		helper.assertTrue(filled == 1000, "vial should fill 1000 mB (got " + filled + ")");

		FluidStack back = vialHandler.getFluidInTank(0);
		helper.assertTrue(back.getFluid() == Fluids.WATER, "vial should hold water");
		helper.assertTrue(Temperature.get(back) == 40,
			"temperature must survive the vial round-trip (got " + Temperature.get(back) + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void solutionBucketPacksMixture(GameTestHelper helper) {
		// the creative "packed mixture" bucket pre-fills its default instance with a
		// solution mode's ion signature + water (solutions are not registered fluids)
		ItemStack bucket = AllContainers.SOLUTION_BUCKETS.get(0).get().getDefaultInstance(); // sulfuric_acid
		IFluidHandlerItem handler = bucket.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
		helper.assertTrue(handler != null, "bucket must expose FLUID_HANDLER_ITEM");

		FluidStack fluid = handler.getFluidInTank(0);
		helper.assertTrue(!fluid.isEmpty() && Mixture.isMixture(fluid), "bucket should hold a mixture");
		Map<String, Integer> ions = Mixture.deriveIonAmounts(fluid);
		helper.assertTrue(ions.getOrDefault("H+1", 0) > 0 && ions.getOrDefault("SO4-2", 0) > 0,
			"sulfuric acid bucket should hold H+ + SO4-- ions (got " + ions + ")");
		helper.assertTrue(Mixture.deriveAmounts(fluid).containsKey(Solution.WATER),
			"bucket should hold the solvent water");
		helper.assertTrue(fluid.getAmount() == 1000, "bucket should hold 1000 mB (got " + fluid.getAmount() + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void slurryBucketPacksSuspendedSolid(GameTestHelper helper) {
		// A slurry bucket carries a native liquor state plus its suspended-solid
		// ledger. Equilibrium may expose dissolved ions in the display projection.
		ItemStack bucket = AllContainers.SLURRY_BUCKETS.get(0).get().getDefaultInstance(); // milk_of_lime
		IFluidHandlerItem handler = bucket.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
		helper.assertTrue(handler != null, "bucket must expose FLUID_HANDLER_ITEM");

		FluidStack fluid = handler.getFluidInTank(0);
		helper.assertTrue(!fluid.isEmpty() && Mixture.isMixture(fluid), "bucket should hold a mixture");
		ResourceLocation slakedLime = new ResourceLocation(ChemicalAddon.MODID, "slaked_lime");
		var state = Mixture.engineSolution(fluid);
		helper.assertTrue(state != null, "bucket must carry an engine-owned liquor state");
		helper.assertTrue(state != null && state.solids().stream().anyMatch(s -> s.speciesId().equals(slakedLime.toString())
			&& s.location() == com.yu1745.chemicaladdon.composition.parity.KernelSolutionState.SolidLocation.SUSPENDED),
			"milk_of_lime should retain suspended slaked lime in the native ledger");
		helper.assertTrue(state != null && state.referenceMb() == fluid.getAmount(),
			"native liquor reference must match the bucket transport amount");
		helper.assertTrue(fluid.getAmount() == 1000, "bucket should hold 1000 mB (got " + fluid.getAmount() + ")");
		helper.succeed();
	}

}
