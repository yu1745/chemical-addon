package com.yu1745.chemicaladdon.gametest;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.reactor.FilterPressBlockEntity;
import com.yu1745.chemicaladdon.reactor.ReactorControllerBlock;
import com.yu1745.chemicaladdon.reactor.ReactorControllerBlockEntity;
import com.yu1745.chemicaladdon.registry.AllBlocks;
import com.yu1745.chemicaladdon.registry.AllFluids;
import com.yu1745.chemicaladdon.registry.AllItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ChemicalAddon.MODID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = ChemicalAddon.MODID, bus = Bus.MOD)
public class ChemicalAddonGameTests {

	private static final int TICKS = 20;

	@SubscribeEvent
	public static void registerTests(RegisterGameTestsEvent event) {
		event.register(ChemicalAddonGameTests.class);
	}

	// ------------------------------------------------------------------ reactor

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void reactorAssembles(GameTestHelper helper) {
		buildReactor(helper);
		ReactorControllerBlockEntity be = reactor(helper);
		helper.assertTrue(be.isAssembled(), "reactor should be assembled after valid structure");
		helper.assertTrue(be.getTank().getTankCapacity(0) >= 16000, "capacity should scale with height");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void reactorAssemblesOpenTopped(GameTestHelper helper) {
		// open-topped variant: top layer left empty, interior visible from above
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, 1, z), brick); // bottom
			}
		}
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				if (x == 2 && z == 2) {
					continue; // interior
				}
				BlockPos p = new BlockPos(x, 2, z);
				helper.setBlock(p, x == 2 && z == 1 ? AllBlocks.REACTOR_CONTROLLER.get().defaultBlockState() : brick);
			}
		}
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR.defaultBlockState());
		ReactorControllerBlockEntity be = (ReactorControllerBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 1));
		helper.assertTrue(be.tryAssemble().ok(), "open-topped structure should validate");
		helper.assertTrue(be.isOpen(), "vessel should be marked open");
		helper.assertTrue(be.getBlockState().getValue(ReactorControllerBlock.OPEN), "controller state should be open");
		// items render inside regardless; just check the buffer still works
		be.getItems().setStackInSlot(0, new ItemStack(AllItems.SULFUR.get()));
		helper.assertTrue(!be.getItems().getStackInSlot(0).isEmpty(), "item buffer should work in open vessel");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void reactorRejectsPartialTop(GameTestHelper helper) {
		// partially sealed top (1 brick on the top layer) must be rejected
		buildReactor(helper);
		// remove the whole top layer except one brick, then re-assemble on a fresh controller
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				if (x == 1 && z == 1) {
					continue; // keep one top brick -> partial
				}
				helper.setBlock(new BlockPos(x, 3, z), Blocks.AIR.defaultBlockState());
			}
		}
		ReactorControllerBlockEntity be = reactor(helper);
		helper.assertTrue(!be.tryAssemble().ok(), "partial top must not assemble");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 30)
	public static void openVesselAbsorbsThrownItemsAndPouredFluids(GameTestHelper helper) {
		// open-topped vessel
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, 1, z), brick);
			}
		}
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				if (x == 2 && z == 2) {
					continue;
				}
				BlockPos p = new BlockPos(x, 2, z);
				helper.setBlock(p, x == 2 && z == 1 ? AllBlocks.REACTOR_CONTROLLER.get().defaultBlockState() : brick);
			}
		}
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR.defaultBlockState());
		ReactorControllerBlockEntity be = (ReactorControllerBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 1));
		helper.assertTrue(be.tryAssemble().ok() && be.isOpen(), "open vessel should assemble");

		BlockPos core = helper.absolutePos(new BlockPos(2, 2, 2)); // interior core (world coords)

		// 1) throw an item entity into the interior -> absorbed into the buffer
		net.minecraft.world.entity.item.ItemEntity thrown = new net.minecraft.world.entity.item.ItemEntity(
			helper.getLevel(), core.getX() + 0.5, core.getY() + 0.5, core.getZ() + 0.5,
			new ItemStack(AllItems.SULFUR.get()));
		helper.getLevel().addFreshEntity(thrown);

		// 2) pour a water source into the interior -> absorbed into the tank
		helper.setBlock(new BlockPos(2, 2, 2), AllFluids.WATER.get().getSource().defaultFluidState().createLegacyBlock());

		helper.startSequence()
			.thenIdle(3)
			.thenExecute(() -> {
				helper.assertTrue(!be.getItems().getStackInSlot(0).isEmpty()
					&& be.getItems().getStackInSlot(0).is(AllItems.SULFUR.get()),
					"thrown item should be absorbed into the vessel buffer");
				helper.assertTrue(hasFluid(be, AllFluids.WATER.get().getSource(), 900),
					"poured water should be absorbed into the tank");
				helper.assertTrue(helper.getBlockState(new BlockPos(2, 2, 2)).isAir(),
					"absorbed fluid block should be consumed (no world fluid left)");
				helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
					new net.minecraft.world.phys.AABB(core).inflate(2), e -> e.getItem().is(AllItems.SULFUR.get()))
					.isEmpty(), "absorbed item entity should be gone");
			})
			.thenSucceed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 40)
	public static void openVesselAbsorbsFluidAtEveryPourSpot(GameTestHelper helper) {
		// open-topped vessel, height 3 (one wall layer): interior core at rel (2,2,2)
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, 1, z), brick);
			}
		}
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				if (x == 2 && z == 2) {
					continue;
				}
				BlockPos p = new BlockPos(x, 2, z);
				helper.setBlock(p, x == 2 && z == 1 ? AllBlocks.REACTOR_CONTROLLER.get().defaultBlockState() : brick);
			}
		}
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR.defaultBlockState());
		ReactorControllerBlockEntity be = (ReactorControllerBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 1));
		helper.assertTrue(be.tryAssemble().ok() && be.isOpen(), "open vessel should assemble");

		// a bucket click can land the source at the core, the open rim, or one
		// block above the rim — all must be absorbed (rel coords; setBlock absorbs).
		// One serial sequence: setBlock + absorb + assert + drain, per spot.
		BlockPos spot1 = new BlockPos(2, 2, 2); // interior core
		BlockPos spot2 = new BlockPos(2, 3, 2); // open rim
		BlockPos spot3 = new BlockPos(2, 4, 2); // one above the rim
		helper.startSequence()
			.thenExecute(() -> helper.setBlock(spot1,
				AllFluids.WATER.get().getSource().defaultFluidState().createLegacyBlock()))
			.thenIdle(3)
			.thenExecute(() -> assertAbsorbed(helper, be, spot1))
			.thenExecute(() -> be.getTank().drain(Integer.MAX_VALUE, FluidAction.EXECUTE))
			.thenExecute(() -> helper.setBlock(spot2,
				AllFluids.WATER.get().getSource().defaultFluidState().createLegacyBlock()))
			.thenIdle(3)
			.thenExecute(() -> assertAbsorbed(helper, be, spot2))
			.thenExecute(() -> be.getTank().drain(Integer.MAX_VALUE, FluidAction.EXECUTE))
			.thenExecute(() -> helper.setBlock(spot3,
				AllFluids.WATER.get().getSource().defaultFluidState().createLegacyBlock()))
			.thenIdle(3)
			.thenExecute(() -> assertAbsorbed(helper, be, spot3))
			.thenSucceed();
	}

	private static void assertAbsorbed(GameTestHelper helper, ReactorControllerBlockEntity be, BlockPos spot) {
		helper.assertTrue(hasFluid(be, AllFluids.WATER.get().getSource(), 900),
			"water poured at " + spot + " should be absorbed into the tank");
		helper.assertTrue(helper.getBlockState(spot).isAir(),
			"fluid block at " + spot + " should be consumed");
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void brokenVesselSpillsContents(GameTestHelper helper) {
		buildReactor(helper);
		ReactorControllerBlockEntity be = reactor(helper);
		be.getTank().fill(new FluidStack(AllFluids.WATER.get().getSource(), 2000), FluidAction.EXECUTE);
		be.getItems().setStackInSlot(0, new ItemStack(AllItems.SULFUR.get()));
		// break a wall brick -> breach: water becomes a real fluid block there, sulfur drops
		BlockPos breach = new BlockPos(1, 2, 1);
		helper.setBlock(breach, Blocks.AIR.defaultBlockState());
		helper.assertTrue(helper.getBlockState(breach).getFluidState().is(AllFluids.WATER.get().getSource()),
			"water should pour out as a real fluid block at the breach");
		helper.assertTrue(be.getTank().getTotalAmount() == 0, "tank should be empty after spilling");
		helper.assertTrue(be.getItems().getStackInSlot(0).isEmpty(), "item buffer should be empty after spilling");
		// item entities land in the pending list on the same tick; wait a moment
		helper.startSequence()
			.thenIdle(5)
			.thenExecute(() -> {
				// NOTE: AABB must use world coords (helper.absolutePos), not structure-relative
				var entities = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
					new net.minecraft.world.phys.AABB(helper.absolutePos(breach)).inflate(8));
				helper.assertTrue(entities.stream().anyMatch(e -> e.getItem().is(AllItems.SULFUR.get())),
					"sulfur should drop as an item entity");
			})
			.thenSucceed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void reactorAutoReformsAfterRepair(GameTestHelper helper) {
		buildReactor(helper);
		ReactorControllerBlockEntity be = reactor(helper);
		// break one wall brick -> structure must de-assemble
		BlockPos brickPos = new BlockPos(1, 2, 1);
		helper.setBlock(brickPos, Blocks.AIR.defaultBlockState());
		helper.assertFalse(be.isAssembled(), "structure should de-assemble when a brick is broken");
		// replace the brick -> the vessel must re-form automatically (onPlace)
		helper.setBlock(brickPos, AllBlocks.CHEMICAL_BRICK.get().defaultBlockState());
		helper.assertTrue(be.isAssembled(), "structure should re-form automatically after repair");
		helper.assertTrue(be.getInward() != null, "inward should be restored for item rendering");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void reactorRejectsBrokenShell(GameTestHelper helper) {
		buildReactor(helper);
		// remove one wall brick -> assembly must fail
		helper.setBlock(new BlockPos(1, 2, 2), Blocks.AIR.defaultBlockState());
		ReactorControllerBlockEntity be = reactor(helper);
		helper.assertFalse(be.isAssembled(), "reactor must not assemble with a missing wall brick");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void capacitySurvivesSerialization(GameTestHelper helper) {
		buildReactor(helper);
		ReactorControllerBlockEntity be = reactor(helper);
		int capacity = be.getTank().getTankCapacity(0);
		helper.assertTrue(capacity >= 16000, "assembled tank capacity should be height-scaled");
		// save -> fresh instance -> load: capacity must survive the round trip
		net.minecraft.nbt.CompoundTag tag = be.saveWithFullMetadata();
		ReactorControllerBlockEntity copy = new ReactorControllerBlockEntity(be.getBlockPos(),
			helper.getLevel().getBlockState(be.getBlockPos()));
		copy.load(tag);
		helper.assertTrue(copy.getTank().getTankCapacity(0) == capacity,
			"capacity must survive save/load (was " + capacity + ", now " + copy.getTank().getTankCapacity(0) + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void reactorReportsDiagnostics(GameTestHelper helper) {
		buildReactor(helper);
		ReactorControllerBlockEntity be = reactor(helper);
		// empty vessel: no ingredients anywhere -> NO_RECIPE (wait one reaction interval)
		helper.startSequence()
			.thenIdle(TICKS)
			.thenExecute(() -> helper.assertTrue(
				be.getStatus() == ReactorControllerBlockEntity.ReactorStatus.NO_RECIPE,
				"empty assembled vessel should report NO_RECIPE (got " + be.getStatus() + ")"))
			.thenExecute(() -> {
				// sulfur + oxygen but no heat -> TEMPERATURE (sulfur_burning requires HEATED)
				be.getItems().setStackInSlot(0, new ItemStack(AllItems.SULFUR.get()));
				be.getTank().fill(new FluidStack(AllFluids.OXYGEN.get().getSource(), 1000), FluidAction.EXECUTE);
			})
			.thenIdle(TICKS)
			.thenExecute(() -> helper.assertTrue(
				be.getStatus() == ReactorControllerBlockEntity.ReactorStatus.TEMPERATURE,
				"ingredients ready but unheated should report TEMPERATURE (got " + be.getStatus() + ")"))
			.thenSucceed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void reactorFluidCapabilityExposed(GameTestHelper helper) {
		buildReactor(helper);
		ReactorControllerBlockEntity be = reactor(helper);
		LazyOptional<IFluidHandler> cap = be.getCapability(ForgeCapabilities.FLUID_HANDLER);
		helper.assertTrue(cap.isPresent(), "FLUID_HANDLER capability must be exposed");
		helper.assertTrue(be.getTank().getTankCapacity(0) > 0, "tank capacity must be > 0");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void brickProxiesCapabilityToController(GameTestHelper helper) {
		buildReactor(helper);
		// a wall brick (not the controller): its FLUID_HANDLER must proxy to the controller
		BlockPos brickPos = new BlockPos(1, 2, 1);
		BlockEntity brickBe = helper.getBlockEntity(brickPos);
		helper.assertTrue(brickBe != null, "brick should have a BE");
		LazyOptional<IFluidHandler> cap = brickBe.getCapability(ForgeCapabilities.FLUID_HANDLER);
		helper.assertTrue(cap.isPresent(), "brick must expose FLUID_HANDLER via proxy");
		IFluidHandler handler = cap.orElse(null);
		int filled = handler.fill(new FluidStack(AllFluids.WATER.get().getSource(), 1000), FluidAction.EXECUTE);
		helper.assertTrue(filled == 1000, "filling through the brick must reach the controller tank");
		ReactorControllerBlockEntity controller = reactor(helper);
		helper.assertTrue(hasFluid(controller, AllFluids.WATER.get().getSource(), 900),
			"controller tank should hold the water poured via the brick");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 40)
	public static void reactorBurnsSulfur(GameTestHelper helper) {
		buildReactor(helper);
		ReactorControllerBlockEntity be = reactor(helper);
		// KINDLED blaze burner below the vessel's bottom layer; mark it creative
		// so its BE never cools it (simulates a permanently fuelled burner)
		BlockState burner = com.simibubi.create.AllBlocks.BLAZE_BURNER.get().defaultBlockState()
			.setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.KINDLED);
		BlockPos burnerPos = new BlockPos(2, 0, 1);
		helper.setBlock(burnerPos, burner);
		if (helper.getBlockEntity(burnerPos) instanceof BlazeBurnerBlockEntity burnerBe) {
			burnerBe.isCreative = true;
		}
		be.getItems().setStackInSlot(0, new ItemStack(AllItems.SULFUR.get()));
		be.getTank().fill(new FluidStack(AllFluids.OXYGEN.get().getSource(), 1000), FluidAction.EXECUTE);
		helper.startSequence()
			.thenIdle(TICKS * 25)
			.thenExecute(() -> {
				helper.assertTrue(be.getTemperature() >= 400, "temperature should rise with a KINDLED burner below");
				helper.assertTrue(hasFluid(be, AllFluids.SULFUR_DIOXIDE.get().getSource(), 900), "SO2 should be produced");
			})
			.thenSucceed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 40)
	public static void reactorAbsorbsSulfurDioxide(GameTestHelper helper) {
		buildReactor(helper);
		ReactorControllerBlockEntity be = reactor(helper);
		be.getTank().fill(new FluidStack(AllFluids.SULFUR_DIOXIDE.get().getSource(), 1000), FluidAction.EXECUTE);
		be.getTank().fill(new FluidStack(AllFluids.WATER.get().getSource(), 1000), FluidAction.EXECUTE);
		helper.startSequence()
			.thenIdle(TICKS * 25)
			.thenExecute(() -> helper.assertTrue(
				hasFluid(be, AllFluids.DILUTE_SULFURIC_ACID.get().getSource(), 1900), "dilute sulfuric acid should be produced"))
			.thenSucceed();
	}

	// ------------------------------------------------------------------ filter press

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 30)
	public static void filterPressFiltersSlurry(GameTestHelper helper) {
		helper.setBlock(new BlockPos(2, 1, 2), AllBlocks.FILTER_PRESS.get().defaultBlockState());
		FilterPressBlockEntity be = (FilterPressBlockEntity) helper.getBlockEntity(new BlockPos(2, 1, 2));
		be.getInput().fill(new FluidStack(AllFluids.SODIUM_BICARBONATE_SLURRY.get().getSource(), 1000), FluidAction.EXECUTE);
		helper.startSequence()
			.thenIdle(TICKS * 15)
			.thenExecute(() -> {
				helper.assertTrue(!be.getItems().getStackInSlot(0).isEmpty()
					&& be.getItems().getStackInSlot(0).is(AllItems.SODIUM_BICARBONATE.get()),
					"cake should be produced");
				helper.assertTrue(hasFluid(be.getOutput(), AllFluids.WATER.get().getSource(), 400), "filtrate should be produced");
			})
			.thenSucceed();
	}

	// ------------------------------------------------------------------ helpers

	private static void buildReactor(GameTestHelper helper) {
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		BlockState controller = AllBlocks.REACTOR_CONTROLLER.get().defaultBlockState();
		// 3x3x3 shell at x=1..3, y=1..3, z=1..3; controller on north wall middle (2,2,1)
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, 1, z), brick);
				helper.setBlock(new BlockPos(x, 3, z), brick);
			}
		}
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				if (x == 2 && z == 2) {
					continue; // interior
				}
				BlockPos p = new BlockPos(x, 2, z);
				helper.setBlock(p, x == 2 && z == 1 ? controller : brick);
			}
		}
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR.defaultBlockState());
		ReactorControllerBlockEntity be = (ReactorControllerBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 1));
		helper.assertTrue(be.tryAssemble().ok(), "structure should validate");
	}

	private static ReactorControllerBlockEntity reactor(GameTestHelper helper) {
		return (ReactorControllerBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 1));
	}

	private static boolean hasFluid(ReactorControllerBlockEntity be, net.minecraft.world.level.material.Fluid fluid, int minAmount) {
		for (FluidStack stack : be.getTank().getFluids()) {
			if (stack.getFluid() == fluid && stack.getAmount() >= minAmount) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasFluid(com.yu1745.chemicaladdon.reactor.ReactorTank tank,
		net.minecraft.world.level.material.Fluid fluid, int minAmount) {
		for (FluidStack stack : tank.getFluids()) {
			if (stack.getFluid() == fluid && stack.getAmount() >= minAmount) {
				return true;
			}
		}
		return false;
	}
}
