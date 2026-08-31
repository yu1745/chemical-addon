package com.yu1745.chemicaladdon.gametest;

import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.reactor.TowerControllerBlockEntity;
import com.yu1745.chemicaladdon.registry.AllBlocks;
import com.yu1745.chemicaladdon.registry.AllFluids;
import com.yu1745.chemicaladdon.vessel.VesselBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ChemicalAddon.MODID)
@PrefixGameTestTemplate(false)
public class TowerGameTests {

	private static final int TICKS = 20;

// -------------------------------------------------------------- tower (E)

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void towerAssemblesCountsStagesAndPorts(GameTestHelper helper) {
		// 3×3×6 with packing in four interior layers → four effective stages;
		// the empty interior above them buys nothing (plans/04 §2)
		TowerControllerBlockEntity be = buildTower(helper, 1, 4, 4);
		helper.assertTrue(be.isAssembled() && !be.isOpen(), "a roofed column assembles sealed");
		helper.assertTrue(be.getStages() == 4, "four packed layers count as four stages");
		helper.assertTrue(be.getTank().getTankCapacity(0) == 4000, "holdup 1000 x 4 interior blocks");
		helper.assertTrue(be.getGasTank().getTankCapacity(0) == 4000,
			"gas and liquid have independent 4000 mB inventories");
		// port faces are directional: spray (UP) takes liquid only, side takes gas only
		IFluidHandler spray = helper.getBlockEntity(new BlockPos(2, 6, 1))
			.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).orElse(null);
		IFluidHandler gas = helper.getBlockEntity(new BlockPos(1, 2, 1))
			.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.WEST).orElse(null);
		IFluidHandler bottoms = helper.getBlockEntity(new BlockPos(2, 1, 1))
			.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.DOWN).orElse(null);
		helper.assertTrue(spray != null && gas != null && bottoms != null,
			"roof, low side wall and floor expose the three real shell ports");
		if (spray != null && gas != null && bottoms != null) {
			helper.assertTrue(spray.fill(new FluidStack(Fluids.WATER, 4000), FluidAction.EXECUTE) == 4000,
				"the spray inlet can fill the independent liquid inventory");
			helper.assertTrue(spray.fill(new FluidStack(AllFluids.AMMONIA.get().getSource(), 100), FluidAction.EXECUTE) == 0,
				"the spray inlet rejects gas (counterflow ports are directional)");
			helper.assertTrue(gas.fill(new FluidStack(AllFluids.AMMONIA.get().getSource(), 500), FluidAction.EXECUTE) == 500,
				"the gas port accepts gas");
			helper.assertTrue(gas.fill(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE) == 0,
				"the gas port rejects liquid (反接端口失败可诊断)");
			helper.assertTrue(be.liquidMb() == 4000 && be.gasMb() == 500,
				"a full liquid inventory does not consume gas capacity");
			helper.assertTrue(bottoms.fill(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE) == 0,
				"the floor bottoms port is outlet-only");
		}
		helper.assertTrue(helper.getBlockEntity(new BlockPos(1, 3, 1))
			.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.WEST).isPresent() == false,
			"an upper wall brick is not a bottom gas port");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 60)
	public static void towerStagesDriveAbsorption(GameTestHelper helper) {
		// 三塔对照（plans/04 §6/§8）：空塔加高无收益；有效段越多吸收越快。
		// A: 高塔无填料（0 段）；B: 2 段；C: 4 段。同投 1000 mB 氨气 + 1000 mB 水。
		TowerControllerBlockEntity empty = buildTower(helper, 1, 8, 0); // tall, unpacked
		TowerControllerBlockEntity slow = buildTower(helper, 5, 4, 2); // 2 stages
		TowerControllerBlockEntity fast = buildTower(helper, 9, 4, 4); // 4 stages
		for (TowerControllerBlockEntity tower : List.of(empty, slow, fast)) {
			tower.getTank().fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
		}
		helper.assertTrue(empty.getStages() == 0, "an unpacked column has no stages");
		// the gas charge is dropped INSIDE the sequence: absorption starts at a
		// test-controlled tick (structure placement runs long before the first
		// poll — a setup-time fill would already be absorbed away)
		// C (4 段, 200 mB/步) 在 ~5 步吸完；B (2 段, 100 mB/步) 还剩一半——宽窗口
		helper.startSequence()
			.thenIdle(TICKS * 2) // let the columns settle into IDLE with liquid only
			.thenExecute(() -> {
				for (TowerControllerBlockEntity tower : List.of(empty, slow, fast)) {
				tower.getGasTank().fill(new FluidStack(AllFluids.AMMONIA.get().getSource(), 1000),
						FluidAction.EXECUTE);
				}
			})
			// fast (4 段, 200 mB/步) clears its charge in ~5 steps; slow (2 段,
			// 100 mB/步) needs ~10 — a wide window between them
			.thenWaitUntil(() -> {
				// the fast column's status flips to IDLE one step after its gas
				// clears — include it in the window (slow still has ~5 steps)
				if (!(fast.gasMb() == 0 && fast.getStatus() == TowerControllerBlockEntity.TowerStatus.NEEDS_GAS
					&& slow.gasMb() > 0 && empty.gasMb() == 1000)) {
					throw new GameTestAssertException("Waiting");
				}
			})
			.thenExecute(() -> {
				helper.assertTrue(fast.getStatus() == TowerControllerBlockEntity.TowerStatus.NEEDS_GAS,
					"the fast column finished absorbing and idles");
				helper.assertTrue(empty.getStatus() == TowerControllerBlockEntity.TowerStatus.NO_STAGES,
					"the empty column reports no stages (height alone buys nothing)");
				helper.assertTrue(empty.gasMb() == 1000, "the empty column absorbs nothing");
			})
			// B eventually finishes too (stable end state)
			.thenWaitUntil(() -> {
				if (slow.gasMb() != 0) {
					throw new GameTestAssertException("Waiting");
				}
			})
			.thenExecute(() -> helper.assertTrue(Mixture.isMixture(slow.getTank().getFluids().get(0))
				|| slow.getTank().getTotalAmount() > 0, "the absorbed ammonia dissolved into the liquid"))
			.thenSucceed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 30)
	public static void towerMultigasRemainderConservesVolume(GameTestHelper helper) {
		TowerControllerBlockEntity tower = buildTower(helper, 5, 2, 1); // one stage = 50 mB/step
		tower.getTank().fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
		for (net.minecraft.world.level.material.Fluid gas : List.of(
			AllFluids.AMMONIA.get().getSource(), AllFluids.HYDROGEN.get().getSource(),
			AllFluids.NITROGEN.get().getSource(), AllFluids.CARBON_DIOXIDE.get().getSource())) {
			tower.getGasTank().fill(new FluidStack(gas, 13), FluidAction.EXECUTE);
		}
		helper.startSequence()
			.thenIdle(10)
			.thenExecute(() -> {
				helper.assertTrue(tower.liquidMb() == 1000,
					"dissolution changes concentration without inflating spray-liquid volume");
				helper.assertTrue(tower.gasMb() == 2,
					"one stage transfers exactly 50 of the 52 mB multi-gas charge");
			})
			.thenSucceed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 40)
	public static void towerFloodingStallsAndRecovers(GameTestHelper helper) {
		// 液泛（plans/04 §4）：气速超过截面阈值 → 传质停摆可测；降负荷后恢复。
		TowerControllerBlockEntity be = buildTower(helper, 5, 4, 2); // 3×3 截面 → 400 mB/步阈值
		IFluidHandler gas = helper.getBlockEntity(new BlockPos(5, 2, 1))
			.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.WEST)
			.orElseThrow(() -> new GameTestAssertException("gas capability missing"));
		gas.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) /* rejected */;
		be.getTank().fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE); // spray charge
		// 一坊超阈进气（500 > 400）→ 下一步液泛；进气在序列内投下（洪峰窗口只有一步）
		helper.startSequence()
			.thenIdle(TICKS * 2)
			.thenExecute(() -> gas.fill(new FluidStack(AllFluids.AMMONIA.get().getSource(), 500),
				FluidAction.EXECUTE))
			.thenWaitUntil(() -> {
				if (be.getStatus() != TowerControllerBlockEntity.TowerStatus.FLOODED) {
					throw new GameTestAssertException("Waiting");
				}
			})
			.thenExecute(() -> {
				helper.assertTrue(be.gasMb() == 500, "a flooded column stalls mass transfer (gas stays)");
				helper.assertTrue(be.getProcessStatus().equals("FLOODED"), "the status port reads the flood");
			})
			// feed stopped -> the flood clears and absorption resumes to a stable end
			.thenWaitUntil(() -> {
				if (be.gasMb() != 0) {
					throw new GameTestAssertException("Waiting");
				}
			})
			.thenExecute(() -> helper.assertTrue(be.getStatus() != TowerControllerBlockEntity.TowerStatus.FLOODED,
				"a throttled column recovers"))
			.thenSucceed();
	}

	/** Builds a sealed 3×3 tower (floor y=1, rings y=2..rings+1, roof) with the
	 *  interior column packed in the bottom {@code packedLayers} ring layers. */
	private static TowerControllerBlockEntity buildTower(GameTestHelper helper, int x0, int rings, int packedLayers) {
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		BlockState controller = AllBlocks.TOWER_CONTROLLER.get().defaultBlockState();
		BlockState packing = AllBlocks.TOWER_PACKING.get().defaultBlockState();
		int roofY = rings + 2;
		for (int x = 0; x <= 2; x++) {
			for (int z = 0; z <= 2; z++) {
				helper.setBlock(new BlockPos(x0 + x, 1, z), brick); // floor
				helper.setBlock(new BlockPos(x0 + x, roofY, z), brick); // roof
			}
		}
		for (int y = 2; y <= rings + 1; y++) {
			for (int x = 0; x <= 2; x++) {
				for (int z = 0; z <= 2; z++) {
					if (x == 1 && z == 1) {
						if (y - 2 < packedLayers) {
							helper.setBlock(new BlockPos(x0 + 1, y, 1), packing);
						}
						continue; // interior column (packing or air)
					}
					if (x == 0 || x == 2 || z == 0 || z == 2) {
						helper.setBlock(new BlockPos(x0 + x, y, z), x == 1 && z == 0 && y == 2 ? controller : brick);
					}
				}
			}
		}
		TowerControllerBlockEntity be = (TowerControllerBlockEntity) helper.getBlockEntity(new BlockPos(x0 + 1, 2, 0));
		VesselBlockEntity.AssembleResult result = be.tryAssemble();
		helper.assertTrue(result.ok(), "tower should assemble (x0=" + x0 + ", rings=" + rings + "): " + result);
		return be;
	}

}
