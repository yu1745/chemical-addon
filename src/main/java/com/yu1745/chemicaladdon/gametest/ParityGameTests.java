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
