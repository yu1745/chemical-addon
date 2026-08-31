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



import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.jsonArray;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.solutionArray;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.range;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.recipeFromA3Json;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.buildReactor;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.reactor;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.buildReactor5x5x5;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.buildReactor5x5x5WithGasAt;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.buildReactor5x5x5WithTrayAt;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.buildReactor5x5x5WithTwoTrays;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.buildReactor5x5x5WithHeadAt;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.buildReactor3x3x5HighController;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.waitFor;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.hasFluid;

@GameTestHolder(ChemicalAddon.MODID)
@PrefixGameTestTemplate(false)
public class VesselHardwareGameTests {
	private static final int TICKS = 20;


	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselA1NarrowViews(GameTestHelper helper) {
		buildReactor(helper);
		ReactorControllerBlockEntity be = reactor(helper);
		helper.assertTrue(be instanceof StructureAccess, "controller must expose structure access");
		helper.assertTrue(be instanceof LiquidProcessAccess, "controller must expose liquid process access");
		helper.assertTrue(be instanceof ProcessReadings, "controller must expose process readings");
		StructureAccess structure = be;
		LiquidProcessAccess liquid = be;
		ProcessReadings readings = be;
		helper.assertTrue(structure.isAssembled() && structure.getSize() == 3 && structure.getHeight() == 1,
			"structure view must report the assembled geometry");
		helper.assertTrue(liquid.getLiquidCapacity() == be.getTank().getTankCapacity(0),
			"liquid view must preserve the legacy tank capacity");
		helper.assertTrue(readings.getTemperature() == be.getTemperature()
			&& readings.getPressure() == be.getPressure(),
			"reading view must preserve temperature and pressure values");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselA2OpenCapabilitySnapshot(GameTestHelper helper) {
		ReactorControllerBlockEntity be = buildReactor3x3x5HighController(helper);
		StructureCapabilities snapshot = ((StructureAccess) be).getStructureCapabilities();
		helper.assertTrue(snapshot.has(ProcessCapability.MIXED_VOLUME),
			"assembled vessel must advertise mixed-volume capability");
		helper.assertTrue(snapshot.has(ProcessCapability.OPEN_TOP)
			&& !snapshot.has(ProcessCapability.SEALED),
			"open vessel must advertise OPEN_TOP only");
		helper.assertTrue(snapshot.size() == 3 && snapshot.height() == 3
			&& snapshot.capacityMb() == be.getTank().getTankCapacity(0)
			&& snapshot.interiorVolumeBlocks() == 3,
			"open capability snapshot must preserve geometry and capacity");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselA2SealedCapabilitySnapshot(GameTestHelper helper) {
		ReactorControllerBlockEntity be = buildReactor5x5x5(helper);
		StructureCapabilities snapshot = ((StructureAccess) be).getStructureCapabilities();
		helper.assertTrue(snapshot.has(ProcessCapability.MIXED_VOLUME),
			"assembled vessel must advertise mixed-volume capability");
		helper.assertTrue(snapshot.has(ProcessCapability.SEALED)
			&& !snapshot.has(ProcessCapability.OPEN_TOP),
			"sealed vessel must advertise SEALED only");
		helper.assertTrue(snapshot.size() == 5 && snapshot.height() == 3
			&& snapshot.capacityMb() == be.getTank().getTankCapacity(0)
			&& snapshot.interiorVolumeBlocks() == 27,
			"sealed capability snapshot must preserve geometry and capacity");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void recipeA3LegacyJsonKeepsMixedVesselMatch(GameTestHelper helper) {
		buildReactor(helper);
		ReactorControllerBlockEntity be = reactor(helper);
		ChemicalReactionRecipe recipe = recipeFromA3Json(new JsonObject());
		helper.assertTrue(recipe.getRequiredCapabilities().size() == 1
			&& recipe.getRequiredCapabilities().contains(ProcessCapability.MIXED_VOLUME),
			"legacy recipe JSON must default to mixed-volume capability");
		helper.assertTrue(recipe.matchesStructureRequirements((StructureAccess) be, (ProcessReadings) be),
			"legacy recipe must still match an assembled vessel");
		JsonObject encoded = new JsonObject();
		recipe.writeAdditional(encoded);
		helper.assertTrue(!encoded.has("requiredCapabilities"),
			"legacy JSON serialization must not gain an observable requirement field");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void recipeA3OpenCapabilityRequirement(GameTestHelper helper) {
		ReactorControllerBlockEntity be = buildReactor3x3x5HighController(helper);
		JsonObject json = new JsonObject();
		json.add("requiredCapabilities", jsonArray("open_top"));
		ChemicalReactionRecipe recipe = recipeFromA3Json(json);
		helper.assertTrue(recipe.matchesStructureRequirements((StructureAccess) be, (ProcessReadings) be),
			"OPEN_TOP requirement must match an open vessel");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void recipeA3SealedCapabilityAndConditionRejection(GameTestHelper helper) {
		ReactorControllerBlockEntity be = buildReactor5x5x5(helper);
		// B1: required parts are enforced against the structure snapshot — a recipe
		// whose part is not installed no longer matches (pre-B1 it was data-only)
		JsonObject json = new JsonObject();
		json.add("requiredCapabilities", jsonArray("sealed"));
		json.add("requiredParts", jsonArray("chemicaladdon:catalyst_bed"));
		ChemicalReactionRecipe sealedRecipe = recipeFromA3Json(json);
		helper.assertTrue(sealedRecipe.getRequiredParts().contains(
			new ResourceLocation(ChemicalAddon.MODID, "catalyst_bed")),
			"required part must be parsed and retained");
		helper.assertTrue(!sealedRecipe.matchesStructureRequirements((StructureAccess) be, (ProcessReadings) be),
			"a recipe whose required part is not installed must be rejected (B1 enforcement)");

		// B1: agitation bounds are enforced against the snapshot's live reading —
		// an unstirred vessel (agitation 0) fails a positive lower bound, a fully
		// rotating stirring head (normalized 1.0) satisfies it
		json = new JsonObject();
		json.add("requiredCapabilities", jsonArray("sealed"));
		JsonObject agitationOnly = new JsonObject();
		JsonObject agitation = new JsonObject();
		agitation.addProperty("min", 0.5);
		agitationOnly.add("agitation", agitation);
		json.add("conditions", agitationOnly);
		ChemicalReactionRecipe needsStirring = recipeFromA3Json(json);
		helper.assertTrue(needsStirring.getConditions().hasAgitation()
				&& !needsStirring.matchesStructureRequirements((StructureAccess) be, (ProcessReadings) be),
			"an unstirred vessel must fail an agitation lower bound (B1 enforcement)");
		ReactorControllerBlockEntity stirred = buildReactor5x5x5(helper, 10, 0, true);
		BlockEntity stirredHeadBe = helper.getBlockEntity(new BlockPos(12, 5, 2));
		helper.assertTrue(stirredHeadBe instanceof StirringHeadBlockEntity,
			"the stirred test vessel must carry a stirring head");
		((StirringHeadBlockEntity) stirredHeadBe).setPinnedSpeed(256f);
		helper.assertTrue(needsStirring.matchesStructureRequirements((StructureAccess) stirred, (ProcessReadings) stirred),
			"a fully rotating head (normalized agitation 1.0) must satisfy the 0.5 lower bound");

		// temperature conditions behave as before
		json = new JsonObject();
		json.add("requiredCapabilities", jsonArray("sealed"));
		JsonObject conditions = new JsonObject();
		JsonObject temperature = new JsonObject();
		temperature.addProperty("min", 300);
		conditions.add("temperature", temperature);
		json.add("conditions", conditions);
		ChemicalReactionRecipe recipe = recipeFromA3Json(json);
		helper.assertTrue(!recipe.matchesStructureRequirements((StructureAccess) be, (ProcessReadings) be),
			"a sealed recipe above the current temperature must be rejected");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void recipeA3NetworkRoundTripKeepsAllCustomFields(GameTestHelper helper) {
		JsonObject json = new JsonObject();
		json.addProperty("deltaHeat", -17);
		json.add("solutions", solutionArray("chemicaladdon:hydrochloric_acid", 120, 0.2, 0.8));
		json.add("solutionOutputs", solutionArray("chemicaladdon:sodium_hydroxide", 80, 0.5, 0.7));
		json.add("requiredCapabilities", new JsonArray());
		json.add("requiredParts", jsonArray("chemicaladdon:catalyst_bed"));
		JsonObject conditions = new JsonObject();
		conditions.add("temperature", range(303, 333));
		conditions.add("pressureKpa", range(-5, 300));
		conditions.add("agitation", range(0.25, 0.75));
		json.add("conditions", conditions);
		ChemicalReactionRecipe original = recipeFromA3Json(json);
		FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
		try {
			original.writeAdditional(buffer);
			ChemicalReactionRecipe copy = recipeFromA3Json(new JsonObject());
			copy.readAdditional(buffer);
			helper.assertTrue(copy.getDeltaHeat() == original.getDeltaHeat()
				&& copy.getSolutions().equals(original.getSolutions())
				&& copy.getSolutionOutputs().equals(original.getSolutionOutputs()),
				"network sync must retain legacy chemical reaction fields");
			helper.assertTrue(copy.getRequiredCapabilities().isEmpty()
				&& copy.getRequiredParts().equals(original.getRequiredParts())
				&& copy.getConditions().toJson().equals(original.getConditions().toJson()),
				"network sync must retain A3 requirements, including explicit empty capabilities");
		} finally {
			buffer.release();
		}
		helper.succeed();
	}

	// -------------------------------------------------- construction package B1

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselB1StirringHeadBindsAndAgitates(GameTestHelper helper) {
		// 5×5×5 sealed reactor whose centre roof block is the stirring head: the
		// head is a vessel_walls block, so the roof stays sealed with it in place
		ReactorControllerBlockEntity be = buildReactor5x5x5(helper, 0, 0, true);
		BlockPos headPos = new BlockPos(2, 5, 2);
		BlockEntity headBe = helper.getBlockEntity(headPos);
		helper.assertTrue(headBe instanceof StirringHeadBlockEntity,
			"the roof centre must hold the stirring head BE");
		StirringHeadBlockEntity head = (StirringHeadBlockEntity) headBe;
		helper.assertTrue(head.getMasterPos() != null && head.getMasterPos().equals(be.getBlockPos()),
			"assembly must bind the head to the controller");
		helper.assertTrue(!head.isPartEffective() && head.effectiveAgitation() == 0f,
			"a stationary head must not be effective");
		StructureCapabilities still = ((StructureAccess) be).getStructureCapabilities();
		helper.assertTrue(still.has(ProcessCapability.SEALED) && !still.has(ProcessCapability.AGITATED)
			&& !still.hasPart(StirringHeadBlockEntity.PART_ID) && still.agitation() == 0f,
			"a stationary head must not publish AGITATED, its part id or agitation");

		// half speed → normalized 0.5 (REFERENCE_RPM 256)
		head.setPinnedSpeed(128f);
		StructureCapabilities stirred = ((StructureAccess) be).getStructureCapabilities();
		helper.assertTrue(stirred.has(ProcessCapability.AGITATED) && stirred.hasPart(StirringHeadBlockEntity.PART_ID),
			"a rotating head must publish AGITATED and its part id");
		helper.assertTrue(Math.abs(stirred.agitation() - 0.5f) < 1.0e-3f,
			"128 RPM must normalize to agitation 0.5 (got " + stirred.agitation() + ")");

		// capability proxy parity with a structural brick (the head IS a shell block)
		helper.assertTrue(head.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).isPresent(),
			"the bound head must proxy the vessel's fluid handler on its sides");
		helper.assertTrue(!head.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).isPresent(),
			"the head's top face must not accept pipes (vessel-top parity)");

		// an overstressed network reads as zero: the head stops agitating
		head.updateFromNetwork(0f, 100f, 1);
		StructureCapabilities halted = ((StructureAccess) be).getStructureCapabilities();
		helper.assertTrue(!halted.has(ProcessCapability.AGITATED) && !halted.hasPart(StirringHeadBlockEntity.PART_ID)
			&& halted.agitation() == 0f,
			"an overstressed head must stop counting as installed/effective");
		head.updateFromNetwork(1000f, 0f, 1); // recover for the break test below

		// breaking the head degrades the vessel to open-topped (ceiling-brick
		// removal path), the part disappears, the vessel survives
		helper.setBlock(headPos, Blocks.AIR.defaultBlockState());
		helper.assertTrue(be.isAssembled() && be.isOpen(),
			"removing the roof head must leave an assembled open-topped vessel");
		StructureCapabilities opened = ((StructureAccess) be).getStructureCapabilities();
		helper.assertTrue(opened.has(ProcessCapability.OPEN_TOP) && !opened.hasPart(StirringHeadBlockEntity.PART_ID),
			"an opened vessel must no longer expose the head part");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselB2SideDistributorAcceptsGasThroughExternalFace(GameTestHelper helper) {
		ReactorControllerBlockEntity vessel = buildReactor5x5x5WithGasAt(helper, 0, 0, 1, 2, 0, Direction.SOUTH);
		GasDistributorBlockEntity distributor = (GasDistributorBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 0));
		vessel.getTank().fill(new FluidStack(Fluids.WATER, 10000), FluidAction.EXECUTE);
		distributor.refreshDiagnostic();
		helper.assertTrue(distributor.getStatus() == GasDistributorBlockEntity.Status.ACCEPTING,
			"a submerged side distributor should be ready");
		helper.assertTrue(distributor.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).isPresent(),
			"only the outside face should expose the inlet capability");
		helper.assertFalse(distributor.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.SOUTH).isPresent(),
			"the vessel-facing nozzle side must not expose a pipe endpoint");

		FluidStack oxygen = new FluidStack(AllFluids.OXYGEN.get().getSource(), 100);
		IFluidHandler ordinaryPort = vessel.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH)
			.orElseThrow(() -> new IllegalStateException());
		helper.assertTrue(!ordinaryPort.isFluidValid(0, oxygen)
			&& ordinaryPort.fill(oxygen, FluidAction.SIMULATE) == 0,
			"ordinary vessel ports must reject external gas so the distributor cannot be bypassed");
		helper.assertTrue(ordinaryPort.fill(new FluidStack(Fluids.WATER, 100), FluidAction.SIMULATE) == 100,
			"ordinary vessel ports must continue accepting liquid");
		int before = vessel.getTank().getTotalAmount();
		helper.assertTrue(distributor.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).orElseThrow(() -> new IllegalStateException())
			.fill(oxygen, FluidAction.SIMULATE) == 100, "SIMULATE should report the available gas flow");
		helper.assertTrue(vessel.getTank().getTotalAmount() == before && distributor.getAcceptedInWindow() == 0,
			"SIMULATE must not change tank contents or the safety window");
		helper.assertTrue(distributor.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).orElseThrow(() -> new IllegalStateException())
			.fill(oxygen, FluidAction.EXECUTE) == 100, "the submerged inlet should accept gas");
		helper.assertTrue(vessel.getTank().getTotalAmount() == before + 100 && distributor.getStatus() == GasDistributorBlockEntity.Status.ACCEPTING,
			"accepted gas must enter the existing ReactorTank");
		helper.assertTrue(distributor.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).orElseThrow(() -> new IllegalStateException())
			.drain(100, FluidAction.EXECUTE).isEmpty(), "the distributor must never drain");
		vessel.getTank().fill(new FluidStack(Fluids.WATER,
			vessel.getTank().getTankCapacity(0) - vessel.getTank().getTotalAmount()), FluidAction.EXECUTE);
		helper.assertTrue(distributor.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).orElseThrow(() -> new IllegalStateException())
			.fill(oxygen, FluidAction.EXECUTE) == 0
			&& distributor.getStatus() == GasDistributorBlockEntity.Status.NO_CAPACITY,
			"a full ReactorTank must report NO_CAPACITY");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 30)
	public static void vesselB2DistributorUsesUnifiedTransferAndRateWindow(GameTestHelper helper) {
		ReactorControllerBlockEntity vessel = buildReactor5x5x5WithGasAt(helper, 0, 0, 1, 2, 0, Direction.SOUTH);
		GasDistributorBlockEntity distributor = (GasDistributorBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 0));
		vessel.getTank().fill(new FluidStack(Fluids.WATER, 10000), FluidAction.EXECUTE);
		FluidStack gas = new FluidStack(AllFluids.SULFUR_DIOXIDE.get().getSource(), 500);
		IFluidHandler inlet = distributor.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).orElseThrow(() -> new IllegalStateException());
		helper.assertTrue(inlet.fill(gas, FluidAction.EXECUTE) == 250, "one distributor is capped at 250 mB per window");
		helper.assertTrue(inlet.fill(gas, FluidAction.EXECUTE) == 0
			&& distributor.getStatus() == GasDistributorBlockEntity.Status.RATE_LIMITED,
			"a second fill in the same window must be rate limited");
		helper.assertTrue(hasFluid(vessel.getTank(), AllFluids.SULFUR_DIOXIDE.get().getSource(), 250),
			"accepted gas must enter the vessel inventory before unified gas-liquid transfer");
		helper.startSequence().thenIdle(10).thenExecute(() -> {
			helper.assertTrue(inlet.fill(new FluidStack(AllFluids.SULFUR_DIOXIDE.get().getSource(), 100), FluidAction.EXECUTE) == 100,
				"the window must reset at exactly 10 ticks");
		}).thenSucceed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselB2RejectsInvalidGasDistributorStates(GameTestHelper helper) {
		ReactorControllerBlockEntity wrongFacing = buildReactor5x5x5WithGasAt(helper, 0, 0, 1, 2, 0, Direction.NORTH);
		GasDistributorBlockEntity outward = (GasDistributorBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 0));
		wrongFacing.getTank().fill(new FluidStack(Fluids.WATER, 10000), FluidAction.EXECUTE);
		helper.assertFalse(outward.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.SOUTH).isPresent(),
			"an outward-facing side nozzle must not publish an input endpoint");
		outward.refreshDiagnostic();
		helper.assertTrue(outward.getStatus() == GasDistributorBlockEntity.Status.WRONG_POSITION_OR_FACING,
			"an outward-facing side nozzle must report the placement diagnostic");

		ReactorControllerBlockEntity dry = buildReactor5x5x5WithGasAt(helper, 5, 0, 6, 2, 0, Direction.SOUTH);
		GasDistributorBlockEntity dryDistributor = (GasDistributorBlockEntity) helper.getBlockEntity(new BlockPos(6, 2, 0));
		helper.assertTrue(dryDistributor.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).orElseThrow(() -> new IllegalStateException())
			.fill(new FluidStack(AllFluids.OXYGEN.get().getSource(), 50), FluidAction.EXECUTE) == 0
			&& dryDistributor.getStatus() == GasDistributorBlockEntity.Status.NOT_SUBMERGED,
			"a dry outlet must reject gas instead of feeding the headspace");
		// water is a real liquid, not a gas whitelist miss
		wrongFacing = buildReactor5x5x5WithGasAt(helper, 10, 0, 11, 2, 0, Direction.SOUTH);
		GasDistributorBlockEntity nonGas = (GasDistributorBlockEntity) helper.getBlockEntity(new BlockPos(11, 2, 0));
		wrongFacing.getTank().fill(new FluidStack(Fluids.WATER, 10000), FluidAction.EXECUTE);
		helper.assertTrue(nonGas.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).orElseThrow(() -> new IllegalStateException())
			.fill(new FluidStack(Fluids.WATER, 50), FluidAction.EXECUTE) == 0
			&& nonGas.getStatus() == GasDistributorBlockEntity.Status.NON_GAS,
			"non-gas fluids must be rejected without an id whitelist");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselB2BottomAndRoofGatesPublishCapabilities(GameTestHelper helper) {
		ReactorControllerBlockEntity bottom = buildReactor5x5x5WithGasAt(helper, 0, 0, 2, 1, 2, Direction.UP);
		GasDistributorBlockEntity bottomDistributor = (GasDistributorBlockEntity) helper.getBlockEntity(new BlockPos(2, 1, 2));
		bottom.getTank().fill(new FluidStack(Fluids.WATER, 2300), FluidAction.EXECUTE);
		helper.assertTrue(bottomDistributor.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.DOWN).isPresent(),
			"the bottom outlet must expose its downward external face");
		helper.assertTrue(bottomDistributor.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.DOWN).orElseThrow(() -> new IllegalStateException())
			.fill(new FluidStack(AllFluids.OXYGEN.get().getSource(), 50), FluidAction.EXECUTE) == 50,
			"an UP-facing bottom distributor should accept submerged gas");
		StructureCapabilities bottomSnapshot = ((StructureAccess) bottom).getStructureCapabilities();
		helper.assertTrue(bottomSnapshot.has(ProcessCapability.GAS_DISPERSED)
			&& bottomSnapshot.hasPart(GasDistributorBlockEntity.PART_ID),
			"a submerged side or bottom distributor must publish GAS_DISPERSED and its part id");

		ReactorControllerBlockEntity roof = buildReactor5x5x5WithGasAt(helper, 10, 0, 12, 5, 2, Direction.DOWN);
		GasDistributorBlockEntity roofDistributor = (GasDistributorBlockEntity) helper.getBlockEntity(new BlockPos(12, 5, 2));
		StructureCapabilities roofSnapshot = ((StructureAccess) roof).getStructureCapabilities();
		helper.assertTrue(roofSnapshot.hasBoundPart(GasDistributorBlockEntity.PART_ID)
			&& !roofSnapshot.hasPart(GasDistributorBlockEntity.PART_ID)
			&& !roofSnapshot.has(ProcessCapability.GAS_DISPERSED),
			"a roof distributor remains diagnostically bound but is not an effective part");
		helper.assertFalse(roofDistributor.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.UP).isPresent(),
			"an invalid roof distributor must not publish a fluid endpoint");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselB2WindowPersistsAndRemovalUnbinds(GameTestHelper helper) {
		ReactorControllerBlockEntity vessel = buildReactor5x5x5WithGasAt(helper, 0, 0, 1, 2, 0, Direction.SOUTH);
		GasDistributorBlockEntity distributor = (GasDistributorBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 0));
		vessel.getTank().fill(new FluidStack(Fluids.WATER, 10000), FluidAction.EXECUTE);
		IFluidHandler inlet = distributor.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).orElseThrow(() -> new IllegalStateException());
		inlet.fill(new FluidStack(AllFluids.OXYGEN.get().getSource(), 250), FluidAction.EXECUTE);
		CompoundTag saved = distributor.saveWithoutMetadata();
		distributor.load(saved);
		helper.assertTrue(distributor.getAcceptedInWindow() == 250 && distributor.getWindowStart() == saved.getLong("gasWindowStart"),
			"the rate window must survive a block-entity reload");
		helper.setBlock(new BlockPos(1, 2, 0), Blocks.AIR.defaultBlockState());
		helper.assertFalse(distributor.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).isPresent(),
			"removing the distributor must revoke its external capability");
		helper.assertFalse(((StructureAccess) vessel).getStructureCapabilities().has(ProcessCapability.GAS_DISPERSED),
			"removal must revoke GAS_DISPERSED");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselB2ReplacingWallRebindsDistributor(GameTestHelper helper) {
		ReactorControllerBlockEntity vessel = buildReactor5x5x5(helper);
		BlockPos distributorPos = new BlockPos(1, 2, 0);
		BlockState distributorState = AllBlocks.GAS_DISTRIBUTOR.get().defaultBlockState()
			.setValue(BlockStateProperties.FACING, Direction.SOUTH);

		// This is the real Level.setBlock replacement path. The old brick's
		// onRemove runs before the new block's onPlace, and both callbacks run
		// before LevelChunk creates the replacement BE.
		helper.setBlock(distributorPos, distributorState);
		BlockEntity replacement = helper.getBlockEntity(distributorPos);
		helper.assertTrue(replacement instanceof GasDistributorBlockEntity,
			"replacing a shell brick must create the distributor BE, not reuse the brick BE");
		GasDistributorBlockEntity distributor = (GasDistributorBlockEntity) replacement;
		helper.assertTrue(vessel.isAssembled(),
			"replacing one legal shell block must keep the vessel assembled");
		helper.assertTrue(vessel.getBlockPos().equals(distributor.getMasterPos()),
			"same-size shell replacement must bind the new distributor after its BE is created");
		vessel.getTank().fill(new FluidStack(Fluids.WATER, 10000), FluidAction.EXECUTE);
		distributor.refreshDiagnostic();
		helper.assertTrue(distributor.getStatus() == GasDistributorBlockEntity.Status.ACCEPTING,
			"a correctly oriented replacement must not remain UNBOUND");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselB2DistributorParticipatesInFirstAssembly(GameTestHelper helper) {
		// The distributor is already a shell block before the controller performs
		// the real assembly; no setMaster shortcut is used.
		ReactorControllerBlockEntity vessel = buildReactor5x5x5WithGasAt(helper, 0, 0, 1, 2, 0, Direction.SOUTH);
		GasDistributorBlockEntity distributor = (GasDistributorBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 0));
		helper.assertTrue(vessel.isAssembled() && vessel.getBlockPos().equals(distributor.getMasterPos()),
			"a preinstalled distributor must bind during first assembly");
		StructureCapabilities snapshot = ((StructureAccess) vessel).getStructureCapabilities();
		helper.assertTrue(snapshot.hasBoundPart(GasDistributorBlockEntity.PART_ID),
			"first assembly must record the distributor as a bound shell part");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselB2WrongFacingStaysBoundAndDiagnosable(GameTestHelper helper) {
		ReactorControllerBlockEntity vessel = buildReactor5x5x5WithGasAt(helper, 0, 0, 1, 2, 0, Direction.NORTH);
		GasDistributorBlockEntity distributor = (GasDistributorBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 0));
		helper.assertTrue(vessel.getBlockPos().equals(distributor.getMasterPos()),
			"an incorrectly oriented distributor is still a legal bound shell cell");
		distributor.refreshDiagnostic();
		helper.assertTrue(distributor.getStatus() == GasDistributorBlockEntity.Status.WRONG_POSITION_OR_FACING,
			"wrong orientation must be diagnosed separately from UNBOUND");
		StructureCapabilities snapshot = ((StructureAccess) vessel).getStructureCapabilities();
		helper.assertTrue(snapshot.hasBoundPart(GasDistributorBlockEntity.PART_ID)
			&& !snapshot.hasPart(GasDistributorBlockEntity.PART_ID),
			"wrong orientation must remain bound but ineffective");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselB2RemovingDistributorRunsStructuralRemoval(GameTestHelper helper) {
		ReactorControllerBlockEntity vessel = buildReactor5x5x5WithGasAt(helper, 0, 0, 1, 2, 0, Direction.SOUTH);
		BlockPos distributorPos = new BlockPos(1, 2, 0);
		GasDistributorBlockEntity distributor = (GasDistributorBlockEntity) helper.getBlockEntity(distributorPos);
		helper.assertTrue(vessel.getBlockPos().equals(distributor.getMasterPos()),
			"precondition: distributor is bound before removal");

		// Real onRemove path: the vessel must lose its structure rather than keep
		// a stale capability snapshot after the legal shell cell is removed.
		helper.setBlock(distributorPos, Blocks.AIR.defaultBlockState());
		helper.assertTrue(helper.getBlockEntity(distributorPos) == null && !vessel.isAssembled(),
			"removing the distributor must remove its BE and invalidate the vessel");
		helper.assertTrue(!((StructureAccess) vessel).getStructureCapabilities()
			.has(ProcessCapability.GAS_DISPERSED),
			"an invalidated vessel must not retain GAS_DISPERSED");

		// Repair through the real placement path as well, proving the old removal
		// did not leave the controller's shell bookkeeping wedged.
		helper.setBlock(distributorPos, AllBlocks.CHEMICAL_BRICK.get().defaultBlockState());
		helper.assertTrue(vessel.isAssembled(), "replacing the removed cell must re-form the vessel");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselB2PlacementFollowsPlayerView(GameTestHelper helper) {
		GasDistributorBlock block = AllBlocks.GAS_DISTRIBUTOR.get();
		BlockPos target = new BlockPos(7, 7, 7);
		ItemStack item = new ItemStack(AllBlocks.GAS_DISTRIBUTOR.get());
		EnumSet<Direction> horizontalResults = EnumSet.noneOf(Direction.class);
		for (float yaw : new float[] { 0f, 90f, 180f, 270f }) {
			final float viewYaw = yaw;
			Player player = new Player(helper.getLevel(), BlockPos.ZERO, 0f,
				new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "placement-test")) {
				@Override
				public float getViewYRot(float partialTicks) {
					return viewYaw;
				}

				@Override
				public boolean isSpectator() {
					return false;
				}

				@Override
				public boolean isCreative() {
					return true;
				}
			};
			// A player outside a wall looks toward the vessel, so the horizontal
			// nearest-looking direction is also the inward nozzle direction.
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target), Direction.NORTH, target, true);
			BlockPlaceContext context = new BlockPlaceContext(helper.getLevel(), player, InteractionHand.MAIN_HAND,
				item, hit);
			BlockState state = block.getStateForPlacement(context);
			Direction expected = context.getNearestLookingDirection();
			helper.assertTrue(state != null && state.getValue(BlockStateProperties.FACING) == expected,
				"horizontal player view must point the nozzle into the wall (yaw=" + yaw
					+ ", nearest=" + context.getNearestLookingDirection() + ", got "
					+ (state == null ? "null" : state.getValue(BlockStateProperties.FACING)) + ")");
			horizontalResults.add(expected);
		}
		helper.assertTrue(horizontalResults.equals(EnumSet.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)),
			"four horizontal player views must produce four horizontal nozzle directions (got "
				+ horizontalResults + ")");

		Player above = new Player(helper.getLevel(), BlockPos.ZERO, 0f,
			new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "placement-test-bottom")) {
			@Override
			public float getViewXRot(float partialTicks) {
				return 90f;
			}

			@Override
			public float getViewYRot(float partialTicks) {
				return 0f;
			}

			@Override
			public boolean isSpectator() {
				return false;
			}

			@Override
			public boolean isCreative() {
				return true;
			}
		};
		// Place on the floor from inside/above while looking down; unlike a wall
		// mount, the vertical view is inverted so the nozzle points upward.
		BlockHitResult bottomHit = new BlockHitResult(Vec3.atCenterOf(target), Direction.DOWN, target, true);
		BlockPlaceContext bottomContext = new BlockPlaceContext(helper.getLevel(), above, InteractionHand.MAIN_HAND,
			item, bottomHit);
		BlockState bottom = block.getStateForPlacement(bottomContext);
		Direction bottomLooking = bottomContext.getNearestLookingDirection();
		Direction bottomExpected = bottomLooking.getAxis().isVertical() ? bottomLooking.getOpposite() : bottomLooking;
		helper.assertTrue(bottom != null && bottom.getValue(BlockStateProperties.FACING) == Direction.UP
			&& bottom.getValue(BlockStateProperties.FACING) == bottomExpected,
			"looking down at a bottom shell cell must produce FACING=UP (nearest="
				+ bottomContext.getNearestLookingDirection() + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselB3TrayBindsAndPublishesCatalystBed(GameTestHelper helper) {
		ReactorControllerBlockEntity vessel = buildReactor5x5x5WithTrayAt(helper, 0, 0, 1, 2, 0, Direction.SOUTH);
		CatalystTrayBlockEntity tray = (CatalystTrayBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 0));
		helper.assertTrue(vessel.getBlockPos().equals(tray.getMasterPos()),
			"assembly must bind the tray to the controller");
		tray.refreshDiagnostic();
		helper.assertTrue(tray.getStatus() == CatalystTrayBlockEntity.Status.EMPTY,
			"a valid wall install without catalyst must report EMPTY");
		helper.assertTrue(!((StructureAccess) vessel).getStructureCapabilities()
				.has(ProcessCapability.CATALYST_BED),
			"an empty tray must not publish CATALYST_BED");

		// load catalyst through the outward item face
		IItemHandler endpoint = tray.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH)
			.orElseThrow(() -> new IllegalStateException("outward face must expose the item endpoint"));
		helper.assertTrue(endpoint.insertItem(0, new ItemStack(AllItems.VANADIUM_PENTOXIDE.get(), 2), false).isEmpty(),
			"the catalyst tag must be accepted");
		tray.refreshDiagnostic();
		helper.assertTrue(tray.getStatus() == CatalystTrayBlockEntity.Status.ACTIVE,
			"a loaded valid tray must be ACTIVE");
		StructureCapabilities snapshot = ((StructureAccess) vessel).getStructureCapabilities();
		helper.assertTrue(snapshot.has(ProcessCapability.CATALYST_BED)
			&& snapshot.hasPart(CatalystTrayBlockEntity.PART_ID),
			"a loaded valid tray must publish CATALYST_BED and its part id");

		// ITEM_HANDLER only on the outward face
		helper.assertTrue(!tray.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.SOUTH).isPresent(),
			"the inward bed face must not expose the item endpoint");
		helper.assertTrue(!tray.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).isPresent(),
			"the wall faces must not expose the item endpoint");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselB3EmptyAndMisalignedTraysDiagnosedButBound(GameTestHelper helper) {
		// wrong facing: still a bound shell cell, never an effective part
		ReactorControllerBlockEntity vessel = buildReactor5x5x5WithTrayAt(helper, 0, 0, 1, 2, 0, Direction.NORTH);
		CatalystTrayBlockEntity outward = (CatalystTrayBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 0));
		outward.getCatalysts().insertItem(0, new ItemStack(AllItems.VANADIUM_PENTOXIDE.get(), 1), false);
		outward.refreshDiagnostic();
		helper.assertTrue(outward.getStatus() == CatalystTrayBlockEntity.Status.WRONG_POSITION_OR_FACING,
			"an outward-facing tray must report the placement diagnostic");
		StructureCapabilities wrong = ((StructureAccess) vessel).getStructureCapabilities();
		helper.assertTrue(wrong.hasBoundPart(CatalystTrayBlockEntity.PART_ID)
				&& !wrong.hasPart(CatalystTrayBlockEntity.PART_ID)
				&& !wrong.has(ProcessCapability.CATALYST_BED),
			"a misaligned tray stays bound but ineffective");

		// roof and floor installs are not side-wall cells
		buildReactor5x5x5WithTrayAt(helper, 5, 0, 7, 5, 2, Direction.DOWN);
		CatalystTrayBlockEntity roof = (CatalystTrayBlockEntity) helper.getBlockEntity(new BlockPos(7, 5, 2));
		roof.getCatalysts().insertItem(0, new ItemStack(AllItems.VANADIUM_PENTOXIDE.get(), 1), false);
		roof.refreshDiagnostic();
		helper.assertTrue(roof.getStatus() == CatalystTrayBlockEntity.Status.WRONG_POSITION_OR_FACING,
			"a roof tray must fail the side-wall gate");
		buildReactor5x5x5WithTrayAt(helper, 10, 0, 12, 1, 2, Direction.UP);
		CatalystTrayBlockEntity floor = (CatalystTrayBlockEntity) helper.getBlockEntity(new BlockPos(12, 1, 2));
		floor.getCatalysts().insertItem(0, new ItemStack(AllItems.VANADIUM_PENTOXIDE.get(), 1), false);
		floor.refreshDiagnostic();
		helper.assertTrue(floor.getStatus() == CatalystTrayBlockEntity.Status.WRONG_POSITION_OR_FACING,
			"a floor tray must fail the side-wall gate");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselB3ItemEndpointFiltersTagAndExtracts(GameTestHelper helper) {
		buildReactor5x5x5WithTrayAt(helper, 0, 0, 1, 2, 0, Direction.SOUTH);
		CatalystTrayBlockEntity tray = (CatalystTrayBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 0));
		IItemHandler endpoint = tray.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH)
			.orElseThrow(() -> new IllegalStateException("outward face must expose the item endpoint"));
		helper.assertTrue(!endpoint.insertItem(0, new ItemStack(AllItems.ROCK_SALT.get(), 1), false).isEmpty(),
			"a non-catalyst item must be rejected whole");
		helper.assertTrue(endpoint.getStackInSlot(0).isEmpty(), "a rejected item must not enter the slot");
		helper.assertTrue(endpoint.insertItem(0, new ItemStack(AllItems.VANADIUM_PENTOXIDE.get(), 3), false).isEmpty(),
			"catalyst must be accepted");
		helper.assertTrue(endpoint.getStackInSlot(0).getCount() == 3, "the slot must hold the inserted stack");
		helper.assertTrue(!endpoint.extractItem(0, 2, false).isEmpty()
				&& endpoint.getStackInSlot(0).getCount() == 1,
			"world extract must drain the tray");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselB3RecipeGatesOnCatalystPart(GameTestHelper helper) {
		ReactorControllerBlockEntity vessel = buildReactor5x5x5WithTrayAt(helper, 0, 0, 1, 2, 0, Direction.SOUTH);
		CatalystTrayBlockEntity tray = (CatalystTrayBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 0));
		JsonObject json = new JsonObject();
		json.add("requiredCapabilities", jsonArray("sealed"));
		json.add("requiredParts", jsonArray("chemicaladdon:catalyst_tray"));
		ChemicalReactionRecipe recipe = recipeFromA3Json(json);
		helper.assertTrue(recipe.getRequiredParts().contains(CatalystTrayBlockEntity.PART_ID),
			"the catalyst_tray part requirement must parse");
		helper.assertTrue(!recipe.matchesStructureRequirements((StructureAccess) vessel, (ProcessReadings) vessel),
			"an empty tray must fail a catalyst-gated recipe");
		tray.getCatalysts().insertItem(0, new ItemStack(AllItems.VANADIUM_PENTOXIDE.get(), 1), false);
		helper.assertTrue(recipe.matchesStructureRequirements((StructureAccess) vessel, (ProcessReadings) vessel),
			"a loaded tray must satisfy a catalyst-gated recipe");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void reactorB3CatalystItemConsumedAfterHundredBatches(GameTestHelper helper) {
		ReactorControllerBlockEntity vessel = buildReactor5x5x5WithTrayAt(helper, 0, 0, 1, 2, 0, Direction.SOUTH);
		CatalystTrayBlockEntity tray = (CatalystTrayBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 0));
		tray.getCatalysts().insertItem(0, new ItemStack(AllItems.VANADIUM_PENTOXIDE.get(), 1), false);
		for (int i = 0; i < CatalystUsage.BATCHES_PER_ITEM - 1; i++) {
			helper.assertTrue(vessel.chargePartBatch(CatalystTrayBlockEntity.PART_ID),
				"every successful batch must charge the first tray");
		}
		helper.assertTrue(tray.getCatalystStack().getCount() == 1 && tray.getBatchesUsed() == 99,
			"99 batches must not consume the item");
		helper.assertTrue(((StructureAccess) vessel).getStructureCapabilities().hasPart(CatalystTrayBlockEntity.PART_ID),
			"the tray stays effective until the item is spent");
		vessel.chargePartBatch(CatalystTrayBlockEntity.PART_ID);
		helper.assertTrue(tray.getCatalystStack().isEmpty() && tray.getBatchesUsed() == 0,
			"the 100th batch must consume the item");
		tray.refreshDiagnostic();
		helper.assertTrue(tray.getStatus() == CatalystTrayBlockEntity.Status.EMPTY,
			"a spent tray must fall back to EMPTY");
		helper.assertTrue(!((StructureAccess) vessel).getStructureCapabilities()
				.has(ProcessCapability.CATALYST_BED),
			"a spent tray must revoke CATALYST_BED (the recipe stops matching)");
		helper.assertTrue(!vessel.chargePartBatch(CatalystTrayBlockEntity.PART_ID),
			"an empty tray cannot absorb a charge");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselB3MultipleTraysChargeDeterministicFirst(GameTestHelper helper) {
		// two valid wall trays on the same ring: assembly order picks (1,3,0) first
		ReactorControllerBlockEntity vessel = buildReactor5x5x5WithTwoTrays(helper);
		CatalystTrayBlockEntity first = (CatalystTrayBlockEntity) helper.getBlockEntity(new BlockPos(1, 3, 0));
		CatalystTrayBlockEntity second = (CatalystTrayBlockEntity) helper.getBlockEntity(new BlockPos(3, 3, 0));
		first.getCatalysts().insertItem(0, new ItemStack(AllItems.VANADIUM_PENTOXIDE.get(), 1), false);
		second.getCatalysts().insertItem(0, new ItemStack(AllItems.VANADIUM_PENTOXIDE.get(), 1), false);
		vessel.chargePartBatch(CatalystTrayBlockEntity.PART_ID);
		helper.assertTrue(first.getBatchesUsed() == 1 && second.getBatchesUsed() == 0,
			"only the deterministic first tray may be charged");
		// spend the first tray entirely — charges move to the second
		for (int i = 0; i < CatalystUsage.BATCHES_PER_ITEM; i++) {
			vessel.chargePartBatch(CatalystTrayBlockEntity.PART_ID);
		}
		helper.assertTrue(first.getCatalystStack().isEmpty() && second.getBatchesUsed() == 1,
			"after the first tray is spent the charge must move to the second");
		helper.assertTrue(((StructureAccess) vessel).getStructureCapabilities()
				.hasPart(CatalystTrayBlockEntity.PART_ID),
			"the second tray keeps the catalyst bed alive");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselB3TrayPersistsAcrossReload(GameTestHelper helper) {
		buildReactor5x5x5WithTrayAt(helper, 0, 0, 1, 2, 0, Direction.SOUTH);
		CatalystTrayBlockEntity tray = (CatalystTrayBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 0));
		tray.getCatalysts().insertItem(0, new ItemStack(AllItems.VANADIUM_PENTOXIDE.get(), 2), false);
		for (int i = 0; i < 7; i++) {
			tray.recordBatchCompletion();
		}
		CompoundTag saved = tray.saveWithoutMetadata();
		tray.load(saved);
		helper.assertTrue(tray.getCatalystStack().getCount() == 2 && tray.getBatchesUsed() == 7,
			"the catalyst charge and batch counter must survive a reload");
		helper.assertTrue(tray.isPartEffective(), "a reloaded loaded tray stays effective");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselB3RemovingTrayInvalidatesVessel(GameTestHelper helper) {
		ReactorControllerBlockEntity vessel = buildReactor5x5x5WithTrayAt(helper, 0, 0, 1, 2, 0, Direction.SOUTH);
		BlockPos trayPos = new BlockPos(1, 2, 0);
		helper.setBlock(trayPos, Blocks.AIR.defaultBlockState());
		helper.assertTrue(helper.getBlockEntity(trayPos) == null && !vessel.isAssembled(),
			"removing the tray must remove its BE and run the structural removal path");
		helper.assertTrue(!((StructureAccess) vessel).getStructureCapabilities()
				.has(ProcessCapability.CATALYST_BED),
			"an invalidated vessel must not retain CATALYST_BED");
		helper.setBlock(trayPos, AllBlocks.CHEMICAL_BRICK.get().defaultBlockState());
		helper.assertTrue(vessel.isAssembled(), "repairing the cell must re-form the vessel");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselB3PlacementFollowsPlayerView(GameTestHelper helper) {
		CatalystTrayBlock block = AllBlocks.CATALYST_TRAY.get();
		BlockPos target = new BlockPos(7, 7, 7);
		ItemStack item = new ItemStack(AllBlocks.CATALYST_TRAY.get());
		EnumSet<Direction> horizontalResults = EnumSet.noneOf(Direction.class);
		for (float yaw : new float[] { 0f, 90f, 180f, 270f }) {
			final float viewYaw = yaw;
			Player player = new Player(helper.getLevel(), BlockPos.ZERO, 0f,
				new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "placement-test")) {
				@Override
				public float getViewYRot(float partialTicks) {
					return viewYaw;
				}

				@Override
				public boolean isSpectator() {
					return false;
				}

				@Override
				public boolean isCreative() {
					return true;
				}
			};
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target), Direction.NORTH, target, true);
			BlockPlaceContext context = new BlockPlaceContext(helper.getLevel(), player, InteractionHand.MAIN_HAND,
				item, hit);
			BlockState state = block.getStateForPlacement(context);
			Direction expected = context.getNearestLookingDirection();
			helper.assertTrue(state != null && state.getValue(BlockStateProperties.FACING) == expected,
				"horizontal player view must point the tray bed into the wall");
			horizontalResults.add(expected);
		}
		helper.assertTrue(horizontalResults
			.equals(EnumSet.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)),
			"four horizontal player views must produce four horizontal bed directions (got "
				+ horizontalResults + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselB1WallPositionHeadIsNotInstalled(GameTestHelper helper) {
		// B1 placement rule: the stirring head is a roof-penetration drive — the
		// same powered head in a WALL cell must stay a bound, proxying shell block
		// yet never publish its part id, AGITATED or agitation
		ReactorControllerBlockEntity wallVessel = buildReactor5x5x5WithHeadAt(helper, 0, 0, 0, 3, 2);
		BlockPos wallHeadPos = new BlockPos(0, 3, 2); // west wall, middle ring layer
		BlockEntity wallHeadBe = helper.getBlockEntity(wallHeadPos);
		helper.assertTrue(wallHeadBe instanceof StirringHeadBlockEntity,
			"the wall cell must hold the stirring head BE");
		StirringHeadBlockEntity wallHead = (StirringHeadBlockEntity) wallHeadBe;
		helper.assertTrue(wallHead.getMasterPos() != null && wallHead.getMasterPos().equals(wallVessel.getBlockPos()),
			"a wall-position head still binds to the controller (shell block semantics)");
		wallHead.setPinnedSpeed(256f); // fully powered
		helper.assertTrue(!wallHead.isPartEffective() && wallHead.effectiveAgitation() == 0f,
			"a powered wall-position head must not be an effective part (roof-only rule)");
		helper.assertTrue(wallVessel.effectiveAgitation() == 0f,
			"the vessel must not read agitation from a wall-position head");
		StructureCapabilities snapshot = ((StructureAccess) wallVessel).getStructureCapabilities();
		helper.assertTrue(!snapshot.has(ProcessCapability.AGITATED) && !snapshot.hasPart(StirringHeadBlockEntity.PART_ID)
				&& snapshot.agitation() == 0f,
			"a powered wall-position head must not publish AGITATED, its part id or agitation");
		// still a shell block: the capability proxy keeps working
		helper.assertTrue(wallHead.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).isPresent(),
			"a wall-position head still proxies the vessel's fluid handler");

		// control: the same powered head on the roof plane DOES publish
		ReactorControllerBlockEntity roofVessel = buildReactor5x5x5(helper, 10, 0, true);
		BlockEntity roofHeadBe = helper.getBlockEntity(new BlockPos(12, 5, 2));
		helper.assertTrue(roofHeadBe instanceof StirringHeadBlockEntity,
			"the roof centre must hold the stirring head BE");
		((StirringHeadBlockEntity) roofHeadBe).setPinnedSpeed(256f);
		StructureCapabilities roofSnapshot = ((StructureAccess) roofVessel).getStructureCapabilities();
		helper.assertTrue(roofSnapshot.has(ProcessCapability.AGITATED)
				&& roofSnapshot.hasPart(StirringHeadBlockEntity.PART_ID)
				&& Math.abs(roofSnapshot.agitation() - 1.0f) < 1.0e-3f,
			"the same powered head on the roof plane must publish AGITATED, its part id and agitation 1.0");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselB1ShaftFollowsLiquidLevel(GameTestHelper helper) {
		// B1 visual layer, geometry level (no pixel assertions): the shaft target
		// depth follows the vessel's liquid level — lower portion of the liquor,
		// clamped off the floor/roof plates, retracted near the roof when empty.
		// 5×5×5 sealed reactor (interior 3×3×3, 27 buckets), centred roof head.
		ReactorControllerBlockEntity be = buildReactor5x5x5(helper, 0, 0, true);
		StirringHeadBlockEntity head = (StirringHeadBlockEntity) helper.getBlockEntity(new BlockPos(2, 5, 2));

		// impeller diameter: 0.65 × interior width 3, wall/height caps slack
		float diameter = head.getImpellerDiameter();
		helper.assertTrue(diameter > 1.8f && diameter < 2.1f,
			"a centred head in a 3-wide interior must size ~1.95 blocks (got " + diameter + ")");
		float half = StirShaftMath.impellerHalfHeight(diameter);

		// empty: retracted just under the roof (never inside the head cell, never deep)
		float empty = head.getShaftTargetDepth();
		helper.assertTrue(empty > 0f && empty < 0.75f,
			"an empty vessel must park the impeller near the roof (got " + empty + ")");

		// half full (13500 mB of 27000): centre rides 30% up the 1.5-block column
		be.getTank().fill(new FluidStack(Fluids.WATER, 13500), FluidAction.EXECUTE);
		float halfFull = head.getShaftTargetDepth();
		helper.assertTrue(halfFull > 2.3f && halfFull < 2.8f,
			"a half-full vessel must run the impeller low in the liquor (got " + halfFull + ")");
		helper.assertTrue(halfFull + half < 3f,
			"the blades must never cross the floor plate");

		// full (27000): the centre rises with the surface, blades stay submerged
		be.getTank().fill(new FluidStack(Fluids.WATER, 13500), FluidAction.EXECUTE);
		float full = head.getShaftTargetDepth();
		helper.assertTrue(full > 1.8f && full < 2.3f,
			"a full vessel must raise the impeller centre to 30% up the column (got " + full + ")");
		helper.assertTrue(full < halfFull, "filling further must raise the impeller, not lower it");

		// drained dry again: back to the roof parking position
		be.getTank().drain(27000, FluidAction.EXECUTE);
		float drained = head.getShaftTargetDepth();
		helper.assertTrue(Math.abs(drained - empty) < 1.0e-3f,
			"a drained vessel must retract the impeller to the same roof parking depth");

		// a wall-position head never draws a shaft at all (decorative shell block)
		ReactorControllerBlockEntity wallVessel = buildReactor5x5x5WithHeadAt(helper, 10, 0, 10, 3, 2);
		StirringHeadBlockEntity wallHead = (StirringHeadBlockEntity) helper.getBlockEntity(new BlockPos(10, 3, 2));
		wallHead.setPinnedSpeed(256f);
		helper.assertTrue(wallHead.getShaftTargetDepth() == 0f && wallHead.getImpellerDiameter() == 0f,
			"a wall-position head must not render a shaft or impeller");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vesselB1ShaftWorksWithHighControllerAndTallVessel(GameTestHelper helper) {
		// the depth is measured from the HEAD's bottom face to the interior floor —
		// the controller may sit on ANY ring layer, so a high-mounted controller
		// must not shift the geometry. Open-topped 3×3 shell, 5 rings (interior
		// 1×1×5), controller on ring 2, head in the roof centre.
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		BlockState controller = AllBlocks.REACTOR_CONTROLLER.get().defaultBlockState();
		BlockState headState = AllBlocks.STIRRING_HEAD.get().defaultBlockState();
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, 1, z), brick); // floor
				helper.setBlock(new BlockPos(x, 7, z), x == 2 && z == 2 ? headState : brick); // roof with the head centred
			}
		}
		for (int y = 2; y <= 6; y++) {
			for (int x = 1; x <= 3; x++) {
				for (int z = 1; z <= 3; z++) {
					if (x == 2 && z == 2) {
						continue; // interior column
					}
					helper.setBlock(new BlockPos(x, y, z), x == 2 && z == 1 && y == 4 ? controller : brick);
				}
			}
		}
		ReactorControllerBlockEntity be =
			(ReactorControllerBlockEntity) helper.getBlockEntity(new BlockPos(2, 4, 1));
		helper.assertTrue(be.tryAssemble().ok(), "sealed 3x3x7 with the controller on ring 2 should assemble");
		StirringHeadBlockEntity head = (StirringHeadBlockEntity) helper.getBlockEntity(new BlockPos(2, 7, 2));
		helper.assertTrue(head.getMasterPos() != null && head.getMasterPos().equals(be.getBlockPos()),
			"the roof head must bind to the high controller");

		// interior 1 wide → impeller 0.65 blocks, still visible but wall-safe
		float diameter = head.getImpellerDiameter();
		helper.assertTrue(diameter > 0.5f && diameter < 0.8f,
			"a 1-wide interior must size the impeller ~0.65 blocks (got " + diameter + ")");

		// empty → roof parking (depth is measured from the head, not the controller)
		float empty = head.getShaftTargetDepth();
		helper.assertTrue(empty > 0f && empty < 0.75f,
			"an empty tall vessel must park the impeller near the roof (got " + empty + ")");

		// shallow (500 mB = 0.5 blocks): the 30% target overshoots, the floor clamp wins
		be.getTank().fill(new FluidStack(Fluids.WATER, 500), FluidAction.EXECUTE);
		float shallow = head.getShaftTargetDepth();
		helper.assertTrue(shallow > 4.7f && shallow < 4.9f,
			"a shallow tall vessel must clamp the impeller just above the floor (got " + shallow + ")");

		// full (5000 mB): centre at 30% up the 5-block column
		be.getTank().fill(new FluidStack(Fluids.WATER, 4500), FluidAction.EXECUTE);
		float full = head.getShaftTargetDepth();
		helper.assertTrue(full > 3.3f && full < 3.7f,
			"a full tall vessel must ride the impeller 30% up the column (got " + full + ")");
		helper.assertTrue(full < shallow, "filling must raise the impeller centre");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 40)
	public static void reactorB1StirringDoublesRecipeProgress(GameTestHelper helper) {
		// two identical 5×5×5 reactors: plain vs fully stirred (256 RPM →
		// coefficient 2.0 vs baseline 1.0). Same charge, same pinned 500 °C
		// (deterministic HEATED rate window), same batch (sulfur burning, 100 t):
		// the stirred vessel finishes the batch the plain one has not.
		ReactorControllerBlockEntity plain = buildReactor5x5x5(helper);
		ReactorControllerBlockEntity stirred = buildReactor5x5x5(helper, 10, 0, true);
		BlockEntity headBe = helper.getBlockEntity(new BlockPos(12, 5, 2));
		helper.assertTrue(headBe instanceof StirringHeadBlockEntity,
			"the stirred vessel must carry a stirring head in its roof");
		StirringHeadBlockEntity head = (StirringHeadBlockEntity) headBe;
		head.setPinnedSpeed(256f);
		for (ReactorControllerBlockEntity reactor : List.of(plain, stirred)) {
			reactor.setPinnedTemperature(500);
			reactor.getItems().setStackInSlot(0, new ItemStack(AllItems.SULFUR.get()));
			reactor.getTank().fill(new FluidStack(AllFluids.OXYGEN.get().getSource(), 1000), FluidAction.EXECUTE);
		}
		// reaction steps run every 10 ticks: plain rate 1.25 → 0.125/step (needs 8
		// steps = 80 t); stirred rate 2.5 → 0.25/step (completes at 40 t). The
		// valid window opens at 50 t (stirred done, plain past 0.5 progress) and
		// closes at 80 t — poll it instead of parking on a fixed tick.
		waitFor(helper.startSequence()
				.thenIdle(50),
			() -> hasFluid(stirred, AllFluids.SULFUR_DIOXIDE.get().getSource(), 900)
				&& plain.getProgress() > 0.5f && plain.getProgress() < 1.0f)
			.thenExecute(() -> {
				helper.assertTrue(hasFluid(stirred, AllFluids.SULFUR_DIOXIDE.get().getSource(), 900),
					"the stirred reactor must have completed its batch (2× rate)");
				helper.assertTrue(!hasFluid(plain, AllFluids.SULFUR_DIOXIDE.get().getSource(), 1),
					"the unstirred reactor must still be mid-batch");
				helper.assertTrue(plain.getProgress() > 0.5f && plain.getProgress() < 1.0f,
					"the unstirred reactor must be visibly mid-batch (got " + plain.getProgress() + ")");
			})
			.thenSucceed();
	}
}
