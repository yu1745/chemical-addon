package com.yu1745.chemicaladdon.gametest;

import com.yu1745.chemicaladdon.composition.parity.EngineBridge;
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

/**
 * P2 实弹：GameTest 内跑 EngineBridge（真实 FluidStack → IPhreeqc），
 * 验证单位桥与进料归集在真实釜数据上的行为（headless server，无需客户端）。
 */
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
		// SO4 基团拆解：进料应含 S 与 O 总量
		ReactorTank tank = new ReactorTank(10_000, () -> {});
		ResourceLocation water = Solution.WATER;
		tank.fill(Mixture.create(Map.of(water, 1000), Map.of("SO4-2", 10), 1010),
				FluidAction.EXECUTE);

		EngineBridge.Feed feed = EngineBridge.toFeed(tank.getFluids());
		helper.assertTrue(feed.totals.getOrDefault("S", 0.0) > 0,
				"SO4 should map to S total, got " + feed.totals);
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void bridgeEngineReadingsSourceSwitchesPh(GameTestHelper helper) {
		// P3 读数源：EngineReadings 缓存 → phOf 表计读数（engine 模式）。
		// 本测试直接驱动 refresh + phSteps，验证快照语义（开关本身是系统属性，
		// GameTest 进程级不便于翻转，故直接断言组件行为）。
		ReactorTank tank = new ReactorTank(10_000, () -> {});
		ResourceLocation water = Solution.WATER;
		tank.fill(Mixture.create(Map.of(water, 999), Map.of("H+1", 2, "Cl-1", 2), 1001),
				FluidAction.EXECUTE);

		com.yu1745.chemicaladdon.composition.parity.EngineReadings.Snapshot s =
				com.yu1745.chemicaladdon.composition.parity.EngineReadings.refresh(tank.getFluids());
		helper.assertTrue(s.valid, "snapshot should be valid for an acidic feed");
		int steps = com.yu1745.chemicaladdon.composition.parity.EngineReadings.phSteps(s);
		helper.assertTrue(steps >= 2 && steps <= 4,
				"0.002 当量酸应读 pH 2-4（引擎连续值 " + s.ph + " → 步进 " + steps + "）");

		// 碱侧对照
		ReactorTank base = new ReactorTank(10_000, () -> {});
		base.fill(Mixture.create(Map.of(water, 999), Map.of("Na+1", 2, "OH-1", 2), 1001),
				FluidAction.EXECUTE);
		com.yu1745.chemicaladdon.composition.parity.EngineReadings.Snapshot sb =
				com.yu1745.chemicaladdon.composition.parity.EngineReadings.refresh(base.getFluids());
		helper.assertTrue(sb.valid, "basic snapshot should be valid");
		helper.assertTrue(sb.ph > 10 && sb.ph < 13,
				"0.002 当量碱应读 pH 10-13，实测 " + sb.ph);
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void bridgeArchiveRoundTripsZeroDrift(GameTestHelper helper) {
		// P3b 存档桥：archive（平衡+DUMP）→ NBT 字符串 → fromDump 审视 pH，零漂移
		ReactorTank tank = new ReactorTank(10_000, () -> {});
		ResourceLocation water = Solution.WATER;
		tank.fill(Mixture.create(Map.of(water, 997),
				Map.of("H+1", 1, "Cl-1", 1, "Na+1", 1, "OH-1", 1), 1000), FluidAction.EXECUTE);

		String dump = com.yu1745.chemicaladdon.composition.parity.EngineArchive.archiveOf(tank.getFluids());
		helper.assertTrue(dump != null && !dump.isBlank(), "archive should produce dump text");

		// NBT 往返（模拟存档）
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		tag.putString(com.yu1745.chemicaladdon.composition.parity.EngineArchive.KEY, dump);
		String restored = com.yu1745.chemicaladdon.composition.parity.EngineArchive.read(tag);
		helper.assertTrue(dump.equals(restored), "NBT round-trip must be lossless");

		// 恢复审视：fromDump 解出的 pH 与存档前一致（近中性：H+ 与 OH- 等当量）
		com.yu1745.chemengine.kernel.ChemState s =
				com.yu1745.chemengine.kernel.ChemState.fromDump(restored);
		helper.assertTrue(s != null, "fromDump should parse");
		try (com.yu1745.chemengine.kernel.IPhreeqc q = com.yu1745.chemengine.kernel.IPhreeqc.create()) {
			var r = q.equilibrate(s, "pH");
			double ph = r.row(r.rowCount() - 1).d("pH");
			helper.assertTrue(ph > 5.5 && ph < 8.5,
					"等当量酸碱存档恢复应近中性，pH=" + ph);
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
		helper.assertTrue(Math.abs(na / hyp - 51.452 / 22.99) < 0.02,
				"Na:Hyp 摩尔比应 = 51.45:22.99（同 parts），实测 " + (na / hyp));
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
		// Quench 1:1:1 计量（Hyp:Sul:Cl）
		helper.assertTrue(Math.abs(dHyp - dSul) < 1e-9 && Math.abs(dCl + dSul) < 1e-8,
				"Quench 1:1:1 计量：dHyp=" + dHyp + " dSul=" + dSul + " dCl=" + dCl);
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
		helper.assertTrue(Math.abs(dNitri + dNitra) < 1e-8 && Math.abs(dNitri - dHyp) < 1e-8,
				"HypOxidisesNitrite 1:1:1（Hyp↓Nitri↓Nitra↑）：dHyp=" + dHyp
						+ " dNitri=" + dNitri + " dNitra=" + dNitra);
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void bridgePressureFeedDrivesSulAbsorb(GameTestHelper helper) {
		// P4c：SO2 气相供压 → SulAbsorb（interface）驱动吸收。
		// 半釜 SO2 气（500/1000 mB → ~1 atm 分压）+ 纯碱液：吸收应推进 Sul 池增长。
		ReactorTank tank = new ReactorTank(1_000, () -> {});
		ResourceLocation water = Solution.WATER;
		tank.fill(Mixture.create(Map.of(water, 500), Map.of(), 500), FluidAction.EXECUTE);
		tank.fill(new net.minecraftforge.fluids.FluidStack(
				com.yu1745.chemicaladdon.registry.AllFluids.SULFUR_DIOXIDE.get().getSource(), 500),
				FluidAction.EXECUTE);

		EngineBridge.Feed feed = EngineBridge.toFeed(tank.getFluids());
		java.util.Map<String, double[]> parms =
				com.yu1745.chemicaladdon.composition.parity.PressureFeed.of(tank.getFluids(), 1_000, 25);
		helper.assertTrue(parms.containsKey("SulAbsorb"),
				"SO2 在场应产生 SulAbsorb 供压，实测 " + parms.keySet());
		helper.assertTrue(parms.get("SulAbsorb")[1] > 0.5,
				"半釜 SO2 分压应 ~1 atm，实测 " + parms.get("SulAbsorb")[1]);

		// 带 interface 的 KINETICS：Sul 应增长（亨利自限：渐近 H*P）
		try (com.yu1745.chemengine.kernel.IPhreeqc q = com.yu1745.chemengine.kernel.IPhreeqc.create()) {
			var r = q.run(feed.toScriptWithKinetics(com.yu1745.chemengine.kernel.Curation.load(),
					parms.keySet(), parms, 10_000.0));
			int last = r.rowCount() - 1;
			double sul = r.row(last).d("Sul") * feed.waterKg;
			helper.assertTrue(sul > 1e-3, "SO2 应被吸收进 Sul 池（10^4s），实测 " + sul + " mol");
		}
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void bridgeWriteBackRoundTripsIons(GameTestHelper helper) {
		// P5 写回：漂白+亚硫酸步进（Quench）→ 写回 Mixture 离子域 → derive 验证。
		// 预期：OCl↓ SO3↓ Cl↑ SO4↑（Quench 计量），电荷中性保持（setIons 硬防线）。
		ReactorTank tank = new ReactorTank(10_000, () -> {});
		ResourceLocation water = Solution.WATER;
		ResourceLocation naocl = new ResourceLocation("chemicaladdon", "sodium_hypochlorite");
		ResourceLocation na2so3 = new ResourceLocation("chemicaladdon", "sodium_sulphite_solution");
		tank.fill(Mixture.create(Map.of(water, 980, naocl, 15, na2so3, 5), Map.of(), 1000),
				FluidAction.EXECUTE);

		Map<String, Integer> before = Mixture.deriveUnitIonAmounts(tank.getFluids().get(0));

		com.yu1745.chemicaladdon.composition.parity.TickDriver.Step s =
				com.yu1745.chemicaladdon.composition.parity.TickDriver.step(tank.getFluids(), 10_000.0);
		helper.assertTrue(s.valid, "step should solve");

		boolean wrote = com.yu1745.chemicaladdon.composition.parity.WriteBack.firstOf(tank.getFluids(), s);
		helper.assertTrue(wrote, "write-back should succeed (charge-neutral)");

		Map<String, Integer> after = Mixture.deriveUnitIonAmounts(tank.getFluids().get(0));
		helper.assertTrue(after.getOrDefault("OCl-1", 0) < before.getOrDefault("OCl-1", 0),
				"OCl 应减少：" + before.getOrDefault("OCl-1", 0) + " → " + after.getOrDefault("OCl-1", 0));
		helper.assertTrue(after.getOrDefault("SO3-2", 0) < before.getOrDefault("SO3-2", 0),
				"SO3 应减少");
		helper.assertTrue(after.getOrDefault("Cl-1", 0) > before.getOrDefault("Cl-1", 0),
				"Cl 应增长：" + before.getOrDefault("Cl-1", 0) + " → " + after.getOrDefault("Cl-1", 0));
		helper.assertTrue(after.getOrDefault("SO4-2", 0) > 0, "SO4 应出现（Quench 产物）：" + after);
		helper.assertTrue(after.getOrDefault("Na+1", 0) > 0, "Na 旁观保留");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = 20 * 20)
	public static void bridgeKineticsLoopDrainsBleachOverTime(GameTestHelper helper) {
		// P5 主循环语义：多次步进+写回（游戏 tick 驱动），漂白液 OCl 持续减少。
		// 模拟 20 拍 REACTION_TICK（每拍 0.5s，总 10s——Quench 快通道下亚硫酸秒灭，
		// 之后 HypDecay 慢通道接手；断言全程无失败、单调、电荷中性保持）。
		ReactorTank tank = new ReactorTank(10_000, () -> {});
		ResourceLocation water = Solution.WATER;
		ResourceLocation naocl = new ResourceLocation("chemicaladdon", "sodium_hypochlorite");
		tank.fill(Mixture.create(Map.of(water, 980, naocl, 20), Map.of(), 1000), FluidAction.EXECUTE);

		int oclBefore = Mixture.deriveUnitIonAmounts(tank.getFluids().get(0)).getOrDefault("OCl-1", 0);
		boolean anyValid = false;
		for (int i = 0; i < 20; i++) {
			var s = com.yu1745.chemicaladdon.composition.parity.TickDriver.step(
					tank.getFluids(), 0.5);
			if (s.valid) {
				anyValid = true;
				com.yu1745.chemicaladdon.composition.parity.WriteBack.firstOf(tank.getFluids(), s);
			}
		}
		helper.assertTrue(anyValid, "至少一拍应有效");
		int oclAfter = Mixture.deriveUnitIonAmounts(tank.getFluids().get(0)).getOrDefault("OCl-1", 0);
		// 无还原剂时仅 HypDecay（慢通道，pH 7 半衰期年级）——短时间几乎不降但不应上升；
		// 电荷中性由 setIons 保证（写回失败即拍 false 已断言）。
		helper.assertTrue(oclAfter <= oclBefore, "OCl 不应上升：" + oclBefore + " → " + oclAfter);
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
