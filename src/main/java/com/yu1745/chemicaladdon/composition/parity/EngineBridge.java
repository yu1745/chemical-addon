package com.yu1745.chemicaladdon.composition.parity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.yu1745.chemengine.kernel.ChemState;
import com.yu1745.chemengine.kernel.Curation;
import com.yu1745.chemengine.kernel.IPhreeqc;
import com.yu1745.chemicaladdon.composition.Chemistry;
import com.yu1745.chemicaladdon.composition.Species;
import com.yu1745.chemicaladdon.composition.SpeciesManager;
import com.yu1745.chemicaladdon.fluid.Mixture;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

/**
 * 适配层（唯一化学权威）：Mixture 四域 → 元素总量 → IPhreeqc 求解。
 *
 * <p>单位桥（mod 固有虚构，引擎切换后明文化）：
 * <ul>
 *   <li>水 part = 1 mB = 1 g（质量锚，从 derive 取真实 mB）；
 *   <li>物种 part = 离子 part = <b>formula-unit 计数</b>（1 part = 1e-4 mol，
 *       见 {@link #UNITS_PER_MOL}——溶剂比/配方/创造栏一直用的约定）；
 *   <li>进料读原始 part（getIons/getMolecules），不经 derive 的份额折算
 *       （份额按 mB 分发，对 FU 计数会按水份额缩放，混合量纲失真）；
 *   <li>H/O 永不作为元素总量输入（PHREEQC 里 H/O 属水与电荷平衡域，喂入即
 *       不收敛）；酸碱身份由 pH charge 涌现；伪池（Hyp/Sul/Nitra/Nitri）优先于
 *       元素归并（介稳身份不可塌）。
 * </ul>
 *
 * <p>进料归集（元素总量语义，铁律：价态分配交给 pe 涌现）：
 * <ul>
 *   <li>分子域：电解质按 species.ions() 拆到元素/伪池（FU 计数×化学计量）；
 *       非电解质分子不进液（unmapped，惰性）；</li>
 *   <li>离子域：先查伪池（OCl→Hyp、SO3→Sul、NO3→Nitra、NO2→Nitri——这是
 *       {@link WriteBack} 的存储编码），再按基团表拆元素；</li>
 *   <li>悬浮/沉降固相不入液（固相写回待相映射）。</li>
 * </ul>
 */
public final class EngineBridge {

	private static final Logger LOGGER = LoggerFactory.getLogger("engine-bridge");

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

	/** 伪池宿主离子 → 引擎伪元素（P4b）：这些基团在内核里是介稳池，不进真实元素账。 */
	static final Map<String, String> PSEUDO_POOLS = Map.of(
			"OCl", "Hyp",   // 次氯酸根 → Hyp 池（漂白液介稳）
			"SO3", "Sul",   // 亚硫酸根 → Sul 池（介稳，防平衡氧化）
			"NO3", "Nitra", // 硝酸根 → Nitra 池（防平衡下被还原成 NH4+）
			"NO2", "Nitri");// 亚硝酸根 → Nitri 池（介稳，歧化/氧化通道在策展）

	/**
	 * 常见基团 → 非氢氧元素组成（离子域/分子域共用的进料拆解）。H/O 一律不产出
	 * （水与酸碱度归电荷平衡域）；SiO3→Si 使硅酸盐可往返。
	 */
	static final Map<String, Map<String, Integer>> GROUPS = Map.of(
			"SO4", Map.of("S", 1),
			"HSO4", Map.of("S", 1),
			"NH4", Map.of("N", 1),
			"CO3", Map.of("C", 1),
			"HCO3", Map.of("C", 1),
			"PO4", Map.of("P", 1),
			"SiO3", Map.of("Si", 1));
			// NO3/NO2 不入本表：离子域走伪池，分子域经 PSEUDO_POOLS 判定后再落元素

	/** unit → g（1 mB 水 = 10000 unit = 1 g，水基准——仅水/质量锚用）。 */
	private static final double GRAMS_PER_UNIT = 1.0 / Chemistry.UNIT_PER_MB;

	/**
	 * 单位桥统一计价（与水同幕）：1 unit = 1e-7 g 水 = <b>1e-7 mol</b> 离子/物种
	 * formula unit，即 1 mB（1e4 unit）= 1e-3 mol——legacy 浓度比
	 * （离子 units/水 units）恰为 millimolal，所有旧读数/配方浓度/写回往返
	 * 在此计价下精确自洽。水 kg = waterUnits/1e7（同一除数）。
	 */
	public static final double UNITS_PER_MOL = 10_000_000.0;

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
			return cols;
		}

		/** 进料 + 策展 KINETICS 块的完整脚本（TickDriver 用）。 */
		public String toScriptWithKinetics(Curation curation, double seconds) {
			return toScriptWithKinetics(curation, null, null, seconds);
		}

		/**
		 * 带 interface 反应 opt-in + 供压参数的完整脚本。
		 *
		 * <p>punch 结构（P6 铁律修正）：SELECTED_OUTPUT 定义在<b>首个模拟内</b>（首个
		 * END 之前）——i_soln 行得以 punch（行 0 = 步进前基线），且已定义块对后续
		 * KINETICS 模拟持续生效（行 1..n = 步进后）。此前定义在 kinetics 模拟之后，
		 * 不回溯 → 池 delta 恒 0（GameTest 假绿暴露）。
		 */
		public String toScriptWithKinetics(Curation curation, java.util.Set<String> include,
				Map<String, double[]> parmOverrides, double seconds) {
			StringBuilder punchList = new StringBuilder();
			for (String k : punchColumns(curation)) {
				punchList.append(' ').append(k);
			}
			StringBuilder sol = new StringBuilder("SOLUTION 1 tick\n");
			sol.append("    temp      ").append(tempC).append('\n');
			sol.append("    pH        7 charge\n");
			sol.append("    water     ").append(waterKg).append(" kg\n");
			for (Map.Entry<String, Double> e : totals.entrySet()) {
				if (e.getValue() > 0) {
					sol.append("    ").append(e.getKey()).append(" ")
							.append(e.getValue() / waterKg).append(" mol/kgw\n");
				}
			}
			sol.append("SELECTED_OUTPUT 1\n");
			sol.append("    -state          true\n");
			sol.append("    -time           true\n");
			sol.append("    -high_precision true\n");
			sol.append("    -totals  ").append(punchList.toString().trim()).append('\n');
			sol.append("    -pH       true\n");
			sol.append("    -pe       true\n");
			sol.append("END\n");
			return sol + curation.ratesBlock()
					+ "USE solution 1\n"
					+ curation.kineticsBlock(include, parmOverrides, seconds) + "\n"
					+ "END\n";
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
			// 分子域：电解质按 FU 计数拆元素（1 物种 part = 1e-4 mol formula unit）
			// 水与溶质均从 derive 份额视图取绝对量（单一 mB/FU 计价下份额折算精确：
			// part 空间是 GCD 无标度比率，原始 part 丢尺度；unit 视图/1e4 = 绝对 FU 数）。
			// mod 固有虚构：1 物种 part = 1e-4 mol formula unit（溶剂比/配方/创造栏约定），
			// 与离子 part（1 part = 1e-4 mol 离子）同坐标系，Σ part ≈ 总 mB，
			// 配方产出/写回/断言全部恒等
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
				double fuMol = e.getValue() / UNITS_PER_MOL; // unit 视图 / 1e7 = FU mol（1 mB = 1e-3 mol）
				for (Species.IonComponent ic : sp.ions()) {
					mergeTokenMol(feed.totals, ic.ion().symbol(), fuMol * ic.count());
				}
			}
			// 离子域（derive unit 视图 / 1e4 = 绝对 mol，摩尔计价）——伪池优先，后基团/裸元素
			for (Map.Entry<String, Integer> e : Mixture.deriveUnitIonAmounts(stack).entrySet()) {
				String sym = e.getKey().replaceAll("[+-]\\d+$", "");
				mergeTokenMol(feed.totals, sym, e.getValue() / UNITS_PER_MOL);
			}
		}
		feed.waterKg = waterUnits * GRAMS_PER_UNIT / 1000.0;
		return feed;
	}

	/**
	 * 一个 token 的摩尔数 → 元素/伪池账（伪池优先——介稳身份不可塌回元素；H/O 永不出）。
	 * token mol 已是该 token 在溶液中的摩尔数：伪池直接记账；基团按组成元素
	 * （本表全部 1:1）记账；裸元素直接记账。H/O/未知 token 静默丢弃。
	 */
	private static void mergeTokenMol(Map<String, Double> totals, String sym, double tokenMol) {
		String pool = PSEUDO_POOLS.get(sym);
		if (pool != null) {
			totals.merge(pool, tokenMol, Double::sum);
			return;
		}
		Map<String, Integer> els = GROUPS.getOrDefault(sym, Map.of(sym, 1));
		for (Map.Entry<String, Integer> el : els.entrySet()) {
			if (!"H".equals(el.getKey()) && !"O".equals(el.getKey()) && ATOMIC.containsKey(el.getKey())) {
				totals.merge(el.getKey(), tokenMol * el.getValue(), Double::sum);
			}
		}
	}

	/** 伪池摩尔质量（与 curation/chemistry.json 的 molarMass 一致）。 */
	static double pseudoPoolMass(String group) {
		return switch (group) {
			case "OCl" -> 51.452;  // Hyp
			case "SO3" -> 80.064;  // Sul
			case "NO3" -> 62.004;  // Nitra
			case "NO2" -> 46.006;  // Nitri
			default -> -1;
		};
	}

	/** 只读求解观察：日志级对照（诊断用），不写回。 */
	public static void observe(Feed feed, String tag) {
		if (feed.isEmpty() || feed.waterKg <= 0 || feed.totals.isEmpty()) {
			return;
		}
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
			LOGGER.info("[engine] {} pH={} pe={} water={}kg totals={} unmapped={}",
					tag, String.format("%.2f", ph), String.format("%.2f", pe),
					String.format("%.3f", feed.waterKg), feed.totals, feed.unmapped);
		} catch (Exception e) {
			LOGGER.warn("[engine] {} solve failed: {}", tag, e.getMessage());
		}
	}
}
