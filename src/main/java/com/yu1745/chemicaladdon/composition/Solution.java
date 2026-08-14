package com.yu1745.chemicaladdon.composition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yu1745.chemicaladdon.ChemicalAddon;

import net.minecraft.resources.ResourceLocation;

/**
 * Transient snapshot the rules engine solves on: one miscible phase's ion
 * multiset + molecular species (amounts in mB) + temperature. This object is
 * rebuilt every tick and is <b>never</b> persisted in a FluidStack — it is a
 * pure function over the mixture's {@code Ions} + {@code Molecules} domains.
 *
 * <p>Ions are now the <b>persistent</b> state (a solution species expands to its
 * ions + water when it enters a vessel), so there is no dissociation/recombination
 * round-trip: the solver edits the ion multiset directly.
 *
 * <p>Solver pipeline (see plans/03 §8):
 * <ol>
 *   <li>{@link #crystallise()} — a supersaturated solute crashes out as a solid
 *       (cooling below its solubility curve removes its whole neutral ion set).
 *       Slow → the solid <b>settles</b> to the bottom ({@link #sediment()});</li>
 *   <li>{@link #neutralise()} — H+ + OH- → H₂O (exothermic);</li>
 *   <li>{@link #precipitate()} — insoluble solids form from ions, least-soluble
 *       (lowest Ksp) first. Fast → the solid stays <b>suspended</b> as a turbid
 *       slurry ({@link #suspended()}).</li>
 * </ol>
 *
 * <p>Amounts are "mole-equivalents": 1 mB × a species' ion count gives that many
 * ion units, so stoichiometry is exact integer arithmetic.
 */
public final class Solution {

	/** The water species id (solvent). */
	public static final ResourceLocation WATER = new ResourceLocation("minecraft", "water");

	private static final String H = "H+1";
	private static final String OH = "OH-1";

	private final Map<String, Long> ions = new LinkedHashMap<>(); // ion id → units
	private final Map<ResourceLocation, Long> molecular = new LinkedHashMap<>(); // water/gases/molecular solutes → mB
	private final Map<ResourceLocation, Long> suspended = new LinkedHashMap<>(); // precipitated solid species → mB (slurry)
	private final Map<ResourceLocation, Long> sediment = new LinkedHashMap<>(); // crystallised solid species → mB (settles)
	private final int temperature;
	private int heat; // °C added by exothermic neutralisation

	public Solution(Map<ResourceLocation, Long> molecules, Map<String, Long> ionAmounts, int temperature) {
		for (Map.Entry<ResourceLocation, Long> e : molecules.entrySet()) {
			if (e.getValue() > 0) {
				this.molecular.put(e.getKey(), e.getValue());
			}
		}
		for (Map.Entry<String, Long> e : ionAmounts.entrySet()) {
			if (e.getValue() > 0) {
				this.ions.put(e.getKey(), e.getValue());
			}
		}
		this.temperature = temperature;
	}

	public void solve() {
		crystallise();
		neutralise();
		precipitate();
		removeNonPositive(molecular);
		removeNonPositive(ions);
	}

	/** Final molecular composition (species id → mB) to write back to the mixture. */
	public Map<ResourceLocation, Long> molecular() {
		return molecular;
	}

	/** Final ion multiset (ion id → units) to write back to the mixture. */
	public Map<String, Long> ions() {
		return ions;
	}

	/** Precipitated solids (solid species id → mB) — stay suspended as a turbid slurry. */
	public Map<ResourceLocation, Long> suspended() {
		return suspended;
	}

	/** Crystallised solids (solid species id → mB) — sink to the bottom as sediment. */
	public Map<ResourceLocation, Long> sediment() {
		return sediment;
	}

	/** °C rise from exothermic reactions (apply to the tank contents). */
	public int heat() {
		return heat;
	}

	// ------------------------------------------------------------ pipeline steps

	/**
	 * Global solubility scale (unit convention, plans/03): solubility is authored
	 * as real-world g solute / 100 g water, and the in-game threshold is
	 * {@code gPer100g / 100 × SOLUBILITY_SCALE} formula units per water mB. This
	 * declares "1 formula unit ≡ 1 g" and "1 mB water ≡ 1 g" and deliberately
	 * ignores molar mass — raise/lower this one constant to rescale every
	 * substance's crystallisation point without touching the data tables.
	 */
	public static final double SOLUBILITY_SCALE = 1.0;

	/** The "per 100 g water" divisor in real solubility tables. */
	private static final double SOLUBILITY_PER_100G = 100.0;

	/**
	 * A supersaturated solute crashes out as a solid: the vessel's continuous
	 * concentration (solute formula units / water mB) exceeds the solubility curve
	 * at the current temperature. S2 simplification: the solute's whole neutral ion
	 * set is removed in one go (no partial crystallisation yet).
	 */
	private void crystallise() {
		double water = molecular.getOrDefault(WATER, 0L);
		if (water <= 0) {
			return; // no solvent → nothing is "dissolved", nothing can be supersaturated
		}
		for (Species s : SpeciesManager.all()) {
			if (!s.isCrystallisable() || !s.isElectrolyte()) {
				continue;
			}
			long form = maxFormable(s); // formula units of this solute dissolved
			if (form <= 0) {
				continue;
			}
			double concentration = (double) form / water; // formula units / water mB
			double threshold = s.solubilityAt(temperature) / SOLUBILITY_PER_100G * SOLUBILITY_SCALE;
			if (threshold >= concentration) {
				continue; // unsaturated at this temperature
			}
			for (Species.IonComponent c : s.ions()) {
				subtract(c.ion().id(), form * c.count());
			}
			sediment.merge(s.solute(), form, Long::sum);
		}
	}

	/** H+ + OH- → H₂O, limited by the scarcer ion. Exothermic. */
	private void neutralise() {
		long n = Math.min(ions.getOrDefault(H, 0L), ions.getOrDefault(OH, 0L));
		if (n <= 0) {
			return;
		}
		subtract(H, n);
		subtract(OH, n);
		molecular.merge(WATER, n, Long::sum);
		heat += (int) (n * NEUTRALISATION_HEAT_PER_MB);
	}

	/** Insoluble solids form from ions, least soluble (lowest Ksp) first. */
	private void precipitate() {
		List<Species> candidates = new ArrayList<>();
		for (Species s : SpeciesManager.all()) {
			if (s.isPrecipitate() && s.isElectrolyte()) {
				candidates.add(s);
			}
		}
		candidates.sort(Comparator.comparingDouble(Species::ksp));
		for (Species s : candidates) {
			while (true) {
				long form = maxFormable(s);
				if (form <= 0) {
					break;
				}
				for (Species.IonComponent c : s.ions()) {
					subtract(c.ion().id(), form * c.count());
				}
				suspended.merge(s.id(), form, Long::sum);
			}
		}
	}

	// ---------------------------------------------------------------- helpers

	/** Largest whole amount of {@code s} formable from the available ions. */
	private long maxFormable(Species s) {
		long form = Long.MAX_VALUE;
		for (Species.IonComponent c : s.ions()) {
			long avail = ions.getOrDefault(c.ion().id(), 0L);
			form = Math.min(form, avail / c.count());
		}
		return form;
	}

	private void subtract(String ionId, long n) {
		long remaining = ions.getOrDefault(ionId, 0L) - n;
		if (remaining <= 0) {
			ions.remove(ionId);
		} else {
			ions.put(ionId, remaining);
		}
	}

	private static void removeNonPositive(Map<?, Long> map) {
		map.values().removeIf(v -> v <= 0);
	}

	/** °C added per mB of water formed by neutralisation (+50°C per 1000 mB). */
	private static final double NEUTRALISATION_HEAT_PER_MB = 0.05;
}
