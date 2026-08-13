package com.yu1745.chemicaladdon.gametest;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.fluid.FluidColors;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.reactor.ChemicalBrickBlock;
import com.yu1745.chemicaladdon.reactor.FilterPressBlockEntity;
import com.yu1745.chemicaladdon.reactor.ReactorControllerBlock;
import com.yu1745.chemicaladdon.reactor.ReactorControllerBlockEntity;
import com.yu1745.chemicaladdon.reactor.ReactorTank;
import com.yu1745.chemicaladdon.reactor.SpillLogic;
import com.yu1745.chemicaladdon.registry.AllBlocks;
import com.yu1745.chemicaladdon.registry.AllFluids;
import com.yu1745.chemicaladdon.registry.AllItems;

import com.simibubi.create.foundation.fluid.FluidIngredient;

import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
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
	public static void strayBrickDoesNotBreakReactor(GameTestHelper helper) {
		// A stray chemical brick touching — but not part of — an assembled reactor
		// shell must not tear the structure down when placed and then removed.
		// Regression: previously onRemove scanned a radius and invalidated every
		// nearby controller, spilling a vessel the brick never belonged to.
		buildReactor(helper);
		ReactorControllerBlockEntity be = reactor(helper);
		be.getTank().fill(new FluidStack(AllFluids.WATER.get().getSource(), 1000), FluidAction.EXECUTE);
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
	public static void mixtureCollapsesAndBlends(GameTestHelper helper) {
		// 2 distinct species coexisting collapse into one mixture stack whose NBT
		// keeps both components (no info loss) and whose colour is the weight blend.
		buildReactor(helper);
		ReactorControllerBlockEntity be = reactor(helper);
		ReactorTank tank = be.getTank();
		tank.fill(new FluidStack(AllFluids.WATER.get().getSource(), 400), FluidAction.EXECUTE);
		tank.fill(new FluidStack(AllFluids.BRINE.get().getSource(), 600), FluidAction.EXECUTE);
		helper.assertTrue(tank.getFluids().size() == 2, "two pure fluids should coexist before collapse");

		tank.collapseIfNeeded();
		helper.assertTrue(tank.getFluids().size() == 1, "should collapse to one mixture stack");
		FluidStack mix = tank.getFluids().get(0);
		helper.assertTrue(Mixture.isMixture(mix), "the single stack should be a mixture");
		Map<ResourceLocation, Integer> comps = Mixture.deriveAmounts(mix);
		helper.assertTrue(comps.size() == 2, "mixture should keep both components (got " + comps.size() + ")");
		helper.assertTrue(mix.getAmount() == 1000, "mixture total should be 1000 mB (got " + mix.getAmount() + ")");

		int color = Mixture.getColor(mix);
		int waterColor = FluidColors.of(new ResourceLocation("chemicaladdon", "water"));
		int brineColor = FluidColors.of(new ResourceLocation("chemicaladdon", "brine"));
		helper.assertTrue(color != waterColor && color != brineColor,
			"mixture colour should be a blend, not either pure colour");
		// Create's pump probes with drain(1, SIMULATE); if that returns empty the
		// pump can never extract the mixture (regression: integer truncation rounded
		// every component to 0 -> amount-0 stack -> read as empty)
		FluidStack probe = tank.drain(1, FluidAction.SIMULATE);
		helper.assertTrue(!probe.isEmpty() && Mixture.isMixture(probe),
			"a 1 mB probe of the mixture must return a non-empty mixture stack so pumps can extract it");
		helper.assertTrue(mix.getAmount() == 1000,
			"a SIMULATE probe must not mutate the tank (got " + mix.getAmount() + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void mixtureDegradesToPure(GameTestHelper helper) {
		// draining one component out of a mixture (the path reactions use) must
		// leave the rest, and when only one component remains the mixture degrades
		// back to a pure fluid stack.
		buildReactor(helper);
		ReactorControllerBlockEntity be = reactor(helper);
		ReactorTank tank = be.getTank();
		tank.fill(new FluidStack(AllFluids.WATER.get().getSource(), 400), FluidAction.EXECUTE);
		tank.fill(new FluidStack(AllFluids.BRINE.get().getSource(), 600), FluidAction.EXECUTE);
		tank.collapseIfNeeded();

		// consume all the water component via the ingredient path (same mechanism
		// completeRecipe uses), then settle -> should degrade to pure brine
		FluidIngredient waterIng = FluidIngredient.fromFluid(AllFluids.WATER.get().getSource(), 400);
		int drained = tank.drainIngredient(waterIng, 400, FluidAction.EXECUTE);
		helper.assertTrue(drained == 400, "should drain 400 mB water from the mixture (got " + drained + ")");
		tank.collapseIfNeeded();

		helper.assertTrue(tank.getFluids().size() == 1, "one stack after degrading");
		FluidStack remain = tank.getFluids().get(0);
		helper.assertTrue(!Mixture.isMixture(remain), "should degrade to a pure fluid");
		helper.assertTrue(remain.getFluid() == AllFluids.BRINE.get().getSource(),
			"remaining fluid should be brine");
		helper.assertTrue(remain.getAmount() == 600,
			"600 mB brine should remain (got " + remain.getAmount() + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void mixtureSpillsAsPureComponents(GameTestHelper helper) {
		// regression: breaking the vessel used to spill the mixture as a single
		// component-less fluid (NBT lost) -> reform re-absorbed many 1-bucket
		// mixtures that never merged. The mixture now decomposes into its PURE
		// components for spilling (those survive world fluid blocks), and reform
		// re-merges them. Uses a 5x5x5 (27-bucket) so the components are >= 1 bucket.
		ReactorControllerBlockEntity be = buildReactor5x5x5(helper);
		ReactorTank tank = be.getTank();
		tank.fill(new FluidStack(AllFluids.WATER.get().getSource(), 2000), FluidAction.EXECUTE);
		tank.fill(new FluidStack(AllFluids.BRINE.get().getSource(), 3000), FluidAction.EXECUTE);
		tank.collapseIfNeeded();
		helper.assertTrue(Mixture.isMixture(tank.getFluids().get(0)), "baseline: tank holds a mixture");

		List<FluidStack> spilled = SpillLogic.queueFluids(tank);
		helper.assertTrue(tank.getFluids().isEmpty(), "the spill must empty the tank");
		helper.assertTrue(!spilled.isEmpty(), "the mixture must pour out (as its components)");
		for (FluidStack s : spilled) {
			helper.assertTrue(!Mixture.isMixture(s),
				"spilled stacks must be pure components (survive world blocks), not a mixture");
		}
		// reform: re-absorb the spilled pure fluids, then settle -> mixture restored
		for (FluidStack s : spilled) {
			tank.fill(s.copy(), FluidAction.EXECUTE);
		}
		tank.collapseIfNeeded();
		helper.assertTrue(tank.getFluids().size() == 1 && Mixture.isMixture(tank.getFluids().get(0)),
			"reform should re-merge the components into one mixture");
		helper.assertTrue(Mixture.deriveAmounts(tank.getFluids().get(0)).size() == 2,
			"both components must survive the spill/reform cycle");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 40)
	public static void mixDegreeRisesOverTime(GameTestHelper helper) {
		// a freshly poured mixture starts at MixDegree 0 (distinct bands) and
		// homogenises over time toward 1 (blended). Without this it read as
		// instantly fully mixed and the un-mixed -> mixed transition was untestable.
		buildReactor(helper);
		ReactorControllerBlockEntity be = reactor(helper);
		ReactorTank tank = be.getTank();
		tank.fill(new FluidStack(AllFluids.WATER.get().getSource(), 400), FluidAction.EXECUTE);
		tank.fill(new FluidStack(AllFluids.BRINE.get().getSource(), 600), FluidAction.EXECUTE);
		tank.collapseIfNeeded();
		float initial = Mixture.getMixDegree(tank.getFluids().get(0));
		helper.assertTrue(initial == 0f, "a freshly mixed mixture should start at MixDegree 0 (got " + initial + ")");
		helper.startSequence()
			.thenIdle(TICKS * 5) // ~5 s -> several MIX_TICK cycles
			.thenExecute(() -> {
				float now = Mixture.getMixDegree(tank.getFluids().get(0));
				helper.assertTrue(now > initial,
					"MixDegree should rise over time (was " + initial + ", now " + now + ")");
			})
			.thenSucceed();
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
		int filled = handler.fill(new FluidStack(AllFluids.WATER.get().getSource(), 1000), FluidAction.EXECUTE);
		helper.assertTrue(filled == 1000, "filling through the brick must reach the controller tank");
		ReactorControllerBlockEntity controller = reactor(helper);
		helper.assertTrue(hasFluid(controller, AllFluids.WATER.get().getSource(), 900),
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
		// 5×5×5 (27 buckets): SO2(1000) + water(1000) inputs need room beyond the
		// minimal 3×3×3's single-bucket capacity.
		ReactorControllerBlockEntity be = buildReactor5x5x5(helper);
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
