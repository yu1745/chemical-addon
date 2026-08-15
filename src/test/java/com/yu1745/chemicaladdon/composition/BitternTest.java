package com.yu1745.chemicaladdon.composition;

import static com.yu1745.chemicaladdon.composition.EngineHarness.assertNeutral;
import static com.yu1745.chemicaladdon.composition.EngineHarness.ions;
import static com.yu1745.chemicaladdon.composition.EngineHarness.sed;
import static com.yu1745.chemicaladdon.composition.EngineHarness.solve;
import static com.yu1745.chemicaladdon.composition.EngineHarness.solveToFixpoint;
import static com.yu1745.chemicaladdon.composition.EngineHarness.water;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * U15① bittern salts: MgCl₂/CaCl₂ solubility curves. Before them, a boiled-down
 * brine's Mg²⁺/Ca²⁺ chlorides were "dry ions" — dissolved forever with nowhere
 * to go. With curves they crystallise on dry-out (and CaCl₂ on deep cooling),
 * so evaporating seawater yields a NaCl+MgCl₂ mixed sediment — the actual
 * bittern chemistry the desalination/purification game plays on.
 */
class BitternTest {

	@BeforeAll
	static void load() {
		EngineHarness.load();
	}

	@Test
	void magnesiumChlorideStaysDissolvedHotAndCrystallisesOnDryOut() {
		// 500 formula units MgCl₂ in 1000 mB water at 100 °C: threshold is
		// 72.7/100 → 727 units cap → everything stays dissolved
		Solution hot = solve(water(1000), ions("Mg+2", 500L, "Cl-1", 1000L), 100);
		assertEquals(0, sed(hot, "magnesium_chloride"), "hot MgCl₂ must stay dissolved");
		assertNeutral(hot);

		// boil the pot dry: the curve species crash out wholesale (evaporite)
		Solution boiled = solveToFixpoint(water(0), hot.ions(), 100);
		assertEquals(500, sed(boiled, "magnesium_chloride"),
			"dry-out must crystallise all MgCl₂ (no dry-ion loss)");
	}

	@Test
	void calciumChlorideCrystallisesOnDeepCooling() {
		// 1000 formula units CaCl₂ in 1000 mB water: cap at 100 °C is 1590
		// (dissolved); cooled to 0 °C the cap is 595 → 68% supersaturation
		// passes the homogeneous nucleation gate and ~405 units crystallise
		// (kinetic, so solve to the fixpoint)
		Solution hot = solve(water(1000), ions("Ca+2", 1000L, "Cl-1", 2000L), 100);
		assertEquals(0, sed(hot, "calcium_chloride"), "hot CaCl₂ must stay dissolved");
		Solution cooled = solveToFixpoint(water(1000), hot.ions(), 0);
		long settled = sed(cooled, "calcium_chloride");
		assertTrue(Math.abs(settled - 405) <= 15,
			"cooling to 0°C should crystallise ~405 units of CaCl₂ (got " + settled + ")");
		assertNeutral(cooled);
	}

	@Test
	void shallowCaCl2SupersaturationStaysMetastableUnseeded() {
		// 700/1000 at 0 °C is only 18% over the 595 cap — inside the metastable
		// zone: nothing crystallises without a seed (U14 nucleation gate), and
		// one seed unit collapses it
		Solution metastable = solveToFixpoint(water(1000), ions("Ca+2", 700L, "Cl-1", 1400L), 0);
		assertEquals(0, sed(metastable, "calcium_chloride"), "shallow supersaturation must stay metastable unseeded");
		Solution seeded = solveToFixpoint(water(1000), metastable.ions(), Map.of(),
			Map.of(EngineHarness.id("calcium_chloride"), 1L), 0);
		assertTrue(sed(seeded, "calcium_chloride") >= 95,
			"one seed should collapse the metastable CaCl₂ to ~105 settled (got "
				+ sed(seeded, "calcium_chloride") + ")");
	}

	@Test
	void brineDryOutYieldsMixedBitternSediment() {
		// seawater-ish NaCl + MgCl₂: boiled dry, BOTH species must land in the
		// sediment domain — mixed crystals are the extraction problem (U15③),
		// never silent mass loss
		Solution dried = solveToFixpoint(water(0),
			ions("Na+1", 360L, "Cl-1", 440L, "Mg+2", 40L), 100);
		assertTrue(sed(dried, "rock_salt") >= 359, "NaCl dry-out (got " + sed(dried, "rock_salt") + ")");
		assertTrue(sed(dried, "magnesium_chloride") >= 39,
			"MgCl₂ dry-out (got " + sed(dried, "magnesium_chloride") + ")");
		assertNeutral(dried);
	}
}
