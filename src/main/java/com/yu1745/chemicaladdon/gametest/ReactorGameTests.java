package com.yu1745.chemicaladdon.gametest;

import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.composition.Solution;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.fluid.Temperature;
import com.yu1745.chemicaladdon.reactor.ReactorTank;
import com.yu1745.chemicaladdon.reactor.RulesEngine;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Home for reactor chemistry and recipe-flow GameTests as they are extracted. */
@GameTestHolder(ChemicalAddon.MODID)
@PrefixGameTestTemplate(false)
public class ReactorGameTests {
	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void rulesEngineNeutralisesAcidAndBase(GameTestHelper helper) {
		// H+ + OH- -> H2O (emergent, no whitelist); Na+ + Cl- remain as ions.
		// Amounts stay under NaCl's saturation (500 f.u. / 1500 water = 0.33 < 0.36)
		// so this test isolates neutralisation from curve crystallisation.
		ResourceLocation water = Solution.WATER;
		ReactorTank tank = new ReactorTank(10000, () -> {});
		FluidStack mix = Mixture.create(
			Map.of(water, 1000),
			Map.of("H+1", 500, "Cl-1", 500, "Na+1", 500, "OH-1", 500),
			3000);
		tank.fill(mix, FluidAction.EXECUTE);

		RulesEngine.apply(tank);

		FluidStack result = tank.getFluids().get(0);
		helper.assertTrue(Mixture.deriveSuspendedAmounts(result).isEmpty(),
			"neutralisation should not suspend any solid");
		helper.assertTrue(Mixture.deriveSedimentAmounts(result).isEmpty(),
			"unsaturated NaCl should not crystallise (got " + Mixture.deriveSedimentAmounts(result) + ")");
		Map<String, Integer> ions = Mixture.deriveIonAmounts(result);
		helper.assertTrue(ions.getOrDefault("H+1", 0) == 0, "H+ should be consumed (got " + ions + ")");
		helper.assertTrue(ions.getOrDefault("OH-1", 0) == 0, "OH- should be consumed (got " + ions + ")");
		helper.assertTrue(ions.getOrDefault("Na+1", 0) == 500, "Na+ should remain (got " + ions + ")");
		helper.assertTrue(ions.getOrDefault("Cl-1", 0) == 500, "Cl- should remain (got " + ions + ")");
		helper.assertTrue(Mixture.deriveAmounts(result).getOrDefault(water, 0) == 1500,
			"neutralisation should produce water (got " + Mixture.deriveAmounts(result) + ")");
		// U16 energy ledger: 500 mB of pairs × 3172 J over a 3000 mB feed body
		// → ΔT = 126 °C (mass-coupled; was a flat +25 °C lump before)
		int t = Temperature.get(result);
		helper.assertTrue(t >= 140 && t <= 152,
			"neutralisation is exothermic, ΔT mass-coupled ≈126 °C (got " + t + "°C)");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void neutralisationExothermScalesWithConcentration(GameTestHelper helper) {
		// U16 acceptance, both directions: the same neutralisation heat
		// released into a concentrated feed flashes hot enough to cross the
		// boiling point (self-boil), while a big dilute vessel barely warms —
		// the mass coupling ΔT = Q/(Σunits × 4.18) that the old flat
		// "+X °C per solve" constant could not express (plans/03 §12).
		ResourceLocation water = Solution.WATER;

		// concentrated 1:1:1 water:H+:OH- → ΔT = 3172/(3 × 4.18) ≈ 253 °C
		ReactorTank hot = new ReactorTank(10000, () -> {});
		hot.fill(Mixture.create(Map.of(water, 500), Map.of("H+1", 500, "OH-1", 500), 1500),
			FluidAction.EXECUTE);
		RulesEngine.apply(hot);
		int tHot = Temperature.get(hot.getFluids().get(0));
		helper.assertTrue(tHot >= 260 && tHot <= 290,
			"concentrated 1:1:1 neutralisation self-boils (ΔT≈253 °C, got " + tHot + "°C)");

		// the same reaction amount diluted in a near-full 10-bucket body
		// (96:1:1): single-digit rise — big-vessel dilution is safe
		ReactorTank big = new ReactorTank(10000, () -> {});
		big.fill(Mixture.create(Map.of(water, 9600), Map.of("H+1", 100, "OH-1", 100), 9800),
			FluidAction.EXECUTE);
		RulesEngine.apply(big);
		int tBig = Temperature.get(big.getFluids().get(0));
		helper.assertTrue(tBig >= 24 && tBig <= 32,
			"the same heat in a full dilute vessel warms single digits (got " + tBig + "°C)");
		helper.succeed();
	}

}
