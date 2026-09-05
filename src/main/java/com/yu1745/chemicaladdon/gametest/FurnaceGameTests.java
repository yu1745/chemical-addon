package com.yu1745.chemicaladdon.gametest;

import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.waitFor;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlockEntity;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.reactor.FurnaceControllerBlockEntity;
import com.yu1745.chemicaladdon.reactor.ReactorTank;
import com.yu1745.chemicaladdon.registry.AllBlocks;
import com.yu1745.chemicaladdon.registry.AllFluids;
import com.yu1745.chemicaladdon.registry.AllItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ChemicalAddon.MODID)
@PrefixGameTestTemplate(false)
public class FurnaceGameTests {

	private static final int TICKS = 20;

	// ------------------------------------------------------------- furnace (D)

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void furnaceAssemblesSealedAndSplitsPorts(GameTestHelper helper) {
		FurnaceControllerBlockEntity be = buildFurnace(helper, 5);
		helper.assertTrue(be.isAssembled() && !be.isOpen(), "a roofed kiln assembles sealed");
		helper.assertTrue(be.getTank().getTankCapacity(0) == 3000, "gas volume 1000 x interior rings 3");
		// the item port: inserts land in the charge bed, extraction only yields product
		IItemHandler feed = helper.getBlockEntity(new BlockPos(6, 5, 1))
			.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
		IItemHandler product = helper.getBlockEntity(new BlockPos(6, 1, 1))
			.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElse(null);
		helper.assertTrue(feed != null && product != null, "the kiln exposes roof feed and hearth product ports");
		if (feed != null && product != null) {
			ItemStack bed = new ItemStack(AllItems.LIMESTONE.get(), 8);
			ItemStack rest = feed.insertItem(0, bed, false);
			helper.assertTrue(rest.isEmpty() && be.getItems().getStackInSlot(0).getCount() == 8,
				"inserts land in the charge bed");
			helper.assertTrue(feed.extractItem(0, 1, false).isEmpty(),
				"the bed is never extractable (feed cannot be sucked back out)");
			ItemStack foreign = new ItemStack(AllItems.SODIUM_BICARBONATE.get(), 2);
			ItemStack rejected = feed.insertItem(0, foreign, false);
			helper.assertTrue(rejected.getCount() == 2 && be.getItems().getStackInSlot(0).getCount() == 8
				&& be.getItems().getStackInSlot(0).is(AllItems.LIMESTONE.get()),
				"an unlike charge is rejected instead of being converted into the existing bed item");
			be.getItems().setStackInSlot(1, new ItemStack(AllItems.QUICKLIME.get(), 3));
			helper.assertTrue(product.extractItem(0, 2, false).getCount() == 2
				&& be.getItems().getStackInSlot(1).getCount() == 1, "extraction yields product only");
		}
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 40)
	public static void furnaceCalcinesLimestoneToLimeAndGas(GameTestHelper helper) {
		// 石灰石煅烧：CaCO3 -> CaO + CO2；需要 SEETHING 级炉温（900 °C）
		FurnaceControllerBlockEntity be = buildFurnace(helper, 5);
		placeBurner(helper, new BlockPos(3, 0, 3), BlazeBurnerBlock.HeatLevel.SEETHING);
		be.getItems().setStackInSlot(0, new ItemStack(AllItems.LIMESTONE.get(), 4));
		be.setPinnedTemperature(900); // fast-forward the heat-up (physical burners covered below)
		waitFor(helper.startSequence()
				.thenIdle(TICKS),
			() -> be.getItems().getStackInSlot(1).getCount() >= 1
				&& be.getTank().getTotalAmount() >= 1000)
			.thenExecute(() -> {
				helper.assertTrue(be.getItems().getStackInSlot(1).is(AllItems.QUICKLIME.get()),
					"limestone calcines into quicklime");
				helper.assertTrue(be.getItems().getStackInSlot(0).getCount() == 3, "one charge item per batch");
				FluidStack gas = be.getTank().drain(new FluidStack(AllFluids.CARBON_DIOXIDE.get().getSource(), 1000),
					FluidAction.EXECUTE);
				helper.assertTrue(gas.getAmount() == 1000, "the kiln gas (CO2) is piped out of the tank");
			})
			.thenSucceed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 40)
	public static void furnaceUnderheatedChargeStaysRaw(GameTestHelper helper) {
		// 欠烧诊断：炉温低于 minTempC 时生料不转化，状态口读 UNDERHEATED
		FurnaceControllerBlockEntity be = buildFurnace(helper, 5);
		be.getItems().setStackInSlot(0, new ItemStack(AllItems.LIMESTONE.get(), 2));
		be.setPinnedTemperature(500); // KINDLED tier: below the 900 °C lime needs
		waitFor(helper.startSequence()
				.thenIdle(TICKS * 2),
			() -> be.getStatus() == FurnaceControllerBlockEntity.FurnaceStatus.UNDERHEATED)
			.thenExecute(() -> {
				helper.assertTrue(be.getItems().getStackInSlot(0).getCount() == 2,
					"an underheated charge stays raw (生料)");
				helper.assertTrue(be.getItems().getStackInSlot(1).isEmpty(), "no product appears");
				helper.assertTrue(be.getProcessStatus().equals("UNDERHEATED"),
					"the status port reads the kiln state");
				// bring the kiln to temperature -> the same charge now converts
				be.setPinnedTemperature(900);
			})
			.thenWaitUntil(() -> {
				if (be.getItems().getStackInSlot(1).isEmpty()) {
					throw new GameTestAssertException("Waiting");
				}
			})
			.thenExecute(() -> helper.assertTrue(be.getItems().getStackInSlot(1).is(AllItems.QUICKLIME.get()),
				"reaching the temperature converts the raw charge"))
			.thenSucceed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 40)
	public static void furnaceCalcinesSodaAndAlumina(GameTestHelper helper) {
		// 重碱煅烧（索尔维闭环的煅烧步）与氢氧化铝脱水：两条 KINDLED 级煅烧线
		FurnaceControllerBlockEntity soda = buildFurnace(helper, 5);
		soda.getItems().setStackInSlot(0, new ItemStack(AllItems.SODIUM_BICARBONATE.get(), 1));
		soda.setPinnedTemperature(500);
		FurnaceControllerBlockEntity alumina = buildFurnace(helper, 10);
		alumina.getItems().setStackInSlot(0, new ItemStack(AllItems.ALUMINIUM_HYDROXIDE.get(), 1));
		alumina.setPinnedTemperature(500);
		waitFor(helper.startSequence()
				.thenIdle(TICKS),
			() -> !soda.getItems().getStackInSlot(1).isEmpty() && !alumina.getItems().getStackInSlot(1).isEmpty())
			.thenExecute(() -> {
				helper.assertTrue(soda.getItems().getStackInSlot(1).is(AllItems.SODA_ASH.get()),
					"bicarbonate calcines into soda ash (the Solvay loop's calcination step)");
				helper.assertTrue(soda.getTank().getTotalAmount() == 1000,
					"soda calcination vents CO2 + steam into the gas tank (got " + soda.getTank().getTotalAmount() + ")");
				helper.assertTrue(alumina.getItems().getStackInSlot(1).is(AllItems.ALUMINA.get()),
					"aluminium hydroxide dehydrates into alumina");
				helper.assertTrue(hasWater(alumina.getTank(), 1000), "the dehydration steam is recoverable");
			})
			.thenSucceed();
	}

	/** Builds a sealed 3x3x5 furnace (floor y=1, rings y=2..4, roof y=5) with the
	 *  controller on the north wall mid-cell. */
	private static FurnaceControllerBlockEntity buildFurnace(GameTestHelper helper, int x0) {
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		BlockState controller = AllBlocks.FURNACE_CONTROLLER.get().defaultBlockState();
		for (int x = 0; x <= 2; x++) {
			for (int z = 0; z <= 2; z++) {
				helper.setBlock(new BlockPos(x0 + x, 1, z), brick); // floor
				helper.setBlock(new BlockPos(x0 + x, 5, z), brick); // roof
			}
		}
		for (int y = 2; y <= 4; y++) {
			for (int x = 0; x <= 2; x++) {
				for (int z = 0; z <= 2; z++) {
					if (x == 1 && z == 1) {
						continue; // interior
					}
					if (x == 0 || x == 2 || z == 0 || z == 2) {
					helper.setBlock(new BlockPos(x0 + x, y, z), x == 1 && z == 0 && y == 2 ? controller : brick);
					}
				}
			}
		}
		FurnaceControllerBlockEntity be = (FurnaceControllerBlockEntity) helper.getBlockEntity(new BlockPos(x0 + 1, 2, 0));
		helper.assertTrue(be.tryAssemble().ok(), "furnace should assemble");
		return be;
	}

	/** Places a creative (never-fuel-ending) Blaze Burner at the given position. */
	private static void placeBurner(GameTestHelper helper, BlockPos pos, BlazeBurnerBlock.HeatLevel level) {
		BlockState burner = com.simibubi.create.AllBlocks.BLAZE_BURNER.get().defaultBlockState()
			.setValue(BlazeBurnerBlock.HEAT_LEVEL, level);
		helper.setBlock(pos, burner);
		if (helper.getBlockEntity(pos) instanceof BlazeBurnerBlockEntity burnerBe) {
			burnerBe.isCreative = true;
		}
	}

	/** True when the tank holds at least {@code min} mB of water, including a
	 * native aqueous state created by the furnace's declared steam output. */
	private static boolean hasWater(ReactorTank tank, int min) {
		return tank.waterInventoryMb() >= min;
	}

}
