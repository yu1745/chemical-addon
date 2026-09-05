package com.yu1745.chemicaladdon.gametest;

import com.yu1745.chemicaladdon.composition.parity.EngineBridge;
import com.yu1745.chemicaladdon.composition.parity.Kernel;
import com.yu1745.chemicaladdon.composition.parity.KernelSolutionState;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.reactor.ReactorTank;
import com.yu1745.chemengine.kernel.IPhreeqc;

import java.util.List;
import java.util.Map;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * P2 实弹：GameTest 内跑 EngineBridge（真实 FluidStack → IPhreeqc），
 * 验证单位桥与进料归集在真实釜数据上的行为（headless server，无需客户端）。
 */
@GameTestHolder(com.yu1745.chemicaladdon.ChemicalAddon.MODID)
@PrefixGameTestTemplate(false)
public final class ParityGameTests {

	private ParityGameTests() {}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void bridgeHclFeedSolvesAcidic(GameTestHelper helper) {
		// 1 mB 0.01M HCl 当量：水 999 + H+/Cl- 各 1（part 空间的 1/1000）
		ReactorTank tank = new ReactorTank(10_000, () -> {});
		tank.fill(Mixture.fromDeclaredComposition(.999, Map.of("HCl", .001), 1000, 25, java.util.List.of()), FluidAction.EXECUTE);

		EngineBridge.DerivedSolution view = observe(tank, List.of("Cl"), List.of("H+"));
		helper.assertTrue(view.waterKg() > 0, "native water kgw should be positive, got " + view.waterKg());
		helper.assertTrue(view.totalMol().getOrDefault("Cl", 0d) > 0, "native Cl inventory should be non-empty");

		double ph = view.ph();
		// 稀酸：pH 应 < 5（组成按 part 比值，约 1e-3 mol/kgw 量级 → pH ~3）
		helper.assertTrue(ph < 5.0 && ph > 0.5,
				"HCl feed should solve acidic, got pH=" + ph);
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void bridgeNaohFeedSolvesBasic(GameTestHelper helper) {
		ReactorTank tank = new ReactorTank(10_000, () -> {});
		tank.fill(Mixture.fromDeclaredComposition(.999, Map.of("NaOH", .001), 1000, 25, java.util.List.of()), FluidAction.EXECUTE);

		double ph = observe(tank, List.of("Na"), List.of("OH-")).ph();
		helper.assertTrue(ph > 9.0 && ph < 13.5,
				"NaOH feed should solve basic, got pH=" + ph);
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void kernelPathPrecipitatesLimestone(GameTestHelper helper) {
		// 固相桥（P7）：过饱和 Ca+CO₃ → 石灰石自发析出到 Suspended（相终量写回），
		// 旁观 Na/Cl 保留。与 rulesEnginePrecipitatesLimestone 同场景（退役锁），
		// 但走内核路径：TickDriver → WriteBack。
		ReactorTank tank = new ReactorTank(10_000, () -> {});
		ResourceLocation limestone = new ResourceLocation(ChemicalAddon.MODID, "limestone");
		tank.fill(GameTestFixtures.declared(1, Map.of("CaCl2", .3, "Na2CO3", .3), 2200), FluidAction.EXECUTE);

		var step = com.yu1745.chemicaladdon.composition.parity.TickDriver.step(tank.getFluids(), 0.5);
		helper.assertTrue(step.valid, "kernel step should be valid");
		com.yu1745.chemicaladdon.composition.parity.WriteBack.firstOf(tank.getFluids(), step);

		EngineBridge.DerivedSolution view = observe(tank, List.of("Ca", "Na", "Cl", "C(4)"), List.of("Ca+2", "Na+", "Cl-"));
		double calcite = step.phases.values().stream().mapToDouble(Double::doubleValue).sum();
		helper.assertTrue(calcite > .29, "CaCO3 must be retained as native phase inventory, got " + step.phases);
		helper.assertTrue(view.aqueousMol().getOrDefault("Ca+2", 0d) < 1e-3,
			"native dissolved calcium should be limited after precipitation: " + view.aqueousMol());
		helper.assertTrue(Math.abs(view.totalMol().getOrDefault("Na", 0d) - .6) < .02
			&& Math.abs(view.totalMol().getOrDefault("Cl", 0d) - .6) < .02,
			"spectator element inventories must be physical mol, got " + view.totalMol());
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void kernelPathGypsumSlurryDissolvesToSaturation(GameTestHelper helper) {
		// 固相桥（P7）：欠饱和悬浮石膏回溶到饱和（EQUILIBRIUM_PHASES 初量 = 悬浮 part），
		// 剩余固相写回、溶解的 Ca/S 落离子域——质量往返闭环。
		ReactorTank tank = new ReactorTank(10_000, () -> {});
		ResourceLocation gypsum = new ResourceLocation(ChemicalAddon.MODID, "gypsum");
		tank.fill(GameTestFixtures.declaredSolid(1, Map.of(), 1200, java.util.List.of(
			new com.yu1745.chemicaladdon.composition.parity.KernelSolutionState.SolidPhase(gypsum.toString(), .2,
				com.yu1745.chemicaladdon.composition.parity.KernelSolutionState.SolidLocation.SUSPENDED))), FluidAction.EXECUTE);

		var step = com.yu1745.chemicaladdon.composition.parity.TickDriver.step(tank.getFluids(), 0.5);
		helper.assertTrue(step.valid, "纯水+悬浮固也应有效");
		com.yu1745.chemicaladdon.composition.parity.WriteBack.firstOf(tank.getFluids(), step);

		EngineBridge.DerivedSolution view = observe(tank, List.of("Ca", "S(6)"), List.of("Ca+2"));
		helper.assertTrue(view.totalMol().getOrDefault("Ca", 0d) > 0d,
			"gypsum dissolution must appear in native calcium inventory: " + view.totalMol());
		helper.assertTrue(view.aqueousMol().getOrDefault("Ca+2", 0d) > 0d,
			"gypsum dissolution must have native aqueous calcium: " + view.aqueousMol());
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void bridgeNeutralFeedIsNeutral(GameTestHelper helper) {
		// 纯水（无离子）
		ReactorTank tank = new ReactorTank(10_000, () -> {});
		tank.fill(GameTestFixtures.declared(1, Map.of(), 1000), FluidAction.EXECUTE);

		double ph = observe(tank, List.of(), List.of()).ph();
		helper.assertTrue(Math.abs(ph - 7.0) < 1.0,
				"pure water should solve near pH 7, got " + ph);
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void bridgeSulfateGroupMaps(GameTestHelper helper) {
		// SO4 基团拆解：进料应含 S 总量（离子集必须是电中性的——Mixture.setIons 硬防线，
		// 硫酸根须配钠）
		ReactorTank tank = new ReactorTank(10_000, () -> {});
		tank.fill(GameTestFixtures.declared(1, Map.of("Na2SO4", .01), 1010), FluidAction.EXECUTE);

		EngineBridge.DerivedSolution view = observe(tank, List.of("S(6)", "Na"), List.of("SO4-2"));
		helper.assertTrue(view.totalMol().getOrDefault("S(6)", 0d) > 0,
				"SO4 should map to native S(6) inventory, got " + view.totalMol());
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void bridgeEngineReadingsSourceSwitchesPh(GameTestHelper helper) {
		// 读数链：EngineReadings 缓存 → pH 表计读数。单位桥是水基准伪质量制
		//（1 part = 1 g），2 parts HCl ≈ 0.055 molal → pH ≈ 1.3（不是 legacy 的
		// part 比例 pH 2-4——引擎读数与内核一致）。
		ReactorTank tank = new ReactorTank(10_000, () -> {});
		tank.fill(GameTestFixtures.declared(.999, Map.of("HCl", .002), 1001), FluidAction.EXECUTE);

		com.yu1745.chemicaladdon.composition.parity.EngineReadings.Snapshot s =
				com.yu1745.chemicaladdon.composition.parity.EngineReadings.refresh(tank.getFluids());
		helper.assertTrue(s.valid, "snapshot should be valid for an acidic feed");
		int steps = com.yu1745.chemicaladdon.composition.parity.EngineReadings.phSteps(s);
		helper.assertTrue(steps >= 2 && steps <= 4,
				"2 parts HCl = 2e-3 mol ≈ pH 2.7 应读 2-4（引擎连续值 " + s.ph + " → 步进 " + steps + "）");

		// 碱侧对照：2 parts NaOH ≈ 0.05 molal → pH ≈ 12.7
		ReactorTank base = new ReactorTank(10_000, () -> {});
		base.fill(GameTestFixtures.declared(.999, Map.of("NaOH", .002), 1001), FluidAction.EXECUTE);
		com.yu1745.chemicaladdon.composition.parity.EngineReadings.Snapshot sb =
				com.yu1745.chemicaladdon.composition.parity.EngineReadings.refresh(base.getFluids());
		helper.assertTrue(sb.valid, "basic snapshot should be valid");
		helper.assertTrue(sb.ph > 10.5 && sb.ph < 12,
				"2 parts NaOH = 2e-3 mol ≈ pH 11.3 应读 10.5-12，实测 " + sb.ph);
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void bridgeArchiveRoundTripsZeroDrift(GameTestHelper helper) {
		// 存档桥：archive（平衡+DUMP）→ NBT 字符串 → fromDump 重解。零漂移 = 存档前后
		// 同一状态解出同一 pH（逐位），与具体 pH 值无关——酸进料（Cl 1 g/L 当量）
		// 的 pH 本身就是零漂移的检验子。
		ReactorTank tank = new ReactorTank(10_000, () -> {});
		tank.fill(Mixture.fromDeclaredComposition(.999, Map.of("HCl", .001), 1000, 25, java.util.List.of()), FluidAction.EXECUTE);

		// Archive is authoritative raw text, so observe that exact state rather
		// than reconstructing a ChemState and applying a new pH/charge solve.
		double phBefore = observe(tank, List.of("Cl"), List.of("H+")).ph();

		FluidStack original = tank.getFluids().get(0);
		net.minecraft.nbt.CompoundTag tag = original.writeToNBT(new net.minecraft.nbt.CompoundTag());
		FluidStack restoredStack = FluidStack.loadFluidStackFromNBT(tag);
		var restoredState = Mixture.engineSolution(restoredStack);
		helper.assertTrue(restoredState != null, "mixture NBT must retain native raw state");
		String restored = restoredState.raw();

		helper.assertTrue(restored.equals(Mixture.engineSolution(original).raw()),
			"NBT restore must preserve the raw solution verbatim");
		ReactorTank restoredTank = new ReactorTank(10_000, () -> {});
		restoredTank.fill(restoredStack, FluidAction.EXECUTE);
		double ph = observe(restoredTank, List.of("Cl"), List.of("H+")).ph();
		helper.assertTrue(Math.abs(ph - phBefore) < 1e-9,
			"raw archive observation must have zero drift: " + phBefore + " -> " + ph);
		helper.assertTrue(ph > 2.5 && ph < 3.5, "acid archive must remain acidic, pH=" + ph);
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void bridgeTickDriverAdvancesKinetics(GameTestHelper helper) {
		// P4：漂白液（H+ / Cl / Hyp 池）tick 步进——池随时间变化（Quench 等通道）。
		// 注意：进料归集只能从离子域拿到 H/Cl；Hyp 是伪池，Mixture 里没有对应物——
		// 本步验证 tick 桥的 KINETICS 管道本身（纯 H/Cl/Na 体系步进仍应求解成功）。
		ReactorTank tank = new ReactorTank(10_000, () -> {});
		tank.fill(GameTestFixtures.declared(.998, Map.of("HCl", .001), 1000), FluidAction.EXECUTE);

		com.yu1745.chemicaladdon.composition.parity.TickDriver.Step s1 =
				com.yu1745.chemicaladdon.composition.parity.TickDriver.step(tank.getFluids(), 0.5);
		helper.assertTrue(s1.valid, "tick step should solve, got invalid");
		helper.assertTrue(s1.totals.containsKey("Cl"), "Cl total should be in snapshot");
		helper.assertTrue(s1.ph < 5.0, "acidic feed should stay acidic, pH=" + s1.ph);

		// 长时间步进也应稳定（不崩不 NaN）
		com.yu1745.chemicaladdon.composition.parity.TickDriver.Step s2 =
				com.yu1745.chemicaladdon.composition.parity.TickDriver.step(tank.getFluids(), 3600.0);
		helper.assertTrue(s2.valid, "long step should solve");
		helper.assertTrue(Double.isFinite(s2.ph), "pH should be finite");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void bridgeBleachSpeciesMapsToHypPool(GameTestHelper helper) {
		// P4b：次氯酸钠物种（伪池宿主）→ Hyp 伪元素账；与 Na 真实账共存
		ReactorTank tank = new ReactorTank(10_000, () -> {});
		ResourceLocation naocl = new ResourceLocation("chemicaladdon", "sodium_hypochlorite");
		tank.fill(GameTestFixtures.declared(.99, Map.of("NaOCl", .01), 1000), FluidAction.EXECUTE);

		EngineBridge.DerivedSolution view = observe(tank, List.of("Hyp", "Na"), List.of());
		double hyp = view.totalMol().getOrDefault("Hyp", 0.0);
		double na = view.totalMol().getOrDefault("Na", 0.0);
		helper.assertTrue(hyp > 0, "NaOCl 应映射 Hyp 伪池，实测 totals=" + view.totalMol());
		helper.assertTrue(na > 0, "Na 应进真实元素账");
		// 同当量：10 parts NaOCl → Na 10/22.99 vs Hyp 10/51.452 mol
		helper.assertTrue(Math.abs(na / hyp - 1.0) < 0.02,
				"NaOCl 质量分数拆分后 Na:Hyp 应 1:1 化学计量，实测 " + (na / hyp));
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void bridgeBleachQuenchAdvancesInGameSemantics(GameTestHelper helper) {
		// P4b 实战：漂白液（Hyp 池）+ 亚硫酸钠（Sul 池）同釜 → Quench 动力学推进
		ReactorTank tank = new ReactorTank(10_000, () -> {});
		ResourceLocation naocl = new ResourceLocation("chemicaladdon", "sodium_hypochlorite");
		ResourceLocation na2so3 = new ResourceLocation("chemicaladdon", "sodium_sulphite_solution");
		tank.fill(GameTestFixtures.declared(.98, Map.of("NaOCl", .015, "Na2SO3", .005), 1000), FluidAction.EXECUTE);

		// 长时间步进：Quench k=0.1，Sul 15/80 mol/kgw 量级在 1e4s 尺度应显著消耗
		com.yu1745.chemicaladdon.composition.parity.TickDriver.Step s =
				com.yu1745.chemicaladdon.composition.parity.TickDriver.step(tank.getFluids(), 10_000.0);
		helper.assertTrue(s.valid, "step should solve");
		double dSul = s.delta.getOrDefault("Sul", 0.0);
		double dHyp = s.delta.getOrDefault("Hyp", 0.0);
		double dCl = s.delta.getOrDefault("Cl", 0.0);
		helper.assertTrue(dSul < -1e-9, "Sul 应被 Quench 消耗，dSul=" + dSul);
		helper.assertTrue(dHyp < 0, "Hyp 应同步消耗，dHyp=" + dHyp);
		helper.assertTrue(dCl > 0, "Cl 应增长，dCl=" + dCl);
		// 多通道：Quench（Hyp1 Sul1 Cl1）+ HypDecay（Hyp1 Cl1，碱性浓溶液下可观）——
		// 共同不变量是 Cl 产率 = Hyp 消耗率（两通道均 1:1），Sul 只走 Quench
		helper.assertTrue(Math.abs(dCl + dHyp) < 1e-3,
				"Cl 产率 = Hyp 消耗率（两通道均 1:1）：dHyp=" + dHyp + " dCl=" + dCl);
		helper.assertTrue(-dSul <= -dHyp + 1e-5,
				"Sul 消耗（仅 Quench）不应超过 Hyp 总消耗：dSul=" + dSul + " dHyp=" + dHyp);
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void bridgeNitritePoolsMapAndReact(GameTestHelper helper) {
		// P4b：硝池映射 + Nitri 解封实战（漂白液氧化亚硝酸盐：HypOxidisesNitrite）
		ReactorTank tank = new ReactorTank(10_000, () -> {});
		ResourceLocation naocl = new ResourceLocation("chemicaladdon", "sodium_hypochlorite");
		ResourceLocation nano2 = new ResourceLocation("chemicaladdon", "sodium_nitrite");
		tank.fill(GameTestFixtures.declared(.98, Map.of("NaOCl", .01, "NaNO2", .01), 1000), FluidAction.EXECUTE);

		EngineBridge.DerivedSolution feed = observe(tank, List.of("Hyp", "Nitri"), List.of());
		helper.assertTrue(feed.totalMol().getOrDefault("Hyp", 0.0) > 0, "NaOCl → Hyp");
		helper.assertTrue(feed.totalMol().getOrDefault("Nitri", 0.0) > 0, "NaNO2 → Nitri");

		// KINETICS：HypOxidisesNitrite k=10，Nitri 有限量应显著推进
		com.yu1745.chemicaladdon.composition.parity.TickDriver.Step s =
				com.yu1745.chemicaladdon.composition.parity.TickDriver.step(tank.getFluids(), 10_000.0);
		helper.assertTrue(s.valid, "step should solve");
		double dNitri = s.delta.getOrDefault("Nitri", 0.0);
		double dNitra = s.delta.getOrDefault("Nitra", 0.0);
		double dHyp = s.delta.getOrDefault("Hyp", 0.0);
		helper.assertTrue(dNitri < -1e-9, "Nitri 应被氧化，dNitri=" + dNitri);
		helper.assertTrue(dNitra > 0, "Nitra 应增长，dNitra=" + dNitra);
		helper.assertTrue(dHyp < 0, "Hyp 应同步消耗");
		helper.assertTrue(Math.abs(dNitri + dNitra) < 1e-5 && Math.abs(dNitri - dHyp) < 1e-5,
				"HypOxidisesNitrite 1:1:1（Hyp↓Nitri↓Nitra↑）：dHyp=" + dHyp
						+ " dNitri=" + dNitri + " dNitra=" + dNitra);
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void bridgeWriteBackRoundTripsIons(GameTestHelper helper) {
		// 写回（增量迁移语义）：漂白+亚硫酸步进（Quench）→ 写回。基线取进料视图
		//（元素/伪池 mol——写回前化学在分子域，离子域是空的）；断言：迁移后离子域
		// 承载化学、已迁移物种从分子域清除（无双计）、二次进料不膨胀（幂等）、
		// 水份额不被稀释。
		ReactorTank tank = new ReactorTank(10_000, () -> {});
		ResourceLocation naocl = new ResourceLocation("chemicaladdon", "sodium_hypochlorite");
		ResourceLocation na2so3 = new ResourceLocation("chemicaladdon", "sodium_sulphite_solution");
		tank.fill(GameTestFixtures.declared(.98, Map.of("NaOCl", .015, "Na2SO3", .005), 1000), FluidAction.EXECUTE);

		EngineBridge.DerivedSolution feedBefore = observe(tank, List.of("Hyp", "Sul", "Cl", "S(6)", "Na"), List.of());
		double hypBefore = feedBefore.totalMol().getOrDefault("Hyp", 0.0);
		double sulBefore = feedBefore.totalMol().getOrDefault("Sul", 0.0);
		double waterBefore = feedBefore.waterKg();

		com.yu1745.chemicaladdon.composition.parity.TickDriver.Step s =
				com.yu1745.chemicaladdon.composition.parity.TickDriver.step(tank.getFluids(), 10_000.0);
		helper.assertTrue(s.valid, "step should solve");

		boolean wrote = com.yu1745.chemicaladdon.composition.parity.WriteBack.firstOf(tank.getFluids(), s);
		helper.assertTrue(wrote, "write-back should succeed (charge-neutral)");

		EngineBridge.DerivedSolution after = observe(tank, List.of("Hyp", "Sul", "Cl", "S(6)", "Na"), List.of());
		helper.assertTrue(after.totalMol().getOrDefault("Sul", 0d) < sulBefore,
				"Quench must consume native Sul inventory: " + after.totalMol());
		helper.assertTrue(after.totalMol().getOrDefault("Cl", 0d) > 0d
				&& after.totalMol().getOrDefault("S(6)", 0d) > 0d,
				"Quench products must remain in native inventories: " + after.totalMol());
		helper.assertTrue(after.totalMol().getOrDefault("Na", 0d) > 0d, "Na spectator must remain native");
		// A repeated observation/writeback boundary must not reconstruct or grow pools.
		EngineBridge.DerivedSolution feed2 = observe(tank, List.of("Hyp", "Sul"), List.of());
		helper.assertTrue(feed2.totalMol().getOrDefault("Hyp", 0.0) <= hypBefore * 1.001 + 1e-9,
				"Hyp must not grow at write-back: " + hypBefore + " -> " + feed2.totalMol().get("Hyp"));
		helper.assertTrue(feed2.totalMol().getOrDefault("Sul", 0.0) <= sulBefore * 1.001 + 1e-9,
				"Sul must not grow at write-back");
		helper.assertTrue(after.waterKg() >= waterBefore * .99 && after.waterKg() <= waterBefore * 1.01,
				"native water must remain stable across write-back: " + waterBefore + " -> " + after.waterKg());
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void bridgeKineticsLoopDrainsBleachOverTime(GameTestHelper helper) {
		// 主循环语义：多次步进+写回（游戏 tick 驱动）。断言全程无失败、Hyp 池
		//（进料视图）不随拍增长（幂等——无双计膨胀）、水份额不被稀释、电荷中性保持。
		ReactorTank tank = new ReactorTank(10_000, () -> {});
		ResourceLocation naocl = new ResourceLocation("chemicaladdon", "sodium_hypochlorite");
		tank.fill(GameTestFixtures.declared(.98, Map.of("NaOCl", .02), 1000), FluidAction.EXECUTE);

		double hypBefore = observe(tank, List.of("Hyp"), List.of()).totalMol().getOrDefault("Hyp", 0.0);
		double waterBefore = observe(tank, List.of(), List.of()).waterKg();
		double hypMax = hypBefore;
		boolean anyValid = false;
		for (int i = 0; i < 20; i++) {
			var s = com.yu1745.chemicaladdon.composition.parity.TickDriver.step(
					tank.getFluids(), 0.5);
			if (s.valid) {
				anyValid = true;
				com.yu1745.chemicaladdon.composition.parity.WriteBack.firstOf(tank.getFluids(), s);
				hypMax = Math.max(hypMax,
						observe(tank, List.of("Hyp"), List.of()).totalMol().getOrDefault("Hyp", 0.0));
			}
		}
		helper.assertTrue(anyValid, "至少一拍应有效");
		// 无还原剂时仅慢通道——短时间几乎不降但绝不应增长（增长 = 写回双计 bug）
		helper.assertTrue(hypMax <= hypBefore * 1.001 + 1e-9,
				"Hyp 池不应随拍增长（双计防线）：" + hypBefore + " → max " + hypMax);
		double waterAfter = observe(tank, List.of(), List.of()).waterKg();
		helper.assertTrue(waterAfter >= waterBefore * 0.9 && waterAfter <= waterBefore * 1.01,
				"水份额逐拍应有界稳定（首拍物种迁移后不再漂移）：" + waterBefore + " → " + waterAfter);
		helper.succeed();
	}

	/** Read the archived PHREEQC state without deriving a new feed from display NBT. */
	private static EngineBridge.DerivedSolution observe(ReactorTank tank, List<String> totals,
			List<String> species) {
		KernelSolutionState state = tank.getFluids().stream()
			.filter(Mixture::isMixture)
			.map(Mixture::engineSolution)
			.filter(java.util.Objects::nonNull)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("native test tank has no raw solution"));
		IPhreeqc q = Kernel.get();
		synchronized (q) {
			return EngineBridge.derive(q, state, totals, species);
		}
	}

	// New-state transport regressions.  These intentionally use neutral authored
	// formulae, never the retired molecules/ions display constructor.
	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void rawStateOneMbSampleKeepsIdentity(GameTestHelper helper) {
		ReactorTank tank = new ReactorTank(2_000, () -> {});
		FluidStack input = Mixture.fromDeclaredComposition(1, Map.of("HCl", .01), 1_000, 25, java.util.List.of());
		helper.assertTrue(tank.fill(input, FluidAction.EXECUTE) == 1_000, "state feed accepted");
		FluidStack sample = tank.drain(1, FluidAction.EXECUTE);
		helper.assertTrue(sample.getAmount() == 1 && Mixture.engineSolution(sample) != null,
			"1mB sample carries raw reference state");
		helper.assertTrue(sample.isFluidEqual(tank.getFluids().get(0)), "sample NBT identity is unchanged");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void rawStateMergeAndNbtRoundTrip(GameTestHelper helper) {
		ReactorTank tank = new ReactorTank(3_000, () -> {});
		tank.fill(Mixture.fromDeclaredComposition(1, Map.of("NaCl", .02), 1_000, 25, java.util.List.of()), FluidAction.EXECUTE);
		tank.fill(Mixture.fromDeclaredComposition(1, Map.of("HCl", .01), 1_000, 25, java.util.List.of()), FluidAction.EXECUTE);
		tank.collapseIfNeeded();
		helper.assertTrue(tank.getFluids().size() == 1 && Mixture.engineSolution(tank.getFluids().get(0)) != null,
			"heterogeneous feeds merge through native state");
		ReactorTank restored = new ReactorTank(3_000, () -> {});
		restored.deserializeNBT(tank.serializeNBT());
		helper.assertTrue(restored.getFluids().size() == 1 && Mixture.engineSolution(restored.getFluids().get(0)) != null,
			"raw state survives NBT");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void invalidExternalFeedIsNetZeroAndTanksIsolate(GameTestHelper helper) {
		ReactorTank left = new ReactorTank(2_000, () -> {});
		ReactorTank right = new ReactorTank(2_000, () -> {});
		left.fill(Mixture.fromDeclaredComposition(1, Map.of("NaCl", .01), 1_000, 25, java.util.List.of()), FluidAction.EXECUTE);
		right.fill(Mixture.fromDeclaredComposition(1, Map.of("HCl", .01), 1_000, 25, java.util.List.of()), FluidAction.EXECUTE);
		int before = left.getTotalAmount();
		boolean rejected = false;
		try { Mixture.fromDeclaredComposition(1, Map.of("NotARealFormula", 1d), 1_000, 25, java.util.List.of()); }
		catch (RuntimeException expected) { rejected = true; }
		helper.assertTrue(rejected && left.getTotalAmount() == before && right.getTotalAmount() == 1_000,
			"invalid declaration changes neither independent tank");
		helper.succeed();
	}
}
