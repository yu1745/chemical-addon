package com.yu1745.chemicaladdon.composition.parity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yu1745.chemicaladdon.composition.Species;
import com.yu1745.chemicaladdon.composition.SpeciesManager;
import com.yu1745.chemicaladdon.composition.parity.TickDriver.Step;
import com.yu1745.chemicaladdon.composition.Ion;
import com.yu1745.chemicaladdon.composition.Solution;
import com.yu1745.chemicaladdon.fluid.Mixture;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

/**
 * 写回器（唯一化学权威的出侧）：内核步进终态 → Mixture 离子域。
 *
 * <p>语义（P6 修正——假绿事件后重写）：<b>增量迁移</b>，不是全量替换：
 * <ul>
 *   <li>离子 part 是<b>摩尔计价</b>（1 part = 10⁻⁴ mol，见
 *       {@link EngineBridge#UNITS_PER_MOL}）——mod 硬不变量 Σ 电荷×part=0
 *       在此计价下即化学中性；克计价下不对称离子对（Na⁺/OCl⁻ 质量比 1:2.2）
 *       永远不中性，H⁺兜底会无限膨胀；
 *   <li>元素/伪池总量 → 存储离子（dominant-ion 近似，如 S→SO4-2、N→NH4+1、
 *       C→HCO3-1）；已迁移的分子域电解质物种清除（其元素已落离子域——不清除
 *       则与离子域双计）；<b>任一组分 token 无法存储的物种（含 H/O 组分——酸类）
 *       整体保留</b>，物种 part 继续作该元素载体。物种 part 是克计价（1 part = 1 g
 *       化合物），迁移时 Σ part 一次性缩减、水份额微升（≤ 溶质质量，有界后稳定）；</li>
 *   <li>存储映射未覆盖的既有离子条目原样保留（不丢质量）；H+1/OH-1 电荷兜底
 *       （摩尔计价下仅出现在真实价态近似失衡时，如 FeCl₃ 存 Fe+2）；
 *       {@link Mixture#setIons} 电荷中性是硬防线。</li>
 * </ul>
 *
 * <p>已知近似（记录在案，价态真值每 tick 由内核重解）：Fe 存 Fe+2（三价铁溶液
 * 以 +1 H+ 补偿电荷）；N 存 NH4+1（硝酸的存储形态偏碱——HNO₃ 首拍以物种
 * part 存在不受影响）；C 存 HCO3-1；Si 存 SiO3-2。
 */
public final class WriteBack {

	/** 元素/伪池 → 存储离子 id（fedMolarMass 仅作参照信息，换算已改摩尔计价）。 */
	private record IonMap(String ionId, double fedMolarMass) {}

	private static final Map<String, IonMap> IONS = buildIonMap();

	private static Map<String, IonMap> buildIonMap() {
		Map<String, IonMap> m = new LinkedHashMap<>();
		// 简单阳/阴离子：存储 id = 元素裸形态
		m.put("Cl", new IonMap("Cl-1", 35.45));
		m.put("Na", new IonMap("Na+1", 22.99));
		m.put("K", new IonMap("K+1", 39.10));
		m.put("Ca", new IonMap("Ca+2", 40.08));
		m.put("Mg", new IonMap("Mg+2", 24.31));
		m.put("Ba", new IonMap("Ba+2", 137.33));
		m.put("Fe", new IonMap("Fe+2", 55.85));
		m.put("Cu", new IonMap("Cu+2", 63.55));
		m.put("Zn", new IonMap("Zn+2", 65.38));
		m.put("Al", new IonMap("Al+3", 26.98));
		m.put("Ag", new IonMap("Ag+1", 107.87));
		m.put("Mn", new IonMap("Mn+2", 54.94));
		m.put("Pb", new IonMap("Pb+2", 207.2));
		m.put("Ni", new IonMap("Ni+2", 58.69));
		m.put("Co", new IonMap("Co+2", 58.93));
		m.put("F", new IonMap("F-1", 19.00));
		m.put("Br", new IonMap("Br-1", 79.90));
		m.put("I", new IonMap("I-1", 126.90));
		// 含氧酸根族：存储 id = 主导阴离子近似（进料质量仍是元素/伪池的）
		m.put("S", new IonMap("SO4-2", 32.06));
		m.put("N", new IonMap("NH4+1", 14.007));
		m.put("C", new IonMap("HCO3-1", 12.011));
		m.put("P", new IonMap("PO4-3", 30.97));
		m.put("Si", new IonMap("SiO3-2", 28.09));
		// 伪池（介稳身份，进料/存储同质量基）
		m.put("Hyp", new IonMap("OCl-1", 51.452));
		m.put("Sul", new IonMap("SO3-2", 80.064));
		m.put("Nitra", new IonMap("NO3-1", 62.004));
		m.put("Nitri", new IonMap("NO2-1", 46.006));
		return Map.copyOf(m);
	}

	/** mol→part：写入原始 part（mB 级，1 mB = 1e-3 mol → part = mol × 1e3；与水 part 同标度）。 */
	private static final double PARTS_PER_MOL = EngineBridge.UNITS_PER_MOL / 10_000.0; // 1e3

	private WriteBack() {}

	/**
	 * 把内核步进终态增量写回 mixture 的离子域（见类注释的迁移规则）。
	 *
	 * @return true 写回成功（电荷中性通过）
	 */
	public static boolean ionsOf(FluidStack stack, Step step) {
		if (!step.valid || !Mixture.isMixture(stack)) {
			return false;
		}
		// 1. 本拍内核结算的元素/伪池 → 存储离子（只替换这些键覆盖的账目）
		Map<String, Long> ionUnits = new LinkedHashMap<>();
		long charge = 0;
		Map<String, Boolean> settled = new LinkedHashMap<>();
		for (Map.Entry<String, Double> e : step.totals.entrySet()) {
			IonMap m = IONS.get(e.getKey());
			if (m == null || e.getValue() == null || e.getValue() <= 0) {
				continue;
			}
			long units = Math.round(e.getValue() * PARTS_PER_MOL);
			if (units <= 0) {
				continue;
			}
			ionUnits.merge(m.ionId(), units, Long::sum);
			charge += (long) Ion.chargeOf(m.ionId()) * units;
			settled.put(e.getKey(), true);
		}
		// 2. 存储映射未覆盖的既有离子条目原样保留（质量不丢；mB 份额视图与写入同标度）
		for (Map.Entry<String, Integer> e : Mixture.deriveIonAmounts(stack).entrySet()) {
			String key = feedKeyOf(e.getKey());
			if (key != null && settled.containsKey(key)) {
				continue; // 该账目已被本拍结算值替换
			}
			ionUnits.merge(e.getKey(), (long) e.getValue(), Long::sum);
			charge += (long) Ion.chargeOf(e.getKey()) * e.getValue();
		}
		// 3. 电荷配平兜底：正过剩补 OH-1，负过剩补 H+1（dominant-ion 近似的代价吸收）
		if (charge > 0) {
			ionUnits.merge("OH-1", charge, Long::sum);
		} else if (charge < 0) {
			ionUnits.merge("H+1", -charge, Long::sum);
		}
		// 4. 分子域：清除已迁移电解质（全部组分 token 都有存储落点的物种）；
		//    全部域以 mB/FU part 级重写（deriveAmounts scale=1——比率空间单一标度：
		//    水/物种 part = mB，离子 part = FU 计数，Σ part ≈ 总 mB）
		Map<ResourceLocation, Long> molUnits = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Integer> e : Mixture.deriveAmounts(stack).entrySet()) {
			if (e.getKey().equals(Solution.WATER) || !isFullySettled(e.getKey())) {
				molUnits.put(e.getKey(), (long) e.getValue());
			}
		}
		Map<ResourceLocation, Long> suspUnits = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Integer> e : Mixture.deriveSuspendedAmounts(stack).entrySet()) {
			suspUnits.put(e.getKey(), (long) e.getValue());
		}
		Map<ResourceLocation, Long> sedUnits = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Integer> e : Mixture.deriveSedimentAmounts(stack).entrySet()) {
			sedUnits.put(e.getKey(), (long) e.getValue());
		}
		Mixture.setMolecules(stack, molUnits);
		Mixture.setSuspended(stack, suspUnits);
		Mixture.setSediment(stack, sedUnits);
		return Mixture.setIons(stack, ionUnits);
	}

	/** 该分子物种的全部离子组分 token 本拍都有存储落点（H/O 组分恒无——酸类保留物种形态）。 */
	private static boolean isFullySettled(ResourceLocation speciesId) {
		Species sp = SpeciesManager.get(speciesId);
		if (sp == null || !sp.isElectrolyte()) {
			return false;
		}
		for (Species.IonComponent ic : sp.ions()) {
			if (feedKeyOfSymbol(ic.ion().symbol()) == null) {
				return false;
			}
		}
		return true;
	}

	/** 存储离子 id → 它进料时记在哪个元素/伪池账上（null = 不参与本套账）。 */
	private static String feedKeyOf(String ionId) {
		return feedKeyOfSymbol(ionId.replaceAll("[+-]\\d+$", ""));
	}

	private static String feedKeyOfSymbol(String sym) {
		String pool = EngineBridge.PSEUDO_POOLS.get(sym);
		if (pool != null) {
			return pool;
		}
		Map<String, Integer> group = EngineBridge.GROUPS.get(sym);
		if (group != null) {
			for (String el : group.keySet()) {
				return el; // 本表全部单元素值
			}
		}
		return EngineBridge.ATOMIC.containsKey(sym) ? sym : null;
	}

	/** 便捷：釜内第一个 mixture 栈写回（离子域 + 固相域 + 体积闭合）。 */
	public static boolean firstOf(List<FluidStack> fluids, Step step) {
		for (FluidStack stack : fluids) {
			if (Mixture.isMixture(stack)) {
				boolean ions = ionsOf(stack, step);
				suspendedOf(stack, step);
				closeVolumeGap(stack);
				return ions;
			}
		}
		return false;
	}

	/**
	 * 体积闭合：四域 Σ part 应恒等于 FluidStack amount（mB 视图恒等，旧引擎同语义）。
	 * 析出消耗 2 离子 part 产 1 固相 part 的缺口/回溶的盈余，一律记在 water part 上
	 * （溶剂份额吸收相变体积；gap<0 时扣减，下限 0）。
	 */
	static void closeVolumeGap(FluidStack stack) {
		long amount = stack.getAmount();
		if (amount <= 0) {
			return;
		}
		long sum = sumOf(Mixture.getMolecules(stack)) + sumOf(Mixture.getIons(stack))
				+ sumOf(Mixture.getSuspended(stack)) + sumOf(Mixture.getSediment(stack));
		long gap = amount - sum;
		if (gap == 0) {
			return;
		}
		Map<ResourceLocation, Long> molecules = new LinkedHashMap<>(Mixture.getMolecules(stack));
		long water = molecules.getOrDefault(Solution.WATER, 0L) + gap;
		molecules.put(Solution.WATER, Math.max(0, water));
		Mixture.setMolecules(stack, molecules);
	}

	private static long sumOf(Map<?, Long> parts) {
		long s = 0;
		for (long v : parts.values()) {
			s += v;
		}
		return s;
	}

	/**
	 * 固相写回：可相映射物种的悬浮量 ← 内核相终量（mol×1e3 = part）；
	 * 不可映射（曲线物种/伪池固相/未知）原样保留；<1 part 的残余丢弃。
	 */
	public static void suspendedOf(FluidStack stack, Step step) {
		if (!step.valid || !Mixture.isMixture(stack)) {
			return;
		}
		Map<ResourceLocation, Long> susp = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Integer> e : Mixture.deriveSuspendedAmounts(stack).entrySet()) {
			if (PhaseBridge.def(e.getKey()) != null) {
				continue; // 本拍由相终量替换
			}
			susp.put(e.getKey(), (long) e.getValue());
		}
		for (Map.Entry<String, Double> e : step.phases.entrySet()) {
			ResourceLocation id = PhaseBridge.speciesOf(e.getKey());
			if (id == null) {
				continue;
			}
			long parts = Math.round(e.getValue() / PhaseBridge.MOL_PER_PART);
			if (parts >= 1) {
				susp.put(id, parts);
			}
		}
		Mixture.setSuspended(stack, susp);
	}
}
