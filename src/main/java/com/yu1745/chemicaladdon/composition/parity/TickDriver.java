package com.yu1745.chemicaladdon.composition.parity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yu1745.chemengine.kernel.ChemState;
import com.yu1745.chemengine.kernel.Curation;
import com.yu1745.chemengine.kernel.IPhreeqc;
import com.yu1745.chemicaladdon.fluid.Mixture;

import net.minecraftforge.fluids.FluidStack;

/**
 * tick 桥：游戏时间 → KINETICS 步进 + 池变化报告（mod 侧唯一化学主循环的驱动器）。
 *
 * <p>时间映射：REACTION_TICK（10 tick = 0.5 s 游戏时间）一步；节奏旋钮 =
 * 策展 k 值（chemistry.json 设计语义）。bulk 反应全量发射。反应釜运行时的
 * 气体先由统一物理传质写入水相，不再从残余气相并行发射 interface 反应。
 *
 * <p>产出 {@link Step}：行 0 = 步进前基线（i_soln punch），末行 = 步进后终态的
 * 元素/伪池总量快照（mol，容器总量）——供 {@link WriteBack} 写回与表计消费。
 *
 * <p>注意：每次 step 从进料重起（无 KINETICS 内部状态续接）——对速率只依赖当前
 * 池浓度的反应（策展表全部如此，TOT/MOL 门控）语义等价；-m 剩量跨 tick 续接
 * 待存档联动（capacity 1000 下无影响）。
 */
public final class TickDriver {

	/** 一次步进的池快照与变化量。 */
	public static final class Step {
		public final boolean valid;
		/** 步进后元素/伪池总量（mol，容器总量）。 */
		public final Map<String, Double> totals;
		/** 相对步进前的变化（mol）。 */
		public final Map<String, Double> delta;
		/** 步进后固相量（PhaseBridge 相名 → mol，绝对量；EQUI punch）。 */
		public final Map<String, Double> phases;
		/** 相对步进前的固相变化（mol；正=析出，负=溶解）。 */
		public final Map<String, Double> phaseDelta;
		/** 相饱和指数（PhaseBridge 相名 → SI，末行；护目镜化验行）。 */
		public final Map<String, Double> phaseSi;
		public final double ph;
		public final double pe;
		public final double seconds;
		/** Raw PHREEQC solution after this step, for the exact next-step continuation. */
		public final String rawSolution;
		/** Actual kg water reported by PHREEQC at the end of this step. */
		public final double waterKg;
		/** Exact engine state for the next transaction; null only when invalid. */
		public final KernelSolutionState state;
		/** Diagnosable failure; callers must not consume or write back on failure. */
		public final String error;

		Step(boolean valid, Map<String, Double> totals, Map<String, Double> delta, double ph, double seconds) {
			this(valid, totals, delta, Map.of(), Map.of(), Map.of(), ph, 4.0, seconds, null, 0.0, null, null);
		}

		Step(boolean valid, Map<String, Double> totals, Map<String, Double> delta,
				Map<String, Double> phases, Map<String, Double> phaseDelta, Map<String, Double> phaseSi,
				double ph, double pe, double seconds) {
			this(valid, totals, delta, phases, phaseDelta, phaseSi, ph, pe, seconds, null, 0.0, null, null);
		}

		Step(boolean valid, Map<String, Double> totals, Map<String, Double> delta,
				Map<String, Double> phases, Map<String, Double> phaseDelta, Map<String, Double> phaseSi,
				double ph, double pe, double seconds, String rawSolution, double waterKg) {
			this(valid, totals, delta, phases, phaseDelta, phaseSi, ph, pe, seconds, rawSolution, waterKg,
				rawSolution == null ? null : new KernelSolutionState(rawSolution, 1), null);
		}
		Step(boolean valid, Map<String, Double> totals, Map<String, Double> delta,
				Map<String, Double> phases, Map<String, Double> phaseDelta, Map<String, Double> phaseSi,
				double ph, double pe, double seconds, String rawSolution, double waterKg,
				KernelSolutionState state, String error) {
			this.valid = valid;
			this.totals = totals;
			this.delta = delta;
			this.phases = phases;
			this.phaseDelta = phaseDelta;
			this.phaseSi = phaseSi;
			this.ph = ph;
			this.pe = pe;
			this.seconds = seconds;
			this.rawSolution = rawSolution;
			this.waterKg = waterKg;
			this.state = state;
			this.error = error;
		}
		static Step error(double seconds, String error) {
			return new Step(false, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), 7, Double.NaN,
					seconds, null, 0, null, error);
		}
	}

	private static final Curation CURATION = Curation.load();

	/** REACTION_TICK = 10 tick = 0.5 s。 */
	public static final double SECONDS_PER_STEP = 0.5;

	private TickDriver() {}

	public static Step step(List<FluidStack> fluids, double seconds) {
		return stepInternal(fluids, null, seconds, 25);
	}

	/** 测试便捷：指定温度的无供压步进。 */
	public static Step step(List<FluidStack> fluids, double seconds, int tempC) {
		return stepInternal(fluids, null, seconds, tempC);
	}

	private static Step stepInternal(List<FluidStack> fluids, Map<String, double[]> parms,
			double seconds, int tempC) {
		EngineBridge.Feed feed = new EngineBridge.Feed();
		java.util.List<KernelSolutionState> states = new java.util.ArrayList<>();
		int totalMb = 0;
		for (FluidStack stack : fluids) {
			if (Mixture.isMixture(stack)) {
				KernelSolutionState state = Mixture.engineSolution(stack);
				if (state == null) return Step.error(seconds, "missing engine-owned solution state");
				if (stack.getAmount() <= 0) return Step.error(seconds, "non-positive mixture amount");
				states.add(state);
				totalMb = Math.addExact(totalMb, stack.getAmount());
			}
		}
		if (states.isEmpty()) return Step.error(seconds, "no mixture state");
		// WriteBack has one canonical FluidStack target. Require ReactorTank to
		// collapse before ticking; otherwise committing a merged vessel state to
		// one entry while retaining the others duplicates inventory.
		if (states.size() != 1) return Step.error(seconds, "multiple mixture states require atomic tank merge");
		// 共享会话（Kernel）：数据库每 JVM 装载一次——每拍 create+装库是切换初版的性能回归
		try {
			IPhreeqc q = Kernel.get();
			synchronized (q) {
			q.clearReactants();
			// 两段模拟：初始（i_soln punch 基线，行 0）+ KINETICS 步进（末行）。
			// Step 的 totals 收集全部 punch 列（含动力学新键），不只进料键。
			KernelSolutionState raw = states.get(0).scale(q, totalMb).atTemperature(tempC);
			// RAW owns the complete component inventory. Feed is only a script
			// builder here; populate its punch vocabulary from the raw declaration
			// so Na/Cl/etc. do not disappear from readings merely because no legacy
			// four-domain feed was decoded.
			feed.totals.putAll(ChemState.fromDump(raw.raw()).totals());
			for (KernelSolutionState.SolidPhase solid : raw.solids()) {
				net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(solid.speciesId());
				PhaseBridge.PhaseDef def = id == null ? null : PhaseBridge.def(id);
				if (def != null) feed.phaseInitial.merge(def.phaseName(), solid.mol(), Double::sum);
			}
			// A SELECTED_OUTPUT declaration alone reuses the shared session's old
			// row.  Observe through the explicit zero-reaction probe so the baseline
			// is a fresh row for this exact RAW solution.  The probe's reactants are
			// deleted before the real continuation cannot inherit them.
			IPhreeqc.RunResult baseline = q.observeRestored(raw.raw(),
				new java.util.ArrayList<>(feed.punchColumns(CURATION)));
			q.clearReactants();
			IPhreeqc.RunResult r = q.runRestored(raw.raw(), feed.restoredScriptWithKinetics(CURATION, null, parms, seconds));
			int last = r.rowCount() - 1;
			int beforeRow = baseline.rowCount() - 1;
			IPhreeqc.RunResult beforeResult = baseline;
			double beforeWaterKg = beforeResult.row(beforeRow).dOr("mass_H2O", 0.0);
			double afterWaterKg = r.row(last).dOr("mass_H2O", beforeWaterKg);
			Map<String, Double> totals = new LinkedHashMap<>();
			Map<String, Double> delta = new LinkedHashMap<>();
			for (String k : feed.punchColumns(CURATION)) {
				double after = r.row(last).dOr(k, 0.0) * afterWaterKg;
				double before = beforeResult.row(beforeRow).dOr(k, 0.0) * beforeWaterKg;
				totals.put(k, after);
				delta.put(k, after - before);
			}
			// 固相终量与 SI（绝对 mol 与无量纲，EQUI/SI punch，不乘 waterKg）
			Map<String, Double> phases = new LinkedHashMap<>();
			Map<String, Double> phaseDelta = new LinkedHashMap<>();
			Map<String, Double> phaseSi = new LinkedHashMap<>();
			for (PhaseBridge.PhaseDef d : PhaseBridge.all()) {
				double mol = r.row(last).dOr(d.phaseName(), 0.0);
				double before = feed.phaseInitial.getOrDefault(d.phaseName(), 0.0);
				if (mol > 0) {
					phases.put(d.phaseName(), mol);
				}
				if (Math.abs(mol - before) > 1e-12) {
					phaseDelta.put(d.phaseName(), mol - before);
				}
				double si = r.row(last).dOr("si_" + d.phaseName(), Double.NaN);
				if (!Double.isNaN(si)) {
					phaseSi.put(d.phaseName(), si);
				}
			}
			String nextRaw = q.runDump(1);
			java.util.List<KernelSolutionState.SolidPhase> nextSolids = new java.util.ArrayList<>();
			// Preserve solid identities that PHREEQC does not model; replace every
			// modeled phase with its native terminal mol amount.
			for (KernelSolutionState.SolidPhase solid : raw.solids()) {
				net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(solid.speciesId());
				if (id == null || PhaseBridge.def(id) == null) nextSolids.add(solid);
			}
			for (PhaseBridge.PhaseDef def : PhaseBridge.all()) {
				double mol = phases.getOrDefault(def.phaseName(), 0.0);
				if (mol <= 0) continue;
				double suspended = 0, sediment = 0;
				for (KernelSolutionState.SolidPhase old : raw.solids()) if (old.speciesId().equals(def.species().toString())) {
					if (old.location() == KernelSolutionState.SolidLocation.SUSPENDED) suspended += old.mol(); else sediment += old.mol();
				}
				double oldTotal = suspended + sediment;
				if (oldTotal > 0 && mol <= oldTotal) {
					nextSolids.add(new KernelSolutionState.SolidPhase(def.species().toString(), mol * suspended / oldTotal, KernelSolutionState.SolidLocation.SUSPENDED));
					nextSolids.add(new KernelSolutionState.SolidPhase(def.species().toString(), mol * sediment / oldTotal, KernelSolutionState.SolidLocation.SEDIMENT));
				} else {
					if (suspended > 0) nextSolids.add(new KernelSolutionState.SolidPhase(def.species().toString(), suspended, KernelSolutionState.SolidLocation.SUSPENDED));
					if (sediment > 0) nextSolids.add(new KernelSolutionState.SolidPhase(def.species().toString(), sediment, KernelSolutionState.SolidLocation.SEDIMENT));
					nextSolids.add(new KernelSolutionState.SolidPhase(def.species().toString(), mol - oldTotal, KernelSolutionState.SolidLocation.SUSPENDED));
				}
			}
			KernelSolutionState next = new KernelSolutionState(nextRaw, totalMb, nextSolids);
			return new Step(true, totals, delta, phases, phaseDelta, phaseSi,
				r.row(last).d("pH"), r.row(last).d("pe"), seconds, nextRaw, afterWaterKg, next, null);
			}
		} catch (Exception e) {
			com.yu1745.chemicaladdon.ChemicalAddon.LOGGER.warn(
				"[engine] native tick continuation failed: {}", e.toString());
			return Step.error(seconds, e.getMessage());
		}
	}
}
