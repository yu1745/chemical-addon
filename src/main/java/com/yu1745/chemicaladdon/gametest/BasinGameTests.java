package com.yu1745.chemicaladdon.gametest;

import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.hasSpecies;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.waitFor;

import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.composition.Solution;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.reactor.FilterPressBlockEntity;
import com.yu1745.chemicaladdon.reactor.ReactorTank;
import com.yu1745.chemicaladdon.reactor.SettlingBasinBlockEntity;
import com.yu1745.chemicaladdon.registry.AllBlocks;
import com.yu1745.chemicaladdon.registry.AllItems;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
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
public class BasinGameTests {

	private static final int TICKS = 20;

	// ------------------------------------------------------- settling basin (U3)
	// The basin had ZERO test coverage before U3 — and its hand-rolled validation
	// could never assemble at all (it required the controller's own cell to be
	// air, SettlingBasinBlockEntity pre-U3). These tests pin the unified template.

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void basinAssemblesAndProxiesFluid(GameTestHelper helper) {
		SettlingBasinBlockEntity be = buildBasin(helper);
		helper.assertTrue(be.isAssembled(), "pool should assemble");
		helper.assertTrue(be.getTank().getTankCapacity(0) == 1000,
			"pool capacity is 1 bucket per interior block (3×3×1 = 1000)");
		helper.assertTrue(be.isOpen(), "pool is always open-topped");
		// the floor brick proxies FLUID_HANDLER to the pool tank (Create FluidTank
		// pattern; a side-less query — the UP face is deliberately excluded)
		BlockEntity floor = helper.getBlockEntity(new BlockPos(2, 1, 2));
		IFluidHandler handler = floor.getCapability(ForgeCapabilities.FLUID_HANDLER, null)
			.orElse(null);
		helper.assertTrue(handler != null, "floor brick should expose a fluid capability");
		if (handler != null) {
			handler.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
		}
		helper.assertTrue(be.getTank().getTotalAmount() == 1000, "poured fluid lands in the pool tank");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void basinSettlesSlurry(GameTestHelper helper) {
		SettlingBasinBlockEntity be = buildBasin(helper);
		// 施工包 C: gravity settling at the area flux — the slurry's suspended
		// solids migrate into the sludge BED (Sediment domain); the basin no longer
		// emits dry cake (plans/05 §1 — that is the filter press's job, fed by the
		// underflow port)
		FluidStack slurry = Mixture.create(
			Map.of(Solution.WATER, 700),
			Map.of(),
			Map.of(new ResourceLocation(ChemicalAddon.MODID, "sodium_bicarbonate"), 300),
			1000);
		be.getTank().fill(slurry, FluidAction.EXECUTE);
		waitFor(helper.startSequence()
				.thenIdle(TICKS),
			() -> be.suspendedMb() == 0 && be.sedimentMb() == 300)
			.thenExecute(() -> {
				helper.assertTrue(be.getItems().getStackInSlot(0).isEmpty(),
					"the basin emits no dry cake (plans/05 §1)");
				// the liquid stayed put — pore entrainment happens at the underflow
				// draw, not while the bed merely accumulates
				helper.assertTrue(hasSpecies(be.getTank(), "water", 700), "clear water should remain");
			})
			.thenSucceed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void basinScalesWithAreaAndDepth(GameTestHelper helper) {
		// 5×5×2: nine interior blocks → 18000 mB capacity, 1800 mB/step
		// clarification flux, 9000 mB bed capacity (plans/05 §2: 面积决定澄清
		// 能力，深度决定缓冲和底泥容量)
		SettlingBasinBlockEntity be = buildBasin(helper, 5, 2);
		helper.assertTrue(be.getTank().getTankCapacity(0) == 18000, "9 blocks × 2 rings × 1000 mB");
		helper.assertTrue(be.interiorArea() == 9, "interior footprint is 3×3 blocks");
		FluidStack slurry = Mixture.create(
			Map.of(Solution.WATER, 3000),
			Map.of(),
			Map.of(new ResourceLocation(ChemicalAddon.MODID, "sodium_bicarbonate"), 1800),
			4800);
		be.getTank().fill(slurry, FluidAction.EXECUTE);
		waitFor(helper.startSequence()
				.thenIdle(TICKS),
			() -> be.suspendedMb() == 0)
			.thenExecute(() -> {
				// area flux 9 × 200 = 1800 mB/step: one step clarifies the whole load
				helper.assertTrue(be.sedimentMb() == 1800, "the deep bed holds all settled solids");
			})
			.thenSucceed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 40)
	public static void basinSludgeBedStallsAtCapacity(GameTestHelper helper) {
		// 5×5×1: bed capacity 4500 — feed more solids than the bed can hold and
		// settling stalls: nothing more can leave suspension until the underflow
		// runs (plans/05 §3 底泥：积累降低性能)
		SettlingBasinBlockEntity be = buildBasin(helper, 5, 1);
		helper.assertTrue(be.getTank().getTankCapacity(0) == 9000, "9 blocks × 1 ring × 1000 mB");
		FluidStack slurry = Mixture.create(
			Map.of(Solution.WATER, 4000),
			Map.of(),
			Map.of(new ResourceLocation(ChemicalAddon.MODID, "sodium_bicarbonate"), 5000),
			9000);
		be.getTank().fill(slurry, FluidAction.EXECUTE);
		waitFor(helper.startSequence()
				.thenIdle(TICKS),
			() -> be.sedimentMb() == 4500)
			.thenExecute(() -> {
				helper.assertTrue(be.suspendedMb() == 500,
					"a full bed stalls settling — the surplus stays in suspension (got " + be.suspendedMb() + ")");
			})
			.thenSucceed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 40)
	public static void basinOverflowSkimsAndEntrains(GameTestHelper helper) {
		// plans/05 §7 step 2: a budgeted surface skim returns clear liquid; an
		// overdrawn pull punches through the supernatant and entrains suspended
		// solids (夹带) — the world-side signal to throttle the pump. The fixture
		// parks in a STABLE state (bed at capacity stalls further settling), so
		// the poll has no timing window to miss.
		SettlingBasinBlockEntity be = buildBasin(helper, 5, 2);
		FluidStack slurry = Mixture.create(
			Map.of(Solution.WATER, 4500),
			Map.of(),
			Map.of(new ResourceLocation(ChemicalAddon.MODID, "sodium_bicarbonate"), 10000),
			14500);
		be.getTank().fill(slurry, FluidAction.EXECUTE);
		IFluidHandler overflow = be.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH)
			.orElseThrow(() -> new GameTestAssertException("overflow capability missing"));
		// stable: the 9000 mB bed is full, 1000 mB of solids stay suspended
		waitFor(helper.startSequence()
				.thenIdle(TICKS),
			() -> be.suspendedMb() == 1000 && be.sedimentMb() == 9000)
			.thenExecute(() -> {
				// budgeted skim: within the standing supernatant (1500 mB) — clear
				FluidStack clean = overflow.drain(1000, FluidAction.EXECUTE);
				helper.assertTrue(!clean.isEmpty() && Mixture.deriveSuspendedAmounts(clean).isEmpty(),
					"a budgeted skim returns clear liquid (got " + Mixture.deriveSuspendedAmounts(clean) + ")");
				// overdrawn pull: entrainment
				FluidStack dirty = overflow.drain(2000, FluidAction.EXECUTE);
				int entrained = Mixture.deriveSuspendedAmounts(dirty).values().stream().mapToInt(Integer::intValue).sum();
				helper.assertTrue(entrained >= 400,
					"an overdrawn pull entrains suspended solids (got " + entrained + ")");
			})
			.thenSucceed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 60)
	public static void basinOverdrawChurnsBedAndRecovers(GameTestHelper helper) {
		// the churn half of the loop: sustained overdrawn pulls kick the settled
		// bed back into suspension (turbidity the S17 gauge reads — the churn
		// outpaces settling), then throttling lets gravity re-clarify to a
		// stable clear state (plans/05 §7 step 2 降泵速恢复)
		SettlingBasinBlockEntity be = buildBasin(helper, 5, 2);
		FluidStack slurry = Mixture.create(
			Map.of(Solution.WATER, 3500),
			Map.of(),
			Map.of(new ResourceLocation(ChemicalAddon.MODID, "sodium_bicarbonate"), 9000),
			12500);
		be.getTank().fill(slurry, FluidAction.EXECUTE);
		IFluidHandler overflow = be.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH)
			.orElseThrow(() -> new GameTestAssertException("overflow capability missing"));
		// stable settled start: bed full at 9000, nothing suspended
		GameTestSequence seq = waitFor(helper.startSequence()
				.thenIdle(TICKS),
			() -> be.suspendedMb() == 0 && be.sedimentMb() == 9000);
		seq.thenExecute(() -> {
				// first overdrawn pull: punches the 800 mB supernatant, records churn
				overflow.drain(3000, FluidAction.EXECUTE);
				helper.assertTrue(be.getClearCreditMb() == 0, "the overdraw empties the supernatant");
			})
			.thenIdle(SettlingBasinBlockEntity.SETTLE_INTERVAL) // observe the first churn step before it re-settles
			.thenExecute(() -> overflow.drain(3000, FluidAction.EXECUTE)) // keep punching
			// churn (2x overdraw/step) now outpaces settling (flux/step): the
			// suspension rises — fire at the first turbid tick
			.thenWaitUntil(() -> {
				if (be.suspendedMb() <= 0) {
					throw new GameTestAssertException("Waiting");
				}
			})
			.thenExecute(() -> helper.assertTrue(be.sedimentMb() < 9000,
				"the churn kicked the bed back up (bed " + be.sedimentMb() + ")"))
			// throttled: no more draws — gravity re-settles to a stable clear pot
			.thenWaitUntil(() -> {
				if (be.suspendedMb() != 0) {
					throw new GameTestAssertException("Waiting");
				}
			})
			.thenExecute(() -> helper.assertTrue(be.getTank().getTotalAmount() - be.sedimentMb() <= 20,
				"a throttled basin fully re-clarifies — only rounding-scale clear liquor remains (total "
					+ be.getTank().getTotalAmount() + " vs bed " + be.sedimentMb() + ")"))
			.thenSucceed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 60)
	public static void basinUnderflowFeedsFilterPress(GameTestHelper helper) {
		// plans/05 §5 池→过滤机联线: the bottom port draws the bed as thickened
		// sludge (~50% solids, reslurried) and the filter press turns it into cake
		// + filtrate — the low-energy continuous pairing
		SettlingBasinBlockEntity be = buildBasin(helper, 5, 1);
		FluidStack slurry = Mixture.create(
			Map.of(Solution.WATER, 4500),
			Map.of(),
			Map.of(new ResourceLocation(ChemicalAddon.MODID, "sodium_bicarbonate"), 4500),
			9000);
		be.getTank().fill(slurry, FluidAction.EXECUTE);
		// settle fully, then pull the underflow
		GameTestSequence seq = waitFor(helper.startSequence()
				.thenIdle(TICKS),
			() -> be.suspendedMb() == 0 && be.sedimentMb() == 4500);
		seq.thenExecute(() -> {
				IFluidHandler port = be.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.DOWN)
					.orElseThrow(() -> new GameTestAssertException("underflow capability missing"));
				// the press intake takes 4000 mB — draw the underflow in batches
				FluidStack batch1 = port.drain(4000, FluidAction.EXECUTE);
				int solids1 = Mixture.deriveSuspendedAmounts(batch1).values().stream().mapToInt(Integer::intValue).sum();
				helper.assertTrue(batch1.getAmount() == 4000 && solids1 == 2000,
					"the underflow is thickened sludge: 50% solids (got " + solids1 + ")");
				helper.setBlock(new BlockPos(8, 1, 8), AllBlocks.FILTER_PRESS.get().defaultBlockState());
				FilterPressBlockEntity press = (FilterPressBlockEntity) helper.getBlockEntity(new BlockPos(8, 1, 8));
				helper.assertTrue(press.getInput().fill(batch1, FluidAction.EXECUTE) == 4000,
					"the press intake accepts the first batch");
			})
			.thenWaitUntil(() -> {
				FilterPressBlockEntity press = (FilterPressBlockEntity) helper.getBlockEntity(new BlockPos(8, 1, 8));
				if (press == null || press.getInput().getTotalAmount() != 0
					|| press.getItems().getStackInSlot(0).getCount() < 2
					|| !press.getItems().getStackInSlot(0).is(AllItems.SODIUM_BICARBONATE.get())) {
					throw new GameTestAssertException("Waiting");
				}
			})
			.thenExecute(() -> {
				// second batch: the rest of the bed (2500 solids, capped at 2000)
				IFluidHandler port = be.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.DOWN)
					.orElseThrow(() -> new GameTestAssertException("underflow capability missing"));
				FluidStack batch2 = port.drain(4000, FluidAction.EXECUTE);
				int solids2 = Mixture.deriveSuspendedAmounts(batch2).values().stream().mapToInt(Integer::intValue).sum();
				helper.assertTrue(batch2.getAmount() == 4000 && solids2 == 2000,
					"the second batch is thickened too (got " + solids2 + ")");
				FilterPressBlockEntity press = (FilterPressBlockEntity) helper.getBlockEntity(new BlockPos(8, 1, 8));
				helper.assertTrue(press.getInput().fill(batch2, FluidAction.EXECUTE) == 4000,
					"the emptied press accepts the complete second batch");
			})
			.thenWaitUntil(() -> {
				FilterPressBlockEntity press = (FilterPressBlockEntity) helper.getBlockEntity(new BlockPos(8, 1, 8));
				if (press == null || press.getInput().getTotalAmount() != 0
					|| press.getItems().getStackInSlot(0).getCount() < 4) {
					throw new GameTestAssertException("Waiting");
				}
			})
			.thenExecute(() -> {
				FilterPressBlockEntity press = (FilterPressBlockEntity) helper.getBlockEntity(new BlockPos(8, 1, 8));
				helper.assertTrue(press.getItems().getStackInSlot(0).getCount() == 4,
					"4000+ mB of solids press into 4 cake items (got " + press.getItems().getStackInSlot(0).getCount() + ")");
				helper.assertTrue(hasSpecies(press.getOutput(), "water", 2000),
					"the freed filtrate passes through (pore water rides the cake)");
			})
			.thenSucceed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void brokenBasinSpillsContents(GameTestHelper helper) {
		SettlingBasinBlockEntity be = buildBasin(helper);
		be.getTank().fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
		// break a wall-ring brick -> breach below the surface: everything pours out
		BlockPos breach = new BlockPos(1, 2, 1);
		helper.setBlock(breach, Blocks.AIR.defaultBlockState());
		helper.assertFalse(be.isAssembled(), "pool should de-assemble when a wall brick breaks");
		helper.assertTrue(helper.getBlockState(breach).getFluidState().is(Fluids.WATER),
			"water should pour out as a real fluid block at the breach");
		helper.assertTrue(be.getTank().getTotalAmount() == 0, "pool tank should drain fully");
		helper.succeed();
	}
	/** Builds an open w×w×rings pool: brick floor at y=1, wall rings at y=2..rings+1
	 *  with the controller mid-way on the north wall, interior air. Assembles it. */
	private static SettlingBasinBlockEntity buildBasin(GameTestHelper helper, int w, int rings) {
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		BlockState controller = AllBlocks.SETTLING_BASIN.get().defaultBlockState();
		int half = (w - 1) / 2;
		int cx = 1 + half;
		for (int x = 1; x <= w; x++) {
			for (int z = 1; z <= w; z++) {
				helper.setBlock(new BlockPos(x, 1, z), brick); // floor
			}
		}
		for (int y = 2; y <= rings + 1; y++) {
			for (int x = 1; x <= w; x++) {
				for (int z = 1; z <= w; z++) {
					if (x == cx && z == cx) {
						continue; // the interior column stays air up to the rim
					}
					if (x == 1 || x == w || z == 1 || z == w) {
						helper.setBlock(new BlockPos(x, y, z), x == cx && z == 1 && y == 2 ? controller : brick);
					}
				}
			}
		}
		SettlingBasinBlockEntity be = (SettlingBasinBlockEntity) helper.getBlockEntity(new BlockPos(cx, 2, 1));
		helper.assertTrue(be.tryAssemble().ok(), w + "x" + w + "x" + rings + " pool should assemble");
		return be;
	}

	/** 3×3×1 pool, assembled. */
	private static SettlingBasinBlockEntity buildBasin(GameTestHelper helper) {
		return buildBasin(helper, 3, 1);
	}
}
