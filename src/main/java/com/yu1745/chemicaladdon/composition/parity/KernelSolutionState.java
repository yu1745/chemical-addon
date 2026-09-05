package com.yu1745.chemicaladdon.composition.parity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yu1745.chemengine.kernel.ChemicalBasis;
import com.yu1745.chemengine.kernel.IPhreeqc;

/**
 * Engine-owned aqueous batch.  Its raw PHREEQC state is the only chemical
 * truth; {@code referenceMb} makes the state transportable through Forge's
 * proportional FluidStack copies.
 */
public record KernelSolutionState(String raw, int referenceMb, List<SolidPhase> solids) {
	public static final int VERSION = 2;
	private static final ChemicalBasis BASIS = ChemicalBasis.loadDefault();
	public enum SolidLocation { SUSPENDED, SEDIMENT }
	public record SolidPhase(String speciesId, double mol, SolidLocation location) {
		public SolidPhase {
			if (speciesId == null || speciesId.isBlank() || !(mol >= 0) || !Double.isFinite(mol) || location == null)
				throw new IllegalArgumentException("solid needs species, finite mol, and location");
		}
	}
	/** Result of an exact transport extraction; neither input object mutates. */
	public record ProportionalRemoval(KernelSolutionState removed, KernelSolutionState remainder) {}

	public KernelSolutionState {
		if (raw == null || raw.isBlank() || referenceMb <= 0)
			throw new IllegalArgumentException("raw solution and reference amount are required");
		solids = canonicalSolids(solids);
	}
	public KernelSolutionState(String raw, int referenceMb) { this(raw, referenceMb, List.of()); }

	/** New external material only; never reinterpret an existing display view. */
	public static KernelSolutionState fromDeclaredFeed(IPhreeqc q, double waterKg,
			Map<String, Double> declaredMol, int referenceMb) {
		return new KernelSolutionState(q.declaredSolution(waterKg, BASIS.internalFormulae(declaredMol), 25), referenceMb, List.of());
	}
	public static KernelSolutionState fromDeclaredFeed(IPhreeqc q, double waterKg,
			Map<String, Double> declaredMol, int referenceMb, double temperatureC, Collection<SolidPhase> solids) {
		return new KernelSolutionState(q.declaredSolution(waterKg, BASIS.internalFormulae(declaredMol), temperatureC), referenceMb,
				new ArrayList<>(solids));
	}

	/** Materialize this batch at an actual fluid amount without equilibrating it. */
	public KernelSolutionState scale(IPhreeqc q, int amountMb) {
		if (amountMb <= 0) throw new IllegalArgumentException("amount must be positive");
		if (amountMb == referenceMb) return this;
		double factor = (double) amountMb / referenceMb;
		return new KernelSolutionState(q.scaleRestored(raw, factor), amountMb, scaleSolids(factor));
	}
	public KernelSolutionState atAmount(IPhreeqc q, int amountMb) { return scale(q, amountMb); }

	/** Change only the physical temperature while preserving archived pools. */
	public KernelSolutionState atTemperature(int temperatureC) {
		return new KernelSolutionState(IPhreeqc.withRestoredTemperature(raw, temperatureC), referenceMb, solids);
	}

	public static KernelSolutionState merge(IPhreeqc q, Collection<KernelSolutionState> states) {
		if (states == null || states.isEmpty()) throw new IllegalArgumentException("states are required");
		Map<String, Double> raws = new LinkedHashMap<>(); List<SolidPhase> solids = new ArrayList<>(); long mb = 0;
		for (KernelSolutionState state : states) {
			if (state == null) throw new IllegalArgumentException("state is required");
			raws.merge(state.raw, 1d, Double::sum); solids.addAll(state.solids); mb = Math.addExact(mb, state.referenceMb);
		}
		if (mb > Integer.MAX_VALUE) throw new IllegalArgumentException("merged amount overflows int");
		return new KernelSolutionState(q.mixSolutions(raws), (int) mb, solids);
	}
	public KernelSolutionState addDeclaredFeed(IPhreeqc q, KernelSolutionState external) {
		return merge(q, List.of(this, external));
	}
	/** Signed neutral-formula transaction against this exact aqueous state. */
	public KernelSolutionState reactDeclared(IPhreeqc q, Map<String, Double> formulaMol) {
		return new KernelSolutionState(q.reactRestored(raw, BASIS.signedInternalFormulae(formulaMol)), referenceMb, solids);
	}
	/** Remove declared neutral formula inventory; native failure leaves this immutable state untouched. */
	public KernelSolutionState removeDeclaredFeed(IPhreeqc q, Map<String, Double> formulaMol) {
		Map<String, Double> negative = new LinkedHashMap<>();
		for (Map.Entry<String, Double> entry : formulaMol.entrySet()) {
			if (entry.getValue() == null || !(entry.getValue() >= 0) || !Double.isFinite(entry.getValue()))
				throw new IllegalArgumentException("removal amount must be finite and non-negative");
			negative.put(entry.getKey(), -entry.getValue());
		}
		return reactDeclared(q, negative);
	}
	/** Water leaves as H2O, never as a display-domain correction. */
	public KernelSolutionState evaporateWater(IPhreeqc q, double waterKg) {
		if (!(waterKg > 0) || !Double.isFinite(waterKg)) throw new IllegalArgumentException("water removal must be positive");
		// PHREEQC's database owns the formula weight.  Do not duplicate an H/O
		// constant here: sit.dat's native GFW is also what REACTION uses.
		return reactDeclared(q, Map.of("H2O", -(waterKg * 1000d) / q.formulaWeight("H2O")));
	}
	/**
	 * Split a transport amount from this state.  The two returned states are
	 * independently scaled canonical inventories; callers must replace their
	 * stored state with {@link ProportionalRemoval#remainder()} only after the
	 * external output has accepted {@link ProportionalRemoval#removed()}.
	 */
	public ProportionalRemoval removeProportionally(IPhreeqc q, int amountMb) {
		if (amountMb <= 0 || amountMb >= referenceMb)
			throw new IllegalArgumentException("removal must be between zero and the reference amount");
		return new ProportionalRemoval(scale(q, amountMb), scale(q, referenceMb - amountMb));
	}
	public KernelSolutionState withSolids(List<SolidPhase> solids) { return new KernelSolutionState(raw, referenceMb, solids); }
	private List<SolidPhase> scaleSolids(double factor) {
		List<SolidPhase> out = new ArrayList<>();
		for (SolidPhase solid : solids) out.add(new SolidPhase(solid.speciesId, solid.mol * factor, solid.location));
		return out;
	}
	private static List<SolidPhase> canonicalSolids(Collection<SolidPhase> input) {
		Map<String, Double> totals = new LinkedHashMap<>();
		if (input != null) for (SolidPhase solid : input) {
			if (solid == null) throw new IllegalArgumentException("solid is required");
			totals.merge(solid.location + "\u0000" + solid.speciesId, solid.mol, Double::sum);
		}
		List<SolidPhase> out = new ArrayList<>();
		for (Map.Entry<String, Double> entry : totals.entrySet()) if (entry.getValue() > 0) {
			int i = entry.getKey().indexOf('\u0000');
			out.add(new SolidPhase(entry.getKey().substring(i + 1), entry.getValue(),
					SolidLocation.valueOf(entry.getKey().substring(0, i))));
		}
		return List.copyOf(out);
	}
}
