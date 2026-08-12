package com.yu1745.chemicaladdon.reactor;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.fluid.FluidIngredient;
import com.yu1745.chemicaladdon.recipe.AllRecipeTypes;
import com.yu1745.chemicaladdon.recipe.ChemicalReactionRecipe;
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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
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

	public static final int TANK_CAPACITY = 16000; // mB per interior block (16 buckets)
	public static final int AMBIENT_TEMP = 20;
	public static final int ITEM_SLOTS = 4;
	public static final int MAX_TEMP = 1000;
	public static final int MIN_SIZE = 3; // shell edge length n (n x n x n cube)
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
	private int size = 0; // shell edge length n (0 = not assembled); interior is (n-2)^3
	private int temperature = AMBIENT_TEMP;
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

	/** Client-side fluid surface animation (chases the real fill state). */
	private final LerpedFloat renderedLevel = LerpedFloat.linear().startWithValue(0);

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
			// chase the fluid surface toward the synced fill state (FluidTank-style easing)
			renderedLevel.chase(getFillState(), 0.5, LerpedFloat.Chaser.EXP);
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
		int height = getHeight();
		int iw = size - 2; // interior footprint (iw x iw)
		Direction side = inward.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
		int sStart = -((size - 1) / 2) + 1; // interior s range starts one in from the wall
		BlockPos core = worldPosition.offset(
			side.getStepX() * sStart + inward.getStepX(), 0,
			side.getStepZ() * sStart + inward.getStepZ());
		// absorb area = interior column + the open rim and one block above it:
		// a bucket click from inside the vessel (or from below the rim) places
		// the source on the far side of the clicked block, i.e. at y = height or
		// height+1 of the column — those must be in range or poured fluids land
		// outside the polled area and are never absorbed
		var area = new net.minecraft.world.phys.AABB(core.getX(), core.getY(), core.getZ(),
			core.getX() + iw, core.getY() + height + 2, core.getZ() + iw);

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
				for (int y = 0; y <= height + 1; y++) {
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
		temperature += (target - temperature) / 10;
		sync();
	}

	private void tickReaction() {
		if (!assembled) {
			setStatus(ReactorStatus.NOT_ASSEMBLED);
			setProgress(0, null);
			return;
		}
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
		int total = 0;
		for (FluidStack stack : tank.getFluids()) {
			if (ingredient.test(stack)) {
				total += stack.getAmount();
			}
		}
		return total >= ingredient.getRequiredAmount();
	}

	private void completeRecipe(ChemicalReactionRecipe recipe) {
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
		for (FluidIngredient fluid : recipe.getFluidIngredients()) {
			int remaining = fluid.getRequiredAmount();
			for (int i = 0; i < tank.getTanks() && remaining > 0; i++) {
				FluidStack stack = tank.getFluidInTank(i);
				if (fluid.test(stack)) {
					int drained = tank.drain(new FluidStack(stack.getFluid(), remaining), IFluidHandler.FluidAction.EXECUTE).getAmount();
					remaining -= drained;
				}
			}
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
		// fluid outputs
		for (FluidStack out : recipe.getFluidResults()) {
			tank.fill(out.copy(), IFluidHandler.FluidAction.EXECUTE);
		}
		// heat effect (exothermic raises temperature)
		if (recipe.getDeltaHeat() != 0) {
			temperature = Math.max(AMBIENT_TEMP, Math.min(MAX_TEMP, temperature + recipe.getDeltaHeat()));
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
	 * Validates the hollow n x n x n brick shell (n = 3..7, the largest complete
	 * cube wins) and returns a structured result: on failure, the face that
	 * progressed furthest, the first broken spot on that face and its position.
	 * The controller sits in the middle of one wall; the interior is (n-2)^3 air.
	 */
	public AssembleResult tryAssemble() {
		if (level == null || level.isClientSide) {
			return new AssembleResult(false, null, AssembleIssue.BOTTOM_GAP, null);
		}
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();

		AssembleResult best = null;
		int bestProgress = -1;

		for (Direction inward : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST }) {
			Direction side = inward.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.EAST;

			// try the largest cube first: the biggest complete n x n x n shell wins
			for (int n = MAX_SIZE; n >= MIN_SIZE; n--) {
				int half = (n - 1) / 2;
				int sStart = -half;
				int sEnd = sStart + n - 1;

				boolean ok = true;
				int progress = 0;
				AssembleIssue firstIssue = null;
				BlockPos firstIssuePos = null;

				// bottom layer (y=-1): full n x n of bricks
				for (int s = sStart; s <= sEnd && ok; s++) {
					for (int d = 0; d <= n - 1 && ok; d++) {
						BlockPos p = cell(s, d, -1, side, inward);
						if (!level.getBlockState(p).is(brick.getBlock())) {
							ok = false;
							firstIssue = AssembleIssue.BOTTOM_GAP;
							firstIssuePos = p;
						}
					}
				}
				if (ok) {
					progress++;
				}

				// ring layers y=0..n-3: the shell wall (s at either end, d=0/n-1) must be
				// brick and the interior must be hollow; (s=0,d=0,y=0) is the controller
				int rings = n - 2;
				for (int y = 0; y < rings && ok; y++) {
					boolean layerIsRing = true;
					for (int s = sStart; s <= sEnd && layerIsRing; s++) {
						for (int d = 0; d <= n - 1 && layerIsRing; d++) {
							if (y == 0 && s == 0 && d == 0) {
								continue; // the controller itself
							}
							BlockPos p = cell(s, d, y, side, inward);
							boolean wall = s == sStart || s == sEnd || d == 0 || d == n - 1;
							if (!wall) {
								if (!level.getBlockState(p).isAir()) {
									layerIsRing = false; // interior must be hollow
									firstIssue = AssembleIssue.INTERIOR_BLOCKED;
									firstIssuePos = p;
								}
							} else if (!level.getBlockState(p).is(brick.getBlock())) {
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

				// top layer at y = n-2: fully sealed (n*n bricks) or fully open
				boolean topOpen = false;
				if (ok) {
					int topBricks = 0;
					for (int s = sStart; s <= sEnd && ok; s++) {
						for (int d = 0; d <= n - 1 && ok; d++) {
							BlockPos p = cell(s, d, n - 2, side, inward);
							if (level.getBlockState(p).is(brick.getBlock())) {
								topBricks++;
							} else if (firstIssue == null) {
								firstIssue = AssembleIssue.PARTIAL_TOP;
								firstIssuePos = p;
							}
						}
					}
					if (topBricks == 0) {
						topOpen = true;
					} else if (topBricks != n * n) {
						ok = false; // partially sealed top
					}
					if (ok) {
						progress++;
					}
				}

				if (ok) {
					assembled = true;
					this.inward = inward;
					this.open = topOpen;
					this.size = n;
					setStatus(ReactorStatus.REACTING);
					tank.setCapacity(TANK_CAPACITY * (n - 2) * (n - 2) * (n - 2)); // per interior block
					bindBricks(worldPosition, inward, side, n);
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
		return best != null ? best : new AssembleResult(false, Direction.NORTH, AssembleIssue.TOO_SHORT, null);
	}

	/** World position of a shell cell (s, d, y) relative to the controller. */
	private BlockPos cell(int s, int d, int y, Direction side, Direction inward) {
		return worldPosition.offset(side.getStepX() * s + inward.getStepX() * d, y,
			side.getStepZ() * s + inward.getStepZ() * d);
	}

	/**
	 * Binds every structural brick of this vessel to the controller and writes
	 * its shell model state (TOP/BOTTOM layer flags + window SHAPE) so the brick
	 * shows the right FluidTank-style wall variant.
	 */
	private void bindBricks(BlockPos masterPos, Direction inward, Direction side, int size) {
		if (level == null) {
			return;
		}
		int half = (size - 1) / 2;
		int sStart = -half;
		int sEnd = sStart + size - 1;
		for (int s = sStart; s <= sEnd; s++) {
			for (int d = 0; d <= size - 1; d++) {
				if (s == 0 && d == 0) {
					continue; // the controller itself
				}
				for (int y = -1; y <= size - 2; y++) {
					BlockPos p = cell(s, d, y, side, inward);
					bindBrick(p, masterPos);
					setBrickState(p, y, s, d, size, sStart, sEnd);
				}
			}
		}
	}

	private void bindBrick(BlockPos pos, @Nullable BlockPos masterPos) {
		if (level == null) {
			return;
		}
		if (level.getBlockEntity(pos) instanceof ChemicalBrickBlockEntity brick) {
			brick.setMaster(masterPos);
		}
	}

	/** Writes the top/bottom/shape flags for the shell model variant of one brick. */
	private void setBrickState(BlockPos pos, int y, int s, int d, int size, int sStart, int sEnd) {
		if (level == null) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (!state.is(AllBlocks.CHEMICAL_BRICK.get())) {
			return; // open top / interior air
		}
		BlockState newState = state.setValue(ChemicalBrickBlock.TOP, y == size - 2)
			.setValue(ChemicalBrickBlock.BOTTOM, y == -1)
			.setValue(ChemicalBrickBlock.SHAPE, windowShape(s, d, size, sStart, sEnd));
		if (!newState.equals(state)) {
			level.setBlock(pos, newState, 3);
		}
	}

	/**
	 * Window layout on the shell (Create FluidTank strategy, generalized to any
	 * n): exactly the centre brick of each of the four faces is windowed, the
	 * rest plain — so every side has a window to see the interior fluid.
	 */
	private static ChemicalBrickBlock.Shape windowShape(int s, int d, int size, int sStart, int sEnd) {
		boolean wall = s == sStart || s == sEnd || d == 0 || d == size - 1;
		if (!wall) {
			return ChemicalBrickBlock.Shape.PLAIN; // interior is air, never set on it
		}
		int sOff = s - sStart; // 0..size-1 along the wall
		int mid = (size - 1) / 2;
		boolean centre = (sOff == mid && (d == 0 || d == size - 1))
			|| (d == mid && (s == sStart || s == sEnd));
		return centre ? ChemicalBrickBlock.Shape.WINDOW : ChemicalBrickBlock.Shape.PLAIN;
	}

	public void invalidateStructure(@Nullable BlockPos leakPos) {
		if (assembled) {
			assembled = false;
			int oldSize = size;
			size = 0;
			setStatus(ReactorStatus.NOT_ASSEMBLED);
			setProgress(0, null);
			// contents become physical again: items drop, fluids pour out of the breach
			BlockPos breach = leakPos != null ? leakPos : worldPosition;
			SpillLogic.spillItems(level, breach, items);
			pendingSpill.clear();
			pendingSpill.addAll(SpillLogic.queueFluids(tank)); // sub-bucket remainder lost by design
			spillLeakPos = breach;
			spillTimer = 4; // first source appears almost immediately
			SpillLogic.tryPlaceOne(level, breach, pendingSpill);
			// clear master pointers and reset brick shell state within reach
			if (level != null) {
				int r = oldSize + 1;
				for (int dx = -r; dx <= r; dx++) {
					for (int dy = -r; dy <= r; dy++) {
						for (int dz = -r; dz <= r; dz++) {
							BlockPos p = worldPosition.offset(dx, dy, dz);
							if (level.getBlockEntity(p) instanceof ChemicalBrickBlockEntity brick) {
								brick.setMaster(null);
								BlockState state = level.getBlockState(p);
								if (state.hasProperty(ChemicalBrickBlock.TOP)) {
									// single-block tank look (top + bottom + window) when free
									level.setBlock(p, state.setValue(ChemicalBrickBlock.TOP, true)
										.setValue(ChemicalBrickBlock.BOTTOM, true)
										.setValue(ChemicalBrickBlock.SHAPE, ChemicalBrickBlock.Shape.WINDOW), 3);
								}
							}
						}
					}
				}
			}
			setChanged();
			sync();
		}
	}

	public boolean isAssembled() {
		return assembled;
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

	/** Shell edge length n of the assembled cube (0 when not assembled). */
	public int getSize() {
		return size;
	}

	/** Interior height in blocks: (n-2) ring layers. */
	public int getHeight() {
		return Math.max(size - 2, 0);
	}

	/** Tank fill fraction (0..1); capacity is height-scaled, so this maps onto the interior height. */
	public float getFillState() {
		return (float) tank.getTotalAmount() / tank.getTankCapacity(0);
	}

	/** Animated fluid surface fraction, interpolated for smooth rendering (client only). */
	public float getRenderedLevel(float partialTicks) {
		return renderedLevel.getValue(partialTicks);
	}

	public int getTemperature() {
		return temperature;
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
		tag.putInt("temperature", temperature);
		tag.put("tank", tank.serializeNBT());
		tag.putInt("tankCapacity", tank.getTankCapacity(0)); // survive reloads (volume-scaled)
		tag.putInt("size", size);
		tag.put("items", items.serializeNBT());
		tag.putFloat("progress", progress);
		if (activeRecipe != null) {
			tag.putString("activeRecipe", activeRecipe.toString());
		}
		tag.putBoolean("open", open);
		if (inward != null) {
			tag.putString("inward", inward.getSerializedName());
		}
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		super.read(tag, clientPacket);
		assembled = tag.getBoolean("assembled");
		temperature = tag.getInt("temperature");
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
		items.deserializeNBT(tag.getCompound("items"));
		progress = tag.getFloat("progress");
		activeRecipe = tag.contains("activeRecipe") ? ResourceLocation.tryParse(tag.getString("activeRecipe")) : null;
		inward = tag.contains("inward") ? Direction.byName(tag.getString("inward")) : null;
		open = tag.getBoolean("open");
	}

	// ------------------------------------------------------------- goggles HUD

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		String spacing = " ";
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("block.chemicaladdon.reactor_controller")));

		// temperature + heat tier
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

		// contents (multi-fluid)
		tooltip.add(Component.literal(spacing).append(Component.translatable("goggles.chemicaladdon.contents")));
		int total = tank.getTotalAmount();
		if (total == 0) {
			tooltip.add(Component.literal(spacing + " ").append(Component.literal("0 mB")).withStyle(ChatFormatting.GRAY));
		} else {
			for (FluidStack stack : tank.getFluids()) {
				tooltip.add(Component.literal(spacing + " ").append(stack.getDisplayName())
					.withStyle(ChatFormatting.GRAY));
				tooltip.add(Component.literal(spacing + "  ")
					.append(Component.literal(stack.getAmount() + " mB")).withStyle(ChatFormatting.GOLD)
					.append(Component.literal(" / " + tank.getTankCapacity(0) + " mB").withStyle(ChatFormatting.DARK_GRAY)));
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
		if (temperature >= 800) {
			return "goggles.chemicaladdon.heat.superheated";
		}
		if (temperature >= 400) {
			return "goggles.chemicaladdon.heat.heated";
		}
		return "goggles.chemicaladdon.heat.none";
	}

}
