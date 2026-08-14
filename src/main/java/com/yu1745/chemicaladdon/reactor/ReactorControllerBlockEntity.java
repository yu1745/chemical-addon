package com.yu1745.chemicaladdon.reactor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.FluidIngredient;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.composition.Species;
import com.yu1745.chemicaladdon.composition.SpeciesManager;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.fluid.Temperature;
import com.yu1745.chemicaladdon.recipe.AllRecipeTypes;
import com.yu1745.chemicaladdon.recipe.ChemicalReactionRecipe;
import com.yu1745.chemicaladdon.recipe.SolutionIngredient;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.registry.AllBlocks;

import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Reaction vessel controller. Holds the multi-fluid tank (stream container),
 * the item buffer, temperature, structure state, the reaction engine
 * (auto-matching of whitelisted chemical_reaction recipes with progress /
 * intermediate completion / delta-heat) and the control panel menu.
 *
 * M1: 3x3x3 structure, fluid IO via Forge FLUID_HANDLER (Create pipes connect
 * directly), item IO via ITEM_HANDLER (funnels/hoppers), Blaze Burner heating,
 * recipes run automatically when inputs and conditions match.
 */
public class ReactorControllerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

	public static final int TANK_CAPACITY = 1000; // mB per interior block (1 bucket — small enough that a few buckets visibly raise the surface)
	public static final int AMBIENT_TEMP = 20;
	public static final int ITEM_SLOTS = 4;
	public static final int MAX_TEMP = 1000;
	public static final int MIN_SIZE = 3; // shell footprint W and height H both in [3, 7]
	public static final int MAX_SIZE = 7;

	private static final int HEAT_TICK = 20;
	private static final int REACTION_TICK = 10;

	private final ReactorTank tank = new ReactorTank(TANK_CAPACITY, this::onTankChanged);
	private final ItemStackHandler items = new ItemStackHandler(ITEM_SLOTS) {
		@Override
		protected void onContentsChanged(int slot) {
			onTankChanged();
		}
	};
	private final LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(() -> tank);
	private final LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> items);

	/** Why the vessel is (not) reacting; shown in the goggles HUD. */
	public enum ReactorStatus {
		NOT_ASSEMBLED, REACTING, TEMPERATURE, OUTPUT_FULL, NO_RECIPE
	}

	/** What exactly is wrong with an attempted assembly (for the failure message). */
	public enum AssembleIssue {
		BOTTOM_GAP, TOP_GAP, RING_GAP, INTERIOR_BLOCKED, TOO_SHORT, PARTIAL_TOP
	}

	/** Structured result of an assembly attempt: which face, which issue, where. */
	public record AssembleResult(boolean ok, @Nullable Direction face, @Nullable AssembleIssue issue,
		@Nullable BlockPos issuePos) {
		public static AssembleResult success() {
			return new AssembleResult(true, null, null, null);
		}
	}

	private boolean assembled = false;
	private boolean open = false; // open-topped (interior visible) vs sealed
	private int size = 0; // shell footprint W (W x W base, 0 = not assembled)
	private int height = 0; // interior ring-layer count (shell height H-2); interior is (W-2)^2 x height
	private int ringLayer = 0; // which ring layer the controller sits on (0 = bottom); for top/bottom port resolution
	// progressive fluid spill after structural breakage (one source per few ticks)
	private final List<FluidStack> pendingSpill = new ArrayList<>();
	@Nullable
	private BlockPos spillLeakPos = null;
	private int spillTimer = 0;
	private int tickCounter = 0;
	private float progress = 0;
	private ReactorStatus status = ReactorStatus.NOT_ASSEMBLED;
	@Nullable
	private ResourceLocation activeRecipe = null;
	@Nullable
	private Direction inward = null; // direction from the controller into the vessel (for item rendering)

	/**
	 * Client-side fluid surface animation: chases the ABSOLUTE surface height in
	 * blocks (fill × interior height), NOT the fill fraction. Create's FluidTank
	 * can chase the fraction because its geometry never changes — this vessel's
	 * height/capacity changes on brick break/place (shrink/extend) while the
	 * amount stays put, and a fraction chase would blend the old fraction with
	 * the new height mid-transition, twitching a surface that never physically
	 * moved (the true surface is total / (TANK_CAPACITY · (w-2)²), independent
	 * of the ring count). Null until first client use so the animation starts
	 * AT the true surface instead of rising from the floor on chunk load
	 * (FluidTankBlockEntity's {@code fluidLevel == null} pattern).
	 */
	private LerpedFloat renderedLevel;

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		// no behaviours yet (gauges/ValueSettings will register here in later milestones)
	}

	public ReactorControllerBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.REACTOR_CONTROLLER.get(), pos, state);
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null) {
			return;
		}
		if (level.isClientSide) {
			if (renderedLevel == null) {
				// first frame after the client learned of the vessel: start AT the
				// true surface (no rise-from-floor on chunk load / dimension entry)
				renderedLevel = LerpedFloat.linear().startWithValue(targetRenderedLevel());
			}
			// chase the ABSOLUTE surface height (see renderedLevel): a capacity
			// change with the amount unchanged (shrink on brick break, regrow on
			// placement) leaves this target exactly where it was, so the surface
			// only eases when fluid actually moves
			renderedLevel.chase(targetRenderedLevel(), 0.5, LerpedFloat.Chaser.EXP);
			renderedLevel.tickChaser();
			return;
		}
		tickCounter++;
		if (tickCounter % HEAT_TICK == 0) {
			updateHeat();
		}
		if (tickCounter % REACTION_TICK == 0) {
			tickReaction();
		}
		tickSpill();
		absorbFromWorld();
		// settle multi-fluid contents into a single mixture (or degrade to pure);
		// runs after absorb/fill so coexisting species collapse same-tick
		tank.collapseIfNeeded();
	}

	/**
	 * Open-topped vessels absorb physical contents dropped/poured into the
	 * interior (the in-world side of the spill loop): item entities become
	 * buffer data (rendered floating inside), fluid source blocks become tank
	 * data — same tick, so "throwing things in" feels immediate.
	 */
	private void absorbFromWorld() {
		if (level == null || level.isClientSide || !assembled || !open || inward == null) {
			return;
		}
		int iw = size - 2; // interior footprint (iw x iw)
		Direction side = inward.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
		int sStart = -((size - 1) / 2) + 1; // interior s range starts one in from the wall
		BlockPos core = worldPosition.offset(
			side.getStepX() * sStart + inward.getStepX(), 0,
			side.getStepZ() * sStart + inward.getStepZ());
		// absorb area = the interior column (from its FLOOR, which sits ringLayer
		// below the controller — the controller may be mounted on ANY ring) up
		// through the open rim and one block above it: a bucket click from inside
		// the vessel (or from below the rim) places the source on the far side of
		// the clicked block, i.e. at the rim layer or one above — those must be in
		// range or poured fluids land outside the polled area and are never absorbed
		int yBottom = getInteriorBottomRelY();
		int yTop = getRoofRelY() + 1;
		var area = new net.minecraft.world.phys.AABB(core.getX(), core.getY() + yBottom, core.getZ(),
			core.getX() + iw, core.getY() + yTop + 1, core.getZ() + iw);

		// items thrown in through the open top
		boolean absorbed = false;
		for (net.minecraft.world.entity.item.ItemEntity entity : level
			.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, area)) {
			ItemStack stack = entity.getItem();
			if (stack.isEmpty()) {
				entity.discard();
				continue;
			}
			ItemStack remainder = ItemHandlerHelper.insertItemStacked(items, stack.copy(), false);
			if (remainder.getCount() < stack.getCount()) {
				entity.setItem(remainder); // partially absorbed
				absorbed = true;
			}
			if (remainder.isEmpty()) {
				entity.discard();
			}
		}

		// fluids poured in (source blocks only; flowing fluid is left to drain)
		for (int dx = 0; dx < iw; dx++) {
			for (int dz = 0; dz < iw; dz++) {
				for (int y = yBottom; y <= yTop; y++) {
					BlockPos p = core.offset(dx, y, dz);
					BlockState bs = level.getBlockState(p);
					if (bs.isAir()) {
						continue;
					}
					net.minecraft.world.level.material.FluidState fs = bs.getFluidState();
					if (fs.isEmpty() || !fs.isSource()) {
						continue;
					}
					int filled = tank.fill(new FluidStack(fs.getType(), 1000), IFluidHandler.FluidAction.EXECUTE);
					if (filled == 1000) {
						level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
						absorbed = true;
					}
				}
			}
		}

		if (absorbed) {
			level.playSound((Player) null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2f, 1.2f);
		}
	}

	/** One source block trickles out of the breach every few ticks. */
	private void tickSpill() {
		if (pendingSpill.isEmpty()) {
			return;
		}
		if (++spillTimer % 5 != 0) {
			return;
		}
		SpillLogic.tryPlaceOne(level, spillLeakPos != null ? spillLeakPos : worldPosition, pendingSpill);
	}

	private void updateHeat() {
		// heating from a Blaze Burner directly below the vessel's bottom layer
		// (controller sits on the first wall layer; bottom is one below, burner two)
		BlockState below = level.getBlockState(worldPosition.below(2));
		int target = switch (BlazeBurnerBlock.getHeatLevelOf(below)) {
			case KINDLED -> 500;
			case SEETHING -> 900;
			default -> AMBIENT_TEMP;
		};
		// the vessel's contents carry the temperature; relax the settled stack
		// toward the burner's target (or back to ambient when unheated)
		List<FluidStack> fluids = tank.getFluids();
		if (fluids.size() != 1) {
			return; // empty, or transiently multi-entry before collapse
		}
		FluidStack stack = fluids.get(0);
		int current = Temperature.get(stack);
		int next = current + (target - current) / 10;
		if (next != current) {
			Temperature.set(stack, next);
			setChanged();
			sync();
		}
	}

	private void tickReaction() {
		if (!assembled) {
			setStatus(ReactorStatus.NOT_ASSEMBLED);
			setProgress(0, null);
			return;
		}
		// emergent chemistry first (double displacement / precipitation / neutralisation
		// / crystallisation derived from species data), then the whitelist recipe engine
		RulesEngine.apply(tank);
		ChemicalReactionRecipe recipe = findRecipe();
		if (recipe == null) {
			// no fully-matching recipe: diagnose whether it is a heat problem
			setStatus(matchesIgnoringHeat() ? ReactorStatus.TEMPERATURE : ReactorStatus.NO_RECIPE);
			setProgress(0, null);
			return;
		}
		if (!canFitOutputs(recipe)) {
			setStatus(ReactorStatus.OUTPUT_FULL);
			setProgress(0, null);
			return;
		}
		setStatus(ReactorStatus.REACTING);
		float next = progress + (float) REACTION_TICK / recipe.getProcessingDuration();
		if (next >= 1.0f) {
			completeRecipe(recipe);
			setProgress(0, recipe.getId());
			sync();
		} else {
			setProgress(next, recipe.getId());
		}
	}

	private void setStatus(ReactorStatus value) {
		if (status != value) {
			status = value;
			sync();
		}
	}

	private void setProgress(float value, @Nullable ResourceLocation recipeId) {
		boolean changed = progress != value || !java.util.Objects.equals(activeRecipe, recipeId);
		progress = value;
		activeRecipe = recipeId;
		if (changed) {
			sync();
		}
	}

	@Nullable
	private ChemicalReactionRecipe findRecipe() {
		if (level == null) {
			return null;
		}
		for (ChemicalReactionRecipe recipe : level.getRecipeManager().getAllRecipesFor(chemicalReactionType())) {
			if (matches(recipe)) {
				return recipe;
			}
		}
		return null;
	}

	private boolean matches(ChemicalReactionRecipe recipe) {
		// heat condition vs current temperature
		int temperature = getTemperature();
		HeatCondition heat = recipe.getRequiredHeat();
		if (heat == HeatCondition.HEATED && temperature < 400) {
			return false;
		}
		if (heat == HeatCondition.SUPERHEATED && temperature < 800) {
			return false;
		}
		return matchesIngredients(recipe);
	}

	/** True when all item+fluid ingredients are present (heat ignored). */
	private boolean matchesIngredients(ChemicalReactionRecipe recipe) {
		for (Ingredient ingredient : recipe.getIngredients()) {
			if (!hasItem(ingredient)) {
				return false;
			}
		}
		for (FluidIngredient fluid : recipe.getFluidIngredients()) {
			if (!hasFluid(fluid)) {
				return false;
			}
		}
		for (SolutionIngredient sol : recipe.getSolutions()) {
			if (tank.countSolution(sol.speciesId()) < sol.amount()) {
				return false;
			}
			if (sol.hasConcentrationRange()) {
				double c = tank.concentrationOf(sol.speciesId());
				if (c < sol.minConcentration() || c > sol.maxConcentration()) {
					return false;
				}
			}
		}
		return true;
	}

	/** Any recipe whose ingredients are ready but whose heat condition is not met. */
	private boolean matchesIgnoringHeat() {
		if (level == null) {
			return false;
		}
		for (ChemicalReactionRecipe recipe : level.getRecipeManager().getAllRecipesFor(chemicalReactionType())) {
			if (matchesIngredients(recipe) && !matches(recipe)) {
				return true;
			}
		}
		return false;
	}

	private boolean canFitOutputs(ChemicalReactionRecipe recipe) {
		int fluidOut = 0;
		for (FluidStack out : recipe.getFluidResults()) {
			fluidOut += out.getAmount();
		}
		if (fluidOut > tank.getTankCapacity(0) - tank.getTotalAmount()) {
			return false;
		}
		for (ProcessingOutput out : recipe.getRollableResults()) {
			ItemStack stack = out.getStack();
			if (!stack.isEmpty() && !ItemHandlerHelper.insertItemStacked(items, stack.copy(), true).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	private boolean hasItem(Ingredient ingredient) {
		for (int i = 0; i < items.getSlots(); i++) {
			ItemStack stack = items.getStackInSlot(i);
			if (!stack.isEmpty() && ingredient.test(stack)) {
				return true;
			}
		}
		return false;
	}

	private boolean hasFluid(FluidIngredient ingredient) {
		// countIngredient looks inside mixture components, so a recipe matches a
		// species dissolved in the mix as well as a pure stack
		return tank.countIngredient(ingredient) >= ingredient.getRequiredAmount();
	}

	private void completeRecipe(ChemicalReactionRecipe recipe) {
		// capture the vessel's temperature before consuming inputs, so the products
		// inherit it (they form in the hot/cold vessel, not at ambient)
		int vesselTemp = getTemperature();
		// consume item inputs (1 per ingredient)
		for (Ingredient ingredient : recipe.getIngredients()) {
			for (int i = 0; i < items.getSlots(); i++) {
				ItemStack stack = items.getStackInSlot(i);
				if (!stack.isEmpty() && ingredient.test(stack)) {
					stack.shrink(1);
					items.setStackInSlot(i, stack);
					break;
				}
			}
		}
		// consume fluid inputs
		// consume fluid inputs (mixture-aware: draws from pure stacks first,
		// then from mixture components, so a recipe can consume a species that is
		// dissolved in the mix)
		for (FluidIngredient fluid : recipe.getFluidIngredients()) {
			tank.drainIngredient(fluid, fluid.getRequiredAmount(), IFluidHandler.FluidAction.EXECUTE);
		}
		// consume solution-species inputs (matched against the dissolved ions)
		for (SolutionIngredient sol : recipe.getSolutions()) {
			tank.drainSolution(sol.speciesId(), sol.amount(), IFluidHandler.FluidAction.EXECUTE);
		}
		// item outputs (chance-based)
		for (ProcessingOutput output : recipe.getRollableResults()) {
			ItemStack out = output.getStack();
			if (out.isEmpty() || (output.getChance() < 1 && level.random.nextFloat() >= output.getChance())) {
				continue;
			}
			ItemStack remainder = ItemHandlerHelper.insertItemStacked(items, out.copy(), false);
			if (!remainder.isEmpty() && level != null) {
				Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), remainder);
			}
		}
		// fluid outputs (pure fluids only — solutions go through solutionOutputs)
		for (FluidStack out : recipe.getFluidResults()) {
			Temperature.set(out, vesselTemp);
			tank.fill(out.copy(), IFluidHandler.FluidAction.EXECUTE);
		}
		// solution-species outputs: expand straight into ions + water at the target
		// concentration (ion mB / water mB)
		for (SolutionIngredient out : recipe.getSolutionOutputs()) {
			Species species = SpeciesManager.get(out.speciesId());
			if (species == null || !species.isSolution() || Double.isNaN(out.targetConcentration())) {
				continue;
			}
			Map<ResourceLocation, Integer> molecules = new LinkedHashMap<>();
			Map<String, Integer> ions = new LinkedHashMap<>();
			species.expand(out.amount(), out.targetConcentration(), molecules, ions);
			int total = 0;
			for (int v : molecules.values()) {
				total += v;
			}
			for (int v : ions.values()) {
				total += v;
			}
			FluidStack mix = Mixture.create(molecules, ions, total);
			Temperature.set(mix, vesselTemp);
			tank.fill(mix, IFluidHandler.FluidAction.EXECUTE);
		}
		// heat effect (exothermic raises temperature)
		if (recipe.getDeltaHeat() != 0) {
			tank.collapseIfNeeded();
			if (!tank.getFluids().isEmpty()) {
				FluidStack stack = tank.getFluids().get(0);
				int t = Math.max(AMBIENT_TEMP, Math.min(MAX_TEMP, Temperature.get(stack) + recipe.getDeltaHeat()));
				Temperature.set(stack, t);
			}
		}
	}

	private void onTankChanged() {
		setChanged();
		if (level != null && !level.isClientSide) {
			sync();
		}
	}

	private void sync() {
		if (level != null && !level.isClientSide) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
			// sendBlockUpdated does NOT carry BE nbt; push an explicit update packet
			// so the client BE (tank contents, temperature, progress) refreshes
			if (level instanceof ServerLevel serverLevel) {
				ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(this);
				serverLevel.getServer().getPlayerList()
					.broadcast(null, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), 64.0,
						serverLevel.dimension(), packet);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private RecipeType<ChemicalReactionRecipe> chemicalReactionType() {
		return (RecipeType<ChemicalReactionRecipe>) (RecipeType<?>) AllRecipeTypes.CHEMICAL_REACTION.getType();
	}

	/**
	 * Validates the 3x3 hollow brick shell with height 3..6. The controller
	 * must sit in the first wall layer (directly above the bottom); the
	 * structure extends 2 blocks in the inward direction, ±1 along the wall,
	 * and MIN_HEIGHT..MAX_HEIGHT vertically. Tries all 4 wall faces.
	 * Tank capacity scales with interior volume (16 buckets per interior layer).
	 */
	/**
	 * Validates the hollow W x W x H brick shell (W,H = 3..7, the largest complete
	 * cuboid wins — Tinkers smeltery style) and returns a structured result: on
	 * failure, the face that progressed furthest, the first broken spot on that
	 * face and its position. The controller sits in the middle of one wall and may
	 * be placed on ANY ring layer (the floor is k+1 layers below it); the interior
	 * is (W-2)^2 x (H-2) air.
	 */
	public AssembleResult tryAssemble() {
		return tryAssemble(MAX_SIZE - 2, Integer.MAX_VALUE, false);
	}

	/**
	 * {@link #tryAssemble()} with bounds used by the shrink path:
	 * {@code maxRings} caps the candidate interior height (a removed ceiling brick
	 * must shrink the vessel, not grow it), {@code ignoreAboveY} (controller-
	 * relative) marks the discarded top zone — layers at/above it are skipped
	 * entirely (their bricks become stray, outside the shell) and a candidate whose
	 * ceiling lies there is treated as open-topped. {@code allowShrink} gates
	 * adopting a SMALLER shell: only the removal path may shrink — a placement
	 * (sealing a half-finished ceiling) must never yank the vessel back down
	 * (that would flicker the height while building taller).
	 */
	private AssembleResult tryAssemble(int maxRings, int ignoreAboveY, boolean allowShrink) {
		if (level == null || level.isClientSide) {
			return new AssembleResult(false, null, AssembleIssue.BOTTOM_GAP, null);
		}

		AssembleResult best = null;
		int bestProgress = -1;

		for (Direction inward : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST }) {
			Direction side = inward.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.EAST;

			// try the largest shell first: widest W first, then tallest H
			for (int w = MAX_SIZE; w >= MIN_SIZE; w--) {
				int half = (w - 1) / 2;
				int sStart = -half;
				int sEnd = sStart + w - 1;
				for (int h = MAX_SIZE; h >= MIN_SIZE; h--) {
					int rings = h - 2; // ring layers between floor and ceiling
					if (rings > maxRings) {
						continue;
					}

					// the controller may sit on ANY ring layer (Tinkers-style): k = its
					// layer counting up from the floor, so the floor is k+1 below it
					for (int k = 0; k < rings; k++) {
						int bottomY = -k - 1;
						int ringY0 = -k;
						int ringY1 = rings - 1 - k;
						int topY = rings - k;

						boolean ok = true;
						int progress = 0;
						AssembleIssue firstIssue = null;
						BlockPos firstIssuePos = null;

						// bottom layer: full w x w of shell blocks
						for (int s = sStart; s <= sEnd && ok; s++) {
							for (int d = 0; d <= w - 1 && ok; d++) {
								BlockPos p = cell(s, d, bottomY, side, inward);
								if (!level.getBlockState(p).is(ChemicalAddon.VESSEL_WALLS)) {
									ok = false;
									firstIssue = AssembleIssue.BOTTOM_GAP;
									firstIssuePos = p;
								}
							}
						}
						if (ok) {
							progress++;
						}

						// ring layers ringY0..ringY1: the shell wall (s at either end, d=0/w-1)
						// must be a vessel block and the interior hollow; (y=0,s=0,d=0) is
						// the controller on its own layer
						for (int y = ringY0; y <= ringY1 && ok; y++) {
							if (y >= ignoreAboveY) {
								progress++; // discarded layer: counts as present, no checks
								continue;
							}
							boolean layerIsRing = true;
							for (int s = sStart; s <= sEnd && layerIsRing; s++) {
								for (int d = 0; d <= w - 1 && layerIsRing; d++) {
									if (y == 0 && s == 0 && d == 0) {
										continue; // the controller itself
									}
								BlockPos p = cell(s, d, y, side, inward);
								boolean wall = s == sStart || s == sEnd || d == 0 || d == w - 1;
								if (!wall) {
									BlockState interior = level.getBlockState(p);
									// interior must be hollow: only air or fluid blocks are allowed.
									// A fluid already sitting inside (e.g. water poured before sealing)
									// must NOT block assembly — it is absorbed into the tank below.
									if (!interior.isAir() && interior.getFluidState().isEmpty()) {
										layerIsRing = false; // a solid block occupies the interior
										firstIssue = AssembleIssue.INTERIOR_BLOCKED;
										firstIssuePos = p;
									}
								} else if (!level.getBlockState(p).is(ChemicalAddon.VESSEL_WALLS)) {
										layerIsRing = false;
										firstIssue = AssembleIssue.RING_GAP;
										firstIssuePos = p;
									}
								}
							}
							if (layerIsRing) {
								progress++;
							} else {
								ok = false;
							}
						}

						// top layer: fully sealed (w*w blocks) or fully open — or discarded
						boolean topOpen = false;
						if (ok) {
							if (topY >= ignoreAboveY) {
								// ceiling lies in the discarded zone (shrink): treat as open,
								// no top-brick checks — those bricks are stray, outside the shell
								topOpen = true;
								progress++;
							} else {
								int topBricks = 0;
								for (int s = sStart; s <= sEnd && ok; s++) {
									for (int d = 0; d <= w - 1 && ok; d++) {
										BlockPos p = cell(s, d, topY, side, inward);
										if (level.getBlockState(p).is(ChemicalAddon.VESSEL_WALLS)) {
											topBricks++;
										} else if (firstIssue == null) {
											firstIssue = AssembleIssue.PARTIAL_TOP;
											firstIssuePos = p;
										}
									}
								}
								if (topBricks == 0) {
									topOpen = true;
								} else if (topBricks != w * w) {
									ok = false; // partially sealed top
								}
								if (ok) {
									progress++;
								}
							}
						}

						if (ok) {
							// A re-validation of a LIVE vessel adopts only strictly larger
							// (extension) or — on the REMOVAL path only — strictly smaller
							// (shrink after a bound brick was removed) shells. A placement
							// must never shrink: sealing a half-finished ceiling would
							// momentarily match a shorter open vessel and yank the height
							// back down (the build flicker). A tie (same volume AND same
							// open state — even a different orientation) keeps the current
							// assembly untouched; an open/sealed change always takes effect
							// (sealing/opening the top is the player's intent). Initial
							// assembly (assembled == false) adopts the largest cuboid.
							int newVol = w * w * rings;
							int curVol = size * size * height;
							if (assembled && newVol == curVol && topOpen == open) {
								return AssembleResult.success(); // tie: keep current assembly
							}
							if (assembled && newVol < curVol && !allowShrink) {
								return AssembleResult.success(); // placement must never shrink
							}
							boolean wasAssembled = assembled;
							int oldSize = size;
							int oldHeight = height;
							assembled = true;
							this.inward = inward;
							this.open = topOpen;
							this.size = w;
							this.height = rings;
							this.ringLayer = k;
							setStatus(ReactorStatus.REACTING);
							tank.setCapacity(TANK_CAPACITY * (w - 2) * (w - 2) * rings); // per interior block
							if (wasAssembled) {
								// re-bind: clear old-shell masters first so bricks that fell OUT
								// of the (shrunk) shell stop proxying capabilities — bindBricks
								// below re-binds the new shell (extension re-binds harmlessly).
								// The radius must cover the shell's vertical extent too (floor
								// is ringLayer+1 below the controller, ceiling rings-ringLayer
								// above): a footprint-only radius misses old floor/ceiling
								// bricks on tall narrow vessels (height > size)
								clearShellMasters(Math.max(Math.max(oldSize, w), Math.max(oldHeight, rings)) + 1);
							} else {
								// coming back from a break: the shell is intact again — stop
								// any leftover trickle from the breach (a repaired vessel must
								// not keep leaking). An extension of a live vessel keeps its
								// (empty) spill state untouched.
								pendingSpill.clear();
								spillLeakPos = null;
								spillTimer = 0;
							}
							// §D: rebuilt smaller than the retained contents -> the excess is
							// turned back into physical fluid (progressive trickle from the new
							// interior top), so the vessel never sits wedged in a permanent
							// over-capacity OUTPUT_FULL state
							int newCap = tank.getTankCapacity(0);
							int nowTotal = tank.getTotalAmount();
							if (nowTotal > newCap) {
								int overflowMb = nowTotal - newCap;
								List<FluidStack> overflow = new ArrayList<>();
								for (FluidStack stack : new ArrayList<>(tank.getFluids())) {
									int take = (int) Math.floor(stack.getAmount() * (double) overflowMb / nowTotal);
									if (take > 0) {
										FluidStack out = stack.copy();
										out.setAmount(take);
										overflow.add(out);
										stack.shrink(take);
									}
								}
								tank.pruneEmpty();
								pendingSpill.addAll(SpillLogic.queueFluids(overflow));
								spillLeakPos = topCenter(w, rings, inward);
								spillTimer = 4;
								SpillLogic.tryPlaceOne(level, spillLeakPos, pendingSpill);
							}
							bindBricks(worldPosition, inward, side, w, rings);
							// absorb any fluid already sitting in the interior (e.g. water poured
							// before the last brick closed the shell) into the tank — source blocks
							// become tank contents, flowing fluids simply evaporate. Without this a
							// sealed vessel would trap fluid invisibly inside (absorbFromWorld only
							// runs while the top is open).
							absorbInteriorOnAssemble(side, inward, w, sStart, sEnd, ringY0, ringY1);
							// update the controller block state so the open/sealed variant shows
							BlockState state = level.getBlockState(worldPosition);
							if (state.hasProperty(ReactorControllerBlock.OPEN)
								&& state.getValue(ReactorControllerBlock.OPEN) != topOpen) {
								level.setBlock(worldPosition, state.setValue(ReactorControllerBlock.OPEN, topOpen), 3);
							}
							setChanged();
							sync();
							return AssembleResult.success();
						}
						// keep the failure diagnostic that progressed furthest
						if (progress > bestProgress) {
							bestProgress = progress;
							best = new AssembleResult(false, inward, firstIssue, firstIssuePos);
						}
					}
				}
			}
		}
		return best != null ? best : new AssembleResult(false, Direction.NORTH, AssembleIssue.TOO_SHORT, null);
	}

	/** World position of a shell cell (s, d, y) relative to the controller. */
	private BlockPos cell(int s, int d, int y, Direction side, Direction inward) {
		return worldPosition.offset(side.getStepX() * s + inward.getStepX() * d, y,
			side.getStepZ() * s + inward.getStepZ() * d);
	}

	/**
	 * §A: re-validate after a structural block was placed near an assembled
	 * vessel. The placed block may have completed a larger shell — adopt the
	 * result only when it is strictly larger (grow, never shrink or re-orient);
	 * {@link #tryAssemble} enforces that, and never spills on success, so the
	 * contents carry over untouched. Blocks far outside the shell's reach are
	 * rejected cheaply (a stray brick must not trigger a full re-validation).
	 */
	public boolean tryExtend(BlockPos placedPos) {
		if (level == null || level.isClientSide || !assembled) {
			return false;
		}
		// fast reject: the placed block must be within one block of the current
		// shell's bounding box to be able to complete a larger cuboid
		int reach = Math.max(size, height) + 2;
		if (Math.abs(placedPos.getX() - worldPosition.getX()) > reach
			|| Math.abs(placedPos.getY() - worldPosition.getY()) > reach
			|| Math.abs(placedPos.getZ() - worldPosition.getZ()) > reach) {
			return false;
		}
		return tryAssemble().ok();
	}

	/**
	 * §D: leak point for an overflow after rebuilding smaller — the top-centre
	 * of the (new) shell's interior, so the excess pours over the rim (open top)
	 * or seeps from the seam (sealed top; {@code SpillLogic.findFreeSpot} walks
	 * outward from the occupied cap block).
	 */
	private BlockPos topCenter(int w, int rings, Direction inward) {
		int dMid = (w - 1) / 2;
		return worldPosition.offset(inward.getStepX() * dMid, rings - ringLayer, inward.getStepZ() * dMid);
	}

	/**
	 * A bound shell block was removed. Before giving up on the vessel, try to
	 * keep it going at the least destructive step:
	 * 1) any legal (smaller or open-changed) shell from a full re-validation;
	 * 2) a CEILING brick removed -> the vessel stays the SAME height and simply
	 *    becomes open-topped (the ceiling layer is discarded, its bricks become
	 *    stray) — the height of a vessel is its ring count, not its lid;
	 * 3) a top RING brick removed -> shrink one ring (lower the vessel);
	 * 4) otherwise the shell has no legal remainder -> full de-assembly (§B
	 *    breach-level spill). Contents survive 1-3 (over-capacity overflows, §D).
	 */
	public void handleStructuralBlockRemoved(BlockPos removedPos) {
		if (level == null || level.isClientSide || !assembled) {
			return;
		}
		// 1) any legal shell from a full re-validation (smaller, or tie-with-open-change)
		if (tryAssemble(MAX_SIZE - 2, Integer.MAX_VALUE, true).ok()) {
			return;
		}
		// 2) ceiling brick removed -> same height, open-topped (discard only the lid
		//    layer, keep every ring); no height change, no de-assembly
		int ceilingLine = height - ringLayer; // controller-relative y of the ceiling layer
		if (tryAssemble(height, ceilingLine, true).ok()) {
			return;
		}
		// 3) top ring brick removed -> drop the ceiling + highest ring (lower by one)
		if (tryShrink()) {
			return;
		}
		// 4) nothing legal remains -> full de-assembly with breach-level spill
		invalidateStructure(removedPos);
	}

	/**
	 * Shrink the vessel by one interior ring: the ceiling layer and the highest
	 * ring layer are treated as discarded (their bricks become stray, out of the
	 * shell) and the remaining shell is re-validated as an open-topped vessel one
	 * ring shorter. Adopts the largest legal result (w unchanged usually), so
	 * removing a top brick lowers the vessel instead of destroying it.
	 */
	private boolean tryShrink() {
		if (height <= 1) {
			return false; // already minimal height (a 3x3x3 has a single ring)
		}
		// controller-relative y of the new ceiling; everything at/above is discarded
		int dropLine = height - 1 - ringLayer;
		return tryAssemble(height - 1, dropLine, true).ok();
	}

	/** Clears master pointers on every brick within a box around the controller. */
	private void clearShellMasters(int radius) {
		if (level == null) {
			return;
		}
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (level.getBlockEntity(worldPosition.offset(dx, dy, dz)) instanceof IMasterBound bound) {
						bound.setMaster(null);
					}
				}
			}
		}
	}

	/**
	 * Binds every structural shell block (any block in the vessel_walls tag —
	 * brick, glass, ...) to this controller so it proxies capabilities and can
	 * report breakage. The y range is controller-RELATIVE and must follow the
	 * controller's ring layer k: floor at -k-1, ceiling at rings-k. A hard-coded
	 * -1..rings (k=0 assumption) leaves the floor unbound when the controller is
	 * mounted higher — the decant hose scans down the interior column and falls
	 * through the unbound floor, never finding the vessel.
	 */
	private void bindBricks(BlockPos masterPos, Direction inward, Direction side, int w, int rings) {
		if (level == null) {
			return;
		}
		int half = (w - 1) / 2;
		int sStart = -half;
		int sEnd = sStart + w - 1;
		for (int s = sStart; s <= sEnd; s++) {
			for (int d = 0; d <= w - 1; d++) {
				if (s == 0 && d == 0) {
					continue; // the controller itself
				}
				for (int y = -ringLayer - 1; y <= rings - ringLayer; y++) {
					bindBrick(cell(s, d, y, side, inward), masterPos);
				}
			}
		}
	}

	private void bindBrick(BlockPos pos, @Nullable BlockPos masterPos) {
		if (level == null) {
			return;
		}
		if (level.getBlockEntity(pos) instanceof IMasterBound bound) {
			bound.setMaster(masterPos);
		}
	}

	/**
	 * Absorbs fluid already sitting in the interior into the tank, called once on
	 * successful assembly. Source blocks become tank contents (1000 mB each, the
	 * same rule as {@link #absorbFromWorld}); flowing (spreading) fluids carry no
	 * volume and are simply cleared to air so they don't linger invisibly inside a
	 * sealed shell. Runs for both open and sealed vessels.
	 */
	private void absorbInteriorOnAssemble(Direction side, Direction inward, int w,
		int sStart, int sEnd, int ringY0, int ringY1) {
		if (level == null) {
			return;
		}
		for (int y = ringY0; y <= ringY1; y++) {
			for (int s = sStart + 1; s <= sEnd - 1; s++) { // interior columns (skip the two wall columns)
				for (int d = 1; d <= w - 2; d++) {          // interior depth  (skip the two wall layers)
					BlockPos p = cell(s, d, y, side, inward);
					BlockState bs = level.getBlockState(p);
					if (bs.isAir()) {
						continue;
					}
					net.minecraft.world.level.material.FluidState fs = bs.getFluidState();
					if (fs.isEmpty()) {
						continue; // a solid block wouldn't have passed validation; defensive skip
					}
					if (fs.isSource()) {
						int filled = tank.fill(new FluidStack(fs.getType(), 1000), IFluidHandler.FluidAction.EXECUTE);
						if (filled == 1000) {
							level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
						}
					} else {
						// flowing fluid: no volume to absorb, just clear it
						level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
					}
				}
			}
		}
	}

	public void invalidateStructure(@Nullable BlockPos leakPos) {
		if (assembled) {
			assembled = false;
			int oldSize = size;
			// §C: keep size/height/inward as lastGeometry — the remaining lower shell
			// still stands and the residual fluid surface must keep rendering while
			// the vessel is de-assembled (see the renderer guard). All logical paths
			// (reaction, absorption, capability proxy) are gated on isAssembled(), so
			// the retained geometry only feeds rendering.
			setStatus(ReactorStatus.NOT_ASSEMBLED);
			setProgress(0, null);
			// contents become physical again: items drop, fluids pour out of the breach.
			// §B: breach-level spill — only the fluid above the breach height pours
			// out; the portion below stays in the tank (auto-lowered surface, recovered
			// on rebuild, plans/10 §2.2). Breaking the controller itself keeps nothing:
			// its NBT dies with the block, so a retained remainder would silently
			// vanish — fall back to a full physical spill.
			BlockPos breach = leakPos != null ? leakPos : worldPosition;
			SpillLogic.spillItems(level, breach, items);
			pendingSpill.clear();
			int total = tank.getTotalAmount();
			if (total <= 0 || height <= 0 || breach.equals(worldPosition)) {
				// full spill: empty tank, no interior, or the controller itself broke
				pendingSpill.addAll(SpillLogic.queueFluids(tank)); // sub-bucket remainder lost by design
			} else {
				// interior ring the breach sits on (controller is on ringLayer; the ring
				// below it holds the fluid that survives — one full layer per ring)
				int ring = Math.max(0, Math.min(height, breach.getY() - worldPosition.getY() + ringLayer));
				int keepMb = (int) ((long) tank.getTankCapacity(0) * ring / height);
				int spillMb = Math.max(0, total - keepMb);
				if (spillMb >= total) {
					pendingSpill.addAll(SpillLogic.queueFluids(tank)); // bottom breach: drains everything
				} else if (spillMb > 0) {
					// proportional split preserves every phase's ratio (gases included)
					List<FluidStack> spilled = new ArrayList<>();
					for (FluidStack stack : new ArrayList<>(tank.getFluids())) {
						int take = (int) Math.floor(stack.getAmount() * (double) spillMb / total);
						if (take > 0) {
							FluidStack out = stack.copy();
							out.setAmount(take);
							spilled.add(out);
							stack.shrink(take);
						}
					}
					tank.pruneEmpty();
					pendingSpill.addAll(SpillLogic.queueFluids(spilled));
				}
				// spillMb == 0 (breach at/above the surface): keep everything
			}
			spillLeakPos = breach;
			spillTimer = 4; // first source appears almost immediately
			SpillLogic.tryPlaceOne(level, breach, pendingSpill);
			// clear master pointers on nearby shell blocks so they stop proxying
			// (radius covers the vertical extent too — height is retained as
			// lastGeometry here, so a tall vessel's floor/ceiling bricks are reached)
			clearShellMasters(Math.max(oldSize, height) + 1);
			setChanged();
			sync();
		}
	}

	public boolean isAssembled() {
		return assembled;
	}

	/** mB still queued to pour out of the breach (server-side spill state; tests/debug). */
	public int getPendingSpillAmount() {
		int total = 0;
		for (FluidStack f : pendingSpill) {
			total += f.getAmount();
		}
		return total;
	}

	/**
	 * The interior (fluid surface + floating items) renders up to 7 blocks away
	 * from the controller block. Without an expanded render bounding box, MC's
	 * frustum cull tests only the controller's own 1×1×1 cell — so the moment the
	 * controller leaves the viewport (even with the fluid surface still on
	 * screen) the whole BE is culled and the contents vanish. Cover the entire
	 * shell footprint (Create FluidTank pattern, FluidTankBlockEntity:372).
	 */
	@Override
	protected net.minecraft.world.phys.AABB createRenderBoundingBox() {
		// §C: a broken-but-not-empty vessel keeps rendering its residual surface
		// in the remaining shell, so the box must stay as large as the last
		// assembly (size/height/inward are retained on invalidation as lastGeometry).
		if ((!assembled && tank.getTotalAmount() <= 0) || size < MIN_SIZE || inward == null) {
			return super.createRenderBoundingBox();
		}
		// controller sits at the wall centre (s=0, d=0, k-th ring). The shell
		// spans s ∈ [-(half), +half] along the wall axis, d ∈ [0, size-1] inward,
		// and the controller can be on any ring layer k ∈ [0, rings-1] — so y may
		// extend up to (height+1) below and above the controller. Cover the worst
		// case in every axis; frustum tests are cheap.
		Direction side = inward.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
		int half = (size - 1) / 2;
		int reach = size - 1; // 0..size-1 blocks of shell in the inward/±side directions
		int dy = height + 1;  // floor below / ceiling above the controller's layer
		int minX = worldPosition.getX() - half;
		int maxX = worldPosition.getX() + half;
		int minZ = worldPosition.getZ() - half;
		int maxZ = worldPosition.getZ() + half;
		if (side.getAxis() == Direction.Axis.X) {
			minX = worldPosition.getX() - half;
			maxX = worldPosition.getX() + half;
			minZ = worldPosition.getZ();
			maxZ = worldPosition.getZ() + reach * inward.getStepZ();
			if (inward.getStepZ() < 0) { minZ = worldPosition.getZ() + reach * inward.getStepZ(); maxZ = worldPosition.getZ(); }
		} else {
			minX = worldPosition.getX();
			maxX = worldPosition.getX() + reach * inward.getStepX();
			if (inward.getStepX() < 0) { minX = worldPosition.getX() + reach * inward.getStepX(); maxX = worldPosition.getX(); }
			minZ = worldPosition.getZ() - half;
			maxZ = worldPosition.getZ() + half;
		}
		int minY = worldPosition.getY() - dy;
		int maxY = worldPosition.getY() + dy;
		return new net.minecraft.world.phys.AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
	}

	public ReactorStatus getStatus() {
		return status;
	}

	/** Direction from the controller into the vessel interior (for item rendering). */
	@Nullable
	public Direction getInward() {
		return inward;
	}

	/** true when the vessel is open-topped (interior visible from above). */
	public boolean isOpen() {
		return open;
	}

	/** Shell footprint W of the assembled cuboid (W x W base; 0 when not assembled). */
	public int getSize() {
		return size;
	}

	/** Interior height in blocks (ring-layer count, H-2). */
	public int getHeight() {
		return Math.max(height, 0);
	}

	/** Y of the floor layer relative to the controller (negative; floor is ringLayer+1 below). */
	public int getFloorRelY() {
		return -ringLayer - 1;
	}

	/**
	 * Controller-relative Y where the fluid body's bottom rests: the TOP face of
	 * the floor blocks (= {@code -ringLayer}). The controller may sit on ANY ring
	 * layer, so this is NOT always 0 — rendering, the liquid-surface math and the
	 * absorb polling all measure from here, never from the controller's own layer.
	 */
	public int getInteriorBottomRelY() {
		return -ringLayer;
	}

	/** Y of the roof layer relative to the controller (positive). */
	public int getRoofRelY() {
		return height - ringLayer;
	}

	/** Tank fill fraction (0..1); capacity is height-scaled, so this maps onto the interior height.
	 *  Clamped: an older save may hold more fluid than the current (smaller) capacity, and an
	 *  over-1 fraction would render the surface above the vessel rim. */
	public float getFillState() {
		int cap = tank.getTankCapacity(0);
		if (cap <= 0) {
			return 0;
		}
		float f = (float) tank.getTotalAmount() / cap;
		return Math.max(0, Math.min(1, f));
	}

	/**
	 * The height the client animates the fluid surface toward: fill × interior
	 * height, in blocks above the interior floor. Deliberately ABSOLUTE (not the
	 * bare fill fraction) so that a ring-count change (shrink/extend) with the
	 * amount unchanged produces the SAME target — the rendered surface stays put
	 * instead of dipping/spiking while the LerpedFloat re-converges.
	 */
	private float targetRenderedLevel() {
		return getFillState() * getHeight();
	}

	/** Animated fluid surface height in blocks (interpolated for smooth rendering; client only). */
	public float getRenderedLevel(float partialTicks) {
		return renderedLevel == null ? targetRenderedLevel() : renderedLevel.getValue(partialTicks);
	}

	/**
	 * World-space Y of the liquid (non-gas) surface — the height the decant hose
	 * tracks. Mirrors {@link ReactorControllerRenderer}'s surface math (interpolated
	 * fill fraction scaled by the interior height, gases excluded) so the hose tip
	 * lands exactly on the rendered surface. Empty vessels report the floor.
	 */
	public float getLiquidSurfaceY(float partialTicks) {
		float levelHeight = getRenderedLevel(partialTicks);
		List<FluidStack> fluids = tank.getFluids();
		int total = tank.getTotalAmount();
		// measure from the interior floor, ringLayer below the controller (the
		// controller may be mounted on any ring — never from its own layer)
		int floorY = worldPosition.getY() + getInteriorBottomRelY();
		if (levelHeight <= 1 / 1024f || fluids.isEmpty() || total <= 0) {
			return floorY; // empty: surface rests on the interior floor
		}
		int liquidAmount = 0;
		for (FluidStack f : fluids) {
			if (!f.getFluid().getFluidType().isLighterThanAir()) {
				liquidAmount += f.getAmount();
			}
		}
		return floorY + levelHeight * liquidAmount / total;
	}

	/** The vessel's temperature = the settled contents' temperature (°C); ambient when empty. */
	public int getTemperature() {
		if (tank.getFluids().isEmpty()) {
			return AMBIENT_TEMP;
		}
		return Temperature.get(tank.getFluids().get(0));
	}

	public ReactorTank getTank() {
		return tank;
	}

	public ItemStackHandler getItems() {
		return items;
	}

	public float getProgress() {
		return progress;
	}

	@Nullable
	public ResourceLocation getActiveRecipe() {
		return activeRecipe;
	}



	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
		if (cap == ForgeCapabilities.FLUID_HANDLER) {
			if (side == Direction.UP) {
				return LazyOptional.empty(); // vessel top never accepts a pipe (side + bottom only)
			}
			return fluidCap.cast();
		}
		if (cap == ForgeCapabilities.ITEM_HANDLER) {
			return itemCap.cast();
		}
		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		fluidCap.invalidate();
		itemCap.invalidate();
	}

	@Override
	protected void write(CompoundTag tag, boolean clientPacket) {
		super.write(tag, clientPacket);
		tag.putBoolean("assembled", assembled);
		tag.put("tank", tank.serializeNBT());
		tag.putInt("tankCapacity", tank.getTankCapacity(0)); // survive reloads (volume-scaled)
		tag.putInt("size", size);
		tag.putInt("height", height);
		tag.putInt("ringLayer", ringLayer);
		tag.put("items", items.serializeNBT());
		tag.putFloat("progress", progress);
		if (activeRecipe != null) {
			tag.putString("activeRecipe", activeRecipe.toString());
		}
		tag.putBoolean("open", open);
		if (inward != null) {
			tag.putString("inward", inward.getSerializedName());
		}
		// status drives the goggles HUD (addToGoggleTooltip reads it client-side);
		// it must be in the sync tag or the client BE keeps its default NOT_ASSEMBLED forever
		tag.putString("status", status.name());
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		super.read(tag, clientPacket);
		assembled = tag.getBoolean("assembled");
		tank.deserializeNBT(tag.getCompound("tank"));
		if (tag.contains("tankCapacity")) {
			tank.setCapacity(tag.getInt("tankCapacity"));
		}
		if (tag.contains("size")) {
			size = tag.getInt("size");
		} else {
			// legacy save: capacity was TANK_CAPACITY * height with a 1x1 interior -> n = height + 2
			int cap = tag.getInt("tankCapacity");
			size = cap > 0 ? (int) Math.round(Math.cbrt(cap / (double) TANK_CAPACITY)) + 2 : 0;
		}
		// legacy saves had no height (cube shells): height = size - 2
		height = tag.contains("height") ? tag.getInt("height") : (size > 0 ? size - 2 : 0);
		ringLayer = tag.getInt("ringLayer"); // legacy saves default 0 (bottom ring)
		items.deserializeNBT(tag.getCompound("items"));
		progress = tag.getFloat("progress");
		activeRecipe = tag.contains("activeRecipe") ? ResourceLocation.tryParse(tag.getString("activeRecipe")) : null;
		inward = tag.contains("inward") ? Direction.byName(tag.getString("inward")) : null;
		open = tag.getBoolean("open");
		// mirror of write(): restore status so the client HUD shows the real server value
		if (tag.contains("status")) {
			try {
				status = ReactorStatus.valueOf(tag.getString("status"));
			} catch (IllegalArgumentException ignored) {
				status = ReactorStatus.NOT_ASSEMBLED; // unknown enum (e.g. older/newer data pack) — safe default
			}
		} else {
			status = ReactorStatus.NOT_ASSEMBLED;
		}
		// a sync packet may have changed structure geometry (size/height/inward/
		// assembled) — drop the cached render bounding box so createRenderBoundingBox
		// recomputes it against the new dimensions (Create FluidTank pattern).
		if (clientPacket && level != null && level.isClientSide) {
			invalidateRenderBoundingBox();
		}
	}

	// ------------------------------------------------------------- goggles HUD

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		String spacing = " ";
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("block.chemicaladdon.reactor_controller")));

		// temperature + heat tier
		int temperature = getTemperature();
		ChatFormatting heatColor = temperature >= 800 ? ChatFormatting.RED
			: temperature >= 400 ? ChatFormatting.GOLD : ChatFormatting.GRAY;
		tooltip.add(Component.literal(spacing).append(Component.translatable("goggles.chemicaladdon.temperature", temperature))
			.withStyle(ChatFormatting.WHITE));
		tooltip.add(Component.literal(spacing).append(Component.translatable(heatTierKey())).withStyle(heatColor));

		// status (why it is / is not reacting)
		ChatFormatting statusColor = switch (status) {
			case REACTING -> ChatFormatting.GREEN;
			case TEMPERATURE -> ChatFormatting.GOLD;
			case OUTPUT_FULL -> ChatFormatting.RED;
			case NOT_ASSEMBLED -> ChatFormatting.RED;
			case NO_RECIPE -> ChatFormatting.GRAY;
		};
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.status"))
			.append(Component.translatable("status.chemicaladdon." + status.name().toLowerCase()))
			.withStyle(statusColor));

		// contents — the vessel's contents are deliberately unnamed: it holds a clear
		// solution, and "you cannot tell what is in it" is the point (plans/03 §6).
		// Only the total is reported; a pure (non-mixture) fluid keeps its name.
		tooltip.add(Component.literal(spacing).append(Component.translatable("goggles.chemicaladdon.contents")));
		int total = tank.getTotalAmount();
		if (total == 0) {
			tooltip.add(Component.literal(spacing + " ").append(Component.literal("0 mB")).withStyle(ChatFormatting.GRAY));
		} else {
			for (FluidStack stack : tank.getFluids()) {
				if (Mixture.isMixture(stack)) {
					tooltip.add(Component.literal(spacing + " ")
						.append(Component.translatable("goggles.chemicaladdon.solution"))
						.withStyle(ChatFormatting.GRAY));
					tooltip.add(Component.literal(spacing + "  ")
						.append(Component.literal(stack.getAmount() + " mB")).withStyle(ChatFormatting.GOLD)
						.append(Component.literal(" / " + tank.getTankCapacity(0) + " mB").withStyle(ChatFormatting.DARK_GRAY)));
					if (ChemicalAddon.ASSAY_ON) {
						// dev assay: full breakdown — dissolved molecular species, then ions,
						// then the two solid domains (suspended slurry / settled sediment)
						for (Map.Entry<ResourceLocation, Integer> e : Mixture.deriveAmounts(stack).entrySet()) {
							Fluid cf = ForgeRegistries.FLUIDS.getValue(e.getKey());
							if (cf == null) {
								continue;
							}
							String name = new FluidStack(cf, e.getValue()).getDisplayName().getString();
							tooltip.add(Component.literal(spacing + "   • " + name + "  " + e.getValue() + " mB")
								.withStyle(ChatFormatting.DARK_GRAY));
						}
						for (Map.Entry<String, Integer> e : Mixture.deriveIonAmounts(stack).entrySet()) {
							tooltip.add(Component.literal(spacing + "   • " + e.getKey() + "  " + e.getValue() + " u")
								.withStyle(ChatFormatting.DARK_GRAY));
						}
						for (Map.Entry<ResourceLocation, Integer> e : Mixture.deriveSuspendedAmounts(stack).entrySet()) {
							var item = ForgeRegistries.ITEMS.getValue(e.getKey());
							String name = item != null ? new ItemStack(item).getHoverName().getString()
								: e.getKey().toString();
							tooltip.add(Component.literal(spacing + "   • " + name + "  " + e.getValue()
									+ " mB " + Component.translatable("goggles.chemicaladdon.suspended").getString())
								.withStyle(ChatFormatting.GRAY));
						}
						for (Map.Entry<ResourceLocation, Integer> e : Mixture.deriveSedimentAmounts(stack).entrySet()) {
							var item = ForgeRegistries.ITEMS.getValue(e.getKey());
							String name = item != null ? new ItemStack(item).getHoverName().getString()
								: e.getKey().toString();
							tooltip.add(Component.literal(spacing + "   • " + name + "  " + e.getValue()
									+ " mB " + Component.translatable("goggles.chemicaladdon.sediment").getString())
								.withStyle(ChatFormatting.GRAY));
						}
					}
				} else {
					tooltip.add(Component.literal(spacing + " ").append(stack.getDisplayName())
						.withStyle(ChatFormatting.GRAY));
					tooltip.add(Component.literal(spacing + "  ")
						.append(Component.literal(stack.getAmount() + " mB")).withStyle(ChatFormatting.GOLD)
						.append(Component.literal(" / " + tank.getTankCapacity(0) + " mB").withStyle(ChatFormatting.DARK_GRAY)));
				}
			}
		}

		// item buffer
		tooltip.add(Component.literal(spacing).append(Component.translatable("goggles.chemicaladdon.items")));
		boolean anyItem = false;
		for (int i = 0; i < items.getSlots(); i++) {
			ItemStack stack = items.getStackInSlot(i);
			if (!stack.isEmpty()) {
				anyItem = true;
				tooltip.add(Component.literal(spacing + " ")
					.append(Component.literal(stack.getHoverName().getString() + " x" + stack.getCount()))
					.withStyle(ChatFormatting.GRAY));
			}
		}
		if (!anyItem) {
			tooltip.add(Component.literal(spacing + " ").append(Component.literal("-")).withStyle(ChatFormatting.DARK_GRAY));
		}

		// reaction progress
		if (status == ReactorStatus.REACTING && activeRecipe != null) {
			tooltip.add(Component.literal(spacing)
				.append(Component.translatable("goggles.chemicaladdon.progress", (int) (progress * 100),
					activeRecipe.getPath()))
				.withStyle(ChatFormatting.GREEN));
		}
		return true;
	}

	private String heatTierKey() {
		int temperature = getTemperature();
		if (temperature >= 800) {
			return "goggles.chemicaladdon.heat.superheated";
		}
		if (temperature >= 400) {
			return "goggles.chemicaladdon.heat.heated";
		}
		return "goggles.chemicaladdon.heat.none";
	}

}
