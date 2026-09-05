package com.yu1745.chemicaladdon.gametest;

import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.composition.parity.EngineBridge;
import com.yu1745.chemicaladdon.composition.parity.Kernel;
import com.yu1745.chemicaladdon.composition.parity.KernelSolutionState;
import com.yu1745.chemicaladdon.composition.parity.PhaseBridge;
import com.yu1745.chemicaladdon.composition.parity.TickDriver;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.item.MixedResidueItem;
import com.yu1745.chemicaladdon.reactor.ReactorTank;
import com.yu1745.chemicaladdon.reactor.SpillLogic;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Conservation tests for RAW/solid separation transactions. */
@GameTestHolder(ChemicalAddon.MODID)
@PrefixGameTestTemplate(false)
public class NativeSeparationGameTests {
	@GameTest(template = "empty_15", timeoutTicks = 200)
	public static void proportionalCopySlurryDrawConservesActualSolid(GameTestHelper helper) {
		FluidStack copied = GameTestFixtures.declaredSolid(2, Map.of("NaCl", .1), 2000,
			"chemicaladdon:sodium_bicarbonate", 2, KernelSolutionState.SolidLocation.SUSPENDED);
		copied.setAmount(1000); // NBT reference remains 2000: pipe-copy case
		ReactorTank tank = new ReactorTank(2000, () -> {}); tank.fill(copied, FluidAction.EXECUTE);
		FluidStack beforeStack = tank.getFluids().get(0);
		double before = solidMol(beforeStack); NativeInventory beforeInventory = inventory(beforeStack);
		FluidStack out = tank.drainSlurryZone(500, FluidAction.EXECUTE);
		helper.assertTrue(out.getAmount() == 500, "requested total output volume");
		helper.assertTrue(Math.abs(before - solidMol(out) - solidMol(tank.getFluids().get(0))) < 1e-9,
			"out + remaining actual solid inventory is conserved");
		assertInventory(helper, beforeInventory, inventory(out), inventory(tank.getFluids().get(0)),
			"slurry draw preserves native liquor");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 200)
	public static void wetCakePayloadRejoinsLiquorAndSolidWithoutLoss(GameTestHelper helper) {
		ReactorTank tank = new ReactorTank(2000, () -> {});
		FluidStack copied = GameTestFixtures.declaredSolid(2, Map.of("NaCl", .1), 2000,
			"chemicaladdon:sodium_bicarbonate", 2, KernelSolutionState.SolidLocation.SUSPENDED);
		copied.setAmount(1000); // Create-style proportional copy: RAW reference remains 2000 mB.
		tank.fill(copied, FluidAction.EXECUTE);
		FluidStack beforeStack = tank.getFluids().get(0);
		double before = solidMol(beforeStack); NativeInventory beforeInventory = inventory(beforeStack); List<ItemStack> cakes = new ArrayList<>();
		tank.extractSolids(cakes::add, false);
		ItemStack cake = cakes.get(0); double cakeMol = MixedResidueItem.engineSolids(cake).stream().mapToDouble(KernelSolutionState.SolidPhase::mol).sum();
		helper.assertTrue(Math.abs(before - cakeMol - solidMol(tank.getFluids().get(0))) < 1e-9, "cake solid ledger is conserved");
		KernelSolutionState liquor = MixedResidueItem.engineLiquor(cake);
		helper.assertTrue(liquor != null, "wet cake carries exact mother-liquor RAW");
		assertInventory(helper, beforeInventory, inventory(liquor), inventory(tank.getFluids().get(0)),
			"wet cake liquor preserves native water and Na/Cl totals");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 200)
	public static void nativeWashDilutesCakeAndConservesRawInventory(GameTestHelper helper) {
		ReactorTank source = new ReactorTank(2000, () -> {});
		ReactorTank wash = new ReactorTank(2000, () -> {});
		ReactorTank filtrate = new ReactorTank(2000, () -> {});
		source.fill(GameTestFixtures.declaredSolid(1, Map.of("NaCl", .1), 1000,
			"chemicaladdon:sodium_bicarbonate", 1, KernelSolutionState.SolidLocation.SUSPENDED), FluidAction.EXECUTE);
		wash.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), FluidAction.EXECUTE);
		NativeInventory beforeSource = inventory(source.getFluids().get(0));
		NativeInventory beforeWash = inventory(wash.getFluids().get(0));
		List<ItemStack> cakes = new ArrayList<>();
		source.extractSolids(cakes::add, false, wash, filtrate);
		helper.assertTrue(cakes.size() == 1, "native press emits one wet cake");
		NativeInventory cake = inventory(MixedResidueItem.engineLiquor(cakes.get(0)));
		helper.assertTrue(cake.naMol() < beforeSource.naMol() * .31,
			"one pore-volume wash substantially dilutes retained cake sodium");
		NativeInventory sourceAfter = inventory(source.getFluids().get(0));
		NativeInventory washAfter = inventory(wash.getFluids().get(0));
		NativeInventory filtrateAfter = inventory(filtrate.getFluids().get(0));
		helper.assertTrue(close(beforeSource.waterKg() + beforeWash.waterKg(),
			cake.waterKg() + sourceAfter.waterKg() + washAfter.waterKg() + filtrateAfter.waterKg())
			&& close(beforeSource.naMol(), cake.naMol() + sourceAfter.naMol() + washAfter.naMol() + filtrateAfter.naMol()),
			"native wash conserves total water and sodium across cake/source/wash/filtrate");
		double cakeSolid = MixedResidueItem.engineSolids(cakes.get(0)).stream().mapToDouble(KernelSolutionState.SolidPhase::mol).sum();
		helper.assertTrue(close(1d, cakeSolid + solidMol(source.getFluids().get(0))
			+ solidMol(wash.getFluids().get(0)) + solidMol(filtrate.getFluids().get(0))),
			"native wash conserves solid inventory without leaking cake solids into filtrate");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 200)
	public static void nativeWashWithoutFiltrateLeavesWashAndCakeLiquorUntouched(GameTestHelper helper) {
		ReactorTank source = new ReactorTank(2000, () -> {});
		ReactorTank wash = new ReactorTank(2000, () -> {});
		source.fill(GameTestFixtures.declaredSolid(1, Map.of("NaCl", .1), 1000,
			"chemicaladdon:sodium_bicarbonate", 1, KernelSolutionState.SolidLocation.SUSPENDED), FluidAction.EXECUTE);
		wash.fill(new FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000), FluidAction.EXECUTE);
		NativeInventory washBefore = inventory(wash.getFluids().get(0));
		List<ItemStack> cakes = new ArrayList<>();
		source.extractSolids(cakes::add, false, wash, null);
		helper.assertTrue(cakes.size() == 1, "dry filtration may still produce its unwashed cake");
		NativeInventory washAfter = inventory(wash.getFluids().get(0));
		NativeInventory cake = inventory(MixedResidueItem.engineLiquor(cakes.get(0)));
		helper.assertTrue(close(washBefore.waterKg(), washAfter.waterKg()) && close(washBefore.naMol(), washAfter.naMol()),
			"missing filtrate leaves native wash inventory untouched");
		helper.assertTrue(cake.naMol() > .02,
			"without a filtrate transaction the original salty pore liquor remains on the cake");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 200)
	public static void nativeSpillQueuePreservesRawInventory(GameTestHelper helper) {
		FluidStack copied = GameTestFixtures.declaredSolid(2, Map.of("NaCl", .1), 2000,
			"chemicaladdon:sodium_bicarbonate", 1, KernelSolutionState.SolidLocation.SUSPENDED);
		copied.setAmount(750);
		NativeInventory before = inventory(copied);
		List<FluidStack> pending = SpillLogic.queueFluids(List.of(copied));
		helper.assertTrue(pending.size() == 1 && pending.get(0).getAmount() == 750,
			"sub-bucket native spill remains queued as one RAW batch");
		helper.assertTrue(Mixture.engineSolution(pending.get(0)) != null, "spill queue retains RAW state");
		assertInventory(helper, before, inventory(pending.get(0)), new NativeInventory(0, 0, 0),
			"spill queue preserves native water and Na/Cl totals");
		helper.succeed();
	}

	/** Native redox products must remain physical ledger entries from precipitation through settling and cake extraction. */
	@GameTest(template = "empty_15", timeoutTicks = 200)
	public static void iodineManganeseAndSulfurNativePhasesSurviveSeparation(GameTestHelper helper) {
		assertNativePhase(helper, "iodine", "I2(cr)");
		assertNativePhase(helper, "manganese_dioxide", "MnO2(s)");
		assertNativePhase(helper, "sulfur", "EngineSulfur");
		List<KernelSolutionState.SolidPhase> products = List.of(
			new KernelSolutionState.SolidPhase("chemicaladdon:iodine", 3, KernelSolutionState.SolidLocation.SUSPENDED),
			new KernelSolutionState.SolidPhase("chemicaladdon:manganese_dioxide", 6, KernelSolutionState.SolidLocation.SUSPENDED),
			new KernelSolutionState.SolidPhase("chemicaladdon:sulfur", 9, KernelSolutionState.SolidLocation.SUSPENDED));
		FluidStack source = GameTestFixtures.declaredSolid(1, Map.of("NaCl", .1), 3000, products);
		FluidStack restored = FluidStack.loadFluidStackFromNBT(source.writeToNBT(new net.minecraft.nbt.CompoundTag()).copy());
		helper.assertTrue(Mixture.engineSolution(restored) != null, "NBT round trip retains the engine-owned state");
		ReactorTank tank = new ReactorTank(3000, () -> {});
		helper.assertTrue(tank.fill(restored, FluidAction.EXECUTE) == 3000, "all serialized native product liquor enters the vessel");
		FluidStack sample = tank.drainSlurryZone(1000, FluidAction.EXECUTE);
		Map<String, Double> oneThird = Map.of("chemicaladdon:iodine", 1d,
			"chemicaladdon:manganese_dioxide", 2d, "chemicaladdon:sulfur", 3d);
		for (var expected : oneThird.entrySet()) {
			helper.assertTrue(close(expected.getValue(), solidMol(sample, expected.getKey()))
				&& close(expected.getValue() * 2, solidMol(tank.getFluids().get(0), expected.getKey())),
				"one-third slurry draw preserves the separate native ledger entry for " + expected.getKey());
		}
		helper.assertTrue(tank.settleSuspended(Long.MAX_VALUE, FluidAction.EXECUTE) > 0,
			"native redox solids settle into the sediment ledger");
		List<ItemStack> cakes = new ArrayList<>();
		tank.extractSolids(cakes::add, true);
		helper.assertTrue(cakes.size() == 1, "settled native redox products form one recoverable cake");
		List<KernelSolutionState.SolidPhase> extracted = MixedResidueItem.engineSolids(cakes.get(0));
		helper.assertTrue(extracted.size() == 3
			&& extracted.stream().allMatch(s -> s.location() == KernelSolutionState.SolidLocation.SEDIMENT)
			&& extracted.stream().map(KernelSolutionState.SolidPhase::speciesId).collect(java.util.stream.Collectors.toSet())
				.equals(java.util.Set.of("chemicaladdon:iodine", "chemicaladdon:manganese_dioxide", "chemicaladdon:sulfur")),
			"cake keeps each native product identity and its sediment location");
		for (var expected : oneThird.entrySet()) {
			helper.assertTrue(close(expected.getValue() * 2, extracted.stream().filter(s -> s.speciesId().equals(expected.getKey()))
				.mapToDouble(KernelSolutionState.SolidPhase::mol).sum()),
				"filter cake preserves the separate native ledger entry for " + expected.getKey());
		}
		helper.succeed();
	}

	private static void assertNativePhase(GameTestHelper helper, String species, String expectedPhase) {
		var def = PhaseBridge.def(new net.minecraft.resources.ResourceLocation(ChemicalAddon.MODID, species));
		helper.assertTrue(def != null && expectedPhase.equals(def.phaseName()) && def.equation() == null,
			"species " + species + " uses the database native phase " + expectedPhase);
	}

	/** A kinetic sulfide oxidation must materialize elemental sulfur in the game-owned native-solid ledger. */
	@GameTest(template = "empty_15", timeoutTicks = 200)
	public static void hypochloriteOxidisedSulfideEntersElementalSulfurLedger(GameTestHelper helper) {
		FluidStack feed = GameTestFixtures.declared(1, Map.of("NaOCl", .005, "Na2S", .010), 1000);
		TickDriver.Step zero = TickDriver.step(List.of(feed), 0);
		helper.assertTrue(zero.valid && zero.state != null, "zero-step sulfide charge commits an engine state");
		Map<String, Double> before = nativeTotals(zero.state, "Hyp", "Sulfide", "Szero");
		helper.assertTrue(before.get("Hyp") > 0 && before.get("Sulfide") > 0 && before.get("Szero") < 1e-12,
			"real NaOCl and Na2S ingress creates protected Hyp/Sulfide inventories without spontaneous Szero");
		// Keep this window short enough that the deliberate excess-Hyp follow-up
		// channel has not yet oxidised the newly formed Szero inventory.
		TickDriver.Step step = TickDriver.step(List.of(feed), 0.000001);
		helper.assertTrue(step.valid && step.state != null, "native sulfide oxidation commits an engine state");
		Map<String, Double> after = nativeTotals(step.state, "Hyp", "Sulfide", "Szero");
		double sulfur = solidMol(step.state, "chemicaladdon:sulfur", KernelSolutionState.SolidLocation.SUSPENDED);
		double sulfurBefore = solidMol(zero.state, "chemicaladdon:sulfur", KernelSolutionState.SolidLocation.SUSPENDED);
		double sulfideLoss = before.get("Sulfide") - after.get("Sulfide");
		double zeroGain = after.get("Szero") - before.get("Szero") + sulfur - sulfurBefore;
		helper.assertTrue(sulfur > 1e-8 && step.phases.getOrDefault("EngineSulfur", 0d) > 1e-8,
			"Hyp + Sulfide produces EngineSulfur and writes it as suspended chemicaladdon:sulfur");
		helper.assertTrue(sulfideLoss > 1e-8 && Math.abs(sulfideLoss - zeroGain) < 1e-8,
			"each consumed protected Sulfide unit becomes dissolved Szero plus exact sulfur-solid ledger inventory");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 200)
	public static void permanganateFormulaIngressEntersManganeseDioxideLedger(GameTestHelper helper) {
		FluidStack feed = GameTestFixtures.declared(1, Map.of("Na2SO3", .010, "KMnO4", .006), 1000);
		TickDriver.Step zero = TickDriver.step(List.of(feed), 0);
		helper.assertTrue(zero.valid && zero.state != null, "zero-step permanganate charge commits an engine state");
		Map<String, Double> before = nativeTotals(zero.state, "Sul", "Mnvii");
		helper.assertTrue(before.get("Sul") > 0 && before.get("Mnvii") > 0,
			"real Na2SO3 and KMnO4 ingress create protected Sul/Mnvii inventories");
		TickDriver.Step step = TickDriver.step(List.of(feed), 0.1);
		helper.assertTrue(step.valid && step.state != null, "native permanganate step commits an engine state");
		double manganeseDioxide = solidMol(step.state, "chemicaladdon:manganese_dioxide",
			KernelSolutionState.SolidLocation.SUSPENDED);
		helper.assertTrue(manganeseDioxide > 1e-8 && step.phases.getOrDefault("MnO2(s)", 0d) > 1e-8,
			"neutral KMnO4 + sulfite route writes native MnO2 to the game solid ledger");
		helper.succeed();
	}

	private static double solidMol(FluidStack stack) {
		KernelSolutionState state = Mixture.engineSolution(stack);
		if (state == null) return 0;
		var q = Kernel.get(); synchronized (q) {
			return state.atAmount(q, stack.getAmount()).solids().stream().mapToDouble(KernelSolutionState.SolidPhase::mol).sum();
		}
	}

	private static double solidMol(FluidStack stack, String speciesId) {
		KernelSolutionState state = Mixture.engineSolution(stack);
		if (state == null) return 0;
		var q = Kernel.get(); synchronized (q) {
			return state.atAmount(q, stack.getAmount()).solids().stream()
				.filter(s -> speciesId.equals(s.speciesId())).mapToDouble(KernelSolutionState.SolidPhase::mol).sum();
		}
	}

	private static double solidMol(KernelSolutionState state, String speciesId, KernelSolutionState.SolidLocation location) {
		return state.solids().stream().filter(s -> speciesId.equals(s.speciesId()) && s.location() == location)
			.mapToDouble(KernelSolutionState.SolidPhase::mol).sum();
	}

	private static Map<String, Double> nativeTotals(KernelSolutionState state, String... components) {
		var q = Kernel.get(); synchronized (q) {
			return EngineBridge.derive(q, state, List.of(components), List.of()).totalMol();
		}
	}

	private record NativeInventory(double waterKg, double naMol, double clMol) {}

	private static NativeInventory inventory(FluidStack stack) {
		KernelSolutionState state = Mixture.engineSolution(stack);
		if (state == null) return new NativeInventory(0, 0, 0);
		var q = Kernel.get(); synchronized (q) { return inventory(state.atAmount(q, stack.getAmount())); }
	}

	private static NativeInventory inventory(KernelSolutionState state) {
		var q = Kernel.get(); synchronized (q) {
			EngineBridge.DerivedSolution view = EngineBridge.derive(q, state, List.of("Na", "Cl"), List.of());
			return new NativeInventory(view.waterKg(), view.totalMol().getOrDefault("Na", Double.NaN),
				view.totalMol().getOrDefault("Cl", Double.NaN));
		}
	}

	private static void assertInventory(GameTestHelper helper, NativeInventory before, NativeInventory first,
			NativeInventory second, String message) {
		helper.assertTrue(close(before.waterKg(), first.waterKg() + second.waterKg()), message + " water");
		helper.assertTrue(close(before.naMol(), first.naMol() + second.naMol()), message + " Na");
		helper.assertTrue(close(before.clMol(), first.clMol() + second.clMol()), message + " Cl");
	}

	private static boolean close(double left, double right) {
		return Double.isFinite(left) && Double.isFinite(right) && Math.abs(left - right) < 1e-8;
	}
}
