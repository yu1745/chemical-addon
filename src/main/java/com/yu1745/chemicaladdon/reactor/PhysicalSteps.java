package com.yu1745.chemicaladdon.reactor;

import java.util.ArrayList;

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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 内核主循环后的游戏物理层。水相只以 {@code KernelSolutionState} 的 PHREEQC
 * RAW 为权威；本类只提交明确的原生事务，绝不从显示 NBT 重建化学状态。
 *
 * <ul>
 *   <li><b>固体投料和晶种</b>：仅映射到 {@code PhaseBridge} 的物品进入真实
 *       固相 ledger，下一内核步决定溶解或析出；湿饼同时合并其 RAW 母液。</li>
 *   <li><b>开口蒸发</b>：以原生 H2O REACTION 撤水，按真实水质量计算潜热冷却，
 *       蒸出量经 {@code ventedQuanta} 交给冷凝器。</li>
 *   <li><b>气液传质</b>：气体以声明中性式进入原生 REACTION，按物种
 *       {@code gasSolubility} 限制每拍转移量。</li>
 * </ul>
 *
 * <p>mB 只表示 Forge 的运输体积；RAW 的 {@code referenceMb} 使抽样按比例
 * 缩放。pH、络合和相平衡均由内核处理，本类不直接修改其派生显示域。
 */
public final class PhysicalSteps {

	private PhysicalSteps() {}

	/**
	 * 一步物理拍（内核步进与写回<b>之后</b>调用）。每项成功操作安装一个新的
	 * 不可变 RAW state；无操作时不会改罐内容。
	 *
	 * <p>蒸发带走的热量按 {@code Q=m·Lv} 和 materialised 水质量计算；下一拍
	 * 是否继续沸腾由当前温度和热源决定。
	 *
	 * @param ventedQuanta 蒸汽上报（quanta 单位；null = 上层不关心）
	 * @param temperature  本拍釜温（蒸发判据 + 写回温度）
	 */
	public static void apply(ReactorTank tank, boolean openVessel, @Nullable IItemHandler items,
			double stirring, @Nullable long[] ventedQuanta, int temperature) {
		boolean rawlessMixture = tank.getFluids().stream()
			.anyMatch(stack -> Mixture.isMixture(stack) && Mixture.engineSolution(stack) == null);
		if (rawlessMixture) {
			// There is intentionally no compatibility chemistry. A malformed
			// mixture is rejected rather than reconstructed from its display tags.
			com.yu1745.chemicaladdon.ChemicalAddon.LOGGER.error(
				"Rejected PhysicalSteps invocation containing a raw-less mixture");
			return;
		}
		// Empty vessels and gas/oil-only contents have no aqueous physical step.
		if (!tank.containsEngineBackedMixture()) return;
		// Every physical mutation below is a native transaction or a solid-ledger
		// update; no display-domain chemistry is reconstructed here.
			if (openVessel && temperature >= RulesEngine.WATER_BOILING_C && !tank.getFluids().isEmpty()) {
				FluidStack stack = tank.getFluids().stream().filter(Mixture::isMixture).findFirst().orElse(null);
				if (stack == null) return;
				var state = Mixture.engineSolution(stack);
				if (state != null) try {
					var q = com.yu1745.chemicaladdon.composition.parity.Kernel.get();
					synchronized (q) {
						int ventedMb = Math.min(RulesEngine.EVAPORATION_RATE_MB, stack.getAmount() - 1);
						if (ventedMb <= 0) return;
						var actual = state.scale(q, stack.getAmount());
						var solved = actual.evaporateWater(q, ventedMb / 1000d);
						var next = new com.yu1745.chemicaladdon.composition.parity.KernelSolutionState(
							solved.raw(), stack.getAmount() - ventedMb, solved.solids());
						stack.setAmount(stack.getAmount() - ventedMb);
						Mixture.setEngineSolution(stack, next);
						// Q=m·Lv, C=mwater·cp; use the native materialised water mass.
						double waterKg = com.yu1745.chemicaladdon.composition.parity.EngineBridge
							.derive(q, actual, java.util.List.of(), java.util.List.of()).waterKg();
						int cooling = waterKg > 0 ? Math.max(1, (int) Math.round(
							(ventedMb / 1000d) * 2260d / (waterKg * 4.18d))) : 0;
						Temperature.set(stack, Math.max(0, temperature - cooling));
						if (ventedQuanta != null) ventedQuanta[0] += (long) ventedMb * Chemistry.QUANTA_PER_MB;
					}
				} catch (RuntimeException ex) {
					com.yu1745.chemicaladdon.ChemicalAddon.LOGGER.warn("Native evaporation rejected: {}", ex.getMessage());
				}
			}
			// Gas injection is also an explicit native REACTION.  A failed model or
			// native solve leaves the gas stack untouched, so an unsupported gas can
			// never disappear into a fake dissolved display value.
			for (FluidStack gas : tank.getFluids()) {
				if (!Miscibility.isGas(gas) || gas.isEmpty()) continue;
				ResourceLocation id = ForgeRegistries.FLUIDS.getKey(gas.getFluid());
				Species species = id == null ? null : SpeciesManager.get(id);
				if (species == null || species.engineFormula() == null || species.engineFormula().isBlank()) continue;
				FluidStack liquid = tank.getFluids().stream().filter(Mixture::isMixture).findFirst().orElse(null);
				if (liquid == null) continue;
				var state = Mixture.engineSolution(liquid);
				if (state == null) continue;
				try {
					var q = com.yu1745.chemicaladdon.composition.parity.Kernel.get();
					synchronized (q) {
						// Gas leaving and dissolved volume entering are one-for-one, so a
						// full vessel can transfer internally. The data solubility also
						// limits a single physical step; it cannot absorb a whole headspace.
						var actual = state.scale(q, liquid.getAmount());
						double waterKg = com.yu1745.chemicaladdon.composition.parity.EngineBridge
							.derive(q, actual, java.util.List.of(), java.util.List.of()).waterKg();
						double retention = Double.isNaN(species.gasSolubility())
							? Solution.GAS_SOLUBILITY_DEFAULT : species.gasSolubility();
						int transfer = (int) Math.min(gas.getAmount(), Math.floor(retention * waterKg * 1000d / 20d));
						if (transfer <= 0) continue;
						var next = actual.reactDeclared(q,
							java.util.Map.of(species.engineFormula(), transfer / 1000d));
						// The dissolved batch owns the transferred physical volume too.
						next = new com.yu1745.chemicaladdon.composition.parity.KernelSolutionState(
							next.raw(), liquid.getAmount() + transfer, next.solids());
						liquid.grow(transfer);
						Mixture.setEngineSolution(liquid, next);
						gas.shrink(transfer);
					}
				} catch (RuntimeException ex) {
					com.yu1745.chemicaladdon.ChemicalAddon.LOGGER.warn("Native gas transfer rejected for {}: {}", id, ex.getMessage());
				}
			}
			// A solid item is a physical seed/feed, not a legacy ion expansion.
			// Only solids with an authored PHREEQC phase may enter the state; the
			// following kernel tick decides whether it dissolves or grows.
			if (items != null) for (int slot = 0; slot < items.getSlots(); slot++) {
				var item = items.getStackInSlot(slot);
				if (item.isEmpty()) continue;
				if (items.extractItem(slot, 1, true).isEmpty()) continue;
				if (item.getItem() instanceof com.yu1745.chemicaladdon.item.MixedResidueItem) {
					var liquor = com.yu1745.chemicaladdon.item.MixedResidueItem.engineLiquor(item);
					var solids = com.yu1745.chemicaladdon.item.MixedResidueItem.engineSolids(item);
					FluidStack liquid = tank.getFluids().stream().filter(Mixture::isMixture).findFirst().orElse(null);
					if (liquor == null || liquid == null) continue;
					var state = Mixture.engineSolution(liquid);
					if (state == null) continue;
					try {
						var q = com.yu1745.chemicaladdon.composition.parity.Kernel.get();
						synchronized (q) {
							var merged = com.yu1745.chemicaladdon.composition.parity.KernelSolutionState.merge(q,
								java.util.List.of(state.scale(q, liquid.getAmount()), liquor));
							if (merged.referenceMb() > tank.getTankCapacity(0) - tank.getTotalAmount() + liquid.getAmount())
								throw new IllegalArgumentException("wet cake liquor does not fit");
							var allSolids = new ArrayList<>(merged.solids()); allSolids.addAll(solids);
							Mixture.setEngineSolution(liquid, merged.withSolids(allSolids));
							liquid.setAmount(merged.referenceMb());
							items.extractItem(slot, 1, false);
						}
					} catch (RuntimeException ex) {
						com.yu1745.chemicaladdon.ChemicalAddon.LOGGER.warn("Wet cake reinjection rejected: {}", ex.getMessage());
					}
					continue;
				}
				for (var phase : com.yu1745.chemicaladdon.composition.parity.PhaseBridge.all()) {
					ResourceLocation solid = phase.species();
					Item whole = ForgeRegistries.ITEMS.getValue(solid);
					Item grain = ForgeRegistries.ITEMS.getValue(new ResourceLocation(solid.getNamespace(),
						solid.getPath() + "_grain"));
					if (item.getItem() != whole && item.getItem() != grain) continue;
					FluidStack liquid = tank.getFluids().stream().filter(Mixture::isMixture).findFirst().orElse(null);
					if (liquid == null) break;
					var state = Mixture.engineSolution(liquid);
					if (state == null) break;
					try {
						var q = com.yu1745.chemicaladdon.composition.parity.Kernel.get();
						synchronized (q) {
							var actual = state.scale(q, liquid.getAmount());
							var nextSolids = new ArrayList<>(actual.solids());
							nextSolids.add(new com.yu1745.chemicaladdon.composition.parity.KernelSolutionState.SolidPhase(
								solid.toString(), item.getItem() == grain ? 1d / RulesEngine.GRAINS_PER_ITEM : 1d,
								com.yu1745.chemicaladdon.composition.parity.KernelSolutionState.SolidLocation.SEDIMENT));
							Mixture.setEngineSolution(liquid, actual.withSolids(nextSolids));
							items.extractItem(slot, 1, false);
						}
					} catch (RuntimeException ex) {
						com.yu1745.chemicaladdon.ChemicalAddon.LOGGER.warn("Native solid feed rejected for {}: {}", solid, ex.getMessage());
					}
					break;
				}
			}
			tank.pruneEmpty();
			return;
	}

}
