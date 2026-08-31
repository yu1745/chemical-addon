package com.yu1745.chemicaladdon.composition.parity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yu1745.chemengine.kernel.ChemState;
import com.yu1745.chemengine.kernel.Curation;
import com.yu1745.chemengine.kernel.IPhreeqc;

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
		public final double seconds;

		Step(boolean valid, Map<String, Double> totals, Map<String, Double> delta, double ph, double seconds) {
			this(valid, totals, delta, Map.of(), Map.of(), Map.of(), ph, seconds);
		}

		Step(boolean valid, Map<String, Double> totals, Map<String, Double> delta,
				Map<String, Double> phases, Map<String, Double> phaseDelta, Map<String, Double> phaseSi,
				double ph, double seconds) {
			this.valid = valid;
			this.totals = totals;
			this.delta = delta;
			this.phases = phases;
			this.phaseDelta = phaseDelta;
			this.phaseSi = phaseSi;
			this.ph = ph;
			this.seconds = seconds;
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
		EngineBridge.Feed feed = EngineBridge.toFeed(fluids);
		feed.tempC = tempC;
		if (feed.waterKg <= 0 || (feed.totals.isEmpty() && feed.phaseInitial.isEmpty())) {
			return new Step(false, Map.of(), Map.of(), 7.0, seconds);
		}
		ChemState.Builder b = ChemState.builder("tick")
				.waterKg(feed.waterKg)
				.pHCharge()
				.tempC(feed.tempC);
		boolean any = false;
		for (Map.Entry<String, Double> e : feed.totals.entrySet()) {
			if (e.getValue() > 0) {
				b.total(e.getKey(), e.getValue());
				any = true;
			}
		}
		if (!any && feed.phaseInitial.isEmpty()) {
			return new Step(false, Map.of(), Map.of(), 7.0, seconds);
		}
		// 共享会话（Kernel）：数据库每 JVM 装载一次——每拍 create+装库是切换初版的性能回归
		try {
			IPhreeqc q = Kernel.get();
			// 两段模拟：初始（i_soln punch 基线，行 0）+ KINETICS 步进（末行）。
			// Step 的 totals 收集全部 punch 列（含动力学新键），不只进料键。
			IPhreeqc.RunResult r = q.run(feed.toScriptWithKinetics(CURATION, seconds));
			int last = r.rowCount() - 1;
			Map<String, Double> totals = new LinkedHashMap<>();
			Map<String, Double> delta = new LinkedHashMap<>();
			for (String k : feed.punchColumns(CURATION)) {
				double after = r.row(last).dOr(k, 0.0) * feed.waterKg;
				double before = r.row(0).dOr(k, 0.0) * feed.waterKg;
				totals.put(k, after);
				delta.put(k, after - before);
			}
			// 固相终量与 SI（绝对 mol 与无量纲，EQUI/SI punch，不乘 waterKg）
			Map<String, Double> phases = new LinkedHashMap<>();
			Map<String, Double> phaseDelta = new LinkedHashMap<>();
			Map<String, Double> phaseSi = new LinkedHashMap<>();
			for (PhaseBridge.PhaseDef d : PhaseBridge.all()) {
				double mol = r.row(last).dOr(d.phaseName(), 0.0);
				double before = r.row(0).dOr(d.phaseName(), 0.0);
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
			return new Step(true, totals, delta, phases, phaseDelta, phaseSi, r.row(last).d("pH"), seconds);
		} catch (Exception e) {
			com.yu1745.chemicaladdon.ChemicalAddon.LOGGER.warn(
				"[engine] tick step failed: {} — script:\n{}", e.toString(),
				feed.toScriptWithKinetics(CURATION, seconds));
			return new Step(false, Map.of(), Map.of(), 7.0, seconds);
		}
	}
}
