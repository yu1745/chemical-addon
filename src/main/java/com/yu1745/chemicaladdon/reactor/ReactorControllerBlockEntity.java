package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.foundation.fluid.FluidIngredient;
import com.yu1745.chemicaladdon.recipe.AllRecipeTypes;
import com.yu1745.chemicaladdon.recipe.ChemicalReactionRecipe;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.registry.AllBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
public class ReactorControllerBlockEntity extends BlockEntity implements MenuProvider {

	public static final int TANK_CAPACITY = 16000; // mB base (16 buckets) at height 3
	public static final int AMBIENT_TEMP = 20;
	public static final int ITEM_SLOTS = 4;
	public static final int MAX_TEMP = 1000;
	public static final int MIN_HEIGHT = 3;
	public static final int MAX_HEIGHT = 6;

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

	private boolean assembled = false;
	private int temperature = AMBIENT_TEMP;
	private int tickCounter = 0;
	private float progress = 0;
	@Nullable
	private ResourceLocation activeRecipe = null;

	public ReactorControllerBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.REACTOR_CONTROLLER.get(), pos, state);
	}

	public void serverTick() {
		if (level == null || level.isClientSide) {
			return;
		}
		tickCounter++;
		if (tickCounter % HEAT_TICK == 0) {
			updateHeat();
		}
		if (tickCounter % REACTION_TICK == 0) {
			tickReaction();
		}
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
			setProgress(0, null);
			return;
		}
		ChemicalReactionRecipe recipe = findRecipe();
		if (recipe == null) {
			setProgress(0, null);
			return;
		}
		float next = progress + (float) REACTION_TICK / recipe.getProcessingDuration();
		if (next >= 1.0f) {
			completeRecipe(recipe);
			setProgress(0, recipe.getId());
			sync();
		} else {
			setProgress(next, recipe.getId());
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
		// item ingredients (each consumes 1)
		for (Ingredient ingredient : recipe.getIngredients()) {
			if (!hasItem(ingredient)) {
				return false;
			}
		}
		// fluid ingredients
		for (FluidIngredient fluid : recipe.getFluidIngredients()) {
			if (!hasFluid(fluid)) {
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

	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag tag = super.getUpdateTag();
		saveAdditional(tag);
		return tag;
	}

	@Nullable
	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
		handleUpdateTag(pkt.getTag());
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
	public boolean tryAssemble() {
		if (level == null || level.isClientSide) {
			return false;
		}
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();

		for (Direction inward : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST }) {
			Direction side = inward.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
			boolean ok = true;

			// bottom layer (y-1): full 3x3 of bricks
			for (int s = -1; s <= 1 && ok; s++) {
				for (int d = 0; d <= 2 && ok; d++) {
					BlockPos p = worldPosition.offset(
						side.getStepX() * s + inward.getStepX() * d, -1, side.getStepZ() * s + inward.getStepZ() * d);
					if (!level.getBlockState(p).is(brick.getBlock())) {
						ok = false;
					}
				}
			}

			// wall layers y=0..: count consecutive ring layers; the ring is the
			// 8 blocks around the interior column (s=0,d=1 is the hollow core on
			// every wall layer, (s=0,d=0) is the controller on y=0 / wall above it)
			int height = 0;
			for (int y = 0; y < MAX_HEIGHT - 2 && ok; y++) {
				boolean layerIsRing = true;
				for (int s = -1; s <= 1 && layerIsRing; s++) {
					for (int d = 0; d <= 2 && layerIsRing; d++) {
						if (y == 0 && s == 0 && d == 0) {
							continue; // the controller itself
						}
						BlockPos p = worldPosition.offset(
							side.getStepX() * s + inward.getStepX() * d, y, side.getStepZ() * s + inward.getStepZ() * d);
						if (s == 0 && d == 1) {
							if (!level.getBlockState(p).isAir()) {
								layerIsRing = false; // interior column must be hollow
							}
						} else if (!level.getBlockState(p).is(brick.getBlock())) {
							layerIsRing = false;
						}
					}
				}
				if (layerIsRing) {
					height++;
				} else {
					break;
				}
			}

			if (height < MIN_HEIGHT - 2) {
				ok = false; // too short (bottom + walls + top needed)
			}

			// top layer at y = height: full 3x3 of bricks
			if (ok) {
				for (int s = -1; s <= 1 && ok; s++) {
					for (int d = 0; d <= 2 && ok; d++) {
						BlockPos p = worldPosition.offset(
							side.getStepX() * s + inward.getStepX() * d, height, side.getStepZ() * s + inward.getStepZ() * d);
						if (!level.getBlockState(p).is(brick.getBlock())) {
							ok = false;
						}
					}
				}
			}

			if (ok) {
				assembled = true;
				tank.setCapacity(TANK_CAPACITY * height); // 16 buckets per interior layer
				bindBricks(worldPosition, inward, side, height);
				setChanged();
				sync();
				return true;
			}
		}
		return false;
	}

	/** Points every structural brick of this vessel at the controller (or clears it). */
	private void bindBricks(BlockPos masterPos, Direction inward, Direction side, int height) {
		if (level == null) {
			return;
		}
		for (int s = -1; s <= 1; s++) {
			for (int d = 0; d <= 2; d++) {
				if (s == 0 && d == 0) {
					continue; // the controller itself
				}
				bindBrick(worldPosition.offset(side.getStepX() * s + inward.getStepX() * d, -1,
					side.getStepZ() * s + inward.getStepZ() * d), masterPos);
				bindBrick(worldPosition.offset(side.getStepX() * s + inward.getStepX() * d, height,
					side.getStepZ() * s + inward.getStepZ() * d), masterPos);
				for (int y = 0; y < height; y++) {
					bindBrick(worldPosition.offset(side.getStepX() * s + inward.getStepX() * d, y,
						side.getStepZ() * s + inward.getStepZ() * d), masterPos);
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

	public void invalidateStructure() {
		if (assembled) {
			assembled = false;
			setProgress(0, null);
			// clear master pointers on nearby bricks so they stop proxying
			if (level != null) {
				for (int dx = -3; dx <= 3; dx++) {
					for (int dy = -3; dy <= 3; dy++) {
						for (int dz = -3; dz <= 3; dz++) {
							if (level.getBlockEntity(worldPosition.offset(dx, dy, dz)) instanceof ChemicalBrickBlockEntity brick) {
								brick.setMaster(null);
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
	public Component getDisplayName() {
		return Component.translatable("block.chemicaladdon.reactor_controller");
	}

	@Nullable
	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
		return new ReactorMenu(id, inventory, worldPosition);
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
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putBoolean("assembled", assembled);
		tag.putInt("temperature", temperature);
		tag.put("tank", tank.serializeNBT());
		tag.put("items", items.serializeNBT());
		tag.putFloat("progress", progress);
		if (activeRecipe != null) {
			tag.putString("activeRecipe", activeRecipe.toString());
		}
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		assembled = tag.getBoolean("assembled");
		temperature = tag.getInt("temperature");
		tank.deserializeNBT(tag.getCompound("tank"));
		items.deserializeNBT(tag.getCompound("items"));
		progress = tag.getFloat("progress");
		activeRecipe = tag.contains("activeRecipe") ? ResourceLocation.tryParse(tag.getString("activeRecipe")) : null;
	}

}
