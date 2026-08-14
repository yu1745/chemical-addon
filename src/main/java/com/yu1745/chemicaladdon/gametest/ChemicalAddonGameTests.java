package com.yu1745.chemicaladdon.gametest;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.composition.Solution;
import com.yu1745.chemicaladdon.composition.Species;
import com.yu1745.chemicaladdon.composition.SpeciesManager;
import com.yu1745.chemicaladdon.fluid.FluidColors;
import com.yu1745.chemicaladdon.fluid.IonColors;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.fluid.Temperature;
import com.yu1745.chemicaladdon.reactor.ChemicalBrickBlock;
import com.yu1745.chemicaladdon.reactor.ChemicalBrickBlockEntity;
import com.yu1745.chemicaladdon.reactor.DecantHoseBlockEntity;
import com.yu1745.chemicaladdon.reactor.FilterPressBlockEntity;
import com.yu1745.chemicaladdon.reactor.PressureGaugeBlockEntity;
import com.yu1745.chemicaladdon.reactor.PressureGaugePanelBlockEntity;
import com.yu1745.chemicaladdon.reactor.ReactorControllerBlock;
import com.yu1745.chemicaladdon.reactor.ReactorControllerBlockEntity;
import com.yu1745.chemicaladdon.reactor.ReactorTank;
import com.yu1745.chemicaladdon.reactor.RulesEngine;
import com.yu1745.chemicaladdon.reactor.SpillLogic;
import com.yu1745.chemicaladdon.reactor.ThermometerBlockEntity;
import com.yu1745.chemicaladdon.reactor.ThermometerPanelBlockEntity;
import com.yu1745.chemicaladdon.registry.AllBlocks;
import com.yu1745.chemicaladdon.registry.AllContainers;
import com.yu1745.chemicaladdon.registry.AllFluids;
import com.yu1745.chemicaladdon.registry.AllItems;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.registries.ForgeRegistries;
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
		ReactorControllerBlockEntity be = buildReactor5x5x5(helper);
		be.getTank().fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
		be.getTank().fill(new FluidStack(AllFluids.THERMAL_OIL.get().getSource(), 1000), FluidAction.EXECUTE);
		be.getTank().fill(new FluidStack(AllFluids.SULFUR_DIOXIDE.get().getSource(), 1000), FluidAction.EXECUTE);
		helper.startSequence()
			.thenIdle(TICKS * 25) // so2_absorption: 200 ticks + slack
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
	public static void collapseDoesNotChurnMixtureRatio(GameTestHelper helper) {
		// regression: collapseIfNeeded on a multi-phase tank used to rebuild the
		// mixture every tick (derive amounts -> Mixture.create GCD-reduce), churning
		// its ratio tag whenever the total isn't divisible by the ratio sum — which
		// broke Create's isFluidEqual flow identity and stalled the pump. A settled
		// phase must be left verbatim.
		ReactorTank tank = new ReactorTank(10000, () -> {});
		ResourceLocation water = Solution.WATER;
		// ratio {5:2:1} (sum 8) with a total (1601) that 8 does NOT divide -> any
		// rebuild would re-derive + GCD-reduce into a different tag
		FluidStack mix = Mixture.create(Map.of(water, 1000), Map.of("H+1", 400, "SO4-2", 200), 1601);
		tank.fill(mix, FluidAction.EXECUTE);
		tank.fill(new FluidStack(AllFluids.THERMAL_OIL.get().getSource(), 500), FluidAction.EXECUTE);
		tank.collapseIfNeeded();

		net.minecraft.nbt.CompoundTag before = tank.getFluids().get(0).getOrCreateTag().copy();
		tank.collapseIfNeeded();
		net.minecraft.nbt.CompoundTag after = tank.getFluids().get(0).getOrCreateTag();

		helper.assertTrue(before.equals(after),
			"collapseIfNeeded must not churn a settled mixture's ratio tag (before=" + before + " after=" + after + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void solventWaterContributesNoColor(GameTestHelper helper) {
		// water is the colourless solvent: it must not tint a mixture. The old
		// saturated blue would dominate any blend and hide the solute's colour.
		ResourceLocation water = Solution.WATER;
		ResourceLocation co2 = new ResourceLocation(ChemicalAddon.MODID, "carbon_dioxide");
		FluidStack mix = Mixture.create(Map.of(water, 900, co2, 100), 1000);
		int color = Mixture.getColor(mix);
		int co2Color = FluidColors.of(co2);
		helper.assertTrue(color == co2Color,
			"solvent water must contribute no colour (got " + Integer.toHexString(color)
				+ ", want " + Integer.toHexString(co2Color) + ")");

		// pure solvent (water only) has nothing coloured -> faint white (no tint):
		// clear water must NOT read as opaque white (or a white precipitate CaCO3
		// would be indistinguishable), but also not fully transparent (the liquid
		// surface must stay visible).
		FluidStack pure = Mixture.create(Map.of(water, 1000), 1000);
		helper.assertTrue(Mixture.getColor(pure) == IonColors.CLEAR_TINT,
			"pure solvent water should render faint white (got " + Integer.toHexString(Mixture.getColor(pure)) + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void mixtureDegradesToPure(GameTestHelper helper) {
		// draining all the solute ions out of an aqueous mixture leaves pure solvent,
		// and a single-component remainder degrades back to a pure fluid stack.
		ReactorTank tank = new ReactorTank(10000, () -> {});
		ResourceLocation water = Solution.WATER;
		FluidStack mix = Mixture.create(Map.of(water, 600), Map.of("H+1", 200, "SO4-2", 100), 900);
		tank.fill(mix, FluidAction.EXECUTE);

		// consume all the acid ions (the path completeRecipe uses)
		int drained = tank.drainSolution(new ResourceLocation(ChemicalAddon.MODID, "sulfuric_acid"), 300,
			FluidAction.EXECUTE);
		helper.assertTrue(drained == 300, "should drain 300 mB of acid ions (got " + drained + ")");
		tank.collapseIfNeeded();

		helper.assertTrue(tank.getFluids().size() == 1, "one stack after degrading (got " + tank.getFluids().size() + ")");
		FluidStack remain = tank.getFluids().get(0);
		helper.assertTrue(!Mixture.isMixture(remain), "should degrade to a pure fluid");
		helper.assertTrue(remain.getFluid() == Fluids.WATER, "remaining fluid should be water");
		helper.assertTrue(remain.getAmount() == 600,
			"600 mB water should remain (got " + remain.getAmount() + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void mixtureSpillsAsPureComponents(GameTestHelper helper) {
		// regression: breaking the vessel must spill a mixture as its PURE fluid
		// component (water) — NOT as a component-less mixture whose NBT cannot survive
		// a world fluid block. Dissolved ions have no registered fluid, so they are
		// lost on spill by design (only fluids can pour out as blocks).
		ReactorTank tank = new ReactorTank(10000, () -> {});
		ResourceLocation water = Solution.WATER;
		FluidStack mix = Mixture.create(Map.of(water, 2000), Map.of("H+1", 400, "SO4-2", 200), 2600);
		tank.fill(mix, FluidAction.EXECUTE);
		helper.assertTrue(Mixture.isMixture(tank.getFluids().get(0)), "baseline: tank holds a mixture");

		List<FluidStack> spilled = SpillLogic.queueFluids(tank);
		helper.assertTrue(tank.getFluids().isEmpty(), "the spill must empty the tank");
		helper.assertTrue(!spilled.isEmpty(), "the mixture must pour out (as its water)");
		for (FluidStack s : spilled) {
			helper.assertTrue(!Mixture.isMixture(s),
				"spilled stacks must be pure components (survive world blocks), not a mixture");
			helper.assertTrue(s.getFluid() == Fluids.WATER, "only water is spillable (ions have no fluid)");
		}
		// reform: re-absorb the spilled water -> pure water, no component-less mixture
		for (FluidStack s : spilled) {
			tank.fill(s.copy(), FluidAction.EXECUTE);
		}
		tank.collapseIfNeeded();
		helper.assertTrue(tank.getFluids().size() == 1 && !Mixture.isMixture(tank.getFluids().get(0)),
			"reform should yield pure water, not a component-less mixture");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void temperatureBlendsOnMerge(GameTestHelper helper) {
		// pouring 40 °C and 20 °C water into the same vessel blends to the
		// amount-weighted average: (40×1000 + 20×1000) / 2000 = 30 °C
		ReactorTank tank = new ReactorTank(10000, () -> {});
		FluidStack hot = new FluidStack(Fluids.WATER, 1000);
		Temperature.set(hot, 40);
		FluidStack cold = new FluidStack(Fluids.WATER, 1000);
		Temperature.set(cold, 20);

		tank.fill(hot, FluidAction.EXECUTE);
		tank.fill(cold, FluidAction.EXECUTE);
		helper.assertTrue(tank.getFluids().size() == 1, "same-species fluids should stack into one entry");
		helper.assertTrue(tank.getTotalAmount() == 2000, "amounts should sum (got " + tank.getTotalAmount() + ")");
		helper.assertTrue(Temperature.get(tank.getFluids().get(0)) == 30,
			"temperature should blend to 30 °C (got " + Temperature.get(tank.getFluids().get(0)) + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void temperatureSurvivesTransfer(GameTestHelper helper) {
		// a pump-style drain carries the fluid's temperature (frozen) so the next
		// vessel can continue heating/cooling from where the last one left off
		ReactorTank src = new ReactorTank(10000, () -> {});
		FluidStack hot = new FluidStack(Fluids.WATER, 1000);
		Temperature.set(hot, 40);
		src.fill(hot, FluidAction.EXECUTE);

		FluidStack drained = src.drain(500, FluidAction.EXECUTE);
		helper.assertTrue(Temperature.get(drained) == 40,
			"drained sample must carry its temperature (frozen) (got " + Temperature.get(drained) + ")");

		ReactorTank dest = new ReactorTank(10000, () -> {});
		dest.fill(drained.copy(), FluidAction.EXECUTE);
		helper.assertTrue(Temperature.get(dest.getFluids().get(0)) == 40,
			"temperature must survive the transfer into another vessel");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void vialPreservesFluidNbt(GameTestHelper helper) {
		// the sample vial's FluidHandlerItemStack must round-trip the whole FluidStack
		// (temperature + mixture NBT), unlike a standard BucketItem which is tag-less
		ItemStack vial = AllContainers.FLUID_VIAL.asStack();
		IFluidHandlerItem vialHandler = vial.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
		helper.assertTrue(vialHandler != null, "vial must expose FLUID_HANDLER_ITEM");

		FluidStack hot = new FluidStack(Fluids.WATER, 1000);
		Temperature.set(hot, 40);
		int filled = vialHandler.fill(hot, FluidAction.EXECUTE);
		helper.assertTrue(filled == 1000, "vial should fill 1000 mB (got " + filled + ")");

		FluidStack back = vialHandler.getFluidInTank(0);
		helper.assertTrue(back.getFluid() == Fluids.WATER, "vial should hold water");
		helper.assertTrue(Temperature.get(back) == 40,
			"temperature must survive the vial round-trip (got " + Temperature.get(back) + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void solutionBucketPacksMixture(GameTestHelper helper) {
		// the creative "packed mixture" bucket pre-fills its default instance with a
		// solution mode's ion signature + water (solutions are not registered fluids)
		ItemStack bucket = AllContainers.SOLUTION_BUCKETS.get(0).get().getDefaultInstance(); // sulfuric_acid
		IFluidHandlerItem handler = bucket.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
		helper.assertTrue(handler != null, "bucket must expose FLUID_HANDLER_ITEM");

		FluidStack fluid = handler.getFluidInTank(0);
		helper.assertTrue(!fluid.isEmpty() && Mixture.isMixture(fluid), "bucket should hold a mixture");
		Map<String, Integer> ions = Mixture.deriveIonAmounts(fluid);
		helper.assertTrue(ions.getOrDefault("H+1", 0) > 0 && ions.getOrDefault("SO4-2", 0) > 0,
			"sulfuric acid bucket should hold H+ + SO4-- ions (got " + ions + ")");
		helper.assertTrue(Mixture.deriveAmounts(fluid).containsKey(Solution.WATER),
			"bucket should hold the solvent water");
		helper.assertTrue(fluid.getAmount() == 1000, "bucket should hold 1000 mB (got " + fluid.getAmount() + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void slurryBucketPacksSuspendedSolid(GameTestHelper helper) {
		// a slurry bucket pre-fills water + a Suspended solid (NOT dissolved ions)
		ItemStack bucket = AllContainers.SLURRY_BUCKETS.get(0).get().getDefaultInstance(); // milk_of_lime
		IFluidHandlerItem handler = bucket.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElse(null);
		helper.assertTrue(handler != null, "bucket must expose FLUID_HANDLER_ITEM");

		FluidStack fluid = handler.getFluidInTank(0);
		helper.assertTrue(!fluid.isEmpty() && Mixture.isMixture(fluid), "bucket should hold a mixture");
		ResourceLocation slakedLime = new ResourceLocation(ChemicalAddon.MODID, "slaked_lime");
		helper.assertTrue(Mixture.deriveSuspendedAmounts(fluid).getOrDefault(slakedLime, 0) > 0,
			"milk_of_lime should hold suspended slaked lime (got " + Mixture.deriveSuspendedAmounts(fluid) + ")");
		helper.assertTrue(Mixture.deriveIonAmounts(fluid).isEmpty(), "a slurry should carry no dissolved ions");
		helper.assertTrue(Mixture.deriveAmounts(fluid).containsKey(Solution.WATER),
			"bucket should hold the solvent water");
		helper.assertTrue(fluid.getAmount() == 1000, "bucket should hold 1000 mB (got " + fluid.getAmount() + ")");
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
		int filled = handler.fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
		helper.assertTrue(filled == 1000, "filling through the brick must reach the controller tank");
		ReactorControllerBlockEntity controller = reactor(helper);
		helper.assertTrue(hasFluid(controller, Fluids.WATER, 900),
			"controller tank should hold the water poured via the brick");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 40)
	public static void reactorBurnsSulfur(GameTestHelper helper) {
		// 5×5×5 (27 buckets) so the recipe inputs/outputs fit — a minimal 3×3×3
		// holds only 1 bucket at 1 bucket/interior-block and cannot run reactions.
		ReactorControllerBlockEntity be = buildReactor5x5x5(helper);
		// KINDLED blaze burner below the vessel's bottom layer; mark it creative
		// so its BE never cools it (simulates a permanently fuelled burner).
		// Controller is at (2,2,0); the floor is at y=1; the burner sits at controller.below(2) = (2,0,0).
		BlockState burner = com.simibubi.create.AllBlocks.BLAZE_BURNER.get().defaultBlockState()
			.setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.KINDLED);
		BlockPos burnerPos = new BlockPos(2, 0, 0);
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
		// SO2 + water -> dilute sulfuric acid (100 formula units → 200 H+ + 100 SO4-- + 2000 water)
		ReactorControllerBlockEntity be = buildReactor5x5x5(helper);
		be.getTank().fill(new FluidStack(AllFluids.SULFUR_DIOXIDE.get().getSource(), 1000), FluidAction.EXECUTE);
		be.getTank().fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
		helper.startSequence()
			.thenIdle(TICKS * 25)
			.thenExecute(() -> {
				helper.assertTrue(hasIon(be.getTank(), "H+1", 200), "sulfuric acid should expand to 200 H+ ions");
				helper.assertTrue(hasIon(be.getTank(), "SO4-2", 100), "sulfuric acid should expand to 100 SO4-- ions");
				helper.assertTrue(hasSpecies(be.getTank(), "water", 2000), "water should be the solvent");
			})
			.thenSucceed();
	}

	// ------------------------------------------------------------ emergent chemistry

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void rulesEngineNeutralisesAcidAndBase(GameTestHelper helper) {
		// H+ + OH- -> H2O (emergent, no whitelist); Na+ + Cl- remain as ions
		ResourceLocation water = Solution.WATER;
		ReactorTank tank = new ReactorTank(10000, () -> {});
		FluidStack mix = Mixture.create(
			Map.of(water, 1000),
			Map.of("H+1", 1000, "Cl-1", 1000, "Na+1", 1000, "OH-1", 1000),
			5000);
		tank.fill(mix, FluidAction.EXECUTE);

		RulesEngine.apply(tank);

		FluidStack result = tank.getFluids().get(0);
		helper.assertTrue(Mixture.deriveSuspendedAmounts(result).isEmpty(),
			"neutralisation should not suspend any solid");
		Map<String, Integer> ions = Mixture.deriveIonAmounts(result);
		helper.assertTrue(ions.getOrDefault("H+1", 0) == 0, "H+ should be consumed (got " + ions + ")");
		helper.assertTrue(ions.getOrDefault("OH-1", 0) == 0, "OH- should be consumed (got " + ions + ")");
		helper.assertTrue(ions.getOrDefault("Na+1", 0) == 1000, "Na+ should remain (got " + ions + ")");
		helper.assertTrue(ions.getOrDefault("Cl-1", 0) == 1000, "Cl- should remain (got " + ions + ")");
		helper.assertTrue(Mixture.deriveAmounts(result).getOrDefault(water, 0) == 2000,
			"neutralisation should produce water (got " + Mixture.deriveAmounts(result) + ")");
		helper.assertTrue(Temperature.get(result) > 20,
			"neutralisation is exothermic (got " + Temperature.get(result) + "°C)");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void rulesEnginePrecipitatesLimestone(GameTestHelper helper) {
		// Ca2+ + CO3-- -> CaCO3(s) (emergent, no whitelist); the solid stays suspended
		ResourceLocation water = Solution.WATER;
		ResourceLocation limestone = new ResourceLocation(ChemicalAddon.MODID, "limestone");
		ReactorTank tank = new ReactorTank(10000, () -> {});
		FluidStack mix = Mixture.create(
			Map.of(water, 1000),
			Map.of("Ca+2", 1000, "Cl-1", 2000, "Na+1", 2000, "CO3-2", 1000),
			7000);
		tank.fill(mix, FluidAction.EXECUTE);

		RulesEngine.apply(tank);

		FluidStack result = tank.getFluids().get(0);
		helper.assertTrue(Mixture.deriveSuspendedAmounts(result).getOrDefault(limestone, 0) == 1000,
			"Ca2+ + CO3-- should precipitate 1000 mB limestone (got " + Mixture.deriveSuspendedAmounts(result) + ")");
		helper.assertTrue(Mixture.deriveSedimentAmounts(result).isEmpty(),
			"fast precipitation should stay suspended, not settle (got " + Mixture.deriveSedimentAmounts(result) + ")");
		Map<String, Integer> ions = Mixture.deriveIonAmounts(result);
		helper.assertTrue(ions.getOrDefault("Ca+2", 0) == 0, "Ca2+ should be consumed (got " + ions + ")");
		helper.assertTrue(ions.getOrDefault("CO3-2", 0) == 0, "CO3-- should be consumed (got " + ions + ")");
		helper.assertTrue(ions.getOrDefault("Na+1", 0) == 2000, "Na+ should remain (got " + ions + ")");
		helper.assertTrue(ions.getOrDefault("Cl-1", 0) == 2000, "Cl- should remain (got " + ions + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void rulesEngineCrystallisesOnCooling(GameTestHelper helper) {
		// NH4+ + NO3- (aq) is stable hot, crystallises ammonium nitrate on cooling.
		// 500 formula units / 200 water = 2.5 f.u./water: below the 100°C threshold
		// (871 g/100g → 8.71) but above the 20°C threshold (192 g/100g → 1.92).
		ResourceLocation water = Solution.WATER;
		ResourceLocation ammoniumNitrate = new ResourceLocation(ChemicalAddon.MODID, "ammonium_nitrate");
		ReactorTank tank = new ReactorTank(10000, () -> {});
		FluidStack hot = Mixture.create(
			Map.of(water, 200),
			Map.of("NH4+1", 500, "NO3-1", 500),
			1200);
		Temperature.set(hot, 100);
		tank.fill(hot, FluidAction.EXECUTE);

		RulesEngine.apply(tank);
		helper.assertTrue(Mixture.deriveSedimentAmounts(tank.getFluids().get(0)).isEmpty(),
			"hot unsaturated solution should not crystallise (sediment)");
		helper.assertTrue(Mixture.deriveSuspendedAmounts(tank.getFluids().get(0)).isEmpty(),
			"hot unsaturated solution should not precipitate (suspended)");

		Temperature.set(tank.getFluids().get(0), 20);
		RulesEngine.apply(tank);
		helper.assertTrue(Mixture.deriveSedimentAmounts(tank.getFluids().get(0)).getOrDefault(ammoniumNitrate, 0) == 500,
			"cooling should crystallise 500 mB ammonium nitrate into sediment (got "
				+ Mixture.deriveSedimentAmounts(tank.getFluids().get(0)) + ")");
		helper.assertTrue(Mixture.deriveSuspendedAmounts(tank.getFluids().get(0)).isEmpty(),
			"crystallisation should not leave suspended solids (got "
				+ Mixture.deriveSuspendedAmounts(tank.getFluids().get(0)) + ")");
		Map<String, Integer> ions = Mixture.deriveIonAmounts(tank.getFluids().get(0));
		helper.assertTrue(ions.getOrDefault("NH4+1", 0) == 0 && ions.getOrDefault("NO3-1", 0) == 0,
			"crystallisation should remove the solute ions (got " + ions + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 40)
	public static void reactorRunsEmergentChemistry(GameTestHelper helper) {
		// the reactor tick must run the rules engine: H+ + Cl- + Na+ + OH- neutralises to Na+ + Cl- + water
		ReactorControllerBlockEntity be = buildReactor5x5x5(helper);
		ResourceLocation water = Solution.WATER;
		FluidStack mix = Mixture.create(
			Map.of(water, 1000),
			Map.of("H+1", 500, "Cl-1", 500, "Na+1", 500, "OH-1", 500),
			3000);
		be.getTank().fill(mix, FluidAction.EXECUTE);
		helper.startSequence()
			.thenIdle(TICKS * 2)
			.thenExecute(() -> {
				helper.assertTrue(!be.getTank().getFluids().isEmpty(), "tank should not be empty after neutralisation");
				helper.assertTrue(hasIon(be.getTank(), "Na+1", 500) && hasIon(be.getTank(), "Cl-1", 500),
					"reactor tick should run the rules engine and neutralise to Na+ + Cl-");
			})
			.thenSucceed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void solutionExpandsAtConcentration(GameTestHelper helper) {
		// a solution mode packs its solute ions + water at a continuous concentration
		Species sulfuric = SpeciesManager.get(new ResourceLocation(ChemicalAddon.MODID, "sulfuric_acid"));
		helper.assertTrue(sulfuric != null && sulfuric.isSolution(), "sulfuric_acid should be a solution mode");

		Map<ResourceLocation, Integer> molecules = new LinkedHashMap<>();
		Map<String, Integer> ions = new LinkedHashMap<>();
		sulfuric.expand(600, 1.0, molecules, ions); // 600 ion mB at C=1.0 → 200 FU + 600 water
		helper.assertTrue(ions.getOrDefault("H+1", 0) == 400 && ions.getOrDefault("SO4-2", 0) == 200,
			"expand should give 400 H+ + 200 SO4-- (got " + ions + ")");
		helper.assertTrue(molecules.getOrDefault(Solution.WATER, 0) == 600,
			"expand should pack 600 water at C=1.0 (got " + molecules + ")");

		molecules.clear();
		ions.clear();
		sulfuric.expand(600, 0.15, molecules, ions); // dilute: water = 600/0.15 = 4000
		helper.assertTrue(molecules.getOrDefault(Solution.WATER, 0) == 4000,
			"expand should pack 4000 water at C=0.15 (got " + molecules + ")");
		helper.assertTrue(Mixture.isChargeNeutral(ions), "expanded ions must stay neutral");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void solutionMatchingIsConcentrationAware(GameTestHelper helper) {
		// continuous concentration: 100 formula units (300 ion mB) + 300 water = C 1.0
		ResourceLocation sulfuric = new ResourceLocation(ChemicalAddon.MODID, "sulfuric_acid");
		ResourceLocation water = Solution.WATER;
		ReactorTank tank = new ReactorTank(10000, () -> {});
		FluidStack mix = Mixture.create(
			Map.of(water, 300),
			Map.of("H+1", 200, "SO4-2", 100),
			600);
		tank.fill(mix, FluidAction.EXECUTE);

		helper.assertTrue(tank.countSolution(sulfuric) == 300,
			"solute ion amount should be 300 mB (got " + tank.countSolution(sulfuric) + ")");
		double c = tank.concentrationOf(sulfuric);
		helper.assertTrue(Math.abs(c - 1.0) < 1e-9, "concentration should be 1.0 (got " + c + ")");

		int drained = tank.drainSolution(sulfuric, 300, FluidAction.EXECUTE);
		helper.assertTrue(drained == 300, "should drain 300 mB of solute ions (got " + drained + ")");
		helper.assertTrue(!hasIon(tank, "H+1", 1) && !hasIon(tank, "SO4-2", 1),
			"the acid ions should be consumed");
		helper.assertTrue(hasSpecies(tank, "water", 300), "the solvent water should remain");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 40)
	public static void reactorConsumesSolutionIngredient(GameTestHelper helper) {
		// a recipe's "solutions" input (species + amount + continuous concentration
		// range) matches and consumes the dissolved ions end-to-end. Concentrated acid
		// (C = 600 ion / 600 water = 1.0) satisfies minConcentration 0.5.
		ReactorControllerBlockEntity be = buildReactor5x5x5(helper);
		ResourceLocation water = Solution.WATER;
		FluidStack mix = Mixture.create(
			Map.of(water, 600),
			Map.of("H+1", 400, "SO4-2", 200),
			1200);
		be.getTank().fill(mix, FluidAction.EXECUTE);
		helper.startSequence()
			.thenIdle(TICKS * 8) // 160 ticks > processingTime 100
			.thenExecute(() -> {
				helper.assertTrue(!hasIon(be.getTank(), "H+1", 1) && !hasIon(be.getTank(), "SO4-2", 1),
					"the acid ions should be consumed by the solution ingredient");
				helper.assertTrue(hasSpecies(be.getTank(), "water", 1200), "water should be produced");
			})
			.thenSucceed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 40)
	public static void reactorProducesSolutionIngredient(GameTestHelper helper) {
		// SO3 + water -> concentrated sulfuric acid via "solutionOutputs" (600 ion mB at C=1.0)
		ReactorControllerBlockEntity be = buildReactor5x5x5(helper);
		be.getTank().fill(new FluidStack(AllFluids.SULFUR_TRIOXIDE.get().getSource(), 1000), FluidAction.EXECUTE);
		be.getTank().fill(new FluidStack(Fluids.WATER, 600), FluidAction.EXECUTE);
		helper.startSequence()
			.thenIdle(TICKS * 8) // 160 ticks > processingTime 100
			.thenExecute(() -> {
				helper.assertTrue(hasIon(be.getTank(), "H+1", 400) && hasIon(be.getTank(), "SO4-2", 200),
					"concentrated acid should be produced as dissolved ions");
				helper.assertTrue(hasSpecies(be.getTank(), "water", 600), "water should be the solvent");
			})
			.thenSucceed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void mixtureRejectsNonNeutralIons(GameTestHelper helper) {
		// charge neutrality is a hard invariant: setIons must reject a non-neutral set
		FluidStack stack = new FluidStack(Mixture.fluid(), 1000);
		boolean ok = Mixture.setIons(stack, Map.of("H+1", 3, "SO4-2", 1)); // +3 -2 = +1
		helper.assertTrue(!ok, "non-charge-neutral ion set must be rejected");
		helper.assertTrue(Mixture.getIons(stack).isEmpty(), "rejected ions must not be written (got " + Mixture.getIons(stack) + ")");

		ok = Mixture.setIons(stack, Map.of("H+1", 2, "SO4-2", 1)); // +2 -2 = 0
		helper.assertTrue(ok, "charge-neutral ion set must be accepted");
		helper.assertTrue(Mixture.getIons(stack).size() == 2, "neutral ions should be stored");
		helper.assertTrue(Mixture.isChargeNeutral(Mixture.getIons(stack)), "stored ions must be neutral");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void mixtureWithIonsDerivesAndTransfers(GameTestHelper helper) {
		// joint {Ions + Molecules} mixture: 10 water + 2 H+ + 1 SO4-2 = 13 parts over 1300 mB
		ResourceLocation water = Solution.WATER;
		FluidStack mix = Mixture.create(
			Map.of(water, 10),
			Map.of("H+1", 2, "SO4-2", 1),
			1300);

		helper.assertTrue(Mixture.getIons(mix).size() == 2, "mixture should carry ions");
		helper.assertTrue(Mixture.isChargeNeutral(Mixture.getIons(mix)), "stored ions must be neutral");

		Map<ResourceLocation, Integer> mol = Mixture.deriveAmounts(mix);
		Map<String, Integer> ions = Mixture.deriveIonAmounts(mix);
		helper.assertTrue(mol.getOrDefault(water, 0) == 1000, "water should derive to 1000 mB (got " + mol + ")");
		helper.assertTrue(ions.getOrDefault("H+1", 0) == 200, "H+1 should derive to 200 mB (got " + ions + ")");
		helper.assertTrue(ions.getOrDefault("SO4-2", 0) == 100, "SO4-2 should derive to 100 mB (got " + ions + ")");

		int total = mol.values().stream().mapToInt(Integer::intValue).sum()
			+ ions.values().stream().mapToInt(Integer::intValue).sum();
		helper.assertTrue(total == 1300, "joint amounts must sum to the total (got " + total + ")");

		// pump-style transfer: tag copied verbatim, amount shrinks — ratio never moves
		FluidStack drained = mix.copy();
		drained.setAmount(650);
		Map<String, Integer> dIons = Mixture.deriveIonAmounts(drained);
		helper.assertTrue(Mixture.isChargeNeutral(Mixture.getIons(drained)), "transferred ions stay neutral");
		helper.assertTrue(dIons.getOrDefault("H+1", 0) == 100, "drained H+1 should be 100 mB (got " + dIons + ")");
		helper.assertTrue(dIons.getOrDefault("SO4-2", 0) == 50, "drained SO4-2 should be 50 mB (got " + dIons + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void mixtureWithSuspendedDerivesAndTransfers(GameTestHelper helper) {
		// Suspended (solid) domain: 600 water + 300 gypsum = 900 mB, ratio 2:1
		ResourceLocation water = Solution.WATER;
		ResourceLocation gypsum = new ResourceLocation(ChemicalAddon.MODID, "gypsum");
		FluidStack mix = Mixture.create(
			Map.of(water, 600),
			Map.of(),
			Map.of(gypsum, 300),
			900);

		helper.assertTrue(Mixture.getSuspended(mix).containsKey(gypsum), "suspended solid should be stored");
		helper.assertTrue(Mixture.getIons(mix).isEmpty(), "no ions in this mix");
		helper.assertTrue(Mixture.deriveAmounts(mix).getOrDefault(water, 0) == 600,
			"water should derive to 600 mB (got " + Mixture.deriveAmounts(mix) + ")");
		helper.assertTrue(Mixture.deriveSuspendedAmounts(mix).getOrDefault(gypsum, 0) == 300,
			"suspended gypsum should derive to 300 mB (got " + Mixture.deriveSuspendedAmounts(mix) + ")");
		helper.assertTrue(Mixture.deriveIonAmounts(mix).isEmpty(), "ion domain should be empty");

		// pump-style transfer: ratio tag copied verbatim — solid ratio never moves
		FluidStack drained = mix.copy();
		drained.setAmount(300);
		helper.assertTrue(Mixture.deriveSuspendedAmounts(drained).getOrDefault(gypsum, 0) == 100,
			"drained gypsum should keep the 1:2 ratio (got " + Mixture.deriveSuspendedAmounts(drained) + ")");
		helper.assertTrue(Mixture.deriveAmounts(drained).getOrDefault(water, 0) == 200,
			"drained water should keep the 1:2 ratio (got " + Mixture.deriveAmounts(drained) + ")");
		helper.succeed();
	}

	// ------------------------------------------------------------------ filter press

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 30)
	public static void filterPressFiltersSlurry(GameTestHelper helper) {
		// a slurry = mixture with a Suspended solid; the filter press separates it:
		// the solid becomes a cake item, the liquid (water) passes to the output
		helper.setBlock(new BlockPos(2, 1, 2), AllBlocks.FILTER_PRESS.get().defaultBlockState());
		FilterPressBlockEntity be = (FilterPressBlockEntity) helper.getBlockEntity(new BlockPos(2, 1, 2));
		ResourceLocation water = Solution.WATER;
		ResourceLocation bicarbonate = new ResourceLocation(ChemicalAddon.MODID, "sodium_bicarbonate");
		FluidStack slurry = Mixture.create(
			Map.of(water, 1000),
			Map.of(),
			Map.of(bicarbonate, 1000),
			2000);
		be.getInput().fill(slurry, FluidAction.EXECUTE);
		helper.startSequence()
			.thenIdle(TICKS * 15)
			.thenExecute(() -> {
				helper.assertTrue(!be.getItems().getStackInSlot(0).isEmpty()
					&& be.getItems().getStackInSlot(0).is(AllItems.SODIUM_BICARBONATE.get()),
					"cake should be produced");
				helper.assertTrue(hasSpecies(be.getOutput(), "water", 900), "filtrate water should be produced");
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

	/** Builds a 5×5×5 sealed reactor (interior 3×3×3 = 27 blocks = 27 buckets) with
	 *  the controller at (2,2,0) and assembles it. Used by tests that need more
	 *  capacity than the minimal 3×3×3 (which holds only 1 bucket). */
	private static ReactorControllerBlockEntity buildReactor5x5x5(GameTestHelper helper) {
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		BlockState controller = AllBlocks.REACTOR_CONTROLLER.get().defaultBlockState();
		for (int x = 0; x <= 4; x++) {
			for (int z = 0; z <= 4; z++) {
				helper.setBlock(new BlockPos(x, 1, z), brick); // floor
				helper.setBlock(new BlockPos(x, 5, z), brick); // sealed ceiling
			}
		}
		for (int y = 2; y <= 4; y++) {
			for (int x = 0; x <= 4; x++) {
				for (int z = 0; z <= 4; z++) {
					if ((x == 0 || x == 4 || z == 0 || z == 4) && !(y == 2 && x == 2 && z == 0)) {
						helper.setBlock(new BlockPos(x, y, z), brick); // ring walls
					}
				}
			}
		}
		helper.setBlock(new BlockPos(2, 2, 0), controller);
		ReactorControllerBlockEntity be = (ReactorControllerBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 0));
		helper.assertTrue(be.tryAssemble().ok(), "5x5x5 reactor should assemble");
		return be;
	}

	/** Builds an OPEN-topped 3×3×5 (3 rings, 3000 mB) with the controller mounted
	 *  on the MIDDLE ring (2,3,1) — ringLayer=1, so the interior floor (y=2) sits
	 *  one block BELOW the controller. Assembles it and returns the controller. */
	private static ReactorControllerBlockEntity buildReactor3x3x5HighController(GameTestHelper helper) {
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		BlockState controller = AllBlocks.REACTOR_CONTROLLER.get().defaultBlockState();
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, 1, z), brick); // floor
			}
		}
		for (int y = 2; y <= 4; y++) {
			for (int x = 1; x <= 3; x++) {
				for (int z = 1; z <= 3; z++) {
					if (x == 2 && z == 2) {
						continue; // interior column
					}
					helper.setBlock(new BlockPos(x, y, z), x == 2 && z == 1 && y == 3 ? controller : brick);
				}
			}
		}
		ReactorControllerBlockEntity be = (ReactorControllerBlockEntity) helper.getBlockEntity(new BlockPos(2, 3, 1));
		helper.assertTrue(be.tryAssemble().ok(), "open 3x3x5 with the controller on ring 1 should assemble");
		return be;
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

	/** mB of a species in a stack: mixture components by ratio, or a pure stack by id. */
	private static int speciesAmount(FluidStack stack, String species) {
		ResourceLocation id = "water".equals(species) ? Solution.WATER : new ResourceLocation(ChemicalAddon.MODID, species);
		if (Mixture.isMixture(stack)) {
			return Mixture.deriveAmounts(stack).getOrDefault(id, 0);
		}
		return id.equals(ForgeRegistries.FLUIDS.getKey(stack.getFluid())) ? stack.getAmount() : 0;
	}

	/** True when the tank holds at least {@code minAmount} mB of a species across all stacks. */
	private static boolean hasSpecies(com.yu1745.chemicaladdon.reactor.ReactorTank tank, String species, int minAmount) {
		int total = 0;
		for (FluidStack stack : tank.getFluids()) {
			total += speciesAmount(stack, species);
		}
		return total >= minAmount;
	}

	/** Units (mole-equivalents) of an ion in a mixture stack's ion domain. */
	private static int ionAmount(FluidStack stack, String ionId) {
		return Mixture.isMixture(stack) ? Mixture.deriveIonAmounts(stack).getOrDefault(ionId, 0) : 0;
	}

	/** True when the tank holds at least {@code minAmount} units of an ion across all stacks. */
	private static boolean hasIon(com.yu1745.chemicaladdon.reactor.ReactorTank tank, String ionId, int minAmount) {
		int total = 0;
		for (FluidStack stack : tank.getFluids()) {
			total += ionAmount(stack, ionId);
		}
		return total >= minAmount;
	}
}
