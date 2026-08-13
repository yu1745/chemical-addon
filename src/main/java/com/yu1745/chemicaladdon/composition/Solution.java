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
 *       (cooling below its solubility curve removes its whole neutral ion set);</li>
 *   <li>{@link #neutralise()} — H+ + OH- → H₂O (exothermic);</li>
 *   <li>{@link #precipitate()} — insoluble solids form from ions, least-soluble
 *       (lowest Ksp) first.</li>
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
	private final Map<ResourceLocation, Long> precipitates = new LinkedHashMap<>(); // solid species → mB
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

	/** Precipitated/crystallised solids (solid species id → mB) to emit as items. */
	public Map<ResourceLocation, Long> precipitates() {
		return precipitates;
	}

	/** °C rise from exothermic reactions (apply to the tank contents). */
	public int heat() {
		return heat;
	}

	// ------------------------------------------------------------ pipeline steps

	/**
	 * A supersaturated solute (solubility at the current temperature &lt; its fixed
	 * concentration) crashes out as a solid. S2 simplification: the solute's whole
	 * neutral ion set is removed in one go (no partial crystallisation yet).
	 */
	private void crystallise() {
		for (Species s : SpeciesManager.all()) {
			if (!s.isCrystallisable() || !s.isElectrolyte()) {
				continue;
			}
			if (s.solubilityAt(temperature) >= s.concentration()) {
				continue; // unsaturated at this temperature
			}
			long form = maxFormable(s);
			if (form <= 0) {
				continue;
			}
			for (Species.IonComponent c : s.ions()) {
				subtract(c.ion().id(), form * c.count());
			}
			precipitates.merge(s.solute(), form, Long::sum);
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
				precipitates.merge(s.id(), form, Long::sum);
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
