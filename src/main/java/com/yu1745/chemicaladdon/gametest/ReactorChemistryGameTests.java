package com.yu1745.chemicaladdon.gametest;

import java.util.List;
import java.util.Map;

import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.composition.Chemistry;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.composition.parity.TickDriver;
import com.yu1745.chemicaladdon.composition.parity.WriteBack;
import com.yu1745.chemicaladdon.reactor.PhysicalSteps;
import com.yu1745.chemicaladdon.reactor.ReactorTank;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Game-level checks for the engine-owned aqueous state.  These tests deliberately
 * use declared external materials: a reactor must never accept a display-only
 * mixture as chemical input.
 */
@GameTestHolder(ChemicalAddon.MODID)
@PrefixGameTestTemplate(false)
public final class ReactorChemistryGameTests {
    private static final int VOLUME_MB = 1_000;

    private ReactorChemistryGameTests() {}

    private static ReactorTank declaredTank(Map<String, Double> formulaMol, int temperatureC) {
        ReactorTank tank = new ReactorTank(4_000, () -> {});
        FluidStack input = Mixture.fromDeclaredComposition(
            1.0, formulaMol, VOLUME_MB, temperatureC, List.of());
        if (tank.fill(input, FluidAction.EXECUTE) != VOLUME_MB)
            throw new IllegalStateException("Declared native mixture was rejected by reactor tank");
        return tank;
    }

    private static TickDriver.Step stepAndCommit(ReactorTank tank, int temperatureC) {
        TickDriver.Step step = TickDriver.step(tank.getFluids(), 0.5, temperatureC);
        if (!step.valid)
            throw new IllegalStateException("Native aqueous step did not produce a solution");
        if (!WriteBack.firstOf(tank.getFluids(), step))
            throw new IllegalStateException("Native aqueous write-back was rejected");
        return step;
    }

    @GameTest(template = "empty_15", timeoutTicks = 100)
    public static void nativeAcidAndBaseUseDeclaredInputs(GameTestHelper helper) {
        ReactorTank acid = declaredTank(Map.of("HCl", 0.02), 25);
        ReactorTank base = declaredTank(Map.of("NaOH", 0.02), 25);
        stepAndCommit(acid, 25);
        stepAndCommit(base, 25);

        long acidHydrogen = Mixture.deriveUnitIonAmounts(acid.getFluidInTank(0)).getOrDefault("H+1", 0);
        long baseHydroxide = Mixture.deriveUnitIonAmounts(base.getFluidInTank(0)).getOrDefault("OH-1", 0);
        helper.assertTrue(acidHydrogen > 0, "Native acid state must project hydrogen for its display");
        helper.assertTrue(baseHydroxide > 0, "Native base state must project hydroxide for its display");
        helper.succeed();
    }

    @GameTest(template = "empty_15", timeoutTicks = 100)
    public static void nativePrecipitationAndAcidDissolutionKeepRawState(GameTestHelper helper) {
        ReactorTank tank = declaredTank(Map.of("CaCl2", 0.30, "Na2CO3", 0.30), 25);
        TickDriver.Step precipitated = stepAndCommit(tank, 25);
        double initialPhaseMol = precipitated.phases.values().stream().mapToDouble(Double::doubleValue).sum();
        helper.assertTrue(initialPhaseMol > 0.0,
            "Calcium carbonate feed must create a native equilibrium phase inventory");

        FluidStack acid = Mixture.fromDeclaredComposition(1.0, Map.of("HCl", 0.20),
            VOLUME_MB, 25, List.of());
        helper.assertTrue(tank.fill(acid, FluidAction.EXECUTE) == VOLUME_MB,
            "Declared acid input must merge with the existing native solution");
        TickDriver.Step dissolved = stepAndCommit(tank, 25);
        double finalPhaseMol = dissolved.phases.values().stream().mapToDouble(Double::doubleValue).sum();
        helper.assertTrue(finalPhaseMol < initialPhaseMol,
            "Acidification must dissolve the stored native precipitate instead of rebuilding display ions");
        helper.assertTrue(Mixture.engineSolution(tank.getFluidInTank(0)) != null,
            "Precipitation and dissolution must leave an engine-owned solution in the tank");
        helper.succeed();
    }

    @GameTest(template = "empty_15", timeoutTicks = 100)
    public static void nativeOpenEvaporationPreservesSolutionAuthority(GameTestHelper helper) {
        ReactorTank tank = declaredTank(Map.of("NaCl", 0.10), 110);
        stepAndCommit(tank, 110);
        int before = tank.getTotalAmount();
        long[] vented = new long[1];
        PhysicalSteps.apply(tank, true, null, 1.0, vented, 110);

        helper.assertTrue(vented[0] > 0, "An open hot reactor must emit water vapor");
        helper.assertTrue(tank.getTotalAmount() < before, "Evaporation must remove liquid volume");
        helper.assertTrue(Mixture.engineSolution(tank.getFluidInTank(0)) != null,
            "Evaporation must scale the native solution instead of reconstructing ions");
        helper.succeed();
    }

    @GameTest(template = "empty_15", timeoutTicks = 100)
    public static void nativeDisplayUsesReferenceWaterAndTracePrecision(GameTestHelper helper) {
        ReactorTank tank = declaredTank(Map.of("NaCl", 0.10), 25);
        FluidStack full = tank.getFluidInTank(0);
        long fullWater = Mixture.deriveUnitAmounts(full).getOrDefault(Mixture.SOLVENT, 0);
        long fullSodium = Mixture.deriveUnitIonAmounts(full).getOrDefault("Na+1", 0);
        helper.assertTrue(fullWater == 1_000L * Chemistry.UNIT_PER_MB,
            "One kg of native water must display as 1,000 mB without joint-domain normalization");
        helper.assertTrue(fullSodium > 0L, "Native sodium must be present in the display projection");

        FluidStack oneMb = full.copy();
        oneMb.setAmount(1);
        long sampleWater = Mixture.deriveUnitAmounts(oneMb).getOrDefault(Mixture.SOLVENT, 0);
        long sampleSodium = Mixture.deriveUnitIonAmounts(oneMb).getOrDefault("Na+1", 0);
        helper.assertTrue(sampleWater == Chemistry.UNIT_PER_MB,
            "A one mB native sample must retain the reference-scaled water amount");
        helper.assertTrue(sampleSodium > 0L && Math.abs(sampleSodium * 1_000L - fullSodium) <= 1_000L,
            "A one mB sample must scale dissolved sodium without an early millimole round-off");

        FluidStack trace = Mixture.fromDeclaredComposition(1.0, Map.of("NaCl", 1.0e-6),
            VOLUME_MB, 25, List.of());
        helper.assertTrue(Mixture.deriveUnitIonAmounts(trace).getOrDefault("Na+1", 0) > 0L,
            "Trace native solute must survive the display cache at unit precision");
        helper.succeed();
    }
}
