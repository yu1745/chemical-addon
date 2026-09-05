package com.yu1745.chemicaladdon.composition.parity;

import java.util.List;

import javax.annotation.Nullable;

import com.yu1745.chemengine.kernel.IPhreeqc;
import com.yu1745.chemicaladdon.fluid.Mixture;

import net.minecraft.core.BlockPos;
import net.minecraftforge.fluids.FluidStack;

/**
 * 表计读数缓存：内核侧求解结果的共享视图。
 *
 * <p>每个 tick 的表计读数（pH/温度/浊度……）不应该各自起一次 IPhreeqc 求解
 * （JNA 每次求解 ~1ms 量级，六种表计 × 多釜会叠加）。主循环每拍步进后由
 * {@link #publish} 直接复用步进结果缓存（零额外求解）；缺少有效快照时
 * 从该容器的原生状态观测，不重建旧四域进料。
 *
 * <p>快照带发布者（B1 修复）：缓存里只有一槽数据，多釜工厂里 A 釜的表计
 * 决不能读 B 釜的 pH——发布时记录控制器坐标，读取时 ({@link #peek(BlockPos)})
 * 只在发布者是读者自己的釜时才用快照，否则返回无效快照。发布者为 null 的
 * 快照（诊断/测试直驱 {@link #refresh}）保持全局可读（旧语义）。</p>
 */
public final class EngineReadings {

	/** 一次求解的读数快照（表计共享）。 */
	public static final class Snapshot {
		public final boolean valid;
		public final double ph;
		public final double pe;
		/** 发布该快照的釜控制器（null = 全局快照，任何读者可见）。 */
		@Nullable
		public final BlockPos publisher;

		private Snapshot(boolean valid, double ph, double pe, @Nullable BlockPos publisher) {
			this.valid = valid;
			this.ph = ph;
			this.pe = pe;
			this.publisher = publisher;
		}

		static final Snapshot INVALID = new Snapshot(false, 7.0, 4.0, null);
	}

	private static volatile Snapshot last = Snapshot.INVALID;

	private EngineReadings() {}

	/** 从步进结果直接发布快照（主循环路径，零额外求解），记录发布釜。 */
	public static void publish(TickDriver.Step step, @Nullable BlockPos publisher) {
		last = step != null && step.valid
				? new Snapshot(true, step.ph, step.pe, publisher)
				: Snapshot.INVALID;
	}

	/** 无有效步进时失效快照。 */
	public static void invalidate() {
		last = Snapshot.INVALID;
	}

	/** 从釜流体求解并缓存（诊断/测试直驱路径）。失败/空釜 → INVALID。 */
	public static Snapshot refresh(List<FluidStack> fluids) {
		KernelSolutionState state = null;
		for (FluidStack fluid : fluids) if (Mixture.isMixture(fluid)) {
			if (state != null || (state = Mixture.engineSolution(fluid)) == null) {
				last = Snapshot.INVALID;
				return last;
			}
		}
		if (state == null) { last = Snapshot.INVALID; return last; }
		try {
			IPhreeqc q = Kernel.get();
			EngineBridge.DerivedSolution view = EngineBridge.derive(q, state, java.util.List.of());
			last = new Snapshot(true, view.ph(), view.pe(), null);
		} catch (Exception e) {
			last = Snapshot.INVALID;
		}
		return last;
	}

	/** 最近一次快照（可能 INVALID）。旧式全局读取——见 {@link #peek(BlockPos)}。 */
	public static Snapshot peek() {
		return last;
	}

	/**
	 * 最近一次对 {@code reader} 釜有效的快照：发布者不是读者自己的釜时返回
	 * INVALID，防止多釜/多测试结构共用一个全局单槽时
	 * 互相污染读数。
	 */
	public static Snapshot peek(@Nullable BlockPos reader) {
		if (last.valid && reader != null && last.publisher != null && !reader.equals(last.publisher)) {
			return Snapshot.INVALID;
		}
		return last;
	}

	/** 引擎侧 pH 的整数刻度（与 Analyte.ph 同粒度：1 pH = 1 步）。 */
	public static int phSteps(Snapshot s) {
		if (!s.valid) {
			return 7;
		}
		int v = (int) Math.round(s.ph);
		return Math.max(0, Math.min(14, v));
	}
}
