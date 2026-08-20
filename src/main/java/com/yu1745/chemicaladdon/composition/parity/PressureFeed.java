package com.yu1745.chemicaladdon.composition.parity;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.yu1745.chemicaladdon.fluid.Miscibility;

import net.minecraftforge.fluids.FluidStack;

/**
 * P4c 供压桥：釜内气相 → interface 反应的 PARM 供压（atm）。
 *
 * <p>语义对齐 {@code ReactorControllerBlockEntity#getPressure()} 的理想气体
 * 模型（pAbs = 101.3 × gas/cap × T/293.15 kPa），但按<b>分压</b>取值：
 * P_SO2 = pAbs × SO₂ 占气相的体积（mB）比——interface 反应吸收的是特定气体，
 * 氮气垫层不该驱动 SO₂ 吸收。
 *
 * <p>产出：{@code parmOverrides} 映射（反应名 → [常数, 分压 atm]），直接喂
 * {@code Curation.kineticsBlock(include, parmOverrides, steps)}；游戏侧供压为 0
 * 时 interface 反应不发射（防虚拟大气污染——策展表铁律）。
 */
public final class PressureFeed {

	/** interface 反应 → 气体物种（mod 侧 fluid id 的 path 部分）。 */
	private static final Map<String, String> REACTION_GAS = Map.of(
			"SulAbsorb", "sulfur_dioxide",
			"ChlorineAbsorbs", "chlorine");

	/** 常数 PARM(1)（亨利/传质系数，默认值来自策展表；游戏侧不可调）。 */
	private static final Map<String, Double> REACTION_CONST = Map.of(
			"SulAbsorb", 1.24,
			"ChlorineAbsorbs", 0.002);

	private static final double ATM = 101.325;

	private PressureFeed() {}

	/**
	 * 从釜流体算 interface 反应的 parmOverrides 与 include 集。
	 *
	 * @param fluids 釜内流体（含气相）
	 * @param capacityMb 釜容量（mB，压力模型的体积项）
	 * @param tempC 釜温
	 * @return 只有分压 > 0 的反应进映射（include 用 keySet）；空釜气 → 空 Map（全不发射）
	 */
	public static Map<String, double[]> of(java.util.List<FluidStack> fluids, int capacityMb, int tempC) {
		Map<String, double[]> out = new HashMap<>();
		if (capacityMb <= 0) {
			return out;
		}
		int gasTotal = 0;
		Map<String, Integer> gasParts = new HashMap<>();
		for (FluidStack stack : fluids) {
			if (!stack.isEmpty() && Miscibility.isGas(stack)) {
				gasTotal += stack.getAmount();
				String path = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getKey(stack.getFluid()).getPath();
				gasParts.merge(path, stack.getAmount(), Integer::sum);
			}
		}
		if (gasTotal <= 0) {
			return out;
		}
		double kelvin = tempC + 273.15;
		double pAbsAtm = gasTotal / (double) capacityMb * (kelvin / 293.15);
		for (Map.Entry<String, String> e : REACTION_GAS.entrySet()) {
			int part = gasParts.getOrDefault(e.getValue(), 0);
			if (part <= 0) {
				continue;
			}
			double partial = pAbsAtm * part / gasTotal;
			if (partial <= 0) {
				continue;
			}
			out.put(e.getKey(), new double[] {REACTION_CONST.get(e.getKey()), partial});
		}
		return out;
	}

	/** include 集（供 kineticsBlock 显式 opt-in）。 */
	public static Set<String> includeOf(Map<String, double[]> parms) {
		return parms.keySet();
	}
}
