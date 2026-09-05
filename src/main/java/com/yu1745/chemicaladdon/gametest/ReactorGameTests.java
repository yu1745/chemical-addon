package com.yu1745.chemicaladdon.gametest;

import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.composition.parity.TickDriver;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.reactor.ReactorTank;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Native RAW-state reactor regressions; RulesEngine's display-ion chemistry is retired. */
@GameTestHolder(ChemicalAddon.MODID)
@PrefixGameTestTemplate(false)
public class ReactorGameTests {
	@GameTest(template = "empty_15", timeoutTicks = 400)
	public static void nativeAcidBaseStepCarriesCanonicalContinuation(GameTestHelper helper) {
		ReactorTank tank = new ReactorTank(10000, () -> {});
		tank.fill(GameTestFixtures.declared(1, Map.of("HCl", .01, "NaOH", .01), 1000), FluidAction.EXECUTE);
		TickDriver.Step step = TickDriver.step(tank.getFluids(), .5);
		helper.assertTrue(step.valid && step.state != null, "native acid/base step must produce RAW continuation: " + step.error);
		helper.assertTrue(step.state.referenceMb() == 1000, "continuation keeps transport reference");
		helper.assertTrue(Math.abs(step.ph - 7) < .5, "equal strong acid/base must neutralize: " + step.ph);
		helper.assertTrue(Math.abs(step.totals.getOrDefault("Na", 0d) - .01) < 1e-8
			&& Math.abs(step.totals.getOrDefault("Cl", 0d) - .01) < 1e-8,
			"neutralization preserves sodium and chloride inventory");
		helper.assertTrue(Mixture.engineSolution(tank.getFluids().get(0)) != null, "fixture never relies on display ions");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 400)
	public static void nativeConsecutiveStepsPreserveWaterAndRaw(GameTestHelper helper) {
		ReactorTank tank = new ReactorTank(10000, () -> {});
		tank.fill(GameTestFixtures.declared(2, Map.of("NaCl", .1), 2000), FluidAction.EXECUTE);
		TickDriver.Step first = TickDriver.step(tank.getFluids(), .5);
		helper.assertTrue(first.valid, "first native step valid");
		Mixture.setEngineSolution(tank.getFluids().get(0), first.state);
		TickDriver.Step second = TickDriver.step(tank.getFluids(), .5);
		helper.assertTrue(second.valid && second.state.referenceMb() == 2000, "second RAW continuation valid");
		helper.assertTrue(second.waterKg > 1.9 && second.waterKg < 2.1, "no display-domain water reconstruction");
		helper.assertTrue(Math.abs(second.waterKg - first.waterKg) < 1e-8,
			"repeated equilibrium must not create or consume water");
		helper.assertTrue(Math.abs(second.totals.getOrDefault("Na", 0d) - .1) < 1e-8
			&& Math.abs(second.totals.getOrDefault("Cl", 0d) - .1) < 1e-8,
			"repeated steps preserve dissolved inventory");
		helper.succeed();
	}
}
