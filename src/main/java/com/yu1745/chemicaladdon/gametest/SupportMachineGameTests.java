package com.yu1745.chemicaladdon.gametest;

import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.buildReactor5x5x5;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.hasIon;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.waitFor;

import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.composition.Solution;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.recipe.ChemicalReactionRecipe;
import com.yu1745.chemicaladdon.reactor.CompressorBlockEntity;
import com.yu1745.chemicaladdon.reactor.ElectrolyzerBlockEntity;
import com.yu1745.chemicaladdon.reactor.HeatExchangerBlockEntity;
import com.yu1745.chemicaladdon.reactor.ReactorControllerBlockEntity;
import com.yu1745.chemicaladdon.reactor.ReactorTank;
import com.yu1745.chemicaladdon.registry.AllBlocks;
import com.yu1745.chemicaladdon.registry.AllFluids;
import com.yu1745.chemicaladdon.vessel.ProcessCapability;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/** GameTests for standalone energy-driven process machines. */
@GameTestHolder(ChemicalAddon.MODID)
@PrefixGameTestTemplate(false)
public class SupportMachineGameTests {

	private static final int TICKS = 20;

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 40)
	public static void compressorGatesPressureRecipe(GameTestHelper helper) {
		ReactorControllerBlockEntity be = buildReactor5x5x5(helper);
		BlockPos compressorPos = new BlockPos(0, 2, 2);
		helper.setBlock(compressorPos, AllBlocks.COMPRESSOR.get().defaultBlockState());
		CompressorBlockEntity compressor = (CompressorBlockEntity) helper.getBlockEntity(compressorPos);
		be.getTank().fill(new FluidStack(AllFluids.NITROGEN.get().getSource(), 400), FluidAction.EXECUTE);
		be.getTank().fill(new FluidStack(AllFluids.HYDROGEN.get().getSource(), 1200), FluidAction.EXECUTE);
		waitFor(helper.startSequence().thenIdle(TICKS * 2),
			() -> compressor.getStatus() == CompressorBlockEntity.Status.NO_POWER)
			.thenExecute(() -> {
				helper.assertTrue(!be.getStructureCapabilities().has(ProcessCapability.PRESSURIZED),
					"a powerless compressor publishes no pressure capability");
				helper.assertTrue(!hasFluid(be, AllFluids.AMMONIA.get().getSource(), 1),
					"the pressure-gated recipe does not run unpowered");
				for (int i = 0; i < 10; i++) compressor.getEnergy().receiveEnergy(2000, false);
			})
			.thenWaitUntil(() -> {
				if (compressor.getStatus() != CompressorBlockEntity.Status.PRESSURIZING)
					throw new GameTestAssertException("Waiting");
			})
			.thenExecute(() -> helper.assertTrue(be.getStructureCapabilities().has(ProcessCapability.PRESSURIZED),
				"a powered compressor holds the vessel at pressure"))
			.thenWaitUntil(() -> {
				if (!hasFluid(be, AllFluids.AMMONIA.get().getSource(), 300))
					throw new GameTestAssertException("Waiting");
			})
			.thenExecute(() -> helper.assertTrue(hasFluid(be, AllFluids.AMMONIA.get().getSource(), 300),
				"N2 + H2 synthesizes ammonia under pressure"))
			.thenSucceed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void fPackageCapabilitiesReviveAndEnergyLoadsAbsolutely(GameTestHelper helper) {
		helper.setBlock(new BlockPos(2, 1, 2), AllBlocks.ELECTROLYZER.get().defaultBlockState());
		helper.setBlock(new BlockPos(4, 1, 2), AllBlocks.HEAT_EXCHANGER.get().defaultBlockState());
		helper.setBlock(new BlockPos(6, 1, 2), AllBlocks.COMPRESSOR.get().defaultBlockState());
		ElectrolyzerBlockEntity cell = (ElectrolyzerBlockEntity) helper.getBlockEntity(new BlockPos(2, 1, 2));
		HeatExchangerBlockEntity hx = (HeatExchangerBlockEntity) helper.getBlockEntity(new BlockPos(4, 1, 2));
		CompressorBlockEntity compressor = (CompressorBlockEntity) helper.getBlockEntity(new BlockPos(6, 1, 2));
		cell.invalidateCaps(); hx.invalidateCaps(); compressor.invalidateCaps();
		cell.reviveCaps(); hx.reviveCaps(); compressor.reviveCaps();
		helper.assertTrue(cell.getCapability(ForgeCapabilities.ENERGY).isPresent()
			&& cell.getCapability(ForgeCapabilities.FLUID_HANDLER).isPresent(), "electrolyzer capabilities revive after a chunk lifecycle");
		helper.assertTrue(hx.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH).isPresent(),
			"heat exchanger capabilities revive after a chunk lifecycle");
		helper.assertTrue(compressor.getCapability(ForgeCapabilities.ENERGY).isPresent(),
			"compressor capability revives after a chunk lifecycle");
		compressor.getEnergy().setEnergyStored(1200);
		CompoundTag saved = compressor.saveWithoutMetadata();
		compressor.getEnergy().setEnergyStored(0);
		compressor.load(saved); compressor.load(saved);
		helper.assertTrue(compressor.getEnergy().getEnergyStored() == 1200,
			"loading the same absolute energy packet twice does not accumulate FE");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 40)
	public static void heatExchangerRecoversAndConserves(GameTestHelper helper) {
		helper.setBlock(new BlockPos(4, 1, 4), AllBlocks.HEAT_EXCHANGER.get().defaultBlockState());
		HeatExchangerBlockEntity hx = (HeatExchangerBlockEntity) helper.getBlockEntity(new BlockPos(4, 1, 4));
		FluidStack hot = new FluidStack(Fluids.WATER, 1000); com.yu1745.chemicaladdon.fluid.Temperature.set(hot, 80);
		FluidStack cold = new FluidStack(Fluids.WATER, 1000); com.yu1745.chemicaladdon.fluid.Temperature.set(cold, 20);
		IFluidHandler hotPort = hx.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.NORTH)
			.orElseThrow(() -> new GameTestAssertException("hot capability missing"));
		IFluidHandler coldPort = hx.getCapability(ForgeCapabilities.FLUID_HANDLER, Direction.EAST)
			.orElseThrow(() -> new GameTestAssertException("cold capability missing"));
		helper.assertTrue(hotPort.fill(hot, FluidAction.EXECUTE) == 1000, "the hot port takes the hot stream");
		helper.assertTrue(coldPort.fill(cold, FluidAction.EXECUTE) == 1000, "the cold port takes the cold stream");
		long joulesBefore = joulesOf(hx);
		waitFor(helper.startSequence().thenIdle(TICKS), () -> Math.abs(hx.getDeltaT()) <= 2)
			.thenExecute(() -> {
				helper.assertTrue(hx.getHotTank().getTotalAmount() == 1000 && hx.getColdTank().getTotalAmount() == 1000,
					"no composition moved between the streams");
				long joulesAfter = joulesOf(hx);
				helper.assertTrue(Math.abs(joulesAfter - joulesBefore) <= 4200, "energy is conserved across the pair");
				helper.assertTrue(hx.getRecoveredJ() > 100000, "the recovered-heat meter accumulates");
			}).thenSucceed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void heatExchangerIdleSideExchangesNothing(GameTestHelper helper) {
		helper.setBlock(new BlockPos(4, 1, 4), AllBlocks.HEAT_EXCHANGER.get().defaultBlockState());
		HeatExchangerBlockEntity hx = (HeatExchangerBlockEntity) helper.getBlockEntity(new BlockPos(4, 1, 4));
		FluidStack hot = new FluidStack(Fluids.WATER, 1000); com.yu1745.chemicaladdon.fluid.Temperature.set(hot, 80);
		hx.getHotTank().fill(hot, FluidAction.EXECUTE);
		waitFor(helper.startSequence().thenIdle(TICKS * 2), () -> com.yu1745.chemicaladdon.fluid.Temperature.get(hx.getHotTank().getFluids().get(0)) == 80 && hx.getRecoveredJ() == 0)
			.thenExecute(() -> {
				helper.assertTrue(hx.getRecoveredJ() == 0, "an empty cold side recovers nothing");
				helper.assertTrue(com.yu1745.chemicaladdon.fluid.Temperature.get(hx.getHotTank().getFluids().get(0)) == 80,
					"the hot stream keeps its temperature");
			}).thenSucceed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 40)
	public static void electrolyzerRunsBothCellLines(GameTestHelper helper) {
		helper.setBlock(new BlockPos(4, 1, 4), AllBlocks.ELECTROLYZER.get().defaultBlockState());
		ElectrolyzerBlockEntity cell = (ElectrolyzerBlockEntity) helper.getBlockEntity(new BlockPos(4, 1, 4));
		FluidStack brine = GameTestFixtures.declared(2, Map.of("NaCl", .2), 2200);
		cell.getTank().fill(brine, FluidAction.EXECUTE);
		ResourceLocation brineId = new ResourceLocation(ChemicalAddon.MODID, "brine");
		ResourceLocation chlorAlkali = new ResourceLocation(ChemicalAddon.MODID,
			"chemical_reaction/chlor_alkali_electrolysis");
		var liveRecipe = cell.getLevel().getRecipeManager().byKey(chlorAlkali).orElseThrow();
		helper.assertTrue(liveRecipe instanceof ChemicalReactionRecipe,
			"chlor-alkali fixture must resolve the live chemical recipe");
		ChemicalReactionRecipe chlorRecipe = (ChemicalReactionRecipe) liveRecipe;
		helper.assertTrue(chlorRecipe.getSolutions().size() == 1
			&& brineId.equals(chlorRecipe.getSolutions().get(0).speciesId()),
			"chlor-alkali must carry its explicit brine constraint");
		int requiredWater = chlorRecipe.getFluidIngredients().get(0).getRequiredAmount();
		int requiredBrine = chlorRecipe.getSolutions().get(0).amount();
		helper.assertTrue(cell.getTank().waterInventoryMb() >= requiredWater
			&& cell.getTank().countSolution(brineId) >= requiredBrine,
			"fresh brine inventory must satisfy chlor-alkali water=" + requiredWater
				+ " and brine=" + requiredBrine + "; inventory water="
				+ cell.getTank().waterInventoryMb() + ", brine=" + cell.getTank().countSolution(brineId));
		helper.assertTrue(matchesCellRecipe(cell, chlorRecipe),
			"chlor-alkali must match before its first tick; water=" + cell.getTank().waterInventoryMb()
				+ ", brine=" + cell.getTank().countSolution(brineId));
		helper.assertTrue(chlorAlkali.equals(selectedCellRecipe(cell)),
			"the selector must prefer the matching solute-constrained recipe before its first tick; selected="
				+ selectedCellRecipe(cell));
		int waterBeforePreflight = cell.getTank().waterInventoryMb();
		int brineBeforePreflight = cell.getTank().countSolution(brineId);
		for (int i = 0; i < 4; i++) cell.getEnergy().receiveEnergy(2000, false);
		helper.setBlock(new BlockPos(8, 1, 8), AllBlocks.ELECTROLYZER.get().defaultBlockState());
		ElectrolyzerBlockEntity waterCell = (ElectrolyzerBlockEntity) helper.getBlockEntity(new BlockPos(8, 1, 8));
		waterCell.getTank().fill(new FluidStack(Fluids.WATER, 600), FluidAction.EXECUTE);
		for (int i = 0; i < 4; i++) waterCell.getEnergy().receiveEnergy(2000, false);
		waitFor(helper.startSequence().thenIdle(10).thenExecute(() -> {
			// completedTank is a preflight candidate. It runs before this 100-tick
			// batch completes and must not consume source state through shared NBT.
			helper.assertTrue(cell.getTank().waterInventoryMb() == waterBeforePreflight
				&& cell.getTank().countSolution(brineId) == brineBeforePreflight
				&& cell.getEnergy().getEnergyStored() == 8_000,
				"electrolyzer preflight must preserve source brine and FE; water="
					+ cell.getTank().waterInventoryMb() + ", brine=" + cell.getTank().countSolution(brineId)
					+ ", FE=" + cell.getEnergy().getEnergyStored());
		}), () -> hasFluidIn(cell.getTank(), AllFluids.HYDROGEN.get().getSource(), 100)
			&& hasFluidIn(waterCell.getTank(), AllFluids.HYDROGEN.get().getSource(), 200))
			.thenExecute(() -> {
				helper.assertTrue(chlorAlkali.equals(cell.getActiveRecipe()),
					"brine must select the solute-constrained chlor-alkali recipe; selected="
						+ cell.getActiveRecipe() + ", caustic=" + cell.getTank()
						.countSolution(new ResourceLocation(ChemicalAddon.MODID, "caustic_soda_solution")));
				helper.assertTrue(hasFluidIn(cell.getTank(), AllFluids.CHLORINE.get().getSource(), 100),
					"chlor-alkali vents chlorine; selected=" + cell.getActiveRecipe());
				helper.assertTrue(cell.getTank().countSolution(new ResourceLocation(ChemicalAddon.MODID, "caustic_soda_solution")) >= 200,
					"the declared 200 mB caustic output must remain available as native alkali inventory");
				helper.assertTrue(hasFluidIn(waterCell.getTank(), AllFluids.OXYGEN.get().getSource(), 100), "water electrolysis vents oxygen");
				helper.assertTrue(cell.getEnergy().getEnergyStored() == 4000, "the cell pays its FE per batch");
			}).thenSucceed();
	}

	/** Diagnose the cell's private complete-input gate without broadening its runtime API. */
	private static boolean matchesCellRecipe(ElectrolyzerBlockEntity cell, ChemicalReactionRecipe recipe) {
		try {
			var method = ElectrolyzerBlockEntity.class.getDeclaredMethod("matchesRecipe", ChemicalReactionRecipe.class);
			method.setAccessible(true);
			return (boolean) method.invoke(cell, recipe);
		} catch (ReflectiveOperationException ex) {
			throw new AssertionError("Cannot inspect the electrolyzer input gate", ex);
		}
	}

	@javax.annotation.Nullable
	private static ResourceLocation selectedCellRecipe(ElectrolyzerBlockEntity cell) {
		try {
			var method = ElectrolyzerBlockEntity.class.getDeclaredMethod("findRecipe");
			method.setAccessible(true);
			ChemicalReactionRecipe selected = (ChemicalReactionRecipe) method.invoke(cell);
			return selected == null ? null : selected.getId();
		} catch (ReflectiveOperationException ex) {
			throw new AssertionError("Cannot inspect the electrolyzer recipe selector", ex);
		}
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 40)
	public static void electrolyzerStallsWithoutPowerThenResumes(GameTestHelper helper) {
		helper.setBlock(new BlockPos(4, 1, 4), AllBlocks.ELECTROLYZER.get().defaultBlockState());
		ElectrolyzerBlockEntity cell = (ElectrolyzerBlockEntity) helper.getBlockEntity(new BlockPos(4, 1, 4));
		cell.getTank().fill(GameTestFixtures.declared(2, Map.of("NaCl", .2), 2200), FluidAction.EXECUTE);
		waitFor(helper.startSequence().thenIdle(TICKS * 2), () -> cell.getStatus() == ElectrolyzerBlockEntity.CellStatus.NO_POWER)
			.thenExecute(() -> {
				helper.assertTrue(cell.getTank().getTotalAmount() == 2200, "a powerless cell consumes nothing");
				helper.assertTrue(cell.getProcessStatus().equals("NO_POWER"), "the status port reads the cell");
				for (int i = 0; i < 4; i++) cell.getEnergy().receiveEnergy(2000, false);
			})
			.thenWaitUntil(() -> { if (!hasFluidIn(cell.getTank(), AllFluids.HYDROGEN.get().getSource(), 100)) throw new GameTestAssertException("Waiting"); })
			.thenExecute(() -> helper.assertTrue(cell.getStatus() != ElectrolyzerBlockEntity.CellStatus.NO_POWER, "a powered cell resumes"))
			.thenSucceed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 40)
	public static void electrolyzerRunsNetZeroBatchFromFullTank(GameTestHelper helper) {
		helper.setBlock(new BlockPos(4, 1, 4), AllBlocks.ELECTROLYZER.get().defaultBlockState());
		ElectrolyzerBlockEntity cell = (ElectrolyzerBlockEntity) helper.getBlockEntity(new BlockPos(4, 1, 4));
		cell.getTank().fill(new FluidStack(Fluids.WATER, ElectrolyzerBlockEntity.TANK_CAPACITY), FluidAction.EXECUTE);
		cell.getEnergy().setEnergyStored(6000);
		waitFor(helper.startSequence().thenIdle(TICKS), () -> hasFluidIn(cell.getTank(), AllFluids.HYDROGEN.get().getSource(), 200))
			.thenExecute(() -> {
				helper.assertTrue(cell.getTank().getTotalAmount() == ElectrolyzerBlockEntity.TANK_CAPACITY, "consume-then-produce transaction permits a full-tank net-zero batch");
				helper.assertTrue(hasFluidIn(cell.getTank(), AllFluids.OXYGEN.get().getSource(), 100), "the full-tank batch commits all outputs atomically");
			}).thenSucceed();
	}

	private static long joulesOf(HeatExchangerBlockEntity hx) {
		return Math.round((avgTempOf(hx.getHotTank()) + avgTempOf(hx.getColdTank())) * 1000 * HeatExchangerBlockEntity.SPECIFIC_HEAT);
	}

	private static double avgTempOf(ReactorTank tank) {
		long weighted = 0; int total = 0;
		for (FluidStack stack : tank.getFluids()) { weighted += (long) com.yu1745.chemicaladdon.fluid.Temperature.get(stack) * stack.getAmount(); total += stack.getAmount(); }
		return total > 0 ? (double) weighted / total : 0;
	}

	private static boolean hasFluidIn(ReactorTank tank, Fluid fluid, int min) {
		for (FluidStack stack : tank.getFluids()) if (!Mixture.isMixture(stack) && stack.getFluid() == fluid && stack.getAmount() >= min) return true;
		return false;
	}

	private static boolean hasFluid(ReactorControllerBlockEntity be, Fluid fluid, int minAmount) {
		return hasFluidIn(be.getTank(), fluid, minAmount);
	}
}
