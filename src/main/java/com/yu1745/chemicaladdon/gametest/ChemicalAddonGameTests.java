package com.yu1745.chemicaladdon.gametest;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.reactor.FilterPressBlockEntity;
import com.yu1745.chemicaladdon.reactor.ReactorControllerBlockEntity;
import com.yu1745.chemicaladdon.registry.AllBlocks;
import com.yu1745.chemicaladdon.registry.AllFluids;
import com.yu1745.chemicaladdon.registry.AllItems;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
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
	public static void reactorRejectsBrokenShell(GameTestHelper helper) {
		buildReactor(helper);
		// remove one wall brick -> assembly must fail
		helper.setBlock(new BlockPos(1, 2, 2), Blocks.AIR.defaultBlockState());
		ReactorControllerBlockEntity be = reactor(helper);
		helper.assertFalse(be.isAssembled(), "reactor must not assemble with a missing wall brick");
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
		helper.assertTrue(be.tryAssemble(), "structure should validate");
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
