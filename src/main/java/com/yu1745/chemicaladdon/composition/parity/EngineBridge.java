package com.yu1745.chemicaladdon.composition.parity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yu1745.chemengine.kernel.ChemState;
import com.yu1745.chemengine.kernel.IPhreeqc;
import com.yu1745.chemengine.kernel.Quanta;
import com.yu1745.chemicaladdon.composition.Chemistry;
import com.yu1745.chemicaladdon.composition.Species;
import com.yu1745.chemicaladdon.composition.SpeciesManager;
import com.yu1745.chemicaladdon.fluid.Mixture;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

/**
 * P2 适配层（骨架）：Mixture 四域 → ChemState（元素总量）→ IPhreeqc 求解。
 *
 * <p>单位桥（P1 实证后定谳）：mod 的 unit = 1/10000 mB（Chemistry.UNIT_PER_MB），
 * 能量账本同源声明 1 mB 水（10000 unit）≡ 1 g——"水基准伪质量"单位制，其他物种的
 * unit 数 = 同体积当量。换算：g = units / 10000；mol = g / molarMass；
 * 水 kgw = waterUnits / 1e7。
 *
 * <p>进料归集策略（元素总量语义，铁律：价态分配交给 pe 涌现）：
 * <ul>
 *   <li>分子域：电解质按 species.ions() 拆（HCl→H+Cl；非电解质分子暂跳过
 *       （P3 精化：按 formula 元素拆）</li>
 *   <li>离子域：Ion.symbol 即元素/基团 token（"H"/"SO4"），基团按组成元素拆
 *       （SO4 → S+4O，O 与水账本合并由引擎自洽）</li>
 *   <li>悬浮/沉降固相：暂不入液（P3 决定固相路径）</li>
 * </ul>
 *
 * <p>当前形态：只读对照（观察者模式）——tickReaction 双跑时调 {@link #observe}，
 * 日志级比对，<b>不写回 Mixture</b>。读数源切换/DUMP 存档/KINETICS 在 P3/P4。
 */
public final class EngineBridge {

	private static final Logger LOGGER = LoggerFactory.getLogger("engine-bridge");

	/** 内建元素摩尔质量表（g/mol）——species JSON 的 formula 解析用。 */
	private static final Map<String, Double> ATOMIC = Map.ofEntries(
			Map.entry("H", 1.008), Map.entry("O", 15.999), Map.entry("Cl", 35.45),
			Map.entry("Na", 22.99), Map.entry("Ca", 40.08), Map.entry("Mg", 24.31),
			Map.entry("Ba", 137.33), Map.entry("Fe", 55.85), Map.entry("Cu", 63.55),
			Map.entry("Zn", 65.38), Map.entry("Al", 26.98), Map.entry("S", 32.06),
			Map.entry("N", 14.007), Map.entry("C", 12.011), Map.entry("K", 39.10),
			Map.entry("Ag", 107.87), Map.entry("Mn", 54.94), Map.entry("Pb", 207.2),
			Map.entry("Ni", 58.69), Map.entry("Co", 58.93), Map.entry("Si", 28.09),
			Map.entry("P", 30.97), Map.entry("F", 19.00), Map.entry("Br", 79.90),
			Map.entry("I", 126.90));

	/** 常见基团 → 元素组成（离子域的 SO4/OH/NH4/CO3 拆解）。 */
	private static final Map<String, Map<String, Integer>> GROUPS = Map.of(
			"OH", Map.of("O", 1, "H", 1),
			"SO4", Map.of("S", 1, "O", 4),
			"HSO4", Map.of("H", 1, "S", 1, "O", 4),
			"NO3", Map.of("N", 1, "O", 3),
			"NO2", Map.of("N", 1, "O", 2),
			"NH4", Map.of("N", 1, "H", 4),
			"CO3", Map.of("C", 1, "O", 3),
			"HCO3", Map.of("H", 1, "C", 1, "O", 3),
			"PO4", Map.of("P", 1, "O", 4));

	/** unit → g（1 mB 水 = 10000 unit = 1 g，水基准伪质量制）。 */
	private static final double GRAMS_PER_UNIT = 1.0 / Chemistry.UNIT_PER_MB;

	/** 进料快照（元素总量 mol + 水量）。 */
	public static final class Feed {
		public double waterKg;
		public double tempC = 25.0;
		public final Map<String, Double> totals = new LinkedHashMap<>();
		/** 未映射的输入（诊断用：分子域非电解质、未知离子）。 */
		public final Map<String, Long> unmapped = new LinkedHashMap<>();

		public boolean isEmpty() {
			return waterKg <= 0 && totals.isEmpty();
		}
	}

	private EngineBridge() {}

	/** 从多个 mixture FluidStack 构造内核进料。 */
	public static Feed toFeed(List<FluidStack> stacks) {
		Feed feed = new Feed();
		long waterUnits = 0;
		for (FluidStack stack : stacks) {
			if (stack.isEmpty() || !Mixture.isMixture(stack)) {
				continue;
			}
			// 分子域
			for (Map.Entry<ResourceLocation, Integer> e : Mixture.deriveUnitAmounts(stack).entrySet()) {
				if (e.getKey().equals(com.yu1745.chemicaladdon.composition.Solution.WATER)) {
					waterUnits += e.getValue();
					continue;
				}
				Species sp = SpeciesManager.get(e.getKey());
				if (sp == null || !sp.isElectrolyte()) {
					feed.unmapped.merge("M:" + e.getKey(), (long) e.getValue(), Long::sum);
					continue;
				}
				double units = e.getValue();
				for (Species.IonComponent ic : sp.ions()) {
					String sym = ic.ion().symbol();
					int count = ic.count();
					Map<String, Integer> els = GROUPS.containsKey(sym)
							? GROUPS.get(sym) : Map.of(sym, 1);
					for (Map.Entry<String, Integer> el : els.entrySet()) {
						feed.totals.merge(el.getKey(),
								units * GRAMS_PER_UNIT * count * el.getValue()
										/ molarMassOf(el.getKey()),
								Double::sum);
					}
				}
			}
			// 离子域（unit 级）
			for (Map.Entry<String, Integer> e : Mixture.deriveUnitIonAmounts(stack).entrySet()) {
				String sym = e.getKey().replaceAll("[+-]\\d+$", "");
				long units = e.getValue();
				Map<String, Integer> els = GROUPS.containsKey(sym) ? GROUPS.get(sym) : Map.of(sym, 1);
				for (Map.Entry<String, Integer> el : els.entrySet()) {
					feed.totals.merge(el.getKey(),
							units * GRAMS_PER_UNIT * el.getValue() / molarMassOf(el.getKey()),
							Double::sum);
				}
			}
		}
		feed.waterKg = waterUnits * GRAMS_PER_UNIT / 1000.0;
		return feed;
	}

	private static double molarMassOf(String element) {
		Double m = ATOMIC.get(element);
		if (m == null) {
			return -1; // 未知元素 → 调用侧 merge 前需检查；当前简单返回负值使 merge 失效
		}
		return m;
	}

	/** 只读求解观察：日志级对照（P2 双跑），不写回。 */
	public static void observe(Feed feed, String tag) {
		if (feed.isEmpty() || feed.waterKg <= 0 || feed.totals.isEmpty()) {
			return;
		}
		// 滤掉未知元素的负 mol（molarMassOf 返回 -1 的防御路径）
		ChemState.Builder b = ChemState.builder(tag)
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
			return;
		}
		try (IPhreeqc q = IPhreeqc.create()) {
			IPhreeqc.RunResult r = q.equilibrate(b.build(), "pH", "pe");
			double ph = r.row(r.rowCount() - 1).d("pH");
			double pe = r.row(r.rowCount() - 1).d("pe");
			LOGGER.info("[parity] {} pH={} pe={} water={}kg totals={} unmapped={}",
					tag, String.format("%.2f", ph), String.format("%.2f", pe),
					String.format("%.3f", feed.waterKg), feed.totals, feed.unmapped);
		} catch (Exception e) {
			LOGGER.warn("[parity] {} solve failed: {}", tag, e.getMessage());
		}
	}
}
