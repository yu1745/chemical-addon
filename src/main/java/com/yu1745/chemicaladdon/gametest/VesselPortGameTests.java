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



import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.buildReactor5x5x5WithInletAt;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.buildReactor5x5x5WithStatusPortAt;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.comparatorSignalOf;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.strongSignalOf;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.waitFor;

@GameTestHolder(ChemicalAddon.MODID)
@PrefixGameTestTemplate(false)
public class VesselPortGameTests {
	private static final int TICKS = 20;

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselB4InletBindsAndMetersThroughOutwardFace(GameTestHelper helper) {
		ReactorControllerBlockEntity vessel = buildReactor5x5x5WithInletAt(helper, 0, 0, 1, 3, 0, Direction.SOUTH);
		MeteringInletBlockEntity inlet = (MeteringInletBlockEntity) helper.getBlockEntity(new BlockPos(1, 3, 0));
		helper.assertTrue(vessel.getBlockPos().equals(inlet.getMasterPos()),
			"a preinstalled inlet must bind during first assembly");
		StructureCapabilities snapshot = ((StructureAccess) vessel).getStructureCapabilities();
		helper.assertTrue(snapshot.hasBoundPart(MeteringInletBlockEntity.PART_ID)
				&& snapshot.hasPart(MeteringInletBlockEntity.PART_ID),
			"a correctly installed inlet must be recorded as an installed part");
		helper.assertTrue(snapshot.capabilities().size() == 2 && snapshot.has(ProcessCapability.MIXED_VOLUME)
				&& snapshot.has(ProcessCapability.SEALED),
			"the metering inlet must contribute its part id but no extra process capability");

		// directionality: only the outward (NORTH) face is a fluid endpoint
		helper.assertTrue(inlet.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).isPresent(),
			"the outward face must expose the metering inlet");
		helper.assertFalse(inlet.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.SOUTH).isPresent(),
			"the vessel-facing side must not expose a pipe endpoint");
		helper.assertFalse(inlet.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).isPresent(),
			"an unmetered face must not proxy the raw vessel tank");

		IFluidHandler endpoint = inlet.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH)
			.orElseThrow(() -> new IllegalStateException());
		// default dose is 1000 mB; a partial EXECUTE counts only what entered
		helper.assertTrue(endpoint.fill(new FluidStack(Fluids.WATER, 600), FluidAction.EXECUTE) == 600,
			"the inlet must admit liquid below the dose");
		helper.assertTrue(inlet.getAdmittedMb() == 600 && vessel.getTank().getTotalAmount() == 600,
			"the admitted counter must track actual EXECUTE fills into the tank");
		inlet.refreshDiagnostic();
		helper.assertTrue(inlet.getStatus() == MeteringInletBlockEntity.Status.METERING,
			"a partially dosed batch must report METERING");
		// SIMULATE reports the dose-limited remainder without counting it
		helper.assertTrue(endpoint.fill(new FluidStack(Fluids.WATER, 900), FluidAction.SIMULATE) == 400,
			"SIMULATE must be capped at the remaining dose");
		helper.assertTrue(inlet.getAdmittedMb() == 600,
			"SIMULATE must not change the admitted counter");
		helper.assertTrue(endpoint.drain(100, FluidAction.EXECUTE).isEmpty(),
			"the inlet must never drain");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselB4InletStopsAtDoseAndSignalsDone(GameTestHelper helper) {
		ReactorControllerBlockEntity vessel = buildReactor5x5x5WithInletAt(helper, 0, 0, 1, 3, 0, Direction.SOUTH);
		MeteringInletBlockEntity inlet = (MeteringInletBlockEntity) helper.getBlockEntity(new BlockPos(1, 3, 0));
		IFluidHandler endpoint = inlet.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH)
			.orElseThrow(() -> new IllegalStateException());
		// an oversized offer is trimmed to exactly the configured dose
		helper.assertTrue(endpoint.fill(new FluidStack(Fluids.WATER, 5000), FluidAction.EXECUTE) == 1000,
			"an oversized fill must be trimmed to the 1000 mB dose");
		helper.assertTrue(inlet.getAdmittedMb() == 1000 && vessel.getTank().getTotalAmount() == 1000,
			"the batch must stop at the dose");
		inlet.refreshDiagnostic();
		helper.assertTrue(inlet.getStatus() == MeteringInletBlockEntity.Status.DONE,
			"a completed batch must report DONE");
		helper.assertTrue(inlet.doneSignal() == 15, "a DONE batch must emit strong redstone 15");
		helper.assertTrue(inlet.analogSignal() == 15, "the comparator must read full scale when done");
		// block-level redstone wiring (strong signal + comparator) — absolute pos
		BlockPos abs = helper.absolutePos(new BlockPos(1, 3, 0));
		helper.assertTrue(helper.getLevel().getBlockState(abs)
			.getSignal(helper.getLevel(), abs, Direction.NORTH) == 15,
			"the block state must emit the strong DONE signal");
		helper.assertTrue(helper.getLevel().getBlockState(abs)
			.getAnalogOutputSignal(helper.getLevel(), abs) == 15,
			"the comparator output must be full when done");
		helper.assertTrue(endpoint.fill(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE) == 0,
			"further fills must be blocked once the batch is DONE");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselB4InletResetAndDoseScrollReArmTheBatch(GameTestHelper helper) {
		ReactorControllerBlockEntity vessel = buildReactor5x5x5WithInletAt(helper, 0, 0, 1, 3, 0, Direction.SOUTH);
		MeteringInletBlockEntity inlet = (MeteringInletBlockEntity) helper.getBlockEntity(new BlockPos(1, 3, 0));
		IFluidHandler endpoint = inlet.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH)
			.orElseThrow(() -> new IllegalStateException());
		// proportional comparator mid-batch: 500/1000 -> 7
		endpoint.fill(new FluidStack(Fluids.WATER, 500), FluidAction.EXECUTE);
		helper.assertTrue(inlet.analogSignal() == 7, "the comparator must scale admitted/dose (got " + inlet.analogSignal() + ")");
		// the physical reset: empty-hand right click clears the counter
		inlet.resetBatch();
		helper.assertTrue(inlet.getAdmittedMb() == 0, "the reset must clear the admitted counter");
		inlet.refreshDiagnostic();
		helper.assertTrue(inlet.getStatus() == MeteringInletBlockEntity.Status.READY,
			"a reset inlet must be READY again");
		helper.assertTrue(inlet.doneSignal() == 0 && inlet.analogSignal() == 0,
			"a reset inlet must stop signaling");
		helper.assertTrue(endpoint.fill(new FluidStack(Fluids.WATER, 1200), FluidAction.EXECUTE) == 1000,
			"a reset batch must accept a full dose again (tank holds it) ");
		// scrolling the dose down re-limits the NEXT batch immediately
		inlet.resetBatch();
		inlet.setDoseSteps(2); // 200 mB
		helper.assertTrue(inlet.getDoseMb() == 200, "the world-scroll dose must be reconfigurable");
		helper.assertTrue(endpoint.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 200,
			"a scrolled-down dose must trim the batch to 200 mB");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselB4InletRejectsGasAndDiagnosesCapacity(GameTestHelper helper) {
		ReactorControllerBlockEntity vessel = buildReactor5x5x5WithInletAt(helper, 0, 0, 1, 3, 0, Direction.SOUTH);
		MeteringInletBlockEntity inlet = (MeteringInletBlockEntity) helper.getBlockEntity(new BlockPos(1, 3, 0));
		IFluidHandler endpoint = inlet.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH)
			.orElseThrow(() -> new IllegalStateException());
		// gases (lighter-than-air) never pass the liquid inlet
		helper.assertTrue(endpoint.fill(new FluidStack(AllFluids.OXYGEN.get().getSource(), 100), FluidAction.EXECUTE) == 0,
			"gas must never enter through the metering inlet");
		helper.assertTrue(inlet.getStatus() == MeteringInletBlockEntity.Status.NON_LIQUID,
			"a gas offer must diagnose NON_LIQUID");
		helper.assertTrue(inlet.getAdmittedMb() == 0, "a rejected gas offer must not count");
		// a full vessel reports NO_CAPACITY
		vessel.getTank().fill(new FluidStack(Fluids.WATER,
			vessel.getTank().getTankCapacity(0) - vessel.getTank().getTotalAmount()), FluidAction.EXECUTE);
		helper.assertTrue(endpoint.fill(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE) == 0,
			"a full vessel must accept nothing");
		helper.assertTrue(inlet.getStatus() == MeteringInletBlockEntity.Status.NO_CAPACITY,
			"a full vessel must diagnose NO_CAPACITY");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselB4MisplacedInletStaysBoundAndDiagnosable(GameTestHelper helper) {
		// FACING points out of the vessel: still a legal shell cell, never an endpoint
		ReactorControllerBlockEntity vessel = buildReactor5x5x5WithInletAt(helper, 0, 0, 1, 3, 0, Direction.NORTH);
		MeteringInletBlockEntity inlet = (MeteringInletBlockEntity) helper.getBlockEntity(new BlockPos(1, 3, 0));
		helper.assertTrue(vessel.getBlockPos().equals(inlet.getMasterPos()),
			"a misplaced inlet is still a legal bound shell cell");
		inlet.refreshDiagnostic();
		helper.assertTrue(inlet.getStatus() == MeteringInletBlockEntity.Status.MISPLACED,
			"an outward-facing inlet must report MISPLACED");
		for (Direction side : Direction.values()) {
			helper.assertFalse(inlet.getCapability(ForgeCapabilities.FLUID_HANDLER, side).isPresent(),
				"a misplaced inlet must publish no fluid endpoint (side " + side + ")");
		}
		StructureCapabilities snapshot = ((StructureAccess) vessel).getStructureCapabilities();
		helper.assertTrue(snapshot.hasBoundPart(MeteringInletBlockEntity.PART_ID)
				&& !snapshot.hasPart(MeteringInletBlockEntity.PART_ID),
			"a misplaced inlet must remain bound but ineffective");
		helper.assertTrue(inlet.doneSignal() == 0 && inlet.analogSignal() == 0,
			"a misplaced inlet must emit no redstone");
		// unbinding (controller broken) resets the diagnostic to UNBOUND — the
		// controller sits at the structure-relative cell (2, 2, 0)
		helper.setBlock(new BlockPos(2, 2, 0), Blocks.AIR.defaultBlockState());
		inlet.refreshDiagnostic();
		helper.assertTrue(inlet.getStatus() == MeteringInletBlockEntity.Status.UNBOUND,
			"an unbound inlet must diagnose UNBOUND");
		helper.assertTrue(inlet.analogSignal() == 0, "an unbound inlet must emit no comparator signal");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselB4InletStateSurvivesReloadAndRemovalRevokes(GameTestHelper helper) {
		ReactorControllerBlockEntity vessel = buildReactor5x5x5WithInletAt(helper, 0, 0, 1, 3, 0, Direction.SOUTH);
		MeteringInletBlockEntity inlet = (MeteringInletBlockEntity) helper.getBlockEntity(new BlockPos(1, 3, 0));
		IFluidHandler endpoint = inlet.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH)
			.orElseThrow(() -> new IllegalStateException());
		inlet.setDoseSteps(5); // 500 mB
		endpoint.fill(new FluidStack(Fluids.WATER, 300), FluidAction.EXECUTE);
		CompoundTag saved = inlet.saveWithoutMetadata();
		inlet.load(saved);
		helper.assertTrue(inlet.getAdmittedMb() == 300 && inlet.getDoseMb() == 500,
			"the admitted counter and the scrolled dose must survive a reload");
		helper.assertTrue(endpoint.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE) == 200,
			"a reloaded inlet must still limit the batch to the remaining dose");
		// removal revokes the endpoint and the part
		helper.setBlock(new BlockPos(1, 3, 0), Blocks.AIR.defaultBlockState());
		helper.assertFalse(inlet.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).isPresent(),
			"removing the inlet must revoke its external capability");
		helper.assertFalse(((StructureAccess) vessel).getStructureCapabilities()
			.hasPart(MeteringInletBlockEntity.PART_ID),
			"removal must revoke the part id");
		helper.succeed();
	}

	/** Builds a 5×5×5 sealed reactor with the B4 metering inlet replacing the
	 *  brick at the structure-relative cell (ix, iy, iz), FACING set to
	 *  {@code facing} (SOUTH on the near wall points into the vessel). */

	public static void vesselStatusPortBindsUnboundSilenceAndMapping(GameTestHelper helper) {
		ReactorControllerBlockEntity vessel = buildReactor5x5x5WithStatusPortAt(helper, 0, 0, 1, 2, 0);
		BlockPos portPos = new BlockPos(1, 2, 0);
		StatusPortBlockEntity port = (StatusPortBlockEntity) helper.getBlockEntity(portPos);
		helper.assertTrue(vessel.getBlockPos().equals(port.getMasterPos()),
			"assembly must bind the status port to the controller");
		// the fixed comparator mapping is locked as a pure function
		helper.assertTrue(StatusPortBlockEntity.comparatorFor("not_assembled") == 0
				&& StatusPortBlockEntity.comparatorFor("reacting") == 4
				&& StatusPortBlockEntity.comparatorFor("temperature") == 8
				&& StatusPortBlockEntity.comparatorFor("output_full") == 12
				&& StatusPortBlockEntity.comparatorFor("no_recipe") == 15
				&& StatusPortBlockEntity.comparatorFor(null) == 0,
			"the fixed comparator mapping must be NOT_ASSEMBLED=0/REACTING=4/TEMPERATURE=8/OUTPUT_FULL=12/NO_RECIPE=15");
		// a stray unbound port anywhere else must stay silent
		BlockPos stray = new BlockPos(9, 1, 9);
		helper.setBlock(stray, AllBlocks.STATUS_PORT.get().defaultBlockState());
		StatusPortBlockEntity strayPort = (StatusPortBlockEntity) helper.getBlockEntity(stray);
		waitFor(helper.startSequence()
				.thenIdle(10), // one reaction step (onAssembled parks at REACTING until then)
			() -> "no_recipe".equals(port.getStatusName()))
			.thenExecute(() -> {
				helper.assertTrue(strayPort.getStatusName() == null, "an unbound port must publish no status");
				helper.assertTrue(strongSignalOf(helper, stray) == 0 && comparatorSignalOf(helper, stray) == 0,
					"an unbound status port must be completely silent");
				helper.assertTrue("no_recipe".equals(port.getStatusName()),
					"an empty assembled reactor must publish NO_RECIPE (got " + port.getStatusName() + ")");
				helper.assertTrue(strongSignalOf(helper, portPos) == 15,
					"an attached non-running status must emit strong 15 (got " + strongSignalOf(helper, portPos) + ", status=" + port.getStatusName() + ")");
				helper.assertTrue(comparatorSignalOf(helper, portPos) == 15,
					"NO_RECIPE must map to comparator 15");
			})
			.thenSucceed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselStatusPortReactingIsNotCompletion(GameTestHelper helper) {
		ReactorControllerBlockEntity vessel = buildReactor5x5x5WithStatusPortAt(helper, 0, 0, 1, 2, 0);
		BlockPos portPos = new BlockPos(1, 2, 0);
		StatusPortBlockEntity port = (StatusPortBlockEntity) helper.getBlockEntity(portPos);
		// deterministic batch: sulfur burning, pinned 500 °C (the B1 fixture)
		vessel.setPinnedTemperature(500);
		vessel.getItems().setStackInSlot(0, new ItemStack(AllItems.SULFUR.get()));
		vessel.getTank().fill(new FluidStack(AllFluids.OXYGEN.get().getSource(), 1000), FluidAction.EXECUTE);
		vessel.setPinnedTemperature(500); // re-pin AFTER the fill so the fresh oxygen is hot from tick one
		helper.startSequence()
			.thenIdle(21)
			.thenExecute(() -> {
				helper.assertTrue("reacting".equals(port.getStatusName()),
					"a running batch must publish REACTING (got " + port.getStatusName() + ")");
				helper.assertTrue(strongSignalOf(helper, portPos) == 0,
					"REACTING must NOT emit the completion edge (strong stays 0)");
				helper.assertTrue(comparatorSignalOf(helper, portPos) == 4,
					"REACTING must map to comparator 4 (got " + comparatorSignalOf(helper, portPos) + ")");
			})
			// past the 80-t batch completion of the B1 fixture — poll for the first
			// tick the status leaves REACTING
			.thenWaitUntil(() -> {
				if ("reacting".equals(port.getStatusName())) {
					throw new GameTestAssertException("Waiting");
				}
			})
			.thenExecute(() -> {
				helper.assertTrue(!"reacting".equals(port.getStatusName()),
					"the finished batch must leave REACTING (got " + port.getStatusName() + ")");
				helper.assertTrue(strongSignalOf(helper, portPos) == 15,
					"leaving REACTING must raise the completion edge (strong 15)");
			})
			.thenSucceed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselStatusPortTeardownAndRebind(GameTestHelper helper) {
		ReactorControllerBlockEntity vessel = buildReactor5x5x5WithStatusPortAt(helper, 0, 0, 1, 2, 0);
		BlockPos portPos = new BlockPos(1, 2, 0);
		StatusPortBlockEntity port = (StatusPortBlockEntity) helper.getBlockEntity(portPos);
		// break a WALL brick (a roof brick only shrinks the vessel) -> de-assemble -> the port must go silent
		BlockPos wallBrick = new BlockPos(0, 2, 1);
		helper.setBlock(wallBrick, Blocks.AIR.defaultBlockState());
		helper.assertFalse(vessel.isAssembled(), "breaking a wall brick must de-assemble the vessel");
		helper.startSequence()
			.thenWaitUntil(() -> {
				if (port.getStatusName() != null) {
					throw new GameTestAssertException("Waiting");
				}
			})
			.thenExecute(() -> {
				helper.assertTrue(port.getStatusName() == null,
					"a de-assembled master must detach the port (got " + port.getStatusName() + ")");
				helper.assertTrue(strongSignalOf(helper, portPos) == 0 && comparatorSignalOf(helper, portPos) == 0,
					"a detached port must be silent");
			})
			.thenExecute(() -> {
				// repair the shell: the vessel re-forms on place and the port must re-bind
				helper.setBlock(wallBrick, AllBlocks.CHEMICAL_BRICK.get().defaultBlockState());
				helper.assertTrue(vessel.isAssembled(), "repairing the wall must re-assemble the vessel");
			})
			.thenIdle(10) // one reaction step after re-assembly
			.thenWaitUntil(() -> {
				if (!"no_recipe".equals(port.getStatusName())) {
					throw new GameTestAssertException("Waiting");
				}
			})
			.thenExecute(() -> {
				helper.assertTrue("no_recipe".equals(port.getStatusName()),
				"the re-bound port must publish the master status again (got " + port.getStatusName() + ")");
				helper.assertTrue(strongSignalOf(helper, portPos) == 15,
					"the re-bound port must re-emit the non-running strong signal (got " + strongSignalOf(helper, portPos) + ", status=" + port.getStatusName() + ")");
			})
			.thenSucceed();
	}
}

