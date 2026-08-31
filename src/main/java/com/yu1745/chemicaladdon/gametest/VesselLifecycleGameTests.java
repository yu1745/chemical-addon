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
import com.yu1745.chemicaladdon.fluid.Miscibility;
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
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.hasFluid;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.hasIon;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.reactor;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.waitFor;



@GameTestHolder(ChemicalAddon.MODID)
@PrefixGameTestTemplate(false)
public class VesselLifecycleGameTests {
	private static final int TICKS = 20;

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void reactorAssembles(GameTestHelper helper) {
		buildReactor(helper);
		ReactorControllerBlockEntity be = reactor(helper);
		helper.assertTrue(be.isAssembled(), "reactor should be assembled after valid structure");
		helper.assertTrue(be.getTank().getTankCapacity(0) >= 1000, "capacity should scale with height");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void reactorAssemblesLargeCube(GameTestHelper helper) {
		// 5x5x5 shell (n=5): interior 3x3x3, controller on the north wall middle (2,2,0)
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		BlockState controller = AllBlocks.REACTOR_CONTROLLER.get().defaultBlockState();
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				helper.setBlock(new BlockPos(x, 1, z), brick); // bottom
				helper.setBlock(new BlockPos(x, 5, z), brick); // top (sealed)
			}
		}
		for (int y = 2; y <= 4; y++) {
			for (int x = 0; x <= 4; x++) {
				for (int z = 0; z <= 4; z++) {
					boolean wall = x == 0 || x == 4 || z == 0 || z == 4;
					if (wall && !(y == 2 && x == 2 && z == 0)) {
						helper.setBlock(new BlockPos(x, y, z), brick);
					}
				}
			}
		}
		helper.setBlock(new BlockPos(2, 2, 0), controller);
		ReactorControllerBlockEntity be = (ReactorControllerBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 0));
		helper.assertTrue(be.tryAssemble().ok(), "5x5x5 cube should assemble");
		helper.assertTrue(be.getSize() == 5, "shell size should be 5 (got " + be.getSize() + ")");
		helper.assertTrue(be.getHeight() == 3, "interior height should be 3");
		helper.assertTrue(be.getTank().getTankCapacity(0) == 1000 * 27,
			"capacity should be 27 interior blocks * 1000 (got " + be.getTank().getTankCapacity(0) + ")");
		// every shell block belongs to the vessel_walls tag (brick + glass series)
		helper.assertTrue(helper.getBlockState(new BlockPos(2, 3, 4)).is(ChemicalAddon.VESSEL_WALLS),
			"shell block should be in the vessel_walls tag");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void reactorAssemblesWithGlassWall(GameTestHelper helper) {
		// Tinkers-style: the shell can be any block in the vessel_walls series —
		// build one face out of transparent chemical_glass, still assembles
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		BlockState glass = AllBlocks.CHEMICAL_GLASS.get().defaultBlockState();
		BlockState controller = AllBlocks.REACTOR_CONTROLLER.get().defaultBlockState();
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
				// south face (z=3) is glass, rest brick; controller on north wall middle
				helper.setBlock(new BlockPos(x, 2, z),
					x == 2 && z == 1 ? controller : z == 3 ? glass : brick);
			}
		}
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.AIR.defaultBlockState());
		ReactorControllerBlockEntity be = (ReactorControllerBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 1));
		helper.assertTrue(be.tryAssemble().ok(), "shell with a glass wall should assemble");
		helper.assertTrue(be.isAssembled(), "glass-walled reactor should be assembled");
		// glass bricks are proxied to the controller too (capability via master)
		BlockEntity glassBe = helper.getBlockEntity(new BlockPos(1, 2, 3));
		helper.assertTrue(glassBe != null, "glass block should have a proxy BE");
		LazyOptional<IFluidHandler> cap = glassBe.getCapability(ForgeCapabilities.FLUID_HANDLER);
		helper.assertTrue(cap.isPresent(), "glass wall must proxy FLUID_HANDLER to the controller");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void reactorAssemblesCuboidShell(GameTestHelper helper) {
		// 5x5x3 cuboid (W=5, H=3): interior 3x3x1 — Tinkers smeltery style, not a cube
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		BlockState controller = AllBlocks.REACTOR_CONTROLLER.get().defaultBlockState();
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				helper.setBlock(new BlockPos(x, 1, z), brick); // bottom
				helper.setBlock(new BlockPos(x, 3, z), brick); // top (sealed)
			}
		}
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				boolean wall = x == 0 || x == 4 || z == 0 || z == 4;
				if (wall && !(x == 2 && z == 0)) {
					helper.setBlock(new BlockPos(x, 2, z), brick);
				}
			}
		}
		helper.setBlock(new BlockPos(2, 2, 0), controller);
		ReactorControllerBlockEntity be = (ReactorControllerBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 0));
		helper.assertTrue(be.tryAssemble().ok(), "5x5x3 cuboid should assemble");
		helper.assertTrue(be.getSize() == 5, "footprint W should be 5 (got " + be.getSize() + ")");
		helper.assertTrue(be.getHeight() == 1, "interior height should be 1 (got " + be.getHeight() + ")");
		helper.assertTrue(be.getTank().getTankCapacity(0) == 1000 * 9,
			"capacity should be 3x3x1 interior * 1000 (got " + be.getTank().getTankCapacity(0) + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void reactorAssemblesWithControllerOnMiddleRing(GameTestHelper helper) {
		// user-style build: solid 5x5 floor, 4 open ring layers (3x3 hollow core),
		// no roof; the controller replaces a wall brick on the 2nd ring layer
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				helper.setBlock(new BlockPos(x, 0, z), brick); // solid floor
			}
		}
		for (int y = 1; y <= 4; y++) {
			for (int x = 0; x <= 4; x++) {
				for (int z = 0; z <= 4; z++) {
					boolean wall = x == 0 || x == 4 || z == 0 || z == 4;
					if (wall && !(y == 2 && x == 2 && z == 0)) {
						helper.setBlock(new BlockPos(x, y, z), brick);
					}
				}
			}
		}
		helper.setBlock(new BlockPos(2, 2, 0), AllBlocks.REACTOR_CONTROLLER.get().defaultBlockState());
		ReactorControllerBlockEntity be = (ReactorControllerBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 0));
		helper.assertTrue(be.tryAssemble().ok(), "controller on a middle ring layer should assemble");
		helper.assertTrue(be.isAssembled(), "should be assembled");
		helper.assertTrue(be.getSize() == 5 && be.getHeight() == 4,
			"5x5 floor + 4 rings should give size 5 height 4 (got " + be.getSize() + "x" + be.getHeight() + ")");
		helper.assertTrue(be.isOpen(), "no roof -> open-topped");
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
		helper.setBlock(new BlockPos(2, 2, 2), Fluids.WATER.defaultFluidState().createLegacyBlock());

		helper.startSequence()
			.thenIdle(3)
			.thenExecute(() -> {
				helper.assertTrue(!be.getItems().getStackInSlot(0).isEmpty()
					&& be.getItems().getStackInSlot(0).is(AllItems.SULFUR.get()),
					"thrown item should be absorbed into the vessel buffer");
				helper.assertTrue(hasFluid(be, Fluids.WATER, 900),
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
				Fluids.WATER.defaultFluidState().createLegacyBlock()))
			.thenIdle(3)
			.thenExecute(() -> assertAbsorbed(helper, be, spot1))
			.thenExecute(() -> be.getTank().drain(Integer.MAX_VALUE, FluidAction.EXECUTE))
			.thenExecute(() -> helper.setBlock(spot2,
				Fluids.WATER.defaultFluidState().createLegacyBlock()))
			.thenIdle(3)
			.thenExecute(() -> assertAbsorbed(helper, be, spot2))
			.thenExecute(() -> be.getTank().drain(Integer.MAX_VALUE, FluidAction.EXECUTE))
			.thenExecute(() -> helper.setBlock(spot3,
				Fluids.WATER.defaultFluidState().createLegacyBlock()))
			.thenIdle(3)
			.thenExecute(() -> assertAbsorbed(helper, be, spot3))
			.thenSucceed();
	}

	private static void assertAbsorbed(GameTestHelper helper, ReactorControllerBlockEntity be, BlockPos spot) {
		helper.assertTrue(hasFluid(be, Fluids.WATER, 900),
			"water poured at " + spot + " should be absorbed into the tank");
		helper.assertTrue(helper.getBlockState(spot).isAir(),
			"fluid block at " + spot + " should be consumed");
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void brokenVesselSpillsContents(GameTestHelper helper) {
		buildReactor(helper);
		ReactorControllerBlockEntity be = reactor(helper);
		be.getTank().fill(new FluidStack(Fluids.WATER, 2000), FluidAction.EXECUTE);
		be.getItems().setStackInSlot(0, new ItemStack(AllItems.SULFUR.get()));
		// break a wall brick -> breach: water becomes a real fluid block there, sulfur drops
		BlockPos breach = new BlockPos(1, 2, 1);
		helper.setBlock(breach, Blocks.AIR.defaultBlockState());
		helper.assertTrue(helper.getBlockState(breach).getFluidState().is(Fluids.WATER),
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
	public static void sealedVesselBreakVentsHeavyGasButRetainsLowLiquid(GameTestHelper helper) {
		ReactorControllerBlockEntity be = buildReactor5x5x5(helper);
		be.getTank().fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
		be.getTank().fill(new FluidStack(AllFluids.NITROGEN_DIOXIDE.get().getSource(), 1000), FluidAction.EXECUTE);
		helper.assertTrue(Miscibility.isGas(new FluidStack(AllFluids.NITROGEN_DIOXIDE.get().getSource(), 1)),
			"NO2 must be classified by its gas flag, independent of density");

		// A middle-ring breach is above this shallow liquid charge. The liquid
		// remains below the hole, while every gas phase escapes the lost enclosure.
		helper.setBlock(new BlockPos(1, 3, 0), Blocks.AIR.defaultBlockState());
		helper.assertFalse(be.isAssembled(), "a middle-ring breach must invalidate the sealed vessel");
		helper.assertTrue(hasFluid(be, Fluids.WATER, 1000), "liquid below the breach must remain contained");
		helper.assertFalse(hasFluid(be, AllFluids.NITROGEN_DIOXIDE.get().getSource(), 1),
			"heavy NO2 gas must vent when the sealed pressure boundary is broken");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void removingSealedVesselLidVentsHeavyGasButRetainsLiquid(GameTestHelper helper) {
		ReactorControllerBlockEntity be = buildReactor5x5x5(helper);
		be.getTank().fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
		be.getTank().fill(new FluidStack(AllFluids.NITROGEN_DIOXIDE.get().getSource(), 1000), FluidAction.EXECUTE);

		// Removing a ceiling brick re-adopts the same shell as a valid open vessel,
		// rather than invalidating it. That transition still loses containment.
		helper.setBlock(new BlockPos(0, 5, 0), Blocks.AIR.defaultBlockState());
		helper.assertTrue(be.isAssembled() && be.isOpen(),
			"removing the lid must keep the same vessel assembled but open");
		helper.assertTrue(hasFluid(be, Fluids.WATER, 1000),
			"opening the lid must retain the liquid inventory");
		helper.assertFalse(hasFluid(be, AllFluids.NITROGEN_DIOXIDE.get().getSource(), 1),
			"heavy NO2 gas must vent on the sealed-to-open transition");
		helper.succeed();
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
	public static void strayBrickDoesNotBreakReactor(GameTestHelper helper) {
		// A stray chemical brick touching — but not part of — an assembled reactor
		// shell must not tear the structure down when placed and then removed.
		// Regression: previously onRemove scanned a radius and invalidated every
		// nearby controller, spilling a vessel the brick never belonged to.
		buildReactor(helper);
		ReactorControllerBlockEntity be = reactor(helper);
		be.getTank().fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
		helper.assertTrue(be.isAssembled() && be.getTank().getTotalAmount() == 1000,
			"baseline: assembled reactor holding 1000 mB");

		// stray brick at x=0: outside the 3x3x3 shell (x=1..3) but adjacent to the wall
		BlockPos stray = new BlockPos(0, 2, 2);
		helper.setBlock(stray, AllBlocks.CHEMICAL_BRICK.get().defaultBlockState());
		helper.assertTrue(be.isAssembled(), "placing a stray brick must not disassemble the reactor");
		helper.assertTrue(be.getTank().getTotalAmount() == 1000, "placing a stray brick must not spill contents");

		// breaking the stray brick must be a complete no-op for the vessel
		helper.setBlock(stray, Blocks.AIR.defaultBlockState());
		helper.assertTrue(be.isAssembled(), "breaking a stray brick must not disassemble the reactor");
		helper.assertTrue(be.getTank().getTotalAmount() == 1000,
			"breaking a stray brick must not spill any fluid");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void reactorAutoExtendsWhenGrownTaller(GameTestHelper helper) {
		// §A: placing vessel-wall blocks next to an ASSEMBLED vessel re-validates and
		// grows it (strictly larger only, contents preserved). Open 3x3x3 -> sealed 3x3x5.
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		BlockState controller = AllBlocks.REACTOR_CONTROLLER.get().defaultBlockState();
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, 1, z), brick); // floor
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
		ReactorControllerBlockEntity be = (ReactorControllerBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 1));
		helper.assertTrue(be.tryAssemble().ok() && be.isOpen(), "open 3x3x3 should assemble");
		be.getTank().fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);

		// grow taller: two more ring layers + a sealed ceiling — each placement may
		// trigger an extension, the last ceiling brick seals the top
		for (int y = 3; y <= 4; y++) {
			for (int x = 1; x <= 3; x++) {
				for (int z = 1; z <= 3; z++) {
					if (x == 2 && z == 2) {
						continue; // interior column
					}
					helper.setBlock(new BlockPos(x, y, z), brick);
				}
			}
		}
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, 5, z), brick); // ceiling
			}
		}
		helper.assertTrue(be.isAssembled(), "placing bricks around an assembled vessel should keep it assembled");
		helper.assertTrue(be.getSize() == 3 && be.getHeight() == 3,
			"vessel should have grown to 3 rings (got " + be.getSize() + "x" + be.getHeight() + ")");
		helper.assertTrue(be.getTank().getTankCapacity(0) == 3000,
			"capacity should scale with the grown height (got " + be.getTank().getTankCapacity(0) + ")");
		helper.assertTrue(be.getTank().getTotalAmount() == 1000, "contents must survive the extension");
		helper.assertTrue(!be.isOpen(), "sealing the top must flip the open flag");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void reactorKeepsFluidBelowBreach(GameTestHelper helper) {
		// §B: a mid-wall breach spills only the fluid ABOVE the breach; the portion
		// below stays in the tank (recovered on rebuild). §C: size/height/inward are
		// retained as lastGeometry so the residual surface keeps rendering.
		ReactorControllerBlockEntity be = buildReactor5x5x5(helper);
		be.getTank().fill(new FluidStack(Fluids.WATER, 27000), FluidAction.EXECUTE);
		helper.assertTrue(be.getTank().getTotalAmount() == 27000, "5x5x5 should hold 27 buckets");

		// break a wall brick on ring 1 (y=3, controller on ring 0): interior ring 1 of 3
		helper.setBlock(new BlockPos(0, 3, 2), Blocks.AIR.defaultBlockState());
		helper.assertFalse(be.isAssembled(), "breaking a wall brick should de-assemble");
		helper.assertTrue(be.getTank().getTotalAmount() == 9000,
			"fluid below the breach must stay in the tank (got " + be.getTank().getTotalAmount() + ")");
		helper.assertTrue(be.getPendingSpillAmount() == 17000,
			"fluid above the breach must be queued (got " + be.getPendingSpillAmount() + ")");
		// §C: last geometry retained for rendering the residual in the remaining shell
		helper.assertTrue(be.getSize() == 5 && be.getHeight() == 3 && be.getInward() != null,
			"last geometry must be retained while de-assembled");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void reactorBottomBreachSpillsAll(GameTestHelper helper) {
		// §B: a floor breach has no fluid below it -> everything pours out
		ReactorControllerBlockEntity be = buildReactor5x5x5(helper);
		be.getTank().fill(new FluidStack(Fluids.WATER, 27000), FluidAction.EXECUTE);
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.AIR.defaultBlockState()); // floor brick under the core
		helper.assertFalse(be.isAssembled(), "floor brick is structural");
		helper.assertTrue(be.getTank().getTotalAmount() == 0, "bottom breach must drain the tank");
		helper.assertTrue(be.getPendingSpillAmount() == 26000,
			"all 27 buckets queued (one source already placed) (got " + be.getPendingSpillAmount() + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void reactorCeilingBrickOpensVessel(GameTestHelper helper) {
		// Removing a CEILING brick must NOT lower or destroy the vessel — the height
		// of a vessel is its ring count, not its lid. The lid layer is discarded
		// (its bricks become stray) and the vessel simply opens up: same 5x5x5,
		// same capacity, no overflow. This is the mirror of sealing.
		ReactorControllerBlockEntity be = buildReactor5x5x5(helper);
		be.getTank().fill(new FluidStack(Fluids.WATER, 27000), FluidAction.EXECUTE);
		helper.setBlock(new BlockPos(0, 5, 0), Blocks.AIR.defaultBlockState()); // ceiling corner
		helper.assertTrue(be.isAssembled(), "removing a ceiling brick must keep the vessel assembled");
		helper.assertTrue(be.getSize() == 5 && be.getHeight() == 3,
			"the height must stay (got " + be.getSize() + "x" + be.getHeight() + ")");
		helper.assertTrue(be.isOpen(), "the vessel should become open-topped");
		helper.assertTrue(be.getTank().getTankCapacity(0) == 27000,
			"capacity must stay (got " + be.getTank().getTankCapacity(0) + ")");
		helper.assertTrue(be.getTank().getTotalAmount() == 27000, "contents must be untouched");
		helper.assertTrue(be.getPendingSpillAmount() == 0, "nothing should spill");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void reactorSmallestCeilingOpensVessel(GameTestHelper helper) {
		// even the smallest vessel (3x3x3, a single ring) survives a ceiling brick
		// removal: it becomes open-topped at the SAME height instead of de-assembling
		buildReactor(helper);
		ReactorControllerBlockEntity be = reactor(helper);
		be.getTank().fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
		helper.setBlock(new BlockPos(1, 3, 1), Blocks.AIR.defaultBlockState()); // ceiling brick
		helper.assertTrue(be.isAssembled(), "the smallest vessel must stay assembled");
		helper.assertTrue(be.isOpen() && be.getHeight() == 1, "it should simply open up");
		helper.assertTrue(be.getTank().getTotalAmount() == 1000, "contents must be kept");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void reactorTopRingShrinkLowersVessel(GameTestHelper helper) {
		// removing a TOP RING brick lowers the vessel one ring: open 3x3x5 (3 rings)
		// -> open 3x3x4 (2 rings), contents overflow down to the new capacity
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		BlockState controller = AllBlocks.REACTOR_CONTROLLER.get().defaultBlockState();
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, 1, z), brick);
			}
		}
		for (int y = 2; y <= 4; y++) {
			for (int x = 1; x <= 3; x++) {
				for (int z = 1; z <= 3; z++) {
					if (x == 2 && z == 2) {
						continue; // interior column
					}
					helper.setBlock(new BlockPos(x, y, z), x == 2 && z == 1 && y == 2 ? controller : brick);
				}
			}
		}
		ReactorControllerBlockEntity be = (ReactorControllerBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 1));
		helper.assertTrue(be.tryAssemble().ok() && be.isOpen() && be.getHeight() == 3,
			"open 3x3x5 should assemble at 3 rings");
		be.getTank().fill(new FluidStack(Fluids.WATER, 3000), FluidAction.EXECUTE);

		helper.setBlock(new BlockPos(1, 4, 1), Blocks.AIR.defaultBlockState()); // top ring (y=4) wall brick
		helper.assertTrue(be.isAssembled(), "removing a top ring brick must keep the vessel assembled");
		helper.assertTrue(be.getHeight() == 2,
			"vessel should lower one ring (got " + be.getHeight() + ")");
		helper.assertTrue(be.isOpen(), "still open-topped");
		helper.assertTrue(be.getTank().getTankCapacity(0) == 2000,
			"capacity should shrink to 2000 (got " + be.getTank().getTankCapacity(0) + ")");
		helper.assertTrue(be.getTank().getTotalAmount() == 2000,
			"over-capacity after lowering must overflow down to the new capacity");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void reactorSurfaceMeasuredFromInteriorFloor(GameTestHelper helper) {
		// The controller may sit on ANY ring layer; the interior floor is ringLayer
		// blocks BELOW it (here: controller on ring 1 of an open 3x3x5, floor at
		// world y=2, controller at y=3). Surface math must measure from that floor
		// — measuring from the controller's own layer floats the surface one block
		// too high and the decant hose would track above the real liquid.
		ReactorControllerBlockEntity be = buildReactor3x3x5HighController(helper);
		helper.assertTrue(be.isOpen() && be.getHeight() == 3,
			"open 3x3x5 should assemble at 3 rings");
		helper.assertTrue(be.getInteriorBottomRelY() == -1,
			"interior bottom is one ring below the controller (got " + be.getInteriorBottomRelY() + ")");

		// empty: the surface rests on the interior floor — one ring BELOW the
		// controller (worldPosition is absolute in GameTests, so assert the
		// controller-relative offset)
		float emptyRel = be.getLiquidSurfaceY(1.0f) - be.getBlockPos().getY();
		helper.assertTrue(emptyRel == -1.0f,
			"empty surface rests on the interior floor, one ring below the controller (got rel "
				+ emptyRel + ")");

		// half full (1500/3000): level = fill × height = 1.5 blocks of ABSOLUTE
		// height above the floor → surface at floor(-1) + 1.5 = controller + 0.5
		be.getTank().fill(new FluidStack(Fluids.WATER, 1500), FluidAction.EXECUTE);
		helper.assertTrue(be.getRenderedLevel(1.0f) == 1.5f,
			"rendered level is the absolute height in blocks, not a fraction (got "
				+ be.getRenderedLevel(1.0f) + ")");
		float halfRel = be.getLiquidSurfaceY(1.0f) - be.getBlockPos().getY();
		helper.assertTrue(halfRel == 0.5f,
			"half-full surface = floor(-1) + 1.5 = controller + 0.5 (got rel " + halfRel + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void openVesselAbsorbsFluidBelowControllerRing(GameTestHelper helper) {
		// Same open 3x3x5 with the controller on ring 1: the interior layer BELOW
		// the controller (y=2) is inside the vessel — a source poured there must be
		// absorbed even though it sits under the controller's own layer. The old
		// polling started at the controller's layer and never reached down there.
		ReactorControllerBlockEntity be = buildReactor3x3x5HighController(helper);
		helper.assertTrue(be.isOpen(), "open vessel should assemble");

		BlockPos belowController = new BlockPos(2, 2, 2); // interior layer UNDER the controller
		BlockPos aboveController = new BlockPos(2, 4, 2); // interior layer above the controller
		BlockPos rim = new BlockPos(2, 5, 2);             // the open rim layer
		helper.startSequence()
			.thenExecute(() -> helper.setBlock(belowController,
				Fluids.WATER.defaultFluidState().createLegacyBlock()))
			.thenIdle(3)
			.thenExecute(() -> assertAbsorbed(helper, be, belowController))
			.thenExecute(() -> be.getTank().drain(Integer.MAX_VALUE, FluidAction.EXECUTE))
			.thenExecute(() -> helper.setBlock(aboveController,
				Fluids.WATER.defaultFluidState().createLegacyBlock()))
			.thenIdle(3)
			.thenExecute(() -> assertAbsorbed(helper, be, aboveController))
			.thenExecute(() -> be.getTank().drain(Integer.MAX_VALUE, FluidAction.EXECUTE))
			.thenExecute(() -> helper.setBlock(rim,
				Fluids.WATER.defaultFluidState().createLegacyBlock()))
			.thenIdle(3)
			.thenExecute(() -> assertAbsorbed(helper, be, rim))
			.thenSucceed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void decantHoseFindsVesselWithHighController(GameTestHelper helper) {
		// Regression (live-caught): bindBricks' y-range hard-coded the controller on
		// the BOTTOM ring (floor at controller-relative -1). With the controller one
		// ring up, the floor bricks (-2) were left UNBOUND — the decant hose scan
		// (findReactorBelow) drops down the open interior column, falls through the
		// unbound floor and never finds the vessel, so the hose never lowers to the
		// liquid surface. The floor must be bound on every ring layer.
		ReactorControllerBlockEntity be = buildReactor3x3x5HighController(helper);
		helper.assertTrue(be.isOpen(), "open vessel should assemble");

		// the floor brick under the interior column must point at the controller
		helper.assertTrue(helper.getBlockEntity(new BlockPos(2, 1, 2)) instanceof ChemicalBrickBlockEntity floor
			&& be.getBlockPos().equals(floor.getMasterPos()),
			"the interior floor brick must be bound to the controller");

		// and a hose scanning from directly above the open top finds the vessel
		// through the interior column + bound floor (returns the SAME controller).
		// NB: absolutePos — the static scan runs against the raw level (GameTest
		// rel coords are structure-local).
		BlockPos hosePos = helper.absolutePos(new BlockPos(2, 5, 2)); // one above the open rim
		ReactorControllerBlockEntity found = DecantHoseBlockEntity.findReactorBelow(helper.getLevel(), hosePos);
		helper.assertTrue(found == be,
			"the decant hose scan must find the vessel below a high-mounted controller");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void reactorControllerBreakSpillsAll(GameTestHelper helper) {
		// §B hard rule: breaking the controller destroys the NBT that would hold a
		// retained remainder -> fall back to a full physical spill (no silent loss)
		ReactorControllerBlockEntity be = buildReactor5x5x5(helper);
		be.getTank().fill(new FluidStack(Fluids.WATER, 27000), FluidAction.EXECUTE);
		helper.setBlock(new BlockPos(2, 2, 0), Blocks.AIR.defaultBlockState()); // the controller itself
		helper.assertTrue(be.getTank().getTotalAmount() == 0,
			"controller break must spill everything (retained remainder would be lost)");
		helper.assertTrue(be.getPendingSpillAmount() == 26000,
			"full spill queued (got " + be.getPendingSpillAmount() + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void reactorSealingDoesNotShrinkHeight(GameTestHelper helper) {
		// Regression (build flicker): sealing a built-up open vessel must NOT
		// momentarily match a shorter open shell while the ceiling is half-finished
		// — a placement may only extend or keep the current height, never shrink it.
		// The height must stay at 3 rings the whole way to the sealed 3x3x5.
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		BlockState controller = AllBlocks.REACTOR_CONTROLLER.get().defaultBlockState();
		// open 3x3x5: floor y=1, rings y=2..4, open top y=5
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, 1, z), brick);
			}
		}
		for (int y = 2; y <= 4; y++) {
			for (int x = 1; x <= 3; x++) {
				for (int z = 1; z <= 3; z++) {
					if (x == 2 && z == 2) {
						continue; // interior column
					}
					helper.setBlock(new BlockPos(x, y, z), x == 2 && z == 1 && y == 2 ? controller : brick);
				}
			}
		}
		ReactorControllerBlockEntity be = (ReactorControllerBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 1));
		helper.assertTrue(be.tryAssemble().ok() && be.isOpen(), "open 3x3x5 should assemble");
		helper.assertTrue(be.getHeight() == 3, "open 3x3x5 should be 3 rings tall");

		// seal the ceiling brick by brick — the height must stay at 3 throughout
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, 5, z), brick);
				helper.assertTrue(be.getHeight() == 3,
					"placing a ceiling brick must not shrink the height (got " + be.getHeight() + ")");
			}
		}
		helper.assertTrue(be.isAssembled() && !be.isOpen(), "fully sealed 3x3x5");
		helper.assertTrue(be.getTank().getTankCapacity(0) == 3000, "capacity should stay 3000");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void reactorRebuildSmallerOverflowsExcess(GameTestHelper helper) {
		// §D: rebuilding smaller than the retained contents overflows the excess as
		// physical fluid — the vessel never sits wedged in an over-capacity state.
		// Break a mid-wall brick (keeps the 9000 mB below the breach), reshape the
		// shell to a 3x3x3 and let the last brick re-assemble it: 9000 > 1000
		// capacity -> 8000 mB overflow.
		ReactorControllerBlockEntity be = buildReactor5x5x5(helper);
		be.getTank().fill(new FluidStack(Fluids.WATER, 27000), FluidAction.EXECUTE);
		// mid-wall breach (ring 1 of 3): keeps the fluid below the breach (9000)
		helper.setBlock(new BlockPos(0, 3, 2), Blocks.AIR.defaultBlockState());
		helper.assertFalse(be.isAssembled(), "mid-wall breach should de-assemble");
		helper.assertTrue(be.getTank().getTotalAmount() == 9000,
			"9000 mB retained below the breach (got " + be.getTank().getTotalAmount() + ")");

		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		// reshape the shell to a 3x3x3 (controller stays at (2,2,0) on the north wall):
		// clear everything outside x=1..3, y=1..3, z=0..2 (unbound by the breach, so
		// clearing them cannot re-trigger invalidation)
		for (int x = 0; x <= 4; x++) {
			for (int y = 1; y <= 5; y++) {
				for (int z = 0; z <= 4; z++) {
					if (x >= 1 && x <= 3 && y >= 1 && y <= 3 && z >= 0 && z <= 2) {
						continue;
					}
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
				}
			}
		}
		// fill in the 3x3x3 walls the 5x5x5 left hollow: ring y=2 then ceiling y=3 —
		// the last placement completes the shell and re-assembles
		helper.setBlock(new BlockPos(1, 2, 1), brick);
		helper.setBlock(new BlockPos(3, 2, 1), brick);
		helper.setBlock(new BlockPos(1, 2, 2), brick);
		helper.setBlock(new BlockPos(2, 2, 2), brick);
		helper.setBlock(new BlockPos(3, 2, 2), brick);
		for (int x = 1; x <= 3; x++) {
			for (int z = 0; z <= 2; z++) {
				helper.setBlock(new BlockPos(x, 3, z), brick); // ceiling (completes the shell)
			}
		}
		helper.assertTrue(be.isAssembled(), "the smaller shell should re-assemble automatically");
		helper.assertTrue(be.getTank().getTankCapacity(0) == 1000,
			"new capacity should be 1000 (got " + be.getTank().getTankCapacity(0) + ")");
		helper.assertTrue(be.getTank().getTotalAmount() == 1000,
			"tank must hold exactly the new capacity, not more (got " + be.getTank().getTotalAmount() + ")");
		int pending = be.getPendingSpillAmount();
		helper.assertTrue(pending > 0 && pending <= 8000,
			"the overflow (8 buckets) must be queued as physical fluid (got " + pending + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void reactorAssemblesOverFluid(GameTestHelper helper) {
		// Build a 3x3x3 shell leaving the controller slot as a brick, pre-fill the
		// interior with water, THEN swap the controller in (closing the shell last).
		// Regression: the interior check used isAir(), so a fluid inside rejected
		// assembly with INTERIOR_BLOCKED; the water must be absorbed into the tank.
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		// floor + ceiling
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, 1, z), brick);
				helper.setBlock(new BlockPos(x, 3, z), brick);
			}
		}
		// middle ring walls (controller slot at (2,2,1) kept as brick for now)
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				if (x == 2 && z == 2) continue; // interior
				helper.setBlock(new BlockPos(x, 2, z), brick);
			}
		}
		// pre-fill the interior with water
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.WATER.defaultBlockState());
		helper.assertTrue(!helper.getBlockState(new BlockPos(2, 2, 2)).isAir(),
			"baseline: water is sitting in the interior");

		// close the shell last by swapping the controller in
		helper.setBlock(new BlockPos(2, 2, 1), AllBlocks.REACTOR_CONTROLLER.get().defaultBlockState());
		ReactorControllerBlockEntity be = (ReactorControllerBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 1));
		helper.assertTrue(be.tryAssemble().ok(), "reactor must assemble even with fluid in the interior");
		helper.assertTrue(be.isAssembled(), "should be assembled");
		// the pre-existing water must have been absorbed into the tank
		helper.assertTrue(helper.getBlockState(new BlockPos(2, 2, 2)).isAir(),
			"interior water must be cleared on assembly");
		helper.assertTrue(be.getTank().getTotalAmount() == 1000,
			"interior water (1 source = 1000 mB) must be absorbed into the tank (got total="
				+ be.getTank().getTotalAmount() + ")");
		helper.succeed();
	}
}
