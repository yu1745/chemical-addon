package com.yu1745.chemicaladdon.gametest;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import io.netty.buffer.Unpooled;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.composition.Chemistry;
import com.yu1745.chemicaladdon.composition.Solution;
import com.yu1745.chemicaladdon.composition.Species;
import com.yu1745.chemicaladdon.composition.SpeciesManager;
import com.yu1745.chemicaladdon.fluid.FluidColors;
import com.yu1745.chemicaladdon.fluid.IonColors;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.fluid.SolidColors;
import com.yu1745.chemicaladdon.fluid.Temperature;
import com.yu1745.chemicaladdon.item.MixedResidueItem;
import com.yu1745.chemicaladdon.item.TestPaperItem;
import com.yu1745.chemicaladdon.reactor.AbstractBaumeGaugeBlockEntity;
import com.yu1745.chemicaladdon.reactor.BaumeGaugeBlockEntity;
import com.yu1745.chemicaladdon.reactor.CatalystTrayBlock;
import com.yu1745.chemicaladdon.reactor.CatalystTrayBlockEntity;
import com.yu1745.chemicaladdon.reactor.CatalystUsage;
import com.yu1745.chemicaladdon.reactor.ChemicalBrickBlock;
import com.yu1745.chemicaladdon.reactor.ChemicalBrickBlockEntity;
import com.yu1745.chemicaladdon.reactor.CrystallizerControllerBlock;
import com.yu1745.chemicaladdon.reactor.CrystallizerControllerBlockEntity;
import com.yu1745.chemicaladdon.reactor.MeteringInletBlockEntity;
import com.yu1745.chemicaladdon.reactor.DecantHoseBlockEntity;
import com.yu1745.chemicaladdon.reactor.FilterPressBlockEntity;
import com.yu1745.chemicaladdon.reactor.GasDistributorBlock;
import com.yu1745.chemicaladdon.reactor.GasDistributorBlockEntity;
import com.yu1745.chemicaladdon.reactor.PhGaugeBlockEntity;
import com.yu1745.chemicaladdon.reactor.PressureGaugeBlockEntity;
import com.yu1745.chemicaladdon.reactor.PressureGaugePanelBlockEntity;
import com.yu1745.chemicaladdon.reactor.ReactorControllerBlock;
import com.yu1745.chemicaladdon.reactor.ReactorControllerBlockEntity;
import com.yu1745.chemicaladdon.reactor.ReactorTank;
import com.yu1745.chemicaladdon.reactor.RulesEngine;
import com.yu1745.chemicaladdon.reactor.SpillLogic;
import com.yu1745.chemicaladdon.reactor.StirShaftMath;
import com.yu1745.chemicaladdon.reactor.StirringHeadBlockEntity;
import com.yu1745.chemicaladdon.reactor.StatusPortBlockEntity;
import com.yu1745.chemicaladdon.reactor.ThermometerBlockEntity;
import com.yu1745.chemicaladdon.reactor.ThermometerPanelBlockEntity;
import com.yu1745.chemicaladdon.reactor.LiquidLevelGaugeBlockEntity;
import com.yu1745.chemicaladdon.reactor.LiquidLevelGaugePanelBlockEntity;
import com.yu1745.chemicaladdon.reactor.TurbidityGaugeBlockEntity;
import com.yu1745.chemicaladdon.recipe.AllRecipeTypes;
import com.yu1745.chemicaladdon.recipe.ChemicalReactionRecipe;
import com.yu1745.chemicaladdon.registry.AllBlocks;
import com.yu1745.chemicaladdon.registry.AllContainers;
import com.yu1745.chemicaladdon.registry.AllFluids;
import com.yu1745.chemicaladdon.registry.AllItems;
import com.yu1745.chemicaladdon.vessel.VesselBlockEntity;
import com.yu1745.chemicaladdon.vessel.StructureAccess;
import com.yu1745.chemicaladdon.vessel.ProcessCapability;
import com.yu1745.chemicaladdon.vessel.StructureCapabilities;
import com.yu1745.chemicaladdon.vessel.LiquidProcessAccess;
import com.yu1745.chemicaladdon.vessel.ProcessReadings;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;



import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.buildReactor;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.buildReactor3x3x5HighController;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.buildReactor5x5x5;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.buildReactor5x5x5WithGasAt;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.hasFluid;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.hasIon;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.reactor;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.waitFor;

@GameTestHolder(ChemicalAddon.MODID)
@PrefixGameTestTemplate(false)
public class ReactorStructureGameTests {
	private static final int TICKS = 20;


	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void immiscibleLiquidsStaySeparate(GameTestHelper helper) {
		// D18: water (aqueous) and thermal_oil (nonpolar) are immiscible — they must
		// NOT collapse into one mixture, but stay as two phases, denser (water) first.
		ReactorTank tank = new ReactorTank(10000, () -> {});
		tank.fill(new FluidStack(Fluids.WATER, 400), FluidAction.EXECUTE);
		tank.fill(new FluidStack(AllFluids.THERMAL_OIL.get().getSource(), 600), FluidAction.EXECUTE);
		helper.assertTrue(tank.getFluids().size() == 2, "two immiscible fluids should coexist");

		tank.collapseIfNeeded();
		helper.assertTrue(tank.getFluids().size() == 2,
			"immiscible liquids must NOT merge into one mixture (got " + tank.getFluids().size() + ")");
		helper.assertTrue(tank.getFluids().get(0).getFluid() == Fluids.WATER,
			"water (denser) should settle as the first phase");
		helper.assertTrue(tank.getFluids().get(1).getFluid() == AllFluids.THERMAL_OIL.get().getSource(),
			"thermal oil should be the second (lighter) phase");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void drainPullsDenserPhaseFirst(GameTestHelper helper) {
		// D18: a generic drain (bottom port) takes the densest phase first — water
		// (1000) before thermal_oil (900); gases (negative density) would come last.
		ReactorTank tank = new ReactorTank(10000, () -> {});
		tank.fill(new FluidStack(AllFluids.THERMAL_OIL.get().getSource(), 600), FluidAction.EXECUTE);
		tank.fill(new FluidStack(Fluids.WATER, 400), FluidAction.EXECUTE);
		tank.collapseIfNeeded();

		FluidStack first = tank.drain(100, FluidAction.EXECUTE);
		helper.assertTrue(first.getFluid() == Fluids.WATER,
			"drain must pull the denser water first");
		helper.assertTrue(tank.getFluids().size() == 2, "the oil should remain after draining water");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void gasStaysSeparateFromLiquid(GameTestHelper helper) {
		// D18: a gas (lighter-than-air) never merges with the aqueous liquid — it
		// stays a separate phase, so the renderer's "gas hangs from the top" is live.
		ReactorTank tank = new ReactorTank(10000, () -> {});
		tank.fill(new FluidStack(Fluids.WATER, 500), FluidAction.EXECUTE);
		tank.fill(new FluidStack(AllFluids.SULFUR_DIOXIDE.get().getSource(), 500), FluidAction.EXECUTE);

		tank.collapseIfNeeded();
		helper.assertTrue(tank.getFluids().size() == 2,
			"gas and liquid must stay as two phases (got " + tank.getFluids().size() + ")");
		boolean hasWater = false, hasGas = false;
		for (FluidStack s : tank.getFluids()) {
			helper.assertTrue(!Mixture.isMixture(s), "neither phase should be a mixture");
			if (s.getFluid() == Fluids.WATER) hasWater = true;
			if (s.getFluid() == AllFluids.SULFUR_DIOXIDE.get().getSource()) hasGas = true;
		}
		helper.assertTrue(hasWater && hasGas, "both the water and the gas should remain");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void miscibleAqueousMerge(GameTestHelper helper) {
		// D18: same-group (aqueous) entries still merge into one mixture — pouring
		// water into an aqueous mixture dilutes it; it does not phase-separate.
		ReactorTank tank = new ReactorTank(10000, () -> {});
		ResourceLocation water = Solution.WATER;
		FluidStack acid = Mixture.create(Map.of(water, 600), Map.of("H+1", 400, "SO4-2", 200), 1200);
		tank.fill(acid, FluidAction.EXECUTE);
		tank.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);

		tank.collapseIfNeeded();
		helper.assertTrue(tank.getFluids().size() == 1,
			"aqueous entries should merge into one (got " + tank.getFluids().size() + ")");
		FluidStack merged = tank.getFluids().get(0);
		helper.assertTrue(Mixture.isMixture(merged), "the merged stack should be a mixture");
		helper.assertTrue(Mixture.deriveIonAmounts(merged).getOrDefault("H+1", 0) == 400,
			"the acid ions should survive the merge");
		helper.assertTrue(merged.getAmount() == 2200,
			"the total should be 1200 + 1000 = 2200 (got " + merged.getAmount() + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void drainLightestPullsLightPhaseFirst(GameTestHelper helper) {
		// D18.5: drainLightest is the reverse of drain(int) — it takes the lightest
		// phase (oil) before the heavier water (decantation).
		ReactorTank tank = new ReactorTank(10000, () -> {});
		tank.fill(new FluidStack(Fluids.WATER, 400), FluidAction.EXECUTE);
		tank.fill(new FluidStack(AllFluids.THERMAL_OIL.get().getSource(), 600), FluidAction.EXECUTE);
		tank.collapseIfNeeded();

		FluidStack light = tank.drainLightest(100, FluidAction.EXECUTE);
		helper.assertTrue(light.getFluid() == AllFluids.THERMAL_OIL.get().getSource(),
			"drainLightest must pull the lighter oil first");
		helper.assertTrue(tank.getFluids().size() == 2, "the water should remain after draining oil");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void decantPortDrainsHeaviestThenStops(GameTestHelper helper) {
		// 分液口: a wall block whose FLUID_HANDLER only exposes the densest (bottom)
		// phase. It latches onto that phase and runs dry when it is exhausted — the
		// lighter phase above never reaches the spout (interface float valve).
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		BlockState controller = AllBlocks.REACTOR_CONTROLLER.get().defaultBlockState();
		BlockState port = AllBlocks.DECANT_PORT.get().defaultBlockState();
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, 1, z), brick);
				helper.setBlock(new BlockPos(x, 3, z), brick);
			}
		}
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				if (x == 2 && z == 2) continue;
				BlockPos p = new BlockPos(x, 2, z);
				BlockState s = (x == 2 && z == 1) ? controller : (x == 1 && z == 1 ? port : brick);
				helper.setBlock(p, s);
			}
		}
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR.defaultBlockState());
		ReactorControllerBlockEntity be = (ReactorControllerBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 1));
		helper.assertTrue(be.tryAssemble().ok(), "structure with a decant port should validate");

		be.getTank().fill(new FluidStack(Fluids.WATER, 400), FluidAction.EXECUTE);
		be.getTank().fill(new FluidStack(AllFluids.THERMAL_OIL.get().getSource(), 600), FluidAction.EXECUTE);
		be.getTank().collapseIfNeeded();

		BlockEntity portBe = helper.getBlockEntity(new BlockPos(1, 2, 1));
		helper.assertTrue(portBe != null, "decant port should have a BE");
		IFluidHandler handler = portBe.getCapability(ForgeCapabilities.FLUID_HANDLER).orElse(null);
		helper.assertTrue(handler != null, "decant port must expose FLUID_HANDLER");

		// drain the whole bottom (water) phase through the port
		FluidStack first = handler.drain(400, FluidAction.EXECUTE);
		helper.assertTrue(first.getFluid() == Fluids.WATER, "the port should drain the denser water first");

		// only oil remains; the port is latched onto water and must run dry (not drain oil)
		FluidStack second = handler.drain(100, FluidAction.EXECUTE);
		helper.assertTrue(second.isEmpty(),
			"after the water is drained the port must stop, not drain the oil");
		helper.assertTrue(be.getTank().getTotalAmount() == 600,
			"only the 600 mB oil should remain (got " + be.getTank().getTotalAmount() + ")");
		helper.assertTrue(hasFluid(be, AllFluids.THERMAL_OIL.get().getSource(), 600),
			"the oil must stay in the vessel");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void decantHoseDrainsLightestThenStops(GameTestHelper helper) {
		// 分液软管: placed above an open-topped vessel, its FLUID_HANDLER drains the
		// lightest (top) phase and, in the default "only top" mode, latches onto it and
		// runs dry once it is gone — the denser phase below is left untouched.
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		BlockState controller = AllBlocks.REACTOR_CONTROLLER.get().defaultBlockState();
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, 1, z), brick); // floor
			}
		}
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				if (x == 2 && z == 2) continue;
				BlockPos p = new BlockPos(x, 2, z);
				helper.setBlock(p, x == 2 && z == 1 ? controller : brick);
			}
		}
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR.defaultBlockState());
		ReactorControllerBlockEntity be = (ReactorControllerBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 1));
		helper.assertTrue(be.tryAssemble().ok() && be.isOpen(), "open reactor should assemble");

		be.getTank().fill(new FluidStack(Fluids.WATER, 400), FluidAction.EXECUTE);
		be.getTank().fill(new FluidStack(AllFluids.THERMAL_OIL.get().getSource(), 600), FluidAction.EXECUTE);
		be.getTank().collapseIfNeeded();

		helper.setBlock(new BlockPos(2, 3, 2), AllBlocks.DECANT_HOSE.get().defaultBlockState());
		BlockEntity hoseBe = helper.getBlockEntity(new BlockPos(2, 3, 2));
		helper.assertTrue(hoseBe != null, "decant hose should have a BE");
		IFluidHandler handler = hoseBe.getCapability(ForgeCapabilities.FLUID_HANDLER).orElse(null);
		helper.assertTrue(handler != null, "decant hose must expose FLUID_HANDLER");

		FluidStack light = handler.drain(100, FluidAction.EXECUTE);
		helper.assertTrue(light.getFluid() == AllFluids.THERMAL_OIL.get().getSource(),
			"the hose should drain the lighter oil first");

		FluidStack rest = handler.drain(500, FluidAction.EXECUTE);
		helper.assertTrue(rest.getFluid() == AllFluids.THERMAL_OIL.get().getSource() && rest.getAmount() == 500,
			"the hose should drain the remaining oil");

		FluidStack after = handler.drain(100, FluidAction.EXECUTE);
		helper.assertTrue(after.isEmpty(), "after the oil is drained the hose must stop, not drain water");
		helper.assertTrue(hasFluid(be, Fluids.WATER, 400), "the water must stay in the vessel");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void decantHoseCanFillReactor(GameTestHelper helper) {
		// 分液软管双向: a pump can also push fluid back into the vessel through the hose
		// (reverse fill) — fill must delegate to the reactor tank, not reject.
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		BlockState controller = AllBlocks.REACTOR_CONTROLLER.get().defaultBlockState();
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, 1, z), brick); // floor
			}
		}
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				if (x == 2 && z == 2) continue;
				BlockPos p = new BlockPos(x, 2, z);
				helper.setBlock(p, x == 2 && z == 1 ? controller : brick);
			}
		}
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR.defaultBlockState());
		ReactorControllerBlockEntity be = (ReactorControllerBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 1));
		helper.assertTrue(be.tryAssemble().ok() && be.isOpen(), "open reactor should assemble");

		helper.setBlock(new BlockPos(2, 3, 2), AllBlocks.DECANT_HOSE.get().defaultBlockState());
		BlockEntity hoseBe = helper.getBlockEntity(new BlockPos(2, 3, 2));
		helper.assertTrue(hoseBe != null, "decant hose should have a BE");
		IFluidHandler handler = hoseBe.getCapability(ForgeCapabilities.FLUID_HANDLER).orElse(null);
		helper.assertTrue(handler != null, "decant hose must expose FLUID_HANDLER");

		// reverse fill: push water back into the vessel through the hose
		int filled = handler.fill(new FluidStack(Fluids.WATER, 500), FluidAction.EXECUTE);
		helper.assertTrue(filled == 500, "the hose should fill the vessel (got " + filled + ")");
		helper.assertTrue(hasFluid(be, Fluids.WATER, 500), "the water must land in the vessel tank");

		// it must still drain the lightest phase (oil floats above water)
		be.getTank().fill(new FluidStack(AllFluids.THERMAL_OIL.get().getSource(), 500), FluidAction.EXECUTE);
		be.getTank().collapseIfNeeded();
		FluidStack drained = handler.drain(100, FluidAction.EXECUTE);
		helper.assertTrue(drained.getFluid() == AllFluids.THERMAL_OIL.get().getSource(),
			"the hose must still drain the lighter oil first after a reverse fill");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void thermometerPanelReadsReactorTemperature(GameTestHelper helper) {
		// S02 thermometer (薄板): mounted on a SHELL BRICK it reads the vessel
		// temperature through the brick's master pointer, and trips the redstone alarm
		// once the temperature reaches the threshold (default 400°C). Comparator reads
		// a temperature-proportional signal.
		buildReactor(helper);
		ReactorControllerBlockEntity reactor = reactor(helper);

		// fill + fix the temperature so the reading is deterministic
		reactor.getTank().fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
		reactor.getTank().collapseIfNeeded();
		Temperature.set(reactor.getTank().getFluids().get(0), 500);

		// mounted on the north-west wall brick at (1,2,1) — behind it is the brick,
		// not the controller, so the read goes panel -> brick.getValidMaster -> reactor
		BlockState thermometer = AllBlocks.THERMOMETER_PANEL.get().defaultBlockState()
			.setValue(BlockStateProperties.FACING, Direction.NORTH);
		helper.setBlock(new BlockPos(1, 2, 0), thermometer);
		ThermometerPanelBlockEntity be = (ThermometerPanelBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 0));
		helper.assertTrue(be != null, "thermometer panel should have a block entity");
		be.tick();
		helper.assertTrue(be.getTemperature() == 500,
			"panel must read the vessel through the shell brick (got " + be.getTemperature() + ")");
		helper.assertTrue(be.isAlarm(), "500°C must trip the default 400°C alarm threshold");

		// strong redstone on alarm; comparator signal proportional to temperature.
		// (getSignal/getAnalogOutputSignal take the ABSOLUTE pos; the helper's
		// getBlockState is relative-aware but getLevel().getBlockState is not.)
		BlockPos thermoPos = new BlockPos(1, 2, 0);
		BlockPos abs = helper.absolutePos(thermoPos);
		BlockState thermoState = helper.getBlockState(thermoPos);
		helper.assertTrue(thermoState.getSignal(helper.getLevel(), abs, Direction.NORTH) == 15,
			"the alarm must emit a strong redstone signal");
		helper.assertTrue(thermoState.getAnalogOutputSignal(helper.getLevel(), abs) == 15,
			"comparator signal should saturate at 15 once the reading reaches the 400°C threshold (dynamic 0°C..threshold scale)");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void wallThermometerReadsOwnReactor(GameTestHelper helper) {
		// S02 thermometer (方块): a shell block filling a wall position — accepted by
		// the structure (vessel_walls tag), bound to the controller, proxies fluid
		// like a brick, and reads its own vessel's temperature.
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		BlockState controller = AllBlocks.REACTOR_CONTROLLER.get().defaultBlockState();
		BlockState thermo = AllBlocks.THERMOMETER.get().defaultBlockState();
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, 1, z), brick); // floor
				helper.setBlock(new BlockPos(x, 3, z), brick); // roof
			}
		}
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				if (x == 2 && z == 2) {
					continue; // interior
				}
				BlockPos p = new BlockPos(x, 2, z);
				BlockState st = (x == 2 && z == 1) ? controller : (x == 3 && z == 1) ? thermo : brick;
				helper.setBlock(p, st);
			}
		}
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR.defaultBlockState());
		ReactorControllerBlockEntity reactor = (ReactorControllerBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 1));
		helper.assertTrue(reactor.tryAssemble().ok(), "reactor with a thermometer wall should assemble");

		reactor.getTank().fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
		reactor.getTank().collapseIfNeeded();
		Temperature.set(reactor.getTank().getFluids().get(0), 500);

		ThermometerBlockEntity be = (ThermometerBlockEntity) helper.getBlockEntity(new BlockPos(3, 2, 1));
		helper.assertTrue(be != null, "wall thermometer should have a block entity");
		be.tick();
		helper.assertTrue(be.getMasterPos() != null, "wall thermometer must be bound to the controller");
		helper.assertTrue(be.getTemperature() == 500,
			"wall thermometer must read its own reactor (got " + be.getTemperature() + ")");
		helper.assertTrue(be.isAlarm(), "500°C must trip the alarm");
		helper.assertTrue(be.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.EAST).isPresent(),
			"wall thermometer must proxy FLUID_HANDLER to the reactor");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void wallThermometerAboveControllerBinds(GameTestHelper helper) {
		// Regression: a shell block sitting directly ABOVE (or below) the controller
		// shares the controller's s/d column. bindBricks used to skip that whole
		// column, so a thermometer in the ceiling over the controller validated fine
		// but never got bound. Only the controller cell itself must be skipped.
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		BlockState controller = AllBlocks.REACTOR_CONTROLLER.get().defaultBlockState();
		BlockState thermo = AllBlocks.THERMOMETER.get().defaultBlockState();
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, 1, z), brick); // floor
				helper.setBlock(new BlockPos(x, 3, z), (x == 2 && z == 1) ? thermo : brick); // ceiling, gauge above controller
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
		ReactorControllerBlockEntity reactor = (ReactorControllerBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 1));
		helper.assertTrue(reactor.tryAssemble().ok(),
			"sealed reactor with a ceiling thermometer above the controller should assemble");
		ThermometerBlockEntity be = (ThermometerBlockEntity) helper.getBlockEntity(new BlockPos(2, 3, 1));
		helper.assertTrue(be != null, "ceiling thermometer should have a block entity");
		helper.assertTrue(be.getMasterPos() != null,
			"ceiling thermometer above the controller must be bound (got " + be.getMasterPos() + ")");
		helper.succeed();
	}

	// ------------------------------------------------------- U1 vessel state layer

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void reactorHeatsAndReadsAllPhases(GameTestHelper helper) {
		// U1/G1+G2: in a multi-phase vessel EVERY phase relaxes toward the burner
		// target (the old updateHeat early-returned on fluids.size() != 1, so after
		// D18 a bystander phase sat at its entry temperature forever), and the
		// vessel-level reading is the amount-weighted average of all phases.
		buildReactor(helper);
		ReactorControllerBlockEntity be = reactor(helper);
		FluidStack hot = new FluidStack(Fluids.WATER, 500);
		Temperature.set(hot, 800);
		FluidStack warm = new FluidStack(AllFluids.THERMAL_OIL.get().getSource(), 500);
		Temperature.set(warm, 400);
		be.getTank().fill(hot, FluidAction.EXECUTE);
		be.getTank().fill(warm, FluidAction.EXECUTE);
		be.getTank().collapseIfNeeded();
		helper.assertTrue(be.getTank().getFluids().size() == 2,
			"water + thermal oil must stay two phases (got " + be.getTank().getFluids().size() + ")");
		helper.assertTrue(be.getTemperature() == 600,
			"vessel temperature must be the amount-weighted average 600°C (got " + be.getTemperature() + ")");
		helper.startSequence()
			.thenIdle(TICKS * 3) // 3 heat cycles (HEAT_TICK = 20), no burner -> ambient target
			.thenExecute(() -> {
				int t0 = Temperature.get(be.getTank().getFluids().get(0));
				int t1 = Temperature.get(be.getTank().getFluids().get(1));
				helper.assertTrue(t0 < 800, "the water phase must relax toward ambient (got " + t0 + "°C)");
				helper.assertTrue(t1 < 400, "the oil phase must relax too (got " + t1 + "°C)");
			})
			.thenSucceed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 40)
	public static void exothermicDeltaHeatsAllPhases(GameTestHelper helper) {
		// U1/G2: an exothermic whitelist recipe heats EVERY phase — the SO2
		// absorption (deltaHeat +100) must warm the inert oil bystander as well,
		// not just whichever stack happens to be entry 0.
		ReactorControllerBlockEntity be = buildReactor5x5x5WithGasAt(
			helper, 0, 0, 1, 2, 0, Direction.SOUTH);
		be.getTank().fill(new FluidStack(Fluids.WATER, 10000), FluidAction.EXECUTE);
		be.getTank().fill(new FluidStack(AllFluids.THERMAL_OIL.get().getSource(), 1000), FluidAction.EXECUTE);
		be.getTank().fill(new FluidStack(AllFluids.SULFUR_DIOXIDE.get().getSource(), 1000), FluidAction.EXECUTE);
		waitFor(helper.startSequence()
				.thenIdle(TICKS * 10), // so2_absorption: 200 ticks processingTime
			() -> hasIon(be.getTank(), "H+1", 200) && hasIon(be.getTank(), "SO4-2", 100))
			.thenExecute(() -> {
				// the absorption product lands in the ion domain (the same expansion
				// reactorAbsorbsSulfurDioxide asserts on)
				helper.assertTrue(hasIon(be.getTank(), "H+1", 200),
					"the absorption reaction should have run");
				helper.assertTrue(hasIon(be.getTank(), "SO4-2", 100),
					"the absorption reaction should have run (sulfate)");
				for (FluidStack stack : be.getTank().getFluids()) {
					helper.assertTrue(Temperature.get(stack) > 20,
						"every phase must carry the exotherm (a stack is still at 20°C)");
				}
			})
			.thenSucceed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void sealedVesselBuildsPressure(GameTestHelper helper) {
		// U1/G3: linear sealed-vessel model P_abs = 1 atm × (gas fraction) × (T/T_amb).
		// A vessel full of gas at ambient reads 0 gauge; heating it pressurises it:
		// 27000/27000 gas at 900°C -> 101 × (1173.15/293.15) − 101 ≈ 303 kPa gauge.
		ReactorControllerBlockEntity be = buildReactor5x5x5(helper);
		be.getTank().fill(new FluidStack(AllFluids.OXYGEN.get().getSource(), 27000), FluidAction.EXECUTE);
		be.setPinnedTemperature(20);
		helper.assertTrue(be.getPressure() == 0,
			"a full gas charge at ambient must read 0 gauge (got " + be.getPressure() + " kPa)");
		be.setPinnedTemperature(900);
		int pressure = be.getPressure();
		helper.assertTrue(pressure >= 300 && pressure <= 306,
			"heating the sealed gas charge must build ~303 kPa (got " + pressure + " kPa)");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void liquidFullVesselStaysAtZeroPressure(GameTestHelper helper) {
		// pressure comes from the gas phase only: a vessel completely full of hot
		// LIQUID must read 0 gauge however hot (liquids are effectively
		// incompressible in the linear model)
		ReactorControllerBlockEntity be = buildReactor5x5x5(helper);
		be.getTank().fill(new FluidStack(Fluids.WATER, 27000), FluidAction.EXECUTE);
		be.setPinnedTemperature(900);
		helper.assertTrue(be.getTank().getTotalAmount() == 27000, "the vessel should be liquid-full");
		helper.assertTrue(be.getPressure() == 0,
			"a liquid-full vessel must not pressurise (got " + be.getPressure() + " kPa)");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void openVesselKeepsAmbientPressure(GameTestHelper helper) {
		// U1/G3: an open-topped vessel vents — the gauge stays at ambient no matter
		// how much hot gas sits in it.
		ReactorControllerBlockEntity be = buildReactor3x3x5HighController(helper); // open top
		helper.assertTrue(be.isOpen(), "the test vessel must be open-topped");
		be.getTank().fill(new FluidStack(AllFluids.OXYGEN.get().getSource(), 3000), FluidAction.EXECUTE);
		be.setPinnedTemperature(900);
		helper.assertTrue(be.getPressure() == 0,
			"an open vessel must never build pressure (got " + be.getPressure() + " kPa)");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void pressureGaugePanelReadsAndAlarms(GameTestHelper helper) {
		// S03 pressure gauge (薄板): mounted on a SHELL BRICK it reads the vessel
		// pressure through the brick's master pointer, trips the redstone alarm
		// past the threshold (default 250 kPa), and the comparator maps the
		// reading onto 0..15 of the 1500 kPa full scale.
		ReactorControllerBlockEntity reactor = buildReactor5x5x5(helper);
		reactor.getTank().fill(new FluidStack(AllFluids.OXYGEN.get().getSource(), 27000), FluidAction.EXECUTE);
		reactor.setPinnedTemperature(900);
		int expected = reactor.getPressure();

		// mounted on the east wall brick at (4,2,2) — behind it is the brick,
		// not the controller, so the read goes panel -> brick.getValidMaster -> reactor
		BlockState gauge = AllBlocks.PRESSURE_GAUGE_PANEL.get().defaultBlockState()
			.setValue(BlockStateProperties.FACING, Direction.EAST);
		helper.setBlock(new BlockPos(5, 2, 2), gauge);
		PressureGaugePanelBlockEntity be = (PressureGaugePanelBlockEntity) helper.getBlockEntity(new BlockPos(5, 2, 2));
		helper.assertTrue(be != null, "pressure gauge panel should have a block entity");
		be.tick();
		helper.assertTrue(be.getPressure() == expected,
			"panel must read the vessel through the shell brick (got " + be.getPressure() + " kPa)");
		helper.assertTrue(be.getThreshold() == 250,
			"default threshold must be 250 kPa (got " + be.getThreshold() + " kPa)");
		helper.assertTrue(be.isAlarm(), expected + " kPa must trip the default 250 kPa threshold");

		BlockPos abs = helper.absolutePos(new BlockPos(5, 2, 2));
		BlockState state = helper.getBlockState(new BlockPos(5, 2, 2));
		helper.assertTrue(state.getSignal(helper.getLevel(), abs, Direction.EAST) == 15,
			"the alarm must emit a strong redstone signal");
		helper.assertTrue(state.getAnalogOutputSignal(helper.getLevel(), abs) == 15,
			"comparator signal should saturate at 15 once the reading reaches the 250 kPa threshold (dynamic 0..threshold scale)");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void wallPressureGaugeReadsOwnReactor(GameTestHelper helper) {
		// S03 pressure gauge (方块): a shell block filling a wall position — accepted
		// by the structure (vessel_walls tag), bound to the controller, proxies
		// fluid like a brick, and reads its own vessel's pressure.
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		BlockState controller = AllBlocks.REACTOR_CONTROLLER.get().defaultBlockState();
		BlockState gaugeBlock = AllBlocks.PRESSURE_GAUGE.get().defaultBlockState();
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, 1, z), brick); // floor
				helper.setBlock(new BlockPos(x, 3, z), brick); // roof (sealed)
			}
		}
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				if (x == 2 && z == 2) {
					continue; // interior
				}
				BlockPos p = new BlockPos(x, 2, z);
				BlockState st = (x == 2 && z == 1) ? controller : (x == 3 && z == 1) ? gaugeBlock : brick;
				helper.setBlock(p, st);
			}
		}
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR.defaultBlockState());
		ReactorControllerBlockEntity reactor = (ReactorControllerBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 1));
		helper.assertTrue(reactor.tryAssemble().ok(), "reactor with a pressure gauge wall should assemble");

		reactor.getTank().fill(new FluidStack(AllFluids.OXYGEN.get().getSource(), 1000), FluidAction.EXECUTE);
		reactor.setPinnedTemperature(900);
		int expected = reactor.getPressure();
		helper.assertTrue(expected > 0, "the sealed hot gas charge must be under pressure");

		PressureGaugeBlockEntity be = (PressureGaugeBlockEntity) helper.getBlockEntity(new BlockPos(3, 2, 1));
		helper.assertTrue(be != null, "wall pressure gauge should have a block entity");
		be.tick();
		helper.assertTrue(be.getMasterPos() != null, "wall pressure gauge must be bound to the controller");
		helper.assertTrue(be.getPressure() == expected,
			"wall pressure gauge must read its own reactor (got " + be.getPressure() + " kPa)");
		helper.assertTrue(be.isAlarm(), "the hot charge must trip the alarm");
		helper.assertTrue(be.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.EAST).isPresent(),
			"wall pressure gauge must proxy FLUID_HANDLER to the reactor");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void collapseDropsCorruptMixtures(GameTestHelper helper) {
		// regression: legacy component-less mixture stacks (left by the old
		// spill/absorb round-trip) accumulated because collapseIfNeeded did nothing
		// when merged was empty. They must now be cleaned up.
		buildReactor(helper);
		ReactorControllerBlockEntity be = reactor(helper);
		ReactorTank tank = be.getTank();
		tank.getFluids().add(new FluidStack(Mixture.fluid(), 1000)); // no components
		tank.getFluids().add(new FluidStack(Mixture.fluid(), 1000)); // no components
		tank.collapseIfNeeded();
		helper.assertTrue(tank.getFluids().isEmpty(),
			"component-less mixture stacks should be dropped, not accumulate (got "
				+ tank.getFluids().size() + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void capacitySurvivesSerialization(GameTestHelper helper) {
		buildReactor(helper);
		ReactorControllerBlockEntity be = reactor(helper);
		int capacity = be.getTank().getTankCapacity(0);
		helper.assertTrue(capacity >= 1000, "assembled tank capacity should be height-scaled");
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
		// empty vessel: no ingredients anywhere -> NO_RECIPE (poll: the first
		// reaction tick's phase drifts under load — the end state is stable)
		GameTestSequence seq = waitFor(helper.startSequence()
				.thenIdle(TICKS),
			() -> be.getStatus() == ReactorControllerBlockEntity.ReactorStatus.NO_RECIPE);
		seq.thenExecute(() -> helper.assertTrue(
				be.getStatus() == ReactorControllerBlockEntity.ReactorStatus.NO_RECIPE,
				"empty assembled vessel should report NO_RECIPE (got " + be.getStatus() + ")"))
			.thenExecute(() -> {
				// sulfur + oxygen but no heat -> TEMPERATURE (sulfur_burning requires HEATED)
				be.getItems().setStackInSlot(0, new ItemStack(AllItems.SULFUR.get()));
				be.getTank().fill(new FluidStack(AllFluids.OXYGEN.get().getSource(), 1000), FluidAction.EXECUTE);
			})
			// poll the stable TEMPERATURE state too (same phase-drift fix)
			.thenWaitUntil(() -> {
				if (be.getStatus() != ReactorControllerBlockEntity.ReactorStatus.TEMPERATURE) {
					throw new GameTestAssertException("Waiting");
				}
			})
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

}
