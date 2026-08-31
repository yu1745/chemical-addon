package com.yu1745.chemicaladdon.gametest;

import com.yu1745.chemicaladdon.composition.parity.EngineBridge;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.composition.Solution;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.reactor.ReactorTank;
import com.yu1745.chemengine.kernel.ChemState;
import com.yu1745.chemengine.kernel.IPhreeqc;

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
		ResourceLocation water = Solution.WATER;
		tank.fill(Mixture.create(Map.of(water, 999), Map.of("H+1", 1, "Cl-1", 1), 1000),
				FluidAction.EXECUTE);

		EngineBridge.Feed feed = EngineBridge.toFeed(tank.getFluids());
		helper.assertTrue(feed.waterKg > 0, "water kgw should be positive, got " + feed.waterKg);
		helper.assertFalse(feed.totals.isEmpty(), "element totals should be non-empty");

		double ph = solvePh(feed, "hcl");
		// 稀酸：pH 应 < 5（组成按 part 比值，约 1e-3 mol/kgw 量级 → pH ~3）
		helper.assertTrue(ph < 5.0 && ph > 0.5,
				"HCl feed should solve acidic, got pH=" + ph);
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void bridgeNaohFeedSolvesBasic(GameTestHelper helper) {
		ReactorTank tank = new ReactorTank(10_000, () -> {});
		ResourceLocation water = Solution.WATER;
		tank.fill(Mixture.create(Map.of(water, 999), Map.of("Na+1", 1, "OH-1", 1), 1000),
				FluidAction.EXECUTE);

		EngineBridge.Feed feed = EngineBridge.toFeed(tank.getFluids());
		double ph = solvePh(feed, "naoh");
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
		ResourceLocation water = Solution.WATER;
		ResourceLocation limestone = new ResourceLocation(ChemicalAddon.MODID, "limestone");
		tank.fill(Mixture.create(Map.of(water, 1000),
			Map.of("Ca+2", 300, "Cl-1", 300, "Na+1", 300, "CO3-2", 300), 2200), FluidAction.EXECUTE);

		var step = com.yu1745.chemicaladdon.composition.parity.TickDriver.step(tank.getFluids(), 0.5);
		helper.assertTrue(step.valid, "kernel step should be valid");
		com.yu1745.chemicaladdon.composition.parity.WriteBack.firstOf(tank.getFluids(), step);

		FluidStack result = tank.getFluids().get(0);
		int suspended = Mixture.deriveSuspendedAmounts(result).getOrDefault(limestone, 0);
		helper.assertTrue(suspended >= 290 && suspended <= 300,
			"Ca+CO₃ 应析出 ~300 mB 石灰石，实测 " + suspended
				+ "（step totals=" + step.totals + " phases=" + step.phases + "）");
		Map<String, Integer> ions = Mixture.deriveIonAmounts(result);
		helper.assertTrue(ions.getOrDefault("Ca+2", 0) <= 1, "Ca+2 应被耗尽，实测 " + ions);
		helper.assertTrue(ions.getOrDefault("Na+1", 0) == 300, "Na+ 旁观保留，实测 " + ions);
		helper.assertTrue(ions.getOrDefault("Cl-1", 0) == 300, "Cl- 旁观保留，实测 " + ions);
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void kernelPathGypsumSlurryDissolvesToSaturation(GameTestHelper helper) {
		// 固相桥（P7）：欠饱和悬浮石膏回溶到饱和（EQUILIBRIUM_PHASES 初量 = 悬浮 part），
		// 剩余固相写回、溶解的 Ca/S 落离子域——质量往返闭环。
		ReactorTank tank = new ReactorTank(10_000, () -> {});
		ResourceLocation water = Solution.WATER;
		ResourceLocation gypsum = new ResourceLocation(ChemicalAddon.MODID, "gypsum");
		tank.fill(Mixture.create(Map.of(water, 1000), Map.of(),
			Map.of(gypsum, 200), Map.of(), 1200), FluidAction.EXECUTE);

		var step = com.yu1745.chemicaladdon.composition.parity.TickDriver.step(tank.getFluids(), 0.5);
		helper.assertTrue(step.valid, "纯水+悬浮固也应有效");
		com.yu1745.chemicaladdon.composition.parity.WriteBack.firstOf(tank.getFluids(), step);

		FluidStack result = tank.getFluids().get(0);
		int suspended = Mixture.deriveSuspendedAmounts(result).getOrDefault(gypsum, 0);
		helper.assertTrue(suspended >= 180 && suspended <= 200,
			"石膏应回溶到饱和（~16 mB 溶解，络合+活度），剩 " + suspended);
		int ca = Mixture.deriveIonAmounts(result).getOrDefault("Ca+2", 0);
		helper.assertTrue(ca >= 10 && ca <= 25, "溶解的 Ca 应落离子域（~16 mB），实测 " + ca);
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void bridgeNeutralFeedIsNeutral(GameTestHelper helper) {
		// 纯水（无离子）
		ReactorTank tank = new ReactorTank(10_000, () -> {});
		ResourceLocation water = Solution.WATER;
		tank.fill(Mixture.create(Map.of(water, 1000), 1000), FluidAction.EXECUTE);

		EngineBridge.Feed feed = EngineBridge.toFeed(tank.getFluids());
		double ph = solvePh(feed, "water");
		helper.assertTrue(Math.abs(ph - 7.0) < 1.0,
				"pure water should solve near pH 7, got " + ph);
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void bridgeSulfateGroupMaps(GameTestHelper helper) {
		// SO4 基团拆解：进料应含 S 总量（离子集必须是电中性的——Mixture.setIons 硬防线，
		// 硫酸根须配钠）
		ReactorTank tank = new ReactorTank(10_000, () -> {});
		ResourceLocation water = Solution.WATER;
		tank.fill(Mixture.create(Map.of(water, 1000), Map.of("SO4-2", 10, "Na+1", 20), 1010),
				FluidAction.EXECUTE);

		EngineBridge.Feed feed = EngineBridge.toFeed(tank.getFluids());
		helper.assertTrue(feed.totals.getOrDefault("S", 0.0) > 0,
				"SO4 should map to S total, got " + feed.totals);
		// H/O 不进元素账（PHREEQC 非法输入，酸碱归电荷平衡）
		helper.assertFalse(feed.totals.containsKey("H") || feed.totals.containsKey("O"),
				"H/O must never be element totals: " + feed.totals);
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void bridgeEngineReadingsSourceSwitchesPh(GameTestHelper helper) {
		// 读数链：EngineReadings 缓存 → pH 表计读数。单位桥是水基准伪质量制
		//（1 part = 1 g），2 parts HCl ≈ 0.055 molal → pH ≈ 1.3（不是 legacy 的
		// part 比例 pH 2-4——引擎读数与内核一致）。
		ReactorTank tank = new ReactorTank(10_000, () -> {});
		ResourceLocation water = Solution.WATER;
		tank.fill(Mixture.create(Map.of(water, 999), Map.of("H+1", 2, "Cl-1", 2), 1001),
				FluidAction.EXECUTE);

		com.yu1745.chemicaladdon.composition.parity.EngineReadings.Snapshot s =
				com.yu1745.chemicaladdon.composition.parity.EngineReadings.refresh(tank.getFluids());
		helper.assertTrue(s.valid, "snapshot should be valid for an acidic feed");
		int steps = com.yu1745.chemicaladdon.composition.parity.EngineReadings.phSteps(s);
		helper.assertTrue(steps >= 2 && steps <= 4,
				"2 parts HCl = 2e-3 mol ≈ pH 2.7 应读 2-4（引擎连续值 " + s.ph + " → 步进 " + steps + "）");

		// 碱侧对照：2 parts NaOH ≈ 0.05 molal → pH ≈ 12.7
		ReactorTank base = new ReactorTank(10_000, () -> {});
		base.fill(Mixture.create(Map.of(water, 999), Map.of("Na+1", 2, "OH-1", 2), 1001),
				FluidAction.EXECUTE);
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
		ResourceLocation water = Solution.WATER;
		tank.fill(Mixture.create(Map.of(water, 999),
				Map.of("H+1", 1, "Cl-1", 1), 1000), FluidAction.EXECUTE);

		// 存档前基准：直接求解的 pH
		double phBefore;
		try (com.yu1745.chemengine.kernel.IPhreeqc q = com.yu1745.chemengine.kernel.IPhreeqc.create()) {
			com.yu1745.chemengine.kernel.ChemState b = com.yu1745.chemengine.kernel.ChemState.builder("base")
					.waterKg(0.999).pHCharge().tempC(25)
					.total("Cl", 1.0e-3 / 0.999).build();
			var r0 = q.equilibrate(b, "pH");
			phBefore = r0.row(r0.rowCount() - 1).d("pH");
		}

		String dump = com.yu1745.chemicaladdon.composition.parity.EngineArchive.archiveOf(tank.getFluids());
		helper.assertTrue(dump != null && !dump.isBlank(), "archive should produce dump text");

		// NBT 往返（模拟存档）
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		tag.putString(com.yu1745.chemicaladdon.composition.parity.EngineArchive.KEY, dump);
		String restored = com.yu1745.chemicaladdon.composition.parity.EngineArchive.read(tag);
		helper.assertTrue(dump.equals(restored), "NBT round-trip must be lossless");

		// 恢复审视：fromDump 重解的 pH 与存档前一致（零漂移）
		com.yu1745.chemengine.kernel.ChemState s =
				com.yu1745.chemengine.kernel.ChemState.fromDump(restored);
		helper.assertTrue(s != null, "fromDump should parse");
		try (com.yu1745.chemengine.kernel.IPhreeqc q = com.yu1745.chemengine.kernel.IPhreeqc.create()) {
			var r = q.equilibrate(s, "pH");
			double ph = r.row(r.rowCount() - 1).d("pH");
			helper.assertTrue(Math.abs(ph - phBefore) < 0.05,
					"存档恢复零漂移：pH " + phBefore + " → " + ph);
			helper.assertTrue(ph > 2.5 && ph < 3.5,
				"酸进料应酸性（1 part HCl = 1e-3 mol ≈ pH 3）：pH=" + ph);
		}
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void bridgeTickDriverAdvancesKinetics(GameTestHelper helper) {
		// P4：漂白液（H+ / Cl / Hyp 池）tick 步进——池随时间变化（Quench 等通道）。
		// 注意：进料归集只能从离子域拿到 H/Cl；Hyp 是伪池，Mixture 里没有对应物——
		// 本步验证 tick 桥的 KINETICS 管道本身（纯 H/Cl/Na 体系步进仍应求解成功）。
		ReactorTank tank = new ReactorTank(10_000, () -> {});
		ResourceLocation water = Solution.WATER;
		tank.fill(Mixture.create(Map.of(water, 998), Map.of("H+1", 1, "Cl-1", 1), 1000),
				FluidAction.EXECUTE);

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
		ResourceLocation water = Solution.WATER;
		ResourceLocation naocl = new ResourceLocation("chemicaladdon", "sodium_hypochlorite");
		tank.fill(Mixture.create(Map.of(water, 990, naocl, 10), Map.of(), 1000), FluidAction.EXECUTE);

		EngineBridge.Feed feed = EngineBridge.toFeed(tank.getFluids());
		double hyp = feed.totals.getOrDefault("Hyp", 0.0);
		double na = feed.totals.getOrDefault("Na", 0.0);
		helper.assertTrue(hyp > 0, "NaOCl 应映射 Hyp 伪池，实测 totals=" + feed.totals);
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
		ResourceLocation water = Solution.WATER;
		ResourceLocation naocl = new ResourceLocation("chemicaladdon", "sodium_hypochlorite");
		ResourceLocation na2so3 = new ResourceLocation("chemicaladdon", "sodium_sulphite_solution");
		tank.fill(Mixture.create(Map.of(water, 980, naocl, 15, na2so3, 5), Map.of(), 1000),
				FluidAction.EXECUTE);

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
		ResourceLocation water = Solution.WATER;
		ResourceLocation naocl = new ResourceLocation("chemicaladdon", "sodium_hypochlorite");
		ResourceLocation nano2 = new ResourceLocation("chemicaladdon", "sodium_nitrite");
		tank.fill(Mixture.create(Map.of(water, 980, naocl, 10, nano2, 10), Map.of(), 1000),
				FluidAction.EXECUTE);

		EngineBridge.Feed feed = EngineBridge.toFeed(tank.getFluids());
		helper.assertTrue(feed.totals.getOrDefault("Hyp", 0.0) > 0, "NaOCl → Hyp");
		helper.assertTrue(feed.totals.getOrDefault("Nitri", 0.0) > 0, "NaNO2 → Nitri");

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
		ResourceLocation water = Solution.WATER;
		ResourceLocation naocl = new ResourceLocation("chemicaladdon", "sodium_hypochlorite");
		ResourceLocation na2so3 = new ResourceLocation("chemicaladdon", "sodium_sulphite_solution");
		tank.fill(Mixture.create(Map.of(water, 980, naocl, 15, na2so3, 5), Map.of(), 1000),
				FluidAction.EXECUTE);

		EngineBridge.Feed feedBefore = EngineBridge.toFeed(tank.getFluids());
		double hypBefore = feedBefore.totals.getOrDefault("Hyp", 0.0);
		double sulBefore = feedBefore.totals.getOrDefault("Sul", 0.0);
		double waterBefore = feedBefore.waterKg;

		com.yu1745.chemicaladdon.composition.parity.TickDriver.Step s =
				com.yu1745.chemicaladdon.composition.parity.TickDriver.step(tank.getFluids(), 10_000.0);
		helper.assertTrue(s.valid, "step should solve");

		boolean wrote = com.yu1745.chemicaladdon.composition.parity.WriteBack.firstOf(tank.getFluids(), s);
		helper.assertTrue(wrote, "write-back should succeed (charge-neutral)");

		Map<String, Integer> after = Mixture.deriveUnitIonAmounts(tank.getFluids().get(0));
		// Sul≈0（Quench 耗尽亚硫酸）：迁移后离子域应承载近零 SO3
		helper.assertTrue(after.getOrDefault("SO3-2", 0) < 100,
				"SO3 应被 Quench 消耗并写回近零，实测 " + after.getOrDefault("SO3-2", 0));
		helper.assertTrue(after.getOrDefault("Cl-1", 0) > 0, "Cl 应增长（Quench 产物）");
		helper.assertTrue(after.getOrDefault("SO4-2", 0) > 0, "SO4 应出现（Quench 产物）");
		helper.assertTrue(after.getOrDefault("Na+1", 0) > 0, "Na 旁观保留");
		// 已迁移物种清除：分子域不应再持有 naocl/na2so3（否则与离子域双计）
		Map<ResourceLocation, Integer> molAfter = Mixture.deriveUnitAmounts(tank.getFluids().get(0));
		helper.assertFalse(molAfter.containsKey(naocl),
				"已迁移电解质应从分子域清除（双计防线）：" + molAfter.keySet());
		helper.assertFalse(molAfter.containsKey(na2so3), "已迁移电解质应从分子域清除");
		// 幂等：写回后再进料，同一池不得增长；水份额不变
		EngineBridge.Feed feed2 = EngineBridge.toFeed(tank.getFluids());
		helper.assertTrue(feed2.totals.getOrDefault("Hyp", 0.0) <= hypBefore * 1.001 + 1e-9,
				"Hyp 二次进料不得增长（幂等）：" + hypBefore + " → " + feed2.totals.get("Hyp"));
		helper.assertTrue(feed2.totals.getOrDefault("Sul", 0.0) <= sulBefore * 1.001 + 1e-9,
				"Sul 二次进料不得增长（幂等）");
		helper.assertTrue(feed2.waterKg >= waterBefore * 0.9 && feed2.waterKg <= waterBefore * 1.01,
				"水份额迁移后应有界稳定（解离扩 Σ 的旧虚构约定，允诘2%级一次性稀释）："
						+ waterBefore + " → " + feed2.waterKg);
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void bridgeKineticsLoopDrainsBleachOverTime(GameTestHelper helper) {
		// 主循环语义：多次步进+写回（游戏 tick 驱动）。断言全程无失败、Hyp 池
		//（进料视图）不随拍增长（幂等——无双计膨胀）、水份额不被稀释、电荷中性保持。
		ReactorTank tank = new ReactorTank(10_000, () -> {});
		ResourceLocation water = Solution.WATER;
		ResourceLocation naocl = new ResourceLocation("chemicaladdon", "sodium_hypochlorite");
		tank.fill(Mixture.create(Map.of(water, 980, naocl, 20), Map.of(), 1000),
				FluidAction.EXECUTE);

		double hypBefore = EngineBridge.toFeed(tank.getFluids()).totals.getOrDefault("Hyp", 0.0);
		double waterBefore = EngineBridge.toFeed(tank.getFluids()).waterKg;
		double hypMax = hypBefore;
		boolean anyValid = false;
		for (int i = 0; i < 20; i++) {
			var s = com.yu1745.chemicaladdon.composition.parity.TickDriver.step(
					tank.getFluids(), 0.5);
			if (s.valid) {
				anyValid = true;
				com.yu1745.chemicaladdon.composition.parity.WriteBack.firstOf(tank.getFluids(), s);
				hypMax = Math.max(hypMax,
						EngineBridge.toFeed(tank.getFluids()).totals.getOrDefault("Hyp", 0.0));
			}
		}
		helper.assertTrue(anyValid, "至少一拍应有效");
		// 无还原剂时仅慢通道——短时间几乎不降但绝不应增长（增长 = 写回双计 bug）
		helper.assertTrue(hypMax <= hypBefore * 1.001 + 1e-9,
				"Hyp 池不应随拍增长（双计防线）：" + hypBefore + " → max " + hypMax);
		double waterAfter = EngineBridge.toFeed(tank.getFluids()).waterKg;
		helper.assertTrue(waterAfter >= waterBefore * 0.9 && waterAfter <= waterBefore * 1.01,
				"水份额逐拍应有界稳定（首拍物种迁移后不再漂移）：" + waterBefore + " → " + waterAfter);
		helper.assertTrue(Mixture.isChargeNeutralLong(Mixture.getIons(tank.getFluids().get(0))),
				"电荷中性应保持");
		helper.succeed();
	}

	private static double solvePh(EngineBridge.Feed feed, String tag) {
		ChemState.Builder b = ChemState.builder(tag)
				.waterKg(feed.waterKg)
				.pHCharge()
				.tempC(feed.tempC);
		feed.totals.forEach(b::total);
		try (IPhreeqc q = IPhreeqc.create()) {
			IPhreeqc.RunResult r = q.equilibrate(b.build(), "pH");
			return r.row(r.rowCount() - 1).d("pH");
		}
	}
}
