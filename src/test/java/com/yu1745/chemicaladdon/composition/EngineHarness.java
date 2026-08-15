package com.yu1745.chemicaladdon.composition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;

/**
 * Shared fixture for the headless engine tests: loads the real species data
 * from the classpath, offers terse constructors for solver states, and
 * domain/color assertions. The engine is pure Java — only {@link
 * ResourceLocation} is touched, which needs no Minecraft bootstrap.
 */
public final class EngineHarness {

	private static boolean loaded;

	private EngineHarness() {}

	/** Load the built-in species once per JVM (idempotent). */
	public static void load() {
		if (!loaded) {
			SpeciesManager.loadBuiltin();
			loaded = true;
		}
	}

	public static ResourceLocation id(String path) {
		return new ResourceLocation(Chemistry.MOD_ID, path);
	}

	public static Map<ResourceLocation, Long> water(long mB) {
		return mol(Solution.WATER, mB);
	}

	public static Map<ResourceLocation, Long> mol(Object... kv) {
		Map<ResourceLocation, Long> out = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			Object k = kv[i];
			ResourceLocation key = k instanceof ResourceLocation r ? r : id((String) k);
			out.put(key, (Long) kv[i + 1]);
		}
		return out;
	}

	public static Map<String, Long> ions(Object... kv) {
		Map<String, Long> out = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) {
			out.put((String) kv[i], (Long) kv[i + 1]);
		}
		return out;
	}

	/** A quick solve: water + ions at a temperature, no initial solids. */
	public static Solution solve(long waterMol, Map<String, Long> ions, int tempC) {
		return solve(water(waterMol), ions, Map.of(), Map.of(), tempC);
	}

	/** A quick solve: molecules (water + solutes) + ions, no initial solids. */
	public static Solution solve(Map<ResourceLocation, Long> mol, Map<String, Long> ions, int tempC) {
		return solve(mol, ions, Map.of(), Map.of(), tempC);
	}

	public static Solution solve(Map<ResourceLocation, Long> mol, Map<String, Long> ions,
			Map<ResourceLocation, Long> suspended, Map<ResourceLocation, Long> sediment, int tempC) {
		Solution s = new Solution(mol, ions, suspended, sediment, tempC);
		s.solve();
		return s;
	}

	/**
	 * Re-solve the state until it stops moving (the vessel equivalent: the
	 * rules engine runs every reaction tick, so multi-step chains — titration,
	 * amphoterism — legitimately take several solves to settle). Returns the
	 * last solved snapshot.
	 */
	/** Fixpoint variant with no initial solids. */
	public static Solution solveToFixpoint(Map<ResourceLocation, Long> mol, Map<String, Long> ions, int tempC) {
		return solveToFixpoint(mol, ions, Map.of(), Map.of(), tempC);
	}

	/** Fixpoint variant: plain water + ions. */
	public static Solution solveToFixpoint(long water, Map<String, Long> ions, int tempC) {
		return solveToFixpoint(water(water), ions, Map.of(), Map.of(), tempC);
	}

	public static Solution solveToFixpoint(Map<ResourceLocation, Long> mol, Map<String, Long> ions,
			Map<ResourceLocation, Long> suspended, Map<ResourceLocation, Long> sediment, int tempC) {
		Solution prev = solve(mol, ions, suspended, sediment, tempC);
		// kinetic states (unseeded crystallisation especially) need a generous budget
		for (int i = 0; i < 4000; i++) {
			Solution next = solve(Map.copyOf(prev.molecular()), Map.copyOf(prev.ions()),
				Map.copyOf(prev.suspended()), Map.copyOf(prev.sediment()), tempC);
			if (next.molecular().equals(prev.molecular()) && next.ions().equals(prev.ions())
				&& next.suspended().equals(prev.suspended()) && next.sediment().equals(prev.sediment())) {
				return next;
			}
			prev = next;
		}
		return prev;
	}

	public static long ion(Solution s, String ionId) {
		return s.ions().getOrDefault(ionId, 0L);
	}

	public static long susp(Solution s, String path) {
		return s.suspended().getOrDefault(id(path), 0L);
	}

	public static long sed(Solution s, String path) {
		return s.sediment().getOrDefault(id(path), 0L);
	}

	public static void assertIon(Solution s, String ionId, long expected) {
		assertEquals(expected, ion(s, ionId), "ion " + ionId + " (all: " + s.ions() + ")");
	}

	public static void assertIonNear(Solution s, String ionId, long expected, long tolerance) {
		long got = ion(s, ionId);
		assertTrue(Math.abs(got - expected) <= tolerance,
			"ion " + ionId + ": expected " + expected + "±" + tolerance + " but got " + got + " (all: " + s.ions() + ")");
	}

	public static void assertSuspended(Solution s, String path, long expected) {
		assertEquals(expected, susp(s, path), "suspended " + path + " (all: " + s.suspended() + ")");
	}

	public static void assertSediment(Solution s, String path, long expected) {
		assertEquals(expected, sed(s, path), "sediment " + path + " (all: " + s.sediment() + ")");
	}

	/** Charge neutrality of an ion map (the mixture's hard invariant). */
	public static long netCharge(Map<String, Long> ions) {
		long q = 0;
		for (Map.Entry<String, Long> e : ions.entrySet()) {
			q += (long) Ion.chargeOf(e.getKey()) * e.getValue();
		}
		return q;
	}

	public static void assertNeutral(Solution s) {
		assertEquals(0L, netCharge(s.ions()), "charge neutrality violated: " + s.ions());
	}

	/** The solved state's blended tint (Mixture.blendColor, integer-domain bridge). */
	public static int tintOf(Solution s) {
		Map<ResourceLocation, Integer> m = new LinkedHashMap<>();
		s.molecular().forEach((k, v) -> m.put(k, (int) Math.min(v, Integer.MAX_VALUE)));
		Map<String, Integer> i = new LinkedHashMap<>();
		s.ions().forEach((k, v) -> i.put(k, (int) Math.min(v, Integer.MAX_VALUE)));
		Map<ResourceLocation, Integer> su = new LinkedHashMap<>();
		s.suspended().forEach((k, v) -> su.put(k, (int) Math.min(v, Integer.MAX_VALUE)));
		return com.yu1745.chemicaladdon.fluid.Mixture.blendColor(m, i, su);
	}
}
