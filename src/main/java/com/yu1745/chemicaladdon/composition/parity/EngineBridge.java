package com.yu1745.chemicaladdon.composition.parity;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yu1745.chemengine.kernel.Curation;
import com.yu1745.chemengine.kernel.ChemicalBasis;
import com.yu1745.chemengine.kernel.IPhreeqc;
import com.yu1745.chemicaladdon.composition.Species;
import com.yu1745.chemicaladdon.composition.SpeciesManager;

import net.minecraft.resources.ResourceLocation;

/**
 * Native-state adapter: declared external materials create RAW states, kinetics
 * continues those states, and read-only projections query them directly.
 * Display tags are never decoded here. Unsupported external material is
 * rejected at its declaration boundary instead of being inferred from ions.
 */
public final class EngineBridge {

	/** 内建元素摩尔质量表（g/mol）——species JSON 的 formula 解析用（与 WriteBack 共享语义）。 */
	static final Map<String, Double> ATOMIC = Map.ofEntries(
			Map.entry("Cl", 35.45), Map.entry("Na", 22.99), Map.entry("Ca", 40.08),
			Map.entry("Mg", 24.31), Map.entry("Ba", 137.33), Map.entry("Fe", 55.85),
			Map.entry("Cu", 63.55), Map.entry("Zn", 65.38), Map.entry("Al", 26.98),
			Map.entry("S", 32.06), Map.entry("K", 39.10), Map.entry("Ag", 107.87),
			Map.entry("Mn", 54.94), Map.entry("Pb", 207.2), Map.entry("Ni", 58.69),
			Map.entry("Co", 58.93), Map.entry("Si", 28.09), Map.entry("P", 30.97),
			Map.entry("F", 19.00), Map.entry("Br", 79.90), Map.entry("I", 126.90),
			Map.entry("N", 14.007), Map.entry("C", 12.011));

	private static final ChemicalBasis BASIS = ChemicalBasis.loadDefault();

	/**
	 * 单位桥统一计价（与水同幕）：1 unit = 1e-7 g 水 = <b>1e-7 mol</b> 离子/物种
	 * formula unit，即 1 mB（1e4 unit）= 1e-3 mol——legacy 浓度比
	 * （离子 units/水 units）恰为 millimolal，所有旧读数/配方浓度/写回往返
	 * 在此计价下精确自洽。水 kg = waterUnits/1e7（同一除数）。
	 */
	public static final double UNITS_PER_MOL = 10_000_000.0;

	/** KINETICS script metadata derived from the native state and solid ledger. */
	public static final class Feed {
		public final Map<String, Double> totals = new LinkedHashMap<>();
		/** 固相初量（PhaseBridge 相名 → mol；悬浮域中可相映射的物种）。 */
		public final Map<String, Double> phaseInitial = new LinkedHashMap<>();

		/**
		 * punch 列 = 进料元素 ∪ 全部伪池（纯水进料也要能 punch 出界面池/动力学产物
		 * 的增量；Step 的 totals 收集同一集合——Quench 产的 Cl、Nitri→Nitra 等新键
		 * 不在进料里，但必须进写回）。
		 */
		public java.util.Set<String> punchColumns(Curation curation) {
			java.util.Set<String> cols = new java.util.LinkedHashSet<>(totals.keySet());
			for (Curation.PseudoElement pe : curation.pseudoElements()) {
				cols.add(pe.element);
			}
			// 动力学可能创造的元素也要 punch（Quench 产 Cl/S、Nitri→Nitra 等）——
			// H/O 除外（非输入量，且 WriteBack 不消费）
			for (Curation.Reaction rx : curation.reactions()) {
				for (String token : rx.formulaView().keySet()) {
					if (!"H".equals(token) && !"O".equals(token)) {
						cols.add(token);
					}
				}
			}
			// 相元素（固相溶解/析出的账目，纯水+悬浮固也要 punch）
			for (PhaseBridge.PhaseDef d : PhaseBridge.all()) {
				for (String el : d.elements()) {
					cols.add(el);
				}
			}
			return cols;
		}

		/**
		 * Continuation for a restored SOLUTION_RAW 1. This never creates a new
		 * SOLUTION or issues {@code pH 7 charge}: the archived aqueous speciation,
		 * charge state and water inventory remain the input state. Phase inventory is
		 * still supplied from the game solid domain because SOLUTION_RAW alone does
		 * not archive EQUILIBRIUM_PHASES.
		 */
		public String restoredScriptWithKinetics(Curation curation, java.util.Set<String> include,
				Map<String, double[]> parmOverrides, double seconds) {
			StringBuilder punchList = new StringBuilder();
			for (String k : punchColumns(curation)) {
				punchList.append(' ').append(k);
			}
			String phases = PhaseBridge.equilibriumPhasesBlock(phaseInitial);
			StringBuilder script = new StringBuilder(PhaseBridge.phasesBlock());
			script.append(phases);
			script.append("SELECTED_OUTPUT 1\n")
					.append("    -state          true\n")
					.append("    -time           true\n")
					.append("    -high_precision true\n")
					.append("    -totals  ").append(punchList.toString().trim()).append('\n')
					.append("    -pH       true\n")
					.append("    -pe       true\n")
					.append("    -water    true\n")
					.append(PhaseBridge.userPunchBlock());
			return script + curation.ratesBlock()
					+ IPhreeqc.runtimeKnobsInline()
					+ "USE solution 1\n"
					+ (phases.isBlank() ? "" : "USE equilibrium_phases 1\n")
					+ curation.kineticsBlock(include, parmOverrides, seconds)
					+ "\nSAVE solution 1\n";
		}

	}

	private EngineBridge() {}

	/** Native, read-only display projection. Values are actual mol, never dominant-ion substitutions. */
	public record DerivedSolution(double waterKg, double ph, double pe, double alkalinityEq, Map<String, Double> totalMol, Map<String, Double> aqueousMol,
			List<KernelSolutionState.SolidPhase> solids) {}

	/**
	 * Query the database's named aqueous species from a RAW state. Unsupported
	 * names fail at the native boundary instead of being silently renamed.
	 */
	public static DerivedSolution derive(IPhreeqc q, KernelSolutionState state, Collection<String> species) {
		return derive(q, state, List.of(), species);
	}
	public static DerivedSolution derive(IPhreeqc q, KernelSolutionState state, Collection<String> totals,
			Collection<String> species) {
		if (state == null || species == null) throw new IllegalArgumentException("state and species are required");
		IPhreeqc.RunResult result = q.observeRestored(state.raw(), List.copyOf(totals), species.toArray(String[]::new));
		if (result.rowCount() == 0) throw new IllegalStateException("native observation produced no row");
		IPhreeqc.RunResult.Row row = result.row(result.rowCount() - 1);
		double water = row.d("mass_H2O");
		Map<String, Double> actual = new LinkedHashMap<>();
		for (String name : species) {
			double molal = row.dOr("m_" + name, Double.NaN);
			if (Double.isNaN(molal)) throw new IllegalArgumentException("native output has no species " + name);
			actual.put(name, molal * water);
		}
		Map<String, Double> totalMol = new LinkedHashMap<>();
		for (String name : totals) totalMol.put(name, row.dOr(name, Double.NaN) * water);
		return new DerivedSolution(water, row.d("pH"), row.d("pe"),
			row.d("native_alk_eq_per_kg") * water, Map.copyOf(totalMol), Map.copyOf(actual), state.solids());
	}

	/**
	 * Explicit external-feed mapping for a declared species amount.  The result
	 * uses an authored neutral real formula and mol, never the old display ion
	 * encoding. KernelSolutionState translates it once at the engine boundary.
	 * A missing mapping is an error at the transaction boundary.
	 */
	public static Map<String, Double> declaredFeedForSpecies(ResourceLocation speciesId, double formulaMol) {
		if (!(formulaMol >= 0.0) || !Double.isFinite(formulaMol))
			throw new IllegalArgumentException("formula amount must be finite and non-negative");
		Species species = SpeciesManager.get(speciesId);
		if (species == null || !species.isElectrolyte())
			throw new IllegalArgumentException("no declared aqueous model for " + speciesId);
		// Do not turn dissociation display ions back into guessed master totals.
		String formula = species.engineFormula();
		if (formula == null) throw new IllegalArgumentException("no PHREEQC formula declared for " + speciesId);
		return Map.of(formula, formulaMol);
	}

	/** Native component lookup for authored phase equations; feed conversion belongs to KernelSolutionState. */
	public static String phreeqcComponent(String symbol) {
		return BASIS.phreeqcComponent(symbol);
	}

}
