package com.yu1745.chemicaladdon.composition.parity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.yu1745.chemicaladdon.composition.parity.TickDriver.Step;
import com.yu1745.chemicaladdon.fluid.Mixture;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;

/**
 * P5 写回器：内核步进终态 → Mixture 离子域。
 *
 * <p>第一版范围（保守）：只写回<b>伪池四离子 + 直接对应元素离子</b>——
 * <pre>
 *   Hyp→OCl-1  Sul→SO3-2  Nitra→NO3-1  Nitri→NO2-1（伪池宿主，单位互转）
 *   Cl→Cl-1  Na→Na+1  S(+6)→SO4-2  ...（简单主离子）
 *   电荷配平：H+1 / OH-1 兜底（setIons 的电荷中性不变式是硬防线）
 * </pre>
 * speciation 的精细形态（络合/水解次物种）不写回——mod 显示层用主导离子
 * 近似，与 RulesEngine 的显示粒度一致。Mixture 分子域（非电解质）与
 * 悬浮/沉降域不动（固相写回待 P6，需相映射）。
 *
 * <p>不变式：写回前后水 part 与元素总量守恒（mol ↔ unit 往返经 molarMass）；
 * 电荷中性由 {@link Mixture#setIons} 拒绝非中性集强制。
 */
public final class WriteBack {

	/** 伪池/元素 → mod 离子 id（charge 后缀）与摩尔质量（g/mol，unit 互转）。 */
	private record IonMap(String ionId, double molarMass) {}

	private static final Map<String, IonMap> POOL_IONS = Map.ofEntries(
			Map.entry("Hyp", new IonMap("OCl-1", 51.452)),
			Map.entry("Sul", new IonMap("SO3-2", 80.064)),
			Map.entry("Nitra", new IonMap("NO3-1", 62.004)),
			Map.entry("Nitri", new IonMap("NO2-1", 46.006)),
			Map.entry("Cl", new IonMap("Cl-1", 35.45)),
			Map.entry("Na", new IonMap("Na+1", 22.99)),
			Map.entry("K", new IonMap("K+1", 39.10)),
			Map.entry("Ca", new IonMap("Ca+2", 40.08)),
			Map.entry("Mg", new IonMap("Mg+2", 24.31)),
			Map.entry("Fe", new IonMap("Fe+2", 55.85)),
			Map.entry("Ba", new IonMap("Ba+2", 137.33)),
			Map.entry("Cu", new IonMap("Cu+2", 63.55)),
			Map.entry("Zn", new IonMap("Zn+2", 65.38)),
			Map.entry("S", new IonMap("SO4-2", 96.06)),
			Map.entry("N", new IonMap("NO3-1", 62.004)));

	private static final double UNITS_PER_MOL = 10_000.0; // unit = g×10⁻⁴ → mol→unit = mol×molarMass×10⁴

	private WriteBack() {}

	/**
	 * 把内核步进终态的溶液部分写回 mixture 的离子域。
	 *
	 * @param stack 目标 mixture FluidStack（第一个 mixture 栈）
	 * @param step  TickDriver 步进结果（totals 为容器 mol 总量）
	 * @return true 写回成功（电荷中性通过）
	 */
	public static boolean ionsOf(FluidStack stack, Step step) {
		if (!step.valid || !Mixture.isMixture(stack)) {
			return false;
		}
		Map<String, Long> ions = new LinkedHashMap<>();
		long charge = 0;
		for (Map.Entry<String, Double> e : step.totals.entrySet()) {
			IonMap m = POOL_IONS.get(e.getKey());
			if (m == null || e.getValue() <= 0) {
				continue;
			}
			long units = Math.round(e.getValue() * m.molarMass() * UNITS_PER_MOL);
			if (units <= 0) {
				continue;
			}
			ions.merge(m.ionId(), units, Long::sum);
			charge += com.yu1745.chemicaladdon.composition.Ion.chargeOf(m.ionId()) * units;
		}
		// 电荷配平兜底：正电荷过剩补 OH-1，负过剩补 H+1（写回器不引入新化学，只保不变式）
		if (charge > 0) {
			ions.merge("OH-1", charge, Long::sum);
		} else if (charge < 0) {
			ions.merge("H+1", -charge, Long::sum);
		}
		return Mixture.setIons(stack, ions);
	}

	/** 便捷：釜内第一个 mixture 栈写回。 */
	public static boolean firstOf(List<FluidStack> fluids, Step step) {
		for (FluidStack stack : fluids) {
			if (Mixture.isMixture(stack)) {
				return ionsOf(stack, step);
			}
		}
		return false;
	}
}
