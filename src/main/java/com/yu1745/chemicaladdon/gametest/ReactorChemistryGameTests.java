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
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.buildReactor5x5x5;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.hasFluid;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.hasIon;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.ionAmount;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.hasSpecies;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.reactor;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.speciesAmount;
import static com.yu1745.chemicaladdon.gametest.GameTestFixtures.waitFor;

@GameTestHolder(ChemicalAddon.MODID)
@PrefixGameTestTemplate(false)
public class ReactorChemistryGameTests {
	private static final int TICKS = 20;

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
		waitFor(helper.startSequence()
				.thenIdle(TICKS * 5), // KINDLED burner heat-up lead
			() -> be.getTemperature() >= 400
				&& hasFluid(be, AllFluids.SULFUR_DIOXIDE.get().getSource(), 900))
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
		waitFor(helper.startSequence()
				.thenIdle(TICKS * 10), // so2_absorption: 200 ticks processingTime
			() -> hasIon(be.getTank(), "H+1", 200) && hasIon(be.getTank(), "SO4-2", 100)
				&& hasSpecies(be.getTank(), "water", 2000))
			.thenExecute(() -> {
				helper.assertTrue(hasIon(be.getTank(), "H+1", 200), "sulfuric acid should expand to 200 H+ ions");
				helper.assertTrue(hasIon(be.getTank(), "SO4-2", 100), "sulfuric acid should expand to 100 SO4-- ions");
				helper.assertTrue(hasSpecies(be.getTank(), "water", 2000), "water should be the solvent");
			})
			.thenSucceed();
	}

	// ------------------------------------------------------------ emergent chemistry

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void rulesEnginePrecipitatesLimestone(GameTestHelper helper) {
		// Ca2+ + CO3-- -> CaCO3(s) (emergent, no whitelist); the solid stays
		// suspended. Spectator Na+ + Cl- stays under NaCl's curve (300/1000 = 0.3
		// < 0.36) so no crystallisation interferes. v2 mass action with the
		// half-unit floor: a very insoluble mineral (log_k -8.3) precipitates to
		// exhaustion — residual 0 units.
		ResourceLocation water = Solution.WATER;
		ResourceLocation limestone = new ResourceLocation(ChemicalAddon.MODID, "limestone");
		ReactorTank tank = new ReactorTank(10000, () -> {});
		FluidStack mix = Mixture.create(
			Map.of(water, 1000),
			Map.of("Ca+2", 300, "Cl-1", 300, "Na+1", 300, "CO3-2", 300),
			2200); // total MUST equal Σ parts: the four domains share one ratio space
		tank.fill(mix, FluidAction.EXECUTE);

		Solution solved = RulesEngine.apply(tank);

		FluidStack result = tank.getFluids().get(0);
		helper.assertTrue(Mixture.deriveSuspendedAmounts(result).getOrDefault(limestone, 0) == 300,
			"Ca2+ + CO3-- should precipitate 300 mB limestone (got " + Mixture.deriveSuspendedAmounts(result) + ")");
		helper.assertTrue(Mixture.deriveSedimentAmounts(result).isEmpty(),
			"fast precipitation should stay suspended, not settle (got " + Mixture.deriveSedimentAmounts(result) + ")");
		Map<String, Integer> ions = Mixture.deriveIonAmounts(result);
		helper.assertTrue(ions.getOrDefault("Ca+2", 0) == 0, "Ca2+ should be consumed (got " + ions + ")");
		helper.assertTrue(ions.getOrDefault("CO3-2", 0) == 0, "CO3-- should be consumed (got " + ions + ")");
		helper.assertTrue(ions.getOrDefault("Na+1", 0) == 300, "Na+ should remain (got " + ions + ")");
		helper.assertTrue(ions.getOrDefault("Cl-1", 0) == 300, "Cl- should remain (got " + ions + ")");
		// speciation report: limestone moved ~all of its 300 mB (solver units)
		// and sits at/over saturation
		boolean reported = solved != null && solved.report().stream()
			.anyMatch(s -> s.target().equals(limestone) && s.moved() >= 299L * Chemistry.UNIT_PER_MB);
		helper.assertTrue(reported, "the speciation report should record limestone precipitating 300 units (got "
			+ (solved == null ? "null" : solved.report()) + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void rulesEngineRedissolvesOnHeating(GameTestHelper helper) {
		// the crystallisation equilibrium is reversible: settled crystals at
		// saturation hold at 20°C (mother liquor stays saturated), then fully
		// redissolve when the curve allows more at 100°C
		ResourceLocation water = Solution.WATER;
		ResourceLocation ammoniumNitrate = new ResourceLocation(ChemicalAddon.MODID, "ammonium_nitrate");
		ReactorTank tank = new ReactorTank(10000, () -> {});
		// the four domains share one ratio space, so the settled 116 must be
		// part of the create() call (post-hoc setSediment would rescale water)
		FluidStack cold = Mixture.create(
			Map.of(water, 200),
			Map.of("NH4+1", 384, "NO3-1", 384),
			Map.of(), Map.of(ammoniumNitrate, 116),
			1084);
		Temperature.set(cold, 20);
		tank.fill(cold, FluidAction.EXECUTE);

		RulesEngine.apply(tank);
		helper.assertTrue(Mixture.deriveSedimentAmounts(tank.getFluids().get(0)).getOrDefault(ammoniumNitrate, 0) == 115,
			"a saturated solution at 20°C holds its sediment (NH4+ hydrolysis shifts the "
				+ "curve balance by one unit; got "
				+ Mixture.deriveSedimentAmounts(tank.getFluids().get(0)) + ")");

		Temperature.set(tank.getFluids().get(0), 100);
		RulesEngine.apply(tank);
		helper.assertTrue(Mixture.deriveSedimentAmounts(tank.getFluids().get(0)).getOrDefault(ammoniumNitrate, 0) == 0,
			"heating to 100°C should redissolve all settled crystals (got "
				+ Mixture.deriveSedimentAmounts(tank.getFluids().get(0)) + ")");
		Map<String, Integer> ions = Mixture.deriveIonAmounts(tank.getFluids().get(0));
		helper.assertTrue(ions.getOrDefault("NH4+1", 0) == 500 && ions.getOrDefault("NO3-1", 0) == 500,
			"redissolved ions should return to solution (got " + ions + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void rulesEnginePrecipitatesBasicCopperCarbonate(GameTestHelper helper) {
		// the flagship demo: soda ash + copper sulfate -> malachite-green basic
		// copper carbonate slurry + a COLOURLESS sodium sulfate mother liquor
		// (Cu+2 is the only coloured species; once it leaves the solution the
		// tint falls back to clear). log_k -10 precipitates to exhaustion.
		ResourceLocation water = Solution.WATER;
		ResourceLocation copperCarbonate = new ResourceLocation(ChemicalAddon.MODID, "copper_carbonate");
		ReactorTank tank = new ReactorTank(10000, () -> {});
		// charge check: Cu +200, Na +400, SO4 -200, CO3 -400 = 0
		FluidStack mix = Mixture.create(
			Map.of(water, 1000),
			Map.of("Cu+2", 100, "SO4-2", 100, "Na+1", 400, "CO3-2", 200),
			1800);
		tank.fill(mix, FluidAction.EXECUTE);

		RulesEngine.apply(tank);

		FluidStack result = tank.getFluids().get(0);
		// on the U18 fine grid the solve reaches the true mass-action equilibrium:
		// all 100 Cu2+ land in malachite proper (50 f.u.), the CO2 side of the
		// net reaction ends as bicarbonate (~101) with the carbonate remainder
		// at ~49 — the old coarse-grid 35/30 mixed basic carbonate was a
		// truncation artifact (the JUNIT PrecipitationTest pins the same 50/0)
		int malachite = Mixture.deriveSuspendedAmounts(result).getOrDefault(copperCarbonate, 0);
		int cuOH = Mixture.deriveSuspendedAmounts(result)
			.getOrDefault(new ResourceLocation(ChemicalAddon.MODID, "copper_hydroxide"), 0);
		helper.assertTrue(2 * malachite + cuOH == 100,
			"every Cu2+ must land in the basic-carbonate precipitate (got "
				+ Mixture.deriveSuspendedAmounts(result) + ")");
		Map<String, Integer> ions = Mixture.deriveIonAmounts(result);
		helper.assertTrue(ions.getOrDefault("Cu+2", 0) == 0, "Cu2+ should be consumed (got " + ions + ")");
		helper.assertTrue(ions.getOrDefault("SO4-2", 0) == 100, "SO4-- spectator should remain (got " + ions + ")");
		helper.assertTrue(ions.getOrDefault("Na+1", 0) == 400, "Na+ spectator should remain (got " + ions + ")");
		helper.assertTrue(ions.getOrDefault("CO3-2", 0) == 49, "carbonate stays behind minus the precipitate (got " + ions + ")");
		helper.assertTrue(ions.getOrDefault("HCO3-1", 0) == 101,
			"the CO2 side of the net reaction ends as bicarbonate (got " + ions + ")");
		// the slurry reads malachite green: the suspended solid is the only
		// coloured domain left (colourless ions + green solid at full depth)
		helper.assertTrue(Mixture.getColor(result) != IonColors.CLEAR_TINT,
			"the copper carbonate slurry should render malachite green (got "
				+ Integer.toHexString(Mixture.getColor(result)) + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void rulesEngineAmmoniaMasksCopper(GameTestHelper helper) {
		// complexation solves BEFORE minerals (SpeciesManager ordering): with
		// ammonia present, [Cu(NH3)4]+2 ties up the copper first and the
		// carbonate finds nothing to precipitate — masking as an emergent
		// property of two equilibria sharing an ion.
		ResourceLocation water = Solution.WATER;
		ResourceLocation ammonia = new ResourceLocation(ChemicalAddon.MODID, "ammonia");
		ResourceLocation copperCarbonate = new ResourceLocation(ChemicalAddon.MODID, "copper_carbonate");
		ReactorTank tank = new ReactorTank(10000, () -> {});
		// charge check: Cu +40, Na +80, SO4 -40, CO3 -80 = 0
		// 400 mB ammonia in 1000 mB water stays fully dissolved (its gasSolubility
		// is 0.5 — the old 2000 mB charge would Henry-split a gas headspace)
		FluidStack mix = Mixture.create(
			Map.of(water, 1000, ammonia, 400),
			Map.of("Cu+2", 20, "SO4-2", 20, "Na+1", 80, "CO3-2", 40),
			1560);
		tank.fill(mix, FluidAction.EXECUTE);

		RulesEngine.apply(tank);

		FluidStack result = tank.getFluids().get(0);
		helper.assertTrue(Mixture.deriveSuspendedAmounts(result).getOrDefault(copperCarbonate, 0) == 0,
			"ammonia-masked copper must not precipitate carbonate (got " + Mixture.deriveSuspendedAmounts(result) + ")");
		Map<String, Integer> ions = Mixture.deriveIonAmounts(result);
		helper.assertTrue(ions.getOrDefault("[Cu(NH3)4]+2", 0) == 20,
			"all copper should sit in the tetraammine complex (got " + ions + ")");
		helper.assertTrue(ions.getOrDefault("Cu+2", 0) == 0, "no free copper should remain (got " + ions + ")");
		helper.assertTrue(ions.getOrDefault("CO3-2", 0) == 37,
			"carbonate should stay in solution (hydrolysis takes a few units; got " + ions + ")");
		helper.assertTrue(Mixture.deriveAmounts(result).getOrDefault(ammonia, 0) >= 300,
			"4 ammonia per copper should be consumed (weak-base ionisation takes a few more, got "
				+ Mixture.deriveAmounts(result) + ")");
		// visibly tinted (the ammonia-dominated blend is a pale blue-green, not
		// the pure complex blue — dissolved ammonia's own tint outweighs 20
		// units of complex by sheer amount; the point here is that colour
		// survives, unlike the carbonate case where everything clears)
		helper.assertTrue(Mixture.getColor(result) != IonColors.CLEAR_TINT,
			"the complex solution should stay visibly tinted (got "
				+ Integer.toHexString(Mixture.getColor(result)) + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void rulesEngineCommonIonEffect(GameTestHelper helper) {
		// excess sulfate drives Ca2+ lower than a 1:1 mix: with SO4 in excess
		// gypsum precipitates Ca to exhaustion (0 units left); at 1:1 the
		// equilibrium stops a unit short (a real saturated mother liquor) —
		// the common-ion effect as an emergent property of mass action
		ResourceLocation water = Solution.WATER;
		ResourceLocation gypsum = new ResourceLocation(ChemicalAddon.MODID, "gypsum");

		ReactorTank excess = new ReactorTank(10000, () -> {});
		// charge check: Ca +600, Na +400, SO4 -1000 = 0
		excess.fill(Mixture.create(Map.of(water, 1000),
			Map.of("Ca+2", 300, "SO4-2", 500, "Na+1", 400), 2200), FluidAction.EXECUTE);
		RulesEngine.apply(excess);
		Map<String, Integer> excessIons = Mixture.deriveIonAmounts(excess.getFluids().get(0));
		helper.assertTrue(excessIons.getOrDefault("Ca+2", 0) == 0,
			"excess sulfate should precipitate Ca to exhaustion (got " + excessIons + ")");
		helper.assertTrue(Mixture.deriveSuspendedAmounts(excess.getFluids().get(0)).getOrDefault(gypsum, 0) == 300,
			"300 mB gypsum should be suspended (got " + Mixture.deriveSuspendedAmounts(excess.getFluids().get(0)) + ")");

		ReactorTank equal = new ReactorTank(10000, () -> {});
		equal.fill(Mixture.create(Map.of(water, 1000),
			Map.of("Ca+2", 100, "SO4-2", 100, "Na+1", 100, "Cl-1", 100), 1400), FluidAction.EXECUTE);
		RulesEngine.apply(equal);
		Map<String, Integer> equalIons = Mixture.deriveIonAmounts(equal.getFluids().get(0));
		// the fine grid keeps a 0.5 mB saturated mother liquor — the mB view
		// rounds it to 0 or 1
		helper.assertTrue(equalIons.getOrDefault("Ca+2", 0) <= 1,
			"at 1:1 the saturated mother liquor keeps ≈0.5 mB Ca (got " + equalIons + ")");
		long gypsumMb = Mixture.deriveSuspendedAmounts(equal.getFluids().get(0)).getOrDefault(gypsum, 0);
		helper.assertTrue(gypsumMb == 99 || gypsumMb == 100,
			"~99.5 mB gypsum should be suspended (got " + gypsumMb + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void rulesEngineDissolvesDroppedSolidItems(GameTestHelper helper) {
		// solid items with a solubility curve dissolve into the aqueous phase
		// one item per tick (1 item = 1000 formula units) and STOP at
		// saturation: the headroom check means a near-saturated brine refuses
		// the next whole item
		ResourceLocation water = Solution.WATER;
		ReactorTank tank = new ReactorTank(20000, () -> {});
		tank.fill(new FluidStack(Fluids.WATER, 5000), FluidAction.EXECUTE);
		ItemStackHandler items = new ItemStackHandler(1);
		items.setStackInSlot(0, new ItemStack(AllItems.ROCK_SALT.get(), 5));

		RulesEngine.apply(tank, false, items);
		Map<String, Integer> ions = Mixture.deriveIonAmounts(tank.getFluids().get(0));
		helper.assertTrue(ions.getOrDefault("Na+1", 0) == 1000 && ions.getOrDefault("Cl-1", 0) == 1000,
			"one rock salt item should dissolve into 1000 units Na+ + Cl- (got " + ions + ")");
		helper.assertTrue(items.getStackInSlot(0).getCount() == 4,
			"exactly one item should be consumed per tick (got " + items.getStackInSlot(0).getCount() + ")");

		// headroom now floor(0.36 x 5000) - 1000 = 800 < 1000: the next whole
		// item does not fit — the brine is done dissolving
		RulesEngine.apply(tank, false, items);
		helper.assertTrue(items.getStackInSlot(0).getCount() == 4,
			"a brine with <1000 units of headroom must not dissolve another whole item (got "
				+ items.getStackInSlot(0).getCount() + ")");
		helper.assertTrue(Mixture.deriveAmounts(tank.getFluids().get(0)).getOrDefault(water, 0) == 5000,
			"dissolution should not consume water (got " + Mixture.deriveAmounts(tank.getFluids().get(0)) + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void rulesEngineEvaporationConcentratesAndCrystallises(GameTestHelper helper) {
		// an open boiling vessel vents water (50 mB per reaction tick),
		// concentrating the solution until the solubility curve is exceeded and
		// the excess crystallises — evaporative crystallisation as a pure
		// emergent chain. A sealed vessel keeps its solvent.
		ResourceLocation water = Solution.WATER;
		ResourceLocation ammoniumNitrate = new ResourceLocation(ChemicalAddon.MODID, "ammonium_nitrate");
		ReactorTank tank = new ReactorTank(20000, () -> {});
		FluidStack hot = Mixture.create(
			Map.of(water, 1000),
			Map.of("NH4+1", 500, "NO3-1", 500),
			2000);
		Temperature.set(hot, 100);
		tank.fill(hot, FluidAction.EXECUTE);

		// sealed control: no evaporation, no crystallisation at 100°C
		RulesEngine.apply(tank, false, null);
		helper.assertTrue(Mixture.deriveAmounts(tank.getFluids().get(0)).getOrDefault(water, 0) == 1000,
			"a sealed vessel must not evaporate (got " + Mixture.deriveAmounts(tank.getFluids().get(0)) + ")");

		// U16 latent heat: one open vent of 50 mB steam carries 2260 J/unit
		// away — over a 2000 mB feed that is -13 °C, so without a heat source
		// the body quenches itself below the boiling point and STOPS venting
		// (boiling needs energy; the self-limiting negative feedback)
		RulesEngine.apply(tank, true, null);
		FluidStack vented = tank.getFluids().get(0);
		helper.assertTrue(Mixture.deriveAmounts(vented).getOrDefault(water, 0) == 950,
			"one open tick should vent 50 mB of steam (got " + Mixture.deriveAmounts(vented) + ")");
		helper.assertTrue(Temperature.get(vented) < 100,
			"latent heat must cool the body below the boil (got " + Temperature.get(vented) + "°C)");
		RulesEngine.apply(tank, true, null);
		helper.assertTrue(Mixture.deriveAmounts(tank.getFluids().get(0)).getOrDefault(water, 0) == 950,
			"no heat source: a quenched body must stop venting (got " + Mixture.deriveAmounts(tank.getFluids().get(0)) + ")");

		// with a heat source (the burner's job — updateHeat in a live reactor;
		// here re-pinned each tick) the boil continues to dryness. Kinetics
		// barely crystallise on the way (the supersaturated window is metastable
		// and short-lived at these rates) — then the EVAPORITE rule fires: no
		// solvent left, all dissolved salt crashes out. Boiling a pot dry
		// yields dry salt.
		for (int i = 0; i < 25; i++) {
			FluidStack stack = tank.getFluids().get(0);
			if (!Mixture.deriveAmounts(stack).getOrDefault(water, 0).equals(0)) {
				Temperature.set(stack, 100); // the burner holding the boil
			}
			RulesEngine.apply(tank, true, null);
		}
		FluidStack result = tank.getFluids().get(0);
		helper.assertTrue(Mixture.deriveAmounts(result).getOrDefault(water, 0) == 0,
			"an open boiling vessel should vent all water (got " + Mixture.deriveAmounts(result) + ")");
		int dried = Mixture.deriveSedimentAmounts(result).getOrDefault(ammoniumNitrate, 0);
		helper.assertTrue(dried >= 495,
			"boiling dry should crash out all the ammonium nitrate as evaporite (got " + dried + ")");
		Map<String, Integer> ions = Mixture.deriveIonAmounts(result);
		helper.assertTrue(ions.getOrDefault("NH4+1", 0) == 0,
			"nothing stays dissolved with the solvent gone (got " + ions + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void rulesEngineCrystallisesOnCooling(GameTestHelper helper) {
		// NH4+ + NO3- (aq): 500 f.u. / 200 water = 2.5, below the 100°C
		// threshold (8.71) but only 30% above the 20°C one (1.92) — under the
		// U14 kinetics model that is INSIDE the metastable zone: cooling alone
		// crystallises nothing, and one seed grain collapses it to equilibrium.
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

		// cooled unseeded: 30% supersaturation is below the nucleation gate —
		// the solution sits METASTABLE (this is the quench-cooled state)
		Temperature.set(tank.getFluids().get(0), 20);
		RulesEngine.apply(tank);
		helper.assertTrue(Mixture.deriveSedimentAmounts(tank.getFluids().get(0)).isEmpty(),
			"a shallowly supersaturated solution must stay metastable unseeded (got "
				+ Mixture.deriveSedimentAmounts(tank.getFluids().get(0)) + ")");

		// drop in one grain: seeded growth runs to the curve over ticks
		// (equilibrium target 500 - floor(1.92 x 200) = 116)
		FluidStack cooled = tank.getFluids().get(0);
		Map<ResourceLocation, Integer> sediment = new LinkedHashMap<>(Mixture.deriveSedimentAmounts(cooled));
		sediment.merge(ammoniumNitrate, 1, Integer::sum);
		tank.setContents(Mixture.deriveAmounts(cooled), Mixture.deriveIonAmounts(cooled), Map.of(), sediment, 20);
		for (int i = 0; i < 400; i++) {
			RulesEngine.apply(tank);
		}
		int crystallised = Mixture.deriveSedimentAmounts(tank.getFluids().get(0)).getOrDefault(ammoniumNitrate, 0);
		helper.assertTrue(crystallised >= 113 && crystallised <= 117,
			"seeding should collapse the metastable solution to its ~116 mB equilibrium (got " + crystallised + ")");
		Map<String, Integer> ions = Mixture.deriveIonAmounts(tank.getFluids().get(0));
		helper.assertTrue(ions.getOrDefault("NH4+1", 0) >= 383 && ions.getOrDefault("NH4+1", 0) <= 390,
			"a saturated mother liquor stays behind (got " + ions + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void grainItemSeedsMetastableSolution(GameTestHelper helper) {
		// U15②: a REAL grain item dropped into a metastable solution seeds it —
		// the grain cannot dissolve (supersaturated) so it joins the Sediment
		// domain, and seeded growth collapses the supersaturation to the curve.
		// 90 f.u. NaCl / 200 water at 20 °C = 0.45 vs cap 0.36: 25% over →
		// inside the metastable zone unseeded.
		ResourceLocation water = Solution.WATER;
		ResourceLocation rockSalt = new ResourceLocation(ChemicalAddon.MODID, "rock_salt");
		ReactorTank tank = new ReactorTank(10000, () -> {});
		FluidStack supersaturated = Mixture.create(
			Map.of(water, 200),
			Map.of("Na+1", 90, "Cl-1", 90),
			380);
		Temperature.set(supersaturated, 20);
		tank.fill(supersaturated, FluidAction.EXECUTE);

		RulesEngine.apply(tank);
		helper.assertTrue(Mixture.deriveSedimentAmounts(tank.getFluids().get(0)).isEmpty(),
			"unseeded shallow supersaturation must stay metastable");

		ItemStackHandler items = new ItemStackHandler(1);
		items.setStackInSlot(0, new ItemStack(AllItems.ROCK_SALT_GRAIN.get(), 1));
		for (int i = 0; i < 200; i++) {
			RulesEngine.apply(tank, false, items);
		}
		helper.assertTrue(items.getStackInSlot(0).isEmpty(),
			"the seed grain should be consumed into the sediment domain");
		// the grain itself (62.5 mB) + the collapsed excess (90 − floor(0.36×200) = 18)
		int settled = Mixture.deriveSedimentAmounts(tank.getFluids().get(0)).getOrDefault(rockSalt, 0);
		helper.assertTrue(settled >= 79 && settled <= 82,
			"seeded growth should collapse to the curve leaving the grain + excess (~80 mB, got " + settled + ")");
		int motherLiquor = Mixture.deriveIonAmounts(tank.getFluids().get(0)).getOrDefault("Na+1", 0);
		helper.assertTrue(motherLiquor >= 71 && motherLiquor <= 73,
			"a saturated NaCl mother liquor (~72 mB) stays behind (got " + motherLiquor + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void sedimentExtractionIsWholeLumpOnly(GameTestHelper helper) {
		// U15③: extraction may never pick species (plans/03 §12). Mixed
		// sediment = mixed-residue items; strict single species = pure items;
		// the sub-item remainder stays in the pot (the heirloom seed).
		ResourceLocation water = Solution.WATER;
		ResourceLocation rockSalt = new ResourceLocation(ChemicalAddon.MODID, "rock_salt");
		ResourceLocation mgCl2 = new ResourceLocation(ChemicalAddon.MODID, "magnesium_chloride");

		// mixed: NaCl 1500 mB + MgCl2 1500 mB -> exactly 3 residue items, no pure salt
		ReactorTank mixed = new ReactorTank(10000, () -> {});
		mixed.fill(Mixture.create(Map.of(water, 1000), Map.of(), Map.of(),
			Map.of(rockSalt, 1500, mgCl2, 1500), 4000), FluidAction.EXECUTE);
		List<ItemStack> out = new ArrayList<>();
		mixed.extractSolids(out::add, true);
		helper.assertTrue(out.size() == 1 && out.get(0).getCount() == 3
			&& out.get(0).is(AllItems.MIXED_RESIDUE.get()),
			"3000 mB of mixed sediment must extract as 3 mixed-residue items (got " + out + ")");
		Map<ResourceLocation, Integer> parts = MixedResidueItem.parts(out.get(0));
		helper.assertTrue(parts.containsKey(rockSalt) && parts.containsKey(mgCl2),
			"the residue NBT must carry both species (got " + parts + ")");
		helper.assertTrue(Mixture.deriveSedimentAmounts(mixed.getFluids().get(0)).isEmpty()
			|| Mixture.getSediment(mixed.getFluids().get(0)).isEmpty(),
			"the whole lump leaves the pot");
		// U16.5 entrainment: the 3000 mB lump drags its pore liquor along —
		// 30% of the extracted volume leaves as the cake's mother liquor
		helper.assertTrue(mixed.getFluids().get(0).getAmount() == 100,
			"the pore liquor leaves with the cake (100 mB stays, got "
				+ mixed.getFluids().get(0).getAmount() + ")");

		// strict single species: 2500 mB NaCl -> 2 PURE items, 500 mB stays
		ReactorTank pure = new ReactorTank(10000, () -> {});
		pure.fill(Mixture.create(Map.of(water, 1000), Map.of(), Map.of(),
			Map.of(rockSalt, 2500), 3500), FluidAction.EXECUTE);
		List<ItemStack> pureOut = new ArrayList<>();
		pure.extractSolids(pureOut::add, true);
		helper.assertTrue(pureOut.size() == 1 && pureOut.get(0).getCount() == 2
			&& pureOut.get(0).is(AllItems.ROCK_SALT.get()),
			"single-species sediment must extract as pure items (got " + pureOut + ")");
		int remainder = Mixture.deriveSedimentAmounts(pure.getFluids().get(0)).getOrDefault(rockSalt, 0);
		helper.assertTrue(remainder >= 499 && remainder <= 501,
			"the sub-item remainder stays as the heirloom seed (~500 mB, got " + remainder + ")");

		// one grain's worth (water:sediment 16:1, ~62.5 mB) can never form an
		// item: it stays forever and keeps re-seeding the pot
		ReactorTank pot = new ReactorTank(10000, () -> {});
		pot.fill(Mixture.create(
			Map.of(water, 16000),
			Map.of(),
			Map.of(),
			Map.of(rockSalt, 1000),
			1062), FluidAction.EXECUTE);
		List<ItemStack> none = new ArrayList<>();
		long extracted = pot.extractSolids(none::add, true);
		helper.assertTrue(extracted == 0 && none.isEmpty(),
			"a sub-item sediment remainder is not extractable (got " + extracted + ")");
		helper.assertTrue(!Mixture.deriveSedimentAmounts(pot.getFluids().get(0)).isEmpty(),
			"the heirloom seed stays in the pot");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void mixedResidueDissolvesBackToIons(GameTestHelper helper) {
		// U15③ "dissolving is the assay": a residue item thrown into water
		// expands its NBT composition exactly into the ion domain, charge-neutral
		ResourceLocation rockSalt = new ResourceLocation(ChemicalAddon.MODID, "rock_salt");
		ResourceLocation mgCl2 = new ResourceLocation(ChemicalAddon.MODID, "magnesium_chloride");
		ItemStack residue = MixedResidueItem.of(Map.of(
			rockSalt, 5_000_000L,
			mgCl2, 5_000_000L));
		ItemStackHandler items = new ItemStackHandler(1);
		items.setStackInSlot(0, residue);

		ReactorTank tank = new ReactorTank(10000, () -> {});
		tank.fill(new FluidStack(Fluids.WATER, 5000), FluidAction.EXECUTE);
		RulesEngine.apply(tank, false, items);
		helper.assertTrue(items.getStackInSlot(0).isEmpty(), "the residue item should fully dissolve");
		Map<String, Integer> ions = Mixture.deriveIonAmounts(tank.getFluids().get(0));
		helper.assertTrue(ions.getOrDefault("Na+1", 0) == 500,
			"the NaCl share should expand to 500 mB Na+ + Cl- (got " + ions + ")");
		helper.assertTrue(ions.getOrDefault("Mg+2", 0) == 500 && ions.getOrDefault("Cl-1", 0) == 1500,
			"the MgCl2 share should expand to Mg+2 + 2 Cl- (got " + ions + ")");
		helper.assertTrue(Mixture.isChargeNeutral(ions), "the expanded ion set must be charge-neutral");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void wetCakeEntrainssMotherLiquor(GameTestHelper helper) {
		// U16.5: extraction is mechanically imperfect — the cake drags its pore
		// liquor along. A single-species salt bed under a DIRTY mother liquor
		// therefore extracts as a residue (not a pure item): the entrained Mg²⁺
		// travels with it, and only washing earns the pure item.
		ResourceLocation water = Solution.WATER;
		ResourceLocation rockSalt = new ResourceLocation(ChemicalAddon.MODID, "rock_salt");
		ReactorTank tank = new ReactorTank(10000, () -> {});
		tank.fill(Mixture.create(
			Map.of(water, 1000),
			Map.of("Na+1", 400, "Cl-1", 600, "Mg+2", 100),
			Map.of(),
			Map.of(rockSalt, 1200),
			3300), FluidAction.EXECUTE);
		List<ItemStack> out = new ArrayList<>();
		tank.extractSolids(out::add, true);
		helper.assertTrue(out.size() == 1 && out.get(0).is(AllItems.MIXED_RESIDUE.get()),
			"an unwashed single-species cake under dirty liquor must be a residue (got " + out + ")");
		Map<ResourceLocation, Integer> parts = MixedResidueItem.parts(out.get(0));
		helper.assertTrue(parts.containsKey(rockSalt), "the solids part must be salt (got " + parts + ")");
		Map<String, Integer> liquor = MixedResidueItem.liquorParts(out.get(0));
		helper.assertTrue(liquor.containsKey("Mg+2") && liquor.containsKey("Na+1") && liquor.containsKey("water"),
			"the pore liquor must ride along in the NBT (got " + liquor + ")");
		// vessel side: a proportional share of the liquid left with the cake
		helper.assertTrue(Math.abs(speciesAmount(tank.getFluids().get(0), "water") - 857) <= 3,
			"the entrained water share leaves the pot (~857 stays, got "
				+ speciesAmount(tank.getFluids().get(0), "water") + ")");
		helper.assertTrue(Math.abs(ionAmount(tank.getFluids().get(0), "Mg+2") - 86) <= 2,
			"the entrained magnesium share leaves the pot (~86 stays, got "
				+ ionAmount(tank.getFluids().get(0), "Mg+2") + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void reslurryWashingDecaysMotherLiquor(GameTestHelper helper) {
		// U16.5 reslurry washing: decant (skim the clear liquid — the settled
		// bed and its pore liquor stay) → refill clean water → decant again.
		// The retained liquor decays geometrically; the bed never leaves.
		ResourceLocation water = Solution.WATER;
		ResourceLocation rockSalt = new ResourceLocation(ChemicalAddon.MODID, "rock_salt");
		ReactorTank tank = new ReactorTank(20000, () -> {});
		tank.fill(Mixture.create(
			Map.of(water, 1000),
			Map.of("Na+1", 500, "Cl-1", 500),
			Map.of(),
			Map.of(rockSalt, 2000),
			4000), FluidAction.EXECUTE);

		// round 1: liquid 2000 mB, pore floor 600 mB -> 1400 mB skimmable
		FluidStack first = tank.decantClear(100_000, FluidAction.EXECUTE);
		helper.assertTrue(first.getAmount() == 1400,
			"the first decant skims the free liquor only (got " + first.getAmount() + ")");
		helper.assertTrue(Math.abs(ionAmount(tank.getFluids().get(0), "Na+1") - 150) <= 1,
			"proportional share leaves: Na 500 -> ~150 (got " + ionAmount(tank.getFluids().get(0), "Na+1") + ")");
		helper.assertTrue(Mixture.deriveSedimentAmounts(tank.getFluids().get(0)).getOrDefault(rockSalt, 0) == 2000,
			"the crystal bed never leaves through a decant");

		// the pore floor holds: a second decant without refill draws nothing
		helper.assertTrue(tank.decantClear(100_000, FluidAction.EXECUTE).isEmpty(),
			"a decant cannot reach below the bed's pore liquor");

		// round 2 after refill: same geometry, geometric decay 150 -> ~35
		tank.fill(new FluidStack(Fluids.WATER, 2000), FluidAction.EXECUTE);
		tank.collapseIfNeeded(); // merge the refill into the aqueous phase (the rules engine does this every tick)
		FluidStack second = tank.decantClear(100_000, FluidAction.EXECUTE);
		helper.assertTrue(second.getAmount() == 2000, "the refilled free liquor is skimmable again");
		int na = ionAmount(tank.getFluids().get(0), "Na+1");
		helper.assertTrue(na >= 32 && na <= 38,
			"the second round decays geometrically (~35, got " + na + ")");
		int bed = Mixture.deriveSedimentAmounts(tank.getFluids().get(0)).getOrDefault(rockSalt, 0);
		helper.assertTrue(bed >= 1998 && bed <= 2002,
			"the bed survives the wash (mB view ±2, got " + bed + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void filterWashDisplacementPurifiesCake(GameTestHelper helper) {
		// U16.5 displacement wash: clean rinse water pushed through the cake
		// (13 pore volumes at ε=0.75) displaces the mother liquor to below the
		// unit grid — the single-species cake comes out as the PURE item, and
		// the spent wash water joins the filtrate.
		ResourceLocation water = Solution.WATER;
		ResourceLocation rockSalt = new ResourceLocation(ChemicalAddon.MODID, "rock_salt");
		ReactorTank input = new ReactorTank(4000, () -> {});
		input.fill(Mixture.create(
			Map.of(water, 1000),
			Map.of("Na+1", 400, "Cl-1", 400),
			Map.of(rockSalt, 1000),
			Map.of(),
			2800), FluidAction.EXECUTE);
		ReactorTank wash = new ReactorTank(4000, () -> {});
		wash.fill(new FluidStack(Fluids.WATER, 4000), FluidAction.EXECUTE);
		ReactorTank filtrate = new ReactorTank(4000, () -> {});

		List<ItemStack> out = new ArrayList<>();
		input.extractSolids(out::add, false, wash, filtrate);
		helper.assertTrue(out.size() == 1 && out.get(0).is(AllItems.ROCK_SALT.get()),
			"a fully displacement-washed single-species cake must come out PURE (got " + out + ")");
		helper.assertTrue(wash.getTotalAmount() == 100,
			"the useful wash maximum is 13 pore volumes (3900 mB spent, got " + (4000 - wash.getTotalAmount()) + ")");
		helper.assertTrue(filtrate.getTotalAmount() == 3900,
			"the spent wash water joins the filtrate (got " + filtrate.getTotalAmount() + ")");
		// the mother liquor it displaced stays in the input as filtrate-to-be
		helper.assertTrue(ionAmount(input.getFluids().get(0), "Na+1") == 400,
			"the displaced mother liquor stays behind for the filtrate");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void washingRedissolvesSolubleBedButNotMinerals(GameTestHelper helper) {
		// U16.5: the wash water's yield cost is emergent — refilling a soluble
		// (curve-species) bed with clean water redissolves product (real
		// washing loss), while a Ksp mineral bed barely notices.
		ResourceLocation water = Solution.WATER;
		ResourceLocation rockSalt = new ResourceLocation(ChemicalAddon.MODID, "rock_salt");
		ResourceLocation limestone = new ResourceLocation(ChemicalAddon.MODID, "limestone");

		// the soluble bed: post-decant pore state + a clean-water refill
		ReactorTank salt = new ReactorTank(20000, () -> {});
		salt.fill(Mixture.create(
			Map.of(water, 2300),
			Map.of("Na+1", 150, "Cl-1", 150),
			Map.of(),
			Map.of(rockSalt, 2000),
			4600), FluidAction.EXECUTE);
		RulesEngine.apply(salt);
		int saltBed = Mixture.deriveSedimentAmounts(salt.getFluids().get(0)).getOrDefault(rockSalt, 0);
		helper.assertTrue(saltBed >= 1250 && saltBed <= 1400,
			"refilling clean water redissolves NaCl product (yield loss, bed ~1322, got " + saltBed + ")");

		// the mineral bed: CaCO3 stays put (Ksp insoluble)
		ReactorTank mineral = new ReactorTank(20000, () -> {});
		mineral.fill(Mixture.create(
			Map.of(water, 2300),
			Map.of(),
			Map.of(),
			Map.of(limestone, 2000),
			4300), FluidAction.EXECUTE);
		RulesEngine.apply(mineral);
		int mineralBed = Mixture.deriveSedimentAmounts(mineral.getFluids().get(0)).getOrDefault(limestone, 0);
		helper.assertTrue(mineralBed >= 1995,
			"a Ksp mineral bed survives washing essentially untouched (got " + mineralBed + ")");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void wetCakeDissolvesBackConservingMass(GameTestHelper helper) {
		// U16.5: dissolving a wet cake expands BOTH phases exactly — the solid
		// salt AND the pore liquor's ions/water land in the ion/molecular
		// domains, charge-neutral, in the NBT's exact proportions.
		ResourceLocation rockSalt = new ResourceLocation(ChemicalAddon.MODID, "rock_salt");
		ItemStack cake = MixedResidueItem.of(
			Map.of(rockSalt, 1900L),
			Map.of("Na+1", 100L, "Cl-1", 100L),
			100L);
		ItemStackHandler items = new ItemStackHandler(1);
		items.setStackInSlot(0, cake);

		ReactorTank tank = new ReactorTank(10000, () -> {});
		tank.fill(new FluidStack(Fluids.WATER, 5000), FluidAction.EXECUTE);
		RulesEngine.apply(tank, false, items);
		helper.assertTrue(items.getStackInSlot(0).isEmpty(), "the wet cake should fully dissolve");
		// parts gcd to {salt 19, Na 1, Cl 1, water 1}/22 of one item:
		// Na⁺ = (19 + 1)/22 × 1000 mB = 909 mB, water gains 1/22 of a bucket
		FluidStack result = tank.getFluids().get(0);
		int na = ionAmount(result, "Na+1");
		int cl = ionAmount(result, "Cl-1");
		helper.assertTrue(Math.abs(na - 909) <= 2 && Math.abs(cl - 909) <= 2,
			"both the solid and the liquor's sodium land in the ion domain (got Na=" + na + " Cl=" + cl + ")");
		helper.assertTrue(Math.abs(speciesAmount(result, "water") - 5045) <= 2,
			"the liquor's water rejoins the solvent (got " + speciesAmount(result, "water") + ")");
		helper.assertTrue(Mixture.isChargeNeutral(Mixture.deriveIonAmounts(result)), "charge neutrality holds");
		helper.succeed();
	}

	@GameTest(template = "empty_15", timeoutTicks = TICKS * 20)
	public static void conductivityGaugeReadsIonicStrength(GameTestHelper helper) {
		// S18 (U16.5): conductivity = 10 × (ion units / water units) on the
		// declared mS scale — molecular solutes do not conduct (the ammonia
		// distinction), and the wall gauge reads its own vessel with the
		// INVERTED alarm: signal = conductivity fell to/below the setpoint.
		ResourceLocation water = Solution.WATER;
		ResourceLocation ammonia = new ResourceLocation(ChemicalAddon.MODID, "ammonia");
		ReactorTank brine = new ReactorTank(10000, () -> {});
		brine.fill(Mixture.create(Map.of(water, 1000), Map.of("Na+1", 500, "Cl-1", 500), 2000),
			FluidAction.EXECUTE);
		helper.assertTrue(com.yu1745.chemicaladdon.reactor.AbstractConductivityGaugeBlockEntity
			.conductivityOf(brine) == 10, "a 1:1 brine reads 10 mS");
		ReactorTank ammoniaWater = new ReactorTank(10000, () -> {});
		ammoniaWater.fill(Mixture.create(Map.of(water, 1000, ammonia, 200), Map.of(), 1200),
			FluidAction.EXECUTE);
		helper.assertTrue(com.yu1745.chemicaladdon.reactor.AbstractConductivityGaugeBlockEntity
			.conductivityOf(ammoniaWater) == 0, "molecular ammonia does not conduct");

		// the wall form: shell block, bound, reading its own vessel
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();
		BlockState controller = AllBlocks.REACTOR_CONTROLLER.get().defaultBlockState();
		BlockState gaugeBlock = AllBlocks.CONDUCTIVITY_GAUGE.get().defaultBlockState();
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				helper.setBlock(new BlockPos(x, 1, z), brick);
				helper.setBlock(new BlockPos(x, 3, z), brick);
			}
		}
		for (int x = 1; x <= 3; x++) {
			for (int z = 1; z <= 3; z++) {
				if (x == 2 && z == 2) {
					continue;
				}
				BlockPos p = new BlockPos(x, 2, z);
				helper.setBlock(p, (x == 2 && z == 1) ? controller : (x == 3 && z == 1) ? gaugeBlock : brick);
			}
		}
		ReactorControllerBlockEntity reactor = (ReactorControllerBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 1));
		helper.assertTrue(reactor.tryAssemble().ok(), "reactor with a conductivity gauge wall should assemble");
		com.yu1745.chemicaladdon.reactor.ConductivityGaugeBlockEntity gauge =
			(com.yu1745.chemicaladdon.reactor.ConductivityGaugeBlockEntity) helper.getBlockEntity(new BlockPos(3, 2, 1));
		helper.assertTrue(gauge != null && gauge.getMasterPos() != null,
			"the wall gauge must be bound to the controller");

		reactor.getTank().fill(brine.drain(100_000, FluidAction.EXECUTE), FluidAction.EXECUTE);
		gauge.tick();
		helper.assertTrue(gauge.isAttached() && gauge.getConductivity() == 10,
			"the wall gauge reads its vessel's conductivity (got " + gauge.getConductivity() + ")");
		helper.assertTrue(!gauge.isAlarm(), "a dirty vessel at 10 mS vs setpoint 5 mS is not clean");

		reactor.getTank().clear();
		reactor.getTank().fill(new FluidStack(Fluids.WATER, 1000), FluidAction.EXECUTE);
		gauge.tick();
			helper.assertTrue(gauge.isAlarm(),
				"the inverted alarm fires when conductivity falls to/below the setpoint (washing-complete)");
			helper.succeed();
		}

	// ------------------------------------------------------- U17 instruments

	/** The minimal 3×3×3 reactor with one wall slot replaced by a gauge block. */
}
