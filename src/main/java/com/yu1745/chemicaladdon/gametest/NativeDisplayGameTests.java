package com.yu1745.chemicaladdon.gametest;

import java.util.List;
import java.util.Map;

import com.yu1745.chemicaladdon.composition.Chemistry;
import com.yu1745.chemicaladdon.composition.Solution;
import com.yu1745.chemicaladdon.fluid.Mixture;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.fluids.FluidStack;

/** Locks the one-way RAW -> display-cache scaling contract. */
@GameTestHolder("chemicaladdon")
@PrefixGameTestTemplate(false)
public final class NativeDisplayGameTests {
	private NativeDisplayGameTests() {}

	@GameTest(template = "empty_15")
	public static void nativeDisplayKeepsWaterAndScalesSample(GameTestHelper helper) {
		FluidStack full = Mixture.fromDeclaredComposition(1.0, Map.of("NaCl", .1), 1000, 25, List.of());
		long water = Mixture.deriveUnitAmounts(full).getOrDefault(Solution.WATER, 0);
		long sodium = Mixture.deriveUnitIonAmounts(full).getOrDefault("Na+1", 0);
		helper.assertTrue(water == 1000L * Chemistry.UNIT_PER_MB, "1 kg water must display as 1000 mB");
		helper.assertTrue(sodium > 900_000 && sodium < 1_100_000,
				"0.1 mol Na+ must not be joint-ratio normalized: " + sodium);
		FluidStack sample = full.copy(); sample.setAmount(1);
		helper.assertTrue(Mixture.deriveUnitAmounts(sample).getOrDefault(Solution.WATER, 0) == Chemistry.UNIT_PER_MB,
				"1 mB sample must contain 1 mB water");
		helper.assertTrue(Math.abs(Mixture.deriveUnitIonAmounts(sample).getOrDefault("Na+1", 0) * 1000L - sodium) <= 1000,
				"sample sodium must be proportional");
		helper.succeed();
	}

	@GameTest(template = "empty_15")
	public static void nativeDisplayKeepsTraceAtUnitPrecision(GameTestHelper helper) {
		FluidStack trace = Mixture.fromDeclaredComposition(1.0, Map.of("NaCl", 1e-6), 1000, 25, List.of());
		helper.assertTrue(Mixture.deriveUnitIonAmounts(trace).getOrDefault("Na+1", 0) > 0,
				"trace native ion must survive reference display caching");
		helper.succeed();
	}
}
