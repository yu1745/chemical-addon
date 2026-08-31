package com.yu1745.chemicaladdon.reactor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.yu1745.chemicaladdon.composition.Chemistry;
import com.yu1745.chemicaladdon.composition.Solution;
import com.yu1745.chemicaladdon.composition.Species;
import com.yu1745.chemicaladdon.composition.SpeciesManager;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.fluid.Miscibility;
import com.yu1745.chemicaladdon.fluid.Temperature;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 内核主循环的 mod 侧物理步骤层（P7.2/P7.3）：内核（IPhreeqc）拥有离子平衡
 * （pH/络合/Ksp 矿物），本层拥有 PHREEQC 不承载的三类自发物理——
 *
 * <ul>
 *   <li><b>投料溶解</b>（{@link RulesEngine#dissolveItems}，曲线饱和封顶、
 *       过饱和时投种入 Sediment、混合盐渣整块展开）；</li>
 *   <li><b>曲线结晶</b>（溶解度表物种的过饱和→沉底、欠饱和回溶、蒸发干涸；
 *       动力学=CRYSTAL_RATE_FRACTION 亲和律 + NUCLEATION_AFFINITY 介稳门 +
 *       无种 NUCLEATION_PENALTY——语义逐行提取自旧 Solution#curveBalance，
 *       物种数据=溶解度表，非 Ksp，内核结构性不含）；</li>
 *   <li><b>开口蒸发</b>（沸腾开釜每拍常数速率蒸水，浓缩由下一拍内核/曲线
 *       感知；蒸出量经 ventedQuanta 上报，M08 冷凝罐回收为馏出水）；</li>
 *   <li><b>通用气液传质</b>（所有独立气相都经同一入口进入水相分子域；
 *       有物种数据时使用其 {@code gasSolubility}，否则使用统一默认值）。</li>
 * </ul>
 *
 * <p>单位空间 = quanta（mB×{@link Chemistry#QUANTA_PER_MB}，U18 细网格），
 * 与旧引擎同刻度：投料/晶粒的分数面额（62.5 mB）与亚 mB 结晶增量在
 * ratio-tag 里无损往返。质量真相在四域映射，写回走
 * {@link ReactorTank#setContentsLong}（总量=Σ单位，水蒸发自动缩量）。
 *
 * <p>与内核的边界：本层不改 pH/不碰 Ksp 矿物（内核每拍先行）；脱气
 * （Henry 表）与反应热记账仍未接（缺口清单 docs/progress.md）。
 */
public final class PhysicalSteps {

	private PhysicalSteps() {}

	/**
	 * 一步物理拍（内核步进与写回<b>之后</b>调用）：投料溶解 → 曲线结晶 →
	 * 开口蒸发 → 有事件才重写釜（避免静置同步churn）。
	 *
	 * <p>U16 潜热自限：蒸发带走汽化潜热（energyJ 语义 = ΔT = Q/(feedUnits×c)，
	 * 与旧 Solution#heatRiseC 同公式）——写回温度降 ~10°C，下一拍再沸腾需要热源
	 * 重新顶回沸点；热源一停（终点切热/撞火）蒸发立即停，不会过冲。
	 *
	 * @param ventedQuanta 蒸汽上报（quanta 单位；null = 上层不关心）
	 * @param temperature  本拍釜温（蒸发判据 + 写回温度）
	 */
	public static void apply(ReactorTank tank, boolean openVessel, @Nullable IItemHandler items,
			double stirring, @Nullable long[] ventedQuanta, int temperature) {
		// 1. 读水相（quanta）：mixture 四域 + 纯水并入溶剂；气体/非极性旁观
		List<FluidStack> bystanders = new ArrayList<>();
		List<FluidStack> gasPhase = new ArrayList<>();
		Map<ResourceLocation, Long> mol = new LinkedHashMap<>();
		Map<String, Long> ions = new LinkedHashMap<>();
		Map<ResourceLocation, Long> susp = new LinkedHashMap<>();
		Map<ResourceLocation, Long> sed = new LinkedHashMap<>();
		long total = 0;
		for (FluidStack stack : tank.getFluids()) {
			if (Mixture.isMixture(stack)) {
				total += stack.getAmount();
				for (Map.Entry<ResourceLocation, Long> e : Mixture.deriveQuantaAmounts(stack).entrySet()) {
					mol.merge(e.getKey(), e.getValue(), Long::sum);
				}
				for (Map.Entry<String, Long> e : Mixture.deriveQuantaIonAmounts(stack).entrySet()) {
					ions.merge(e.getKey(), e.getValue(), Long::sum);
				}
				for (Map.Entry<ResourceLocation, Long> e : Mixture.deriveQuantaSuspendedAmounts(stack).entrySet()) {
					susp.merge(e.getKey(), e.getValue(), Long::sum);
				}
				for (Map.Entry<ResourceLocation, Long> e : Mixture.deriveQuantaSedimentAmounts(stack).entrySet()) {
					sed.merge(e.getKey(), e.getValue(), Long::sum);
				}
			} else if (!Miscibility.isGas(stack) && Miscibility.AQUEOUS.equals(Miscibility.groupOf(stack))) {
				total += stack.getAmount();
				ResourceLocation id = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
				if (id != null) {
					mol.merge(id, (long) stack.getAmount() * Chemistry.QUANTA_PER_MB, Long::sum);
				}
			} else if (Miscibility.isGas(stack)) {
				gasPhase.add(stack.copy());
			} else {
				bystanders.add(stack);
			}
		}
		if (total <= 0) {
			return;
		}
		long feedUnits = total * Chemistry.QUANTA_PER_MB; // U16 热容基准：入拍质量体

		// 2. 鼓泡气液传质：所有气体从同一个路径进入 molecular 域；本拍后续
		// 配方以及下一拍 IPhreeqc 只消费这份已经实际从气相扣除的传质量。
		boolean absorbed = absorbGasPhase(mol, gasPhase);

		// 3. 投料溶解（过饱和时投种 → Sediment，成核基底）
		boolean consumed = items != null
			&& RulesEngine.dissolveItems(mol, ions, susp, sed, items, temperature);

		// 4. 曲线结晶（过饱和→沉底生长 / 欠饱和回溶 / 干涸全析）
		boolean moved = crystalliseCurves(mol, ions, sed, temperature, stirring);

		// 5. 开口蒸发：沸腾每拍常数速率蒸水（浓缩由下一拍感知）；
		//    汽化潜热冷却剩余液体（U16 自限：无热源续热则自灭）
		boolean vented = false;
		int tempAfter = temperature;
		if (openVessel && temperature >= RulesEngine.WATER_BOILING_C) {
			long amount = evaporateWater(mol,
				RulesEngine.EVAPORATION_RATE_MB * Chemistry.QUANTA_PER_MB);
			if (amount > 0) {
				vented = true;
				if (ventedQuanta != null) {
					ventedQuanta[0] += amount;
				}
				// feedUnits 里的同刻度热量账：Q = vented×2260，ΔT = Q/(feedUnits×4.18)
				tempAfter = temperature - Math.max(1, (int) Math.round(
					(double) amount * Chemistry.VAPORISATION_J_PER_UNIT
						/ (feedUnits * Chemistry.HEAT_CAPACITY_PER_UNIT)));
				if (tempAfter < 0) {
					tempAfter = 0;
				}
			}
		}

		// 6. 有事件才重写（静置不动 = 无同步churn）
		if (!absorbed && !consumed && !moved && !vented) {
			return;
		}
		tank.setContentsLong(mol, ions, susp, sed, tempAfter, Chemistry.QUANTA_PER_MB);
		for (FluidStack s : gasPhase) {
			if (!s.isEmpty()) {
				tank.fill(s, FluidAction.EXECUTE);
			}
		}
		for (FluidStack s : bystanders) {
			tank.fill(s.copy(), FluidAction.EXECUTE);
		}
	}

	private static boolean absorbGasPhase(Map<ResourceLocation, Long> molecular,
			List<FluidStack> gasPhase) {
		long water = molecular.getOrDefault(Solution.WATER, 0L);
		if (water <= 0) {
			return false;
		}
		boolean moved = false;
		for (FluidStack gas : gasPhase) {
			ResourceLocation id = ForgeRegistries.FLUIDS.getKey(gas.getFluid());
			Species species = id == null ? null : SpeciesManager.get(id);
			if (id == null) {
				continue;
			}
			double retention = species == null || Double.isNaN(species.gasSolubility())
				? Solution.GAS_SOLUBILITY_DEFAULT : species.gasSolubility();
			long capacity = Math.max(0L, (long) Math.floor(retention * water));
			long available = Math.max(0L, capacity - molecular.getOrDefault(id, 0L));
			int transferMb = (int) Math.min(gas.getAmount(), available / Chemistry.QUANTA_PER_MB);
			if (transferMb <= 0) {
				continue;
			}
			molecular.merge(id, (long) transferMb * Chemistry.QUANTA_PER_MB, Long::sum);
			gas.shrink(transferMb);
			moved = true;
		}
		return moved;
	}

	/**
	 * 溶解度表结晶（语义提取自旧 Solution#curveBalance 首拍）：
	 * <ul>
	 *   <li><b>生长</b>：每拍至多 CRYSTAL_RATE_FRACTION×水×(c/c_sat−1)×stirring
	 *       （亲和律，几何逼近不过冲）；无沉底晶时须过 NUCLEATION_AFFINITY
	 *       介稳门且速率×NUCLEATION_PENALTY——淬冷液亚稳悬挂，一粒种子塌缩；</li>
	 *   <li><b>回溶</b>：欠饱和且有沉底晶 → 即时回溶到曲线；</li>
	 *   <li><b>干涸</b>：溶剂耗尽 → 溶解物整批析出（熬干出锅盐）。</li>
	 * </ul>
	 *
	 * @return 是否有任何移动（决定是否重写釜）
	 */
	private static boolean crystalliseCurves(Map<ResourceLocation, Long> molecular, Map<String, Long> ions,
			Map<ResourceLocation, Long> sediment, int temperature, double stirring) {
		long water = molecular.getOrDefault(Solution.WATER, 0L);
		boolean moved = false;
		for (Species s : SpeciesManager.all()) {
			if (!s.isCrystallisable() || !s.isElectrolyte()) {
				continue;
			}
			long form = Solution.formableUnits(ions, s);
			long settled = sediment.getOrDefault(s.solute(), 0L);
			if (water <= 0) {
				// 蒸发干涸：溶剂没了，全部溶解物成批析出
				if (form > 0) {
					for (Species.IonComponent c : s.ions()) {
						ions.merge(c.ion().id(), -form * c.count(), Long::sum);
					}
					sediment.merge(s.solute(), form, Long::sum);
					moved = true;
				}
				continue;
			}
			double threshold = Solution.solubilityThreshold(s, temperature);
			long cap = (long) Math.floor(threshold * water);
			if (form > cap) {
				long excess = form - cap;
				double affinity = cap > 0 ? (double) form / cap - 1 : 1;
				if (settled <= 0 && affinity < Solution.NUCLEATION_AFFINITY) {
					continue; // 介稳：无种且未过成核门——什么都不发生
				}
				double rate = Solution.CRYSTAL_RATE_FRACTION * water * affinity * stirring;
				if (settled <= 0) {
					rate *= Solution.NUCLEATION_PENALTY; // 均相成核慢
				}
				long move = Math.min(excess, Math.max(1, Math.round(rate)));
				for (Species.IonComponent c : s.ions()) {
					ions.merge(c.ion().id(), -move * c.count(), Long::sum);
				}
				sediment.merge(s.solute(), move, Long::sum);
				moved = true;
			} else if (settled > 0 && form < cap) {
				// 欠饱和且有晶：即时回溶到曲线
				long move = Math.min(cap - form, settled);
				for (Species.IonComponent c : s.ions()) {
					ions.merge(c.ion().id(), move * c.count(), Long::sum);
				}
				sediment.merge(s.solute(), -move, Long::sum);
				moved = true;
			}
		}
		return moved;
	}

	/** 从分子域蒸走至多 {@code units} 水（quanta）；返回实际蒸出量。 */
	private static long evaporateWater(Map<ResourceLocation, Long> molecular, long units) {
		long vented = Math.min(units, molecular.getOrDefault(Solution.WATER, 0L));
		if (vented <= 0) {
			return 0;
		}
		molecular.merge(Solution.WATER, -vented, Long::sum);
		return vented;
	}
}
