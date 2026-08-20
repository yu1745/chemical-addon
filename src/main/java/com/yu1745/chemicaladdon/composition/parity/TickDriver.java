package com.yu1745.chemicaladdon.composition.parity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yu1745.chemengine.kernel.ChemState;
import com.yu1745.chemengine.kernel.Curation;
import com.yu1745.chemengine.kernel.IPhreeqc;

import net.minecraftforge.fluids.FluidStack;

/**
 * P4 tick 桥：游戏时间 → KINETICS 步进 + 池变化报告。
 *
 * <p>时间映射：REACTION_TICK（10 tick = 0.5 s 游戏时间）一步；节奏旋钮 =
 * 策展 k 值（chemistry.json 设计语义）。bulk 反应全量发射（界面反应需场景
 * 显式供 PARM，本层不管——P4c 接压力表）。
 *
 * <p>产出 {@link Step}：步进后的元素/伪池总量快照（mol）——供写回层
 * （P4b：元素总量 → Mixture 物种树的反向映射）与表计消费。当前阶段
 * Step 作为观察值落日志 + GameTest 断言，不动 Mixture。
 */
public final class TickDriver {

	/** 一次步进的池快照与变化量。 */
	public static final class Step {
		public final boolean valid;
		/** 步进后元素/伪池总量（mol，容器总量）。 */
		public final Map<String, Double> totals;
		/** 相对步进前的变化（mol）。 */
		public final Map<String, Double> delta;
		public final double ph;
		public final double seconds;

		Step(boolean valid, Map<String, Double> totals, Map<String, Double> delta, double ph, double seconds) {
			this.valid = valid;
			this.totals = totals;
			this.delta = delta;
			this.ph = ph;
			this.seconds = seconds;
		}
	}

	private static final Curation CURATION = Curation.load();

	/** REACTION_TICK = 10 tick = 0.5 s。 */
	public static final double SECONDS_PER_STEP = 0.5;

	private TickDriver() {}

	/**
	 * 步进一拍：进料 → 平衡（初值）→ KINETICS 推进 dt → 返回终态快照。
	 *
	 * <p>注意：当前每次 step 从进料重起（无 KINETICS 内部状态续接）——对
	 * 速率只依赖当前池浓度的反应（策展表全部如此，TOT/MOL 门控）语义等价；
	 * KINETICS 的 -m 剩量状态跨 tick 续接是 P4b 存档联动的一部分。
	 */
	/** 带 interface 供压的步进（P4c 主循环路径）。 */
	public static Step stepWithPressure(List<FluidStack> fluids, Map<String, double[]> parms, double seconds) {
		return stepInternal(fluids, parms, seconds);
	}

	public static Step step(List<FluidStack> fluids, double seconds) {
		return stepInternal(fluids, null, seconds);
	}

	private static Step stepInternal(List<FluidStack> fluids, Map<String, double[]> parms, double seconds) {
		EngineBridge.Feed feed = EngineBridge.toFeed(fluids);
		if (feed.waterKg <= 0 || feed.totals.isEmpty()) {
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
		if (!any) {
			return new Step(false, Map.of(), Map.of(), 7.0, seconds);
		}
		try (IPhreeqc q = IPhreeqc.create()) {
			// 两段模拟：初始（i_soln punch 基线）+ KINETICS 步进（末行）
			IPhreeqc.RunResult r = q.run(parms == null || parms.isEmpty()
					? feed.toScriptWithKinetics(CURATION, seconds)
					: feed.toScriptWithKinetics(CURATION, parms.keySet(), parms, seconds));
			int last = r.rowCount() - 1;
			Map<String, Double> totals = new LinkedHashMap<>();
			Map<String, Double> delta = new LinkedHashMap<>();
			for (String k : feed.totals.keySet()) {
				double after = r.row(last).d(k) * feed.waterKg;
				double before = r.row(0).d(k) * feed.waterKg;
				totals.put(k, after);
				delta.put(k, after - before);
			}
			return new Step(true, totals, delta, r.row(last).d("pH"), seconds);
		} catch (Exception e) {
			return new Step(false, Map.of(), Map.of(), 7.0, seconds);
		}
	}
}
