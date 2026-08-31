package com.yu1745.chemicaladdon.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.composition.Solution;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.reactor.ReactorControllerBlockEntity;
import com.yu1745.chemicaladdon.reactor.ReactorTank;
import com.yu1745.chemicaladdon.recipe.ChemicalReactionRecipe;
import com.yu1745.chemicaladdon.recipe.AllRecipeTypes;
import com.yu1745.chemicaladdon.registry.AllBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.BooleanSupplier;

/** Small package-level fixtures shared by split GameTest suites. */
final class GameTestFixtures {
	private GameTestFixtures() {
	}

	static ReactorControllerBlockEntity reactor(GameTestHelper helper) {
		return (ReactorControllerBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 1));
	}

	static ReactorControllerBlockEntity buildSmallReactor(GameTestHelper helper) {
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		BlockState controller = AllBlocks.REACTOR_CONTROLLER.get().defaultBlockState();
		for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) {
			helper.setBlock(new BlockPos(x, 1, z), brick);
			helper.setBlock(new BlockPos(x, 3, z), brick);
		}
		for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) {
			if (x == 2 && z == 2) continue;
			helper.setBlock(new BlockPos(x, 2, z), x == 2 && z == 1 ? controller : brick);
		}
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR.defaultBlockState());
		ReactorControllerBlockEntity be = reactor(helper);
		helper.assertTrue(be.tryAssemble().ok(), "structure should validate");
		return be;
	}

	static void buildReactor(GameTestHelper helper) {
		buildSmallReactor(helper);
	}

	static JsonArray jsonArray(String value) {
		JsonArray array = new JsonArray();
		array.add(value);
		return array;
	}

	static JsonArray solutionArray(String species, int amount, double min, double max) {
		JsonArray array = new JsonArray();
		JsonObject solution = new JsonObject();
		solution.addProperty("species", species);
		solution.addProperty("amount", amount);
		solution.addProperty("minConcentration", min);
		solution.addProperty("maxConcentration", max);
		array.add(solution);
		return array;
	}

	static JsonObject range(double min, double max) {
		JsonObject range = new JsonObject();
		range.addProperty("min", min);
		range.addProperty("max", max);
		return range;
	}

	static ChemicalReactionRecipe recipeFromA3Json(JsonObject json) {
		json.add("ingredients", new JsonArray());
		json.add("results", new JsonArray());
		return (ChemicalReactionRecipe) AllRecipeTypes.CHEMICAL_REACTION.getSerializer()
			.fromJson(new ResourceLocation(ChemicalAddon.MODID, "gametest/a3"), json);
	}

	static ReactorControllerBlockEntity buildReactor5x5x5(GameTestHelper helper) {
		return buildReactor5x5x5(helper, 0, 0, false);
	}

	static ReactorControllerBlockEntity buildReactor5x5x5(GameTestHelper helper, int x0, int z0,
		boolean stirringHead) {
		return buildReactor5x5x5WithHeadAt(helper, x0, z0,
			stirringHead ? x0 + 2 : -1, stirringHead ? 5 : -1, stirringHead ? z0 + 2 : -1);
	}

	static ReactorControllerBlockEntity buildReactor5x5x5WithGasAt(GameTestHelper helper, int x0, int z0,
		int gx, int gy, int gz, Direction facing) {
		BlockState distributor = AllBlocks.GAS_DISTRIBUTOR.get().defaultBlockState()
			.setValue(BlockStateProperties.FACING, facing);
		return build5x5x5WithPart(helper, x0, z0, gx, gy, gz, distributor, "5x5x5 B2 reactor should assemble");
	}

	static ReactorControllerBlockEntity buildReactor5x5x5WithTrayAt(GameTestHelper helper, int x0, int z0,
		int tx, int ty, int tz, Direction facing) {
		BlockState tray = AllBlocks.CATALYST_TRAY.get().defaultBlockState()
			.setValue(BlockStateProperties.FACING, facing);
		return build5x5x5WithPart(helper, x0, z0, tx, ty, tz, tray, "5x5x5 B3 reactor should assemble");
	}

	static ReactorControllerBlockEntity buildReactor5x5x5WithInletAt(GameTestHelper helper, int x0, int z0,
		int ix, int iy, int iz, Direction facing) {
		BlockState inlet = AllBlocks.METERING_INLET.get().defaultBlockState()
			.setValue(BlockStateProperties.FACING, facing);
		return build5x5x5WithPart(helper, x0, z0, ix, iy, iz, inlet, "5x5x5 B4 reactor should assemble");
	}

	static ReactorControllerBlockEntity buildReactor5x5x5WithStatusPortAt(GameTestHelper helper, int x0, int z0,
		int px, int py, int pz) {
		return build5x5x5WithPart(helper, x0, z0, px, py, pz, AllBlocks.STATUS_PORT.get().defaultBlockState(),
			"5x5x5 reactor with a status port wall should assemble");
	}

	static ReactorControllerBlockEntity buildReactor5x5x5WithHeadAt(GameTestHelper helper, int x0, int z0,
		int hx, int hy, int hz) {
		return build5x5x5WithPart(helper, x0, z0, hx, hy, hz, AllBlocks.STIRRING_HEAD.get().defaultBlockState(),
			"5x5x5 reactor should assemble");
	}

	static ReactorControllerBlockEntity buildReactor5x5x5WithTwoTrays(GameTestHelper helper) {
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		BlockState tray = AllBlocks.CATALYST_TRAY.get().defaultBlockState()
			.setValue(BlockStateProperties.FACING, Direction.SOUTH);
		for (int x = 0; x <= 4; x++) for (int y = 1; y <= 5; y++) for (int z = 0; z <= 4; z++) {
			if (y != 1 && y != 5 && x != 0 && x != 4 && z != 0 && z != 4) continue;
			if (x == 2 && y == 2 && z == 0) continue;
			helper.setBlock(new BlockPos(x, y, z), y == 3 && z == 0 && (x == 1 || x == 3) ? tray : brick);
		}
		helper.setBlock(new BlockPos(2, 2, 0), AllBlocks.REACTOR_CONTROLLER.get().defaultBlockState());
		ReactorControllerBlockEntity be = (ReactorControllerBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 0));
		helper.assertTrue(be.tryAssemble().ok(), "5x5x5 two-tray reactor should assemble");
		return be;
	}

	static ReactorControllerBlockEntity buildReactor3x3x5HighController(GameTestHelper helper) {
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		BlockState controller = AllBlocks.REACTOR_CONTROLLER.get().defaultBlockState();
		for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) helper.setBlock(new BlockPos(x, 1, z), brick);
		for (int y = 2; y <= 4; y++) for (int x = 1; x <= 3; x++) for (int z = 1; z <= 3; z++) {
			if (x == 2 && z == 2) continue;
			helper.setBlock(new BlockPos(x, y, z), x == 2 && z == 1 && y == 3 ? controller : brick);
		}
		ReactorControllerBlockEntity be = (ReactorControllerBlockEntity) helper.getBlockEntity(new BlockPos(2, 3, 1));
		helper.assertTrue(be.tryAssemble().ok(), "open 3x3x5 with the controller on ring 1 should assemble");
		return be;
	}

	private static ReactorControllerBlockEntity build5x5x5WithPart(GameTestHelper helper, int x0, int z0,
		int partX, int partY, int partZ, BlockState part, String message) {
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		for (int x = 0; x <= 4; x++) for (int y = 1; y <= 5; y++) for (int z = 0; z <= 4; z++) {
			if (y != 1 && y != 5 && x != 0 && x != 4 && z != 0 && z != 4) continue;
			if (x == 2 && y == 2 && z == 0) continue;
			helper.setBlock(new BlockPos(x0 + x, y, z0 + z), x0 + x == partX && y == partY && z0 + z == partZ ? part : brick);
		}
		helper.setBlock(new BlockPos(x0 + 2, 2, z0), AllBlocks.REACTOR_CONTROLLER.get().defaultBlockState());
		ReactorControllerBlockEntity be = (ReactorControllerBlockEntity) helper.getBlockEntity(new BlockPos(x0 + 2, 2, z0));
		helper.assertTrue(be.tryAssemble().ok(), message);
		return be;
	}

	static GameTestSequence waitFor(GameTestSequence sequence, BooleanSupplier condition) {
		return sequence.thenWaitUntil(() -> {
			if (!condition.getAsBoolean()) throw new GameTestAssertException("Waiting");
		});
	}

	static boolean hasFluid(ReactorControllerBlockEntity reactor, Fluid fluid, int minAmount) {
		return hasFluid(reactor.getTank(), fluid, minAmount);
	}

	static boolean hasFluid(ReactorTank tank, Fluid fluid, int minAmount) {
		return tank.getFluids().stream().anyMatch(stack -> stack.getFluid() == fluid && stack.getAmount() >= minAmount);
	}

	static int speciesAmount(FluidStack stack, String species) {
		ResourceLocation id = "water".equals(species) ? Solution.WATER : new ResourceLocation(ChemicalAddon.MODID, species);
		return Mixture.isMixture(stack) ? Mixture.deriveAmounts(stack).getOrDefault(id, 0)
			: id.equals(ForgeRegistries.FLUIDS.getKey(stack.getFluid())) ? stack.getAmount() : 0;
	}

	static boolean hasSpecies(ReactorTank tank, String species, int minAmount) {
		return tank.getFluids().stream().mapToInt(stack -> speciesAmount(stack, species)).sum() >= minAmount;
	}

	static int ionAmount(FluidStack stack, String ionId) {
		return Mixture.isMixture(stack) ? Mixture.deriveIonAmounts(stack).getOrDefault(ionId, 0) : 0;
	}

	static boolean hasIon(ReactorTank tank, String ionId, int minAmount) {
		return tank.getFluids().stream().mapToInt(stack -> ionAmount(stack, ionId)).sum() >= minAmount;
	}

	static int strongSignalOf(GameTestHelper helper, BlockPos pos) {
		BlockPos absolute = helper.absolutePos(pos);
		return helper.getLevel().getBlockState(absolute).getSignal(helper.getLevel(), absolute, Direction.NORTH);
	}

	static int comparatorSignalOf(GameTestHelper helper, BlockPos pos) {
		BlockPos absolute = helper.absolutePos(pos);
		return helper.getLevel().getBlockState(absolute).getAnalogOutputSignal(helper.getLevel(), absolute);
	}
}
