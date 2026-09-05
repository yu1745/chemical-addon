package com.yu1745.chemicaladdon.composition.parity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.yu1745.chemengine.kernel.IPhreeqc;
import com.yu1745.chemengine.kernel.ChemState;
import com.yu1745.chemengine.kernel.Curation;
import com.yu1745.chemicaladdon.composition.Species;

/** Physical contracts for the engine-owned transport representation. */
class KernelSolutionStateTest {
	@Test void declaredStrongAcidAndBaseDoNotUseNeutralPhChargeDefault() {
		try (IPhreeqc q = IPhreeqc.create()) {
			KernelSolutionState acid = KernelSolutionState.fromDeclaredFeed(q, 1, Map.of("HCl", .01), 1000);
			KernelSolutionState base = KernelSolutionState.fromDeclaredFeed(q, 1, Map.of("NaOH", .01), 1000);
			double acidPh = ph(q, acid.raw());
			double basePh = ph(q, base.raw());
			assertTrue(acidPh < 3, "HCl state pH=" + acidPh);
			assertTrue(basePh > 11, "NaOH state pH=" + basePh);
		}
	}

	@Test void scalingAndRemovalScaleWaterAndSolidInventoryTogether() {
		try (IPhreeqc q = IPhreeqc.create()) {
			KernelSolutionState state = new KernelSolutionState(q.declaredSolution(2, Map.of("NaCl", .1), 25), 2000,
					List.of(new KernelSolutionState.SolidPhase("chemicaladdon:limestone", .3,
							KernelSolutionState.SolidLocation.SUSPENDED)));
			KernelSolutionState half = state.scale(q, 1000);
			assertEquals(.15, half.solids().get(0).mol(), 1e-12);
			assertEquals(1, water(q, half.raw()), 1e-8);
			KernelSolutionState.ProportionalRemoval split = state.removeProportionally(q, 500);
			assertEquals(500, split.removed().referenceMb());
			assertEquals(1500, split.remainder().referenceMb());
			assertEquals(.075, split.removed().solids().get(0).mol(), 1e-12);
			assertThrows(IllegalArgumentException.class, () -> state.removeProportionally(q, 2000));
		}
	}

	@Test void mergeRepeatedRawAccumulatesRatherThanDroppingOneInput() {
		try (IPhreeqc q = IPhreeqc.create()) {
			KernelSolutionState one = KernelSolutionState.fromDeclaredFeed(q, 1, Map.of("NaCl", .1), 1000);
			KernelSolutionState merged = KernelSolutionState.merge(q, List.of(one, one));
			assertEquals(2000, merged.referenceMb());
			assertEquals(2, water(q, merged.raw()), 1e-8);
			assertNotEquals(one.raw(), merged.raw());
		}
	}

	@Test void observationUsesFreshRowsAndSharedSessionDoesNotLeakBetweenFeeds() {
		try (IPhreeqc q = IPhreeqc.create()) {
			KernelSolutionState acid = KernelSolutionState.fromDeclaredFeed(q, 1, Map.of("HCl", .01), 1000);
			KernelSolutionState base = KernelSolutionState.fromDeclaredFeed(q, 1, Map.of("NaOH", .01), 1000);
			double a1 = q.observeRestored(acid.raw()).row(0).d("pH");
			double b = q.observeRestored(base.raw()).row(0).d("pH");
			double a2 = q.observeRestored(acid.raw()).row(0).d("pH");
			assertTrue(a1 < 3 && b > 11 && a2 < 3, a1 + "," + b + "," + a2);
		}
	}

	@Test void projectionReadsNativeMolalityColumns() {
		try (IPhreeqc q = IPhreeqc.create()) {
			KernelSolutionState salt = KernelSolutionState.fromDeclaredFeed(q, 1, Map.of("NaCl", .1), 1000);
			EngineBridge.DerivedSolution view = EngineBridge.derive(q, salt, List.of("Na+", "Cl-"));
			assertTrue(view.aqueousMol().get("Na+") > .09);
			assertTrue(view.aqueousMol().get("Cl-") > .09);
		}
	}

	@Test void displayPhaseAndHydrateFormulaeNormalizeBeforeDeclaredFeed() {
		assertEquals("KNO3", Species.normalizeEngineFormula("KNO3(aq)"));
		assertEquals("CuSO4:5H2O", Species.normalizeEngineFormula("CuSO4·5H2O"));
		try (IPhreeqc q = IPhreeqc.create()) {
			KernelSolutionState state = KernelSolutionState.fromDeclaredFeed(q, 1,
					Map.of(Species.normalizeEngineFormula("NaCl(aq)"), .1), 1000);
			assertTrue(q.observeRestored(state.raw()).rowCount() > 0);
		}
	}

	@Test void realMetastableFormulaeCrossTheKssBoundaryWithoutExposingAliases() {
		try (IPhreeqc q = IPhreeqc.create()) {
			KernelSolutionState state = KernelSolutionState.fromDeclaredFeed(q, 1,
					Map.of("NaOCl", .01, "Na2SO3", .02, "NaNO2", .03), 1000);
			var view = EngineBridge.derive(q, state, List.of("Hyp", "Sul", "Nitri", "Na"), List.of());
			assertEquals(.01, view.totalMol().get("Hyp"), 1e-10);
			assertEquals(.02, view.totalMol().get("Sul"), 1e-10);
			assertEquals(.03, view.totalMol().get("Nitri"), 1e-10);
			assertEquals(.08, view.totalMol().get("Na"), 1e-10);
		}
	}

	@Test void failedRemovalAndEvaporationLeaveImmutableStateUnchanged() {
		try (IPhreeqc q = IPhreeqc.create()) {
			KernelSolutionState state = KernelSolutionState.fromDeclaredFeed(q, 1, Map.of("NaCl", .01), 1000);
			String raw = state.raw();
			assertThrows(RuntimeException.class, () -> state.removeDeclaredFeed(q, Map.of("NaCl", 1e9)));
			assertEquals(raw, state.raw());
			assertThrows(RuntimeException.class, () -> state.evaporateWater(q, 1e9));
			assertEquals(raw, state.raw());
		}
	}

	@Test void sulfuricAcidExactInventoryCanBeWithdrawnWithoutRebuildingWater() {
		try (IPhreeqc q = IPhreeqc.create()) {
			// Transport volume is independent from water mass: this mirrors the
			// 900 mB GameTest stack carrying .6 kg water and .1 mol H2SO4.
			KernelSolutionState acid = KernelSolutionState.fromDeclaredFeed(q, .6, Map.of("H2SO4", .1), 900);
			var before = EngineBridge.derive(q, acid, List.of("S(6)"), List.of());
			KernelSolutionState drained = acid.removeDeclaredFeed(q, Map.of("H2SO4", .1));
			var after = EngineBridge.derive(q, drained, List.of("S(6)"), List.of());
			assertEquals(.1, before.totalMol().get("S(6)"), 1e-10,
				"native input inventory: water=" + before.waterKg() + ", alkalinity=" + before.alkalinityEq());
			assertEquals(0, after.totalMol().get("S(6)"), 1e-9);
			// derive() deliberately uses a candidate zero-reaction equilibrium to
			// obtain a fresh row; it rounds its displayed water mass at ~1e-9 kg.
			// The transaction itself never rebuilds or replaces the archived water.
			assertEquals(before.waterKg(), after.waterKg(), 2e-9);
		}
	}

	@Test void sharedSessionWithdrawsTheExactSulfuricGameFixtureAfterInvalidInput() {
		IPhreeqc q = Kernel.get();
		synchronized (q) {
			assertThrows(RuntimeException.class,
				() -> KernelSolutionState.fromDeclaredFeed(q, .3, Map.of("NotARealFormula", .1), 600));
			KernelSolutionState acid = KernelSolutionState.fromDeclaredFeed(q, .3, Map.of("H2SO4", .1), 600);
			var before = EngineBridge.derive(q, acid, List.of("S(6)"), List.of());
			KernelSolutionState drained = acid.removeDeclaredFeed(q, Map.of("H2SO4", .1));
			var after = EngineBridge.derive(q, drained, List.of("S(6)"), List.of());
			assertEquals(.1, before.totalMol().get("S(6)"), 1e-10);
			assertEquals(0, after.totalMol().get("S(6)"), 1e-9);
		}
	}

	@Test void sharedSessionWithdrawsTheFullSulfuricGameTestVolumeAfterInvalidInput() {
		IPhreeqc q = Kernel.get();
		synchronized (q) {
			assertThrows(RuntimeException.class,
				() -> KernelSolutionState.fromDeclaredFeed(q, .6, Map.of("NotARealFormula", .1), 900));
			KernelSolutionState acid = KernelSolutionState.fromDeclaredFeed(q, .6, Map.of("H2SO4", .1), 900);
			KernelSolutionState drained = acid.removeDeclaredFeed(q, Map.of("H2SO4", .1));
			var after = EngineBridge.derive(q, drained, List.of("S(6)"), List.of());
			assertEquals(0, after.totalMol().get("S(6)"), 1e-9);
			assertEquals(.6, after.waterKg(), 2e-9);
		}
	}

	@Test void exactSulfuricWithdrawalSurvivesHighPrecisionObservationWarmup() {
		try (IPhreeqc q = IPhreeqc.create()) {
			String water = q.declaredSolution(1, Map.of(), 25);
			q.observeRestored(water, List.of("S(6)"), "H+", "SO4-2");
			KernelSolutionState acid = KernelSolutionState.fromDeclaredFeed(q, .6, Map.of("H2SO4", .1), 900);
			KernelSolutionState drained = acid.removeDeclaredFeed(q, Map.of("H2SO4", .1));
			var after = EngineBridge.derive(q, drained, List.of("S(6)"), List.of());
			assertEquals(0, after.totalMol().get("S(6)"), 1e-9);
			assertEquals(.6, after.waterKg(), 2e-9);
		}
	}

	@Test void exactSulfuricWithdrawalSurvivesHighPrecisionRestoredContinuationAndMix() {
		try (IPhreeqc q = IPhreeqc.create()) {
			KernelSolutionState acid = KernelSolutionState.fromDeclaredFeed(q, .6, Map.of("H2SO4", .1), 900);
			q.runRestored(acid.raw(), """
				SELECTED_OUTPUT 1
				    -high_precision true
				    -totals S(6)
				USE solution 1
				REACTION 1 native tick probe
				    H2O 0
				    1 mol
				SAVE solution 1
				""");
			KernelSolutionState continued = new KernelSolutionState(q.runDump(1), 900);
			KernelSolutionState drained = continued.removeDeclaredFeed(q, Map.of("H2SO4", .1));
			var afterTick = EngineBridge.derive(q, drained, List.of("S(6)"), List.of());
			assertEquals(0, afterTick.totalMol().get("S(6)"), 1e-9);
			assertEquals(.6, afterTick.waterKg(), 2e-9);

			KernelSolutionState doubled = KernelSolutionState.merge(q, List.of(acid, acid));
			KernelSolutionState emptied = doubled.removeDeclaredFeed(q, Map.of("H2SO4", .2));
			var afterMix = EngineBridge.derive(q, emptied, List.of("S(6)"), List.of());
			assertEquals(0, afterMix.totalMol().get("S(6)"), 1e-9);
			assertEquals(1.2, afterMix.waterKg(), 2e-9);
		}
	}



	@Test void oneMillibucketSplitRetainsTraceInventoryAndRecombinesExactly() {
		try (IPhreeqc q = IPhreeqc.create()) {
			KernelSolutionState original = KernelSolutionState.fromDeclaredFeed(q, 1, Map.of("NaCl", 1e-7), 1000);
			var split = original.removeProportionally(q, 1);
			var trace = EngineBridge.derive(q, split.removed(), List.of("Na"), List.of());
			assertEquals(1e-10, trace.totalMol().get("Na"), 1e-16);
			KernelSolutionState joined = KernelSolutionState.merge(q, List.of(split.removed(), split.remainder()));
			var restored = EngineBridge.derive(q, joined, List.of("Na"), List.of());
			assertEquals(1e-7, restored.totalMol().get("Na"), 1e-13);
			assertEquals(1, restored.waterKg(), 1e-9);
		}
	}

	@Test void unknownDeclaredElementFailsInsteadOfSilentlyBecomingWater() {
		try (IPhreeqc q = IPhreeqc.create()) {
			assertThrows(RuntimeException.class,
					() -> KernelSolutionState.fromDeclaredFeed(q, 1, Map.of("UnknowniumCl", .1), 1000));
			KernelSolutionState valid = KernelSolutionState.fromDeclaredFeed(q, 1, Map.of("HCl", .01), 1000);
			assertTrue(ph(q, valid.raw()) < 3, "failed feed must not contaminate the next vessel");
		}
	}

	@Test void failedDeclarationResetsSessionBeforeRawKineticsContinuation() {
		try (IPhreeqc q = IPhreeqc.create()) {
			KernelSolutionState valid = KernelSolutionState.fromDeclaredFeed(q, 1, Map.of("NaCl", .01), 1000);
			assertThrows(RuntimeException.class,
					() -> KernelSolutionState.fromDeclaredFeed(q, 1, Map.of("NotARealFormula", .1), 1000));
			EngineBridge.Feed feed = new EngineBridge.Feed();
			feed.totals.putAll(ChemState.fromDump(valid.raw()).totals());
			assertTrue(q.runRestored(valid.raw(), feed.restoredScriptWithKinetics(
				Curation.load(), null, null, .5)).rowCount() > 0,
				"a bad declaration must not poison the next restored KINETICS step");
		}
	}

	@Test void evaporationRemovesDeclaredWaterMassAndKeepsDissolvedSodium() {
		try (IPhreeqc q = IPhreeqc.create()) {
			KernelSolutionState original = KernelSolutionState.fromDeclaredFeed(q, 1, Map.of("NaCl", .1), 1000);
			KernelSolutionState concentrated = original.evaporateWater(q, .1);
			var before = EngineBridge.derive(q, original, List.of("Na"), List.of());
			var after = EngineBridge.derive(q, concentrated, List.of("Na"), List.of());
			assertEquals(.1, before.waterKg() - after.waterKg(), 1e-8);
			assertEquals(before.totalMol().get("Na"), after.totalMol().get("Na"), 1e-10);
		}
	}

	private static double ph(IPhreeqc q, String raw) {
		return q.observeRestored(raw).row(0).d("pH");
	}
	private static double water(IPhreeqc q, String raw) {
		return q.observeRestored(raw).row(0).d("mass_H2O");
	}
}
