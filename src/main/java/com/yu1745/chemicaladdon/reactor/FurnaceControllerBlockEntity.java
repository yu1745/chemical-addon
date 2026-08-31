package com.yu1745.chemicaladdon.reactor;

import java.util.List;

import javax.annotation.Nullable;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.yu1745.chemicaladdon.recipe.AllRecipeTypes;
import com.yu1745.chemicaladdon.recipe.CalcinationRecipe;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.vessel.ProcessReadings;
import com.yu1745.chemicaladdon.vessel.VesselBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * Furnace controller (施工包 D): the fourth vessel topology — a tall solid-bed
 * kiln. It reuses the shared vessel structure layer (hollow W×W×rings shell,
 * 3×3…7×7 × up to 12 rings, sealed roof) but runs its own kiln state machine
 * (plans/06 §2: never a repainted aqueous reactor):
 *
 * <ul>
 *   <li><b>charge bed</b> — item slot 0 in (hopper/pipe), single-item batches;</li>
 *   <li><b>heat</b> — the bed carries the temperature; Blaze Burners directly
 *       below the hearth (the full footprint under the floor, any of them) set
 *       the target (KINDLED 500 °C / SEETHING 900 °C), with a guaranteed +1
 *       convergence step so the asymptote cannot strand the bed 1 °C short;</li>
 *   <li><b>calcination</b> — {@code chemicaladdon:calcination} recipes
 *       (item → item + kiln gas, {@code minTempC}); below the temperature the
 *       charge stays raw (欠烧诊断), above {@code minTempC + 300} the kiln warns
 *       OVERHEATED (结瘤 territory — penalty arrives with the FE electrode);</li>
 *   <li><b>kiln gas</b> — the recipe's fluid results land in the vessel tank
 *       (CO₂ / steam); pipe them from the side (gases drain last from the bottom).</li>
 * </ul>
 *
 * <p>Item ports by face: inserts always land in the charge bed (slot 0);
 * extraction only ever yields product (slot 1) — a hopper under the furnace
 * cannot suck the feed back out.
 */
public class FurnaceControllerBlockEntity extends VesselBlockEntity
	implements IHaveGoggleInformation, ProcessReadings {

	public static final int AMBIENT_TEMP = 20;
	public static final int MAX_TEMP = 1200;
	private static final int HEAT_TICK = 20;
	private static final int KILN_TICK = 10;

	/** Why the kiln is (not) calcining; shown in the goggles HUD / status port. */
	public enum FurnaceStatus {
		NOT_ASSEMBLED, NO_RECIPE, UNDERHEATED, CALCINING, OUTPUT_FULL, OVERHEATED
	}

	private int tickCounter = 0;
	private int temperature = AMBIENT_TEMP;
	private float progress = 0;
	private FurnaceStatus status = FurnaceStatus.NOT_ASSEMBLED;
	@Nullable
	private ResourceLocation activeRecipe = null;
	/** Debug/dev temperature pin (-1 = unpinned; the GameTests' fast-forward). */
	private int pinnedTemperature = -1;

	private final IItemHandler bedHandler = new IItemHandler() {
		@Override
		public int getSlots() {
			return 2;
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			return items.getStackInSlot(slot);
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			// inserts always land in the charge bed
			if (slot != 0 || stack.isEmpty()) {
				return stack;
			}
			ItemStack bed = items.getStackInSlot(0);
			if (!bed.isEmpty() && !ItemStack.isSameItemSameTags(bed, stack)) {
				return stack;
			}
			int limit = Math.min(getSlotLimit(0), stack.getMaxStackSize());
			int space = bed.isEmpty() ? limit : Math.min(limit, bed.getMaxStackSize()) - bed.getCount();
			int take = Math.min(space, stack.getCount());
			if (take <= 0) {
				return stack;
			}
			if (!simulate) {
				if (bed.isEmpty()) {
					ItemStack put = stack.copy();
					put.setCount(take);
					items.setStackInSlot(0, put);
				} else {
					bed.grow(take);
					onContentsChanged();
				}
			}
			ItemStack remainder = stack.copy();
			remainder.shrink(take);
			return remainder;
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			// extraction only ever yields product (slot 1)
			if (slot != 1) {
				return ItemStack.EMPTY;
			}
			ItemStack product = items.getStackInSlot(1);
			if (product.isEmpty() || amount <= 0) {
				return ItemStack.EMPTY;
			}
			int take = Math.min(amount, product.getCount());
			if (simulate) {
				ItemStack out = product.copy();
				out.setCount(take);
				return out;
			}
			ItemStack out = product.split(take);
			onContentsChanged();
			return out;
		}

		@Override
		public int getSlotLimit(int slot) {
			return 64;
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			if (slot != 0 || stack.isEmpty()) {
				return false;
			}
			ItemStack bed = items.getStackInSlot(0);
			return bed.isEmpty() || ItemStack.isSameItemSameTags(bed, stack);
		}
	};
	private LazyOptional<IItemHandler> bedPort = LazyOptional.of(() -> bedHandler);
	private final IItemHandler feedHandler = new IItemHandler() {
		@Override public int getSlots() { return 1; }
		@Override public ItemStack getStackInSlot(int slot) { return slot == 0 ? bedHandler.getStackInSlot(0) : ItemStack.EMPTY; }
		@Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			return slot == 0 ? bedHandler.insertItem(0, stack, simulate) : stack;
		}
		@Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
		@Override public int getSlotLimit(int slot) { return slot == 0 ? bedHandler.getSlotLimit(0) : 0; }
		@Override public boolean isItemValid(int slot, ItemStack stack) { return slot == 0 && bedHandler.isItemValid(0, stack); }
	};
	private final IItemHandler productHandler = new IItemHandler() {
		@Override public int getSlots() { return 1; }
		@Override public ItemStack getStackInSlot(int slot) { return slot == 0 ? bedHandler.getStackInSlot(1) : ItemStack.EMPTY; }
		@Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
		@Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
			return slot == 0 ? bedHandler.extractItem(1, amount, simulate) : ItemStack.EMPTY;
		}
		@Override public int getSlotLimit(int slot) { return slot == 0 ? bedHandler.getSlotLimit(1) : 0; }
		@Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
	};
	private LazyOptional<IItemHandler> feedPort = LazyOptional.of(() -> feedHandler);
	private LazyOptional<IItemHandler> productPort = LazyOptional.of(() -> productHandler);

	public FurnaceControllerBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.FURNACE_CONTROLLER.get(), pos, state, 1000, 2);
	}

	// ------------------------------------------------------------ shape hooks

	@Override
	protected int minSize() {
		return 3;
	}

	@Override
	protected int maxSize() {
		return 7; // plans/06 §2: 底面 3×3 ~ 7×7
	}

	@Override
	protected int minRings() {
		return 1;
	}

	@Override
	protected int maxRings() {
		return 12; // plans/06 §2: 高 3~12 的高瘦料柱
	}

	@Override
	protected int capacityFor(int w, int rings) {
		return 1000 * (w - 2) * (w - 2) * rings; // kiln-gas volume: 1 bucket per interior block
	}

	@Override
	protected void onAssembled() {
		setStatus(FurnaceStatus.NO_RECIPE);
	}

	@Override
	protected void onStructureInvalidated() {
		setStatus(FurnaceStatus.NOT_ASSEMBLED);
		progress = 0;
		activeRecipe = null;
	}

	// ------------------------------------------------------------------ tick

	@Override
	protected void vesselTick() {
		tickCounter++;
		if (tickCounter % HEAT_TICK == 0) {
			updateHeat();
		}
		if (tickCounter % KILN_TICK == 0) {
			tickCalcination();
		}
	}

	private void updateHeat() {
		int target = pinnedTemperature >= 0 ? pinnedTemperature : hearthTarget();
		int next = temperature + Integer.signum(target - temperature)
			* Math.max(1, Math.abs(target - temperature) / 10);
		// guaranteed ±1 convergence: the plain /10 relaxation truncates to +0 one
		// step short of the target and would strand a 900 °C charge at ~891 °C
		if (Math.abs(target - temperature) <= 1) {
			next = target;
		}
		if (next != temperature) {
			temperature = next;
			setChanged();
			sync();
		}
	}

	/**
	 * The °C the bed relaxes toward: the hottest Blaze Burner directly below the
	 * hearth — the full interior footprint one layer under the floor (an
	 * industrial furnace has burners all around the hearth, not just under the
	 * controller).
	 */
	private int hearthTarget() {
		if (level == null || !isAssembled() || getInward() == null) {
			return AMBIENT_TEMP;
		}
		int size = getSize();
		int iw = size - 2;
		Direction inward = getInward();
		Direction side = inward.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
		int sStart = -((size - 1) / 2) + 1;
		int burnerY = getFloorRelY() - 1;
		int best = AMBIENT_TEMP;
		for (int s = 0; s < iw; s++) {
			for (int d = 0; d < iw; d++) {
				BlockPos p = worldPosition.offset(
					side.getStepX() * (sStart + s) + inward.getStepX() * (1 + d), burnerY,
					side.getStepZ() * (sStart + s) + inward.getStepZ() * (1 + d));
				int target = switch (BlazeBurnerBlock.getHeatLevelOf(level.getBlockState(p))) {
					case KINDLED -> 500;
					case SEETHING -> 900;
					default -> AMBIENT_TEMP;
				};
				best = Math.max(best, target);
			}
		}
		return best;
	}

	/** Debug/dev: hold the bed at {@code t} °C, or {@code -1} to resume burner heating. */
	public void setPinnedTemperature(int t) {
		pinnedTemperature = t < 0 ? -1 : Math.max(AMBIENT_TEMP, Math.min(MAX_TEMP, t));
		if (pinnedTemperature >= 0) {
			temperature = pinnedTemperature;
		}
		setChanged();
		sync();
	}

	// ------------------------------------------------------------ calcination

	private void tickCalcination() {
		if (!isAssembled()) {
			setStatus(FurnaceStatus.NOT_ASSEMBLED);
			progress = 0;
			activeRecipe = null;
			return;
		}
		CalcinationRecipe recipe = findRecipe();
		if (recipe == null) {
			// no fully-matching recipe: is it a charge problem or a heat problem?
			setStatus(findChargeRecipeIgnoringHeat() != null ? FurnaceStatus.UNDERHEATED : FurnaceStatus.NO_RECIPE);
			progress = 0;
			activeRecipe = null;
			return;
		}
		if (!canFitOutputs(recipe)) {
			setStatus(FurnaceStatus.OUTPUT_FULL);
			progress = 0;
			activeRecipe = null;
			return;
		}
		if (temperature < recipe.getMinTempC()) {
			setStatus(FurnaceStatus.UNDERHEATED); // 欠烧：生料不转化
			progress = 0;
			activeRecipe = null;
			return;
		}
		setStatus(temperature > recipe.getOverheatC() ? FurnaceStatus.OVERHEATED : FurnaceStatus.CALCINING);
		progress += (float) KILN_TICK / recipe.getProcessingDuration();
		activeRecipe = recipe.getId();
		if (progress >= 1.0f) {
			complete(recipe);
			progress = 0;
			sync();
		} else {
			sync();
		}
	}

	@Nullable
	private CalcinationRecipe findRecipe() {
		if (level == null || isOpen()) {
			return null; // calcination runs in a closed kiln; an open top vents everything
		}
		for (CalcinationRecipe recipe : level.getRecipeManager().getAllRecipesFor(AllRecipeTypes.calcinationType())) {
			if (recipe.matchesCharge(() -> new ChargeBedIterator()) && temperature >= recipe.getMinTempC()) {
				return recipe;
			}
		}
		return null;
	}

	@Nullable
	private CalcinationRecipe findChargeRecipeIgnoringHeat() {
		if (level == null || isOpen()) {
			return null;
		}
		for (CalcinationRecipe recipe : level.getRecipeManager().getAllRecipesFor(AllRecipeTypes.calcinationType())) {
			if (recipe.matchesCharge(() -> new ChargeBedIterator())) {
				return recipe;
			}
		}
		return null;
	}

	/** Iterates the charge bed (slot 0) — a two-slot inventory narrowed to the feed. */
	private class ChargeBedIterator implements java.util.Iterator<ItemStack> {
		private boolean done;

		@Override
		public boolean hasNext() {
			return !done;
		}

		@Override
		public ItemStack next() {
			done = true;
			return items.getStackInSlot(0);
		}
	}

	private boolean canFitOutputs(CalcinationRecipe recipe) {
		// Reserve the single product slot cumulatively. Simulating every result
		// independently would let two outputs each claim the same free space.
		ItemStack reserved = items.getStackInSlot(1).copy();
		for (var out : recipe.getRollableResults()) {
			ItemStack stack = out.getStack();
			if (stack.isEmpty()) {
				continue;
			}
			if (reserved.isEmpty()) {
				reserved = stack.copy();
			} else if (!ItemStack.isSameItemSameTags(reserved, stack)) {
				return false;
			} else {
				reserved.grow(stack.getCount());
			}
			if (reserved.getCount() > reserved.getMaxStackSize()) {
				return false;
			}
		}
		int fluidOut = 0;
		for (FluidStack out : recipe.getFluidResults()) {
			fluidOut += out.getAmount();
		}
		return fluidOut <= tank.getTankCapacity(0) - tank.getTotalAmount();
	}

	/** A product-only item view for the fit check (the bed slot is not free space). */
	private IItemHandler productOnly() {
		return new IItemHandler() {
			@Override
			public int getSlots() {
				return 1;
			}

			@Override
			public ItemStack getStackInSlot(int slot) {
				return items.getStackInSlot(1);
			}

			@Override
			public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
				ItemStack product = items.getStackInSlot(1);
				int space = product.isEmpty() ? 64
					: Math.min(product.getMaxStackSize() - product.getCount(), stack.getMaxStackSize());
				int take = Math.min(space, stack.getCount());
				if (take <= 0) {
					return stack;
				}
				if (!simulate) {
					if (product.isEmpty()) {
						ItemStack put = stack.copy();
						put.setCount(take);
						items.setStackInSlot(1, put);
					} else {
						product.grow(take);
						onContentsChanged();
					}
				}
				ItemStack remainder = stack.copy();
				remainder.shrink(take);
				return remainder;
			}

			@Override
			public ItemStack extractItem(int slot, int amount, boolean simulate) {
				return ItemStack.EMPTY;
			}

			@Override
			public int getSlotLimit(int slot) {
				return 64;
			}

			@Override
			public boolean isItemValid(int slot, ItemStack stack) {
				return true;
			}
		};
	}

	private void complete(CalcinationRecipe recipe) {
		// consume one charge set from the bed (single-item granularity)
		for (var ingredient : recipe.getIngredients()) {
			ItemStack bed = items.getStackInSlot(0);
			if (!bed.isEmpty() && ingredient.test(bed)) {
				bed.shrink(1);
				if (bed.isEmpty()) {
					items.setStackInSlot(0, ItemStack.EMPTY);
				}
				onContentsChanged();
			}
		}
		// products into slot 1, kiln gas into the tank
		for (var out : recipe.getRollableResults()) {
			ItemStack stack = out.getStack();
			if (stack.isEmpty() || (out.getChance() < 1 && level.random.nextFloat() >= out.getChance())) {
				continue;
			}
			// canFitOutputs reserved this slot for the whole batch; never fall back
			// to the charge bed, which would contaminate feed or silently lose yield.
			ItemHandlerHelper.insertItemStacked(productOnly(), stack.copy(), false);
		}
		for (FluidStack out : recipe.getFluidResults()) {
			tank.fill(out.copy(), FluidAction.EXECUTE);
		}
	}

	private void setStatus(FurnaceStatus value) {
		if (status != value) {
			status = value;
			sync();
		}
	}

	// ------------------------------------------------------------------ reads

	public FurnaceStatus getStatus() {
		return status;
	}

	public float getProgress() {
		return progress;
	}

	@Override
	public String getProcessStatus() {
		return status.name();
	}

	@Override
	public float getProcessProgress() {
		return progress;
	}

	@Override
	public int getTemperature() {
		return temperature;
	}

	@Override
	public int getPressure() {
		return 0; // the kiln vents through the gas port; no sealed pressure model in D1
	}

	@Override
	public int getPh() {
		return 7;
	}

	@Override
	public int getTurbidity() {
		return 0;
	}

	@Override
	public int getBaume() {
		return 0;
	}

	@Override
	public int getConductivity() {
		return 0;
	}

	// ------------------------------------------------------------- capability

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
		if (cap == ForgeCapabilities.ITEM_HANDLER) {
			return side == null && isAssembled() ? bedPort.cast() : LazyOptional.empty();
		}
		return super.getCapability(cap, side);
	}

	/** Top roof is feed-only; bottom hearth is product-only. */
	public <T> LazyOptional<T> getShellItemCapability(BlockPos shellPos, @Nullable Direction side) {
		if (!isAssembled() || side == null) {
			return LazyOptional.empty();
		}
		int relY = shellPos.getY() - worldPosition.getY();
		if (relY == getRoofRelY() && side == Direction.UP) {
			return feedPort.cast();
		}
		if (relY == getInteriorBottomRelY() - 1 && side == Direction.DOWN) {
			return productPort.cast();
		}
		return LazyOptional.empty();
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		bedPort.invalidate();
		feedPort.invalidate();
		productPort.invalidate();
	}

	@Override
	public void reviveCaps() {
		super.reviveCaps();
		bedPort = LazyOptional.of(() -> bedHandler);
		feedPort = LazyOptional.of(() -> feedHandler);
		productPort = LazyOptional.of(() -> productHandler);
	}

	// ---------------------------------------------------------- serialization

	@Override
	protected void write(CompoundTag tag, boolean clientPacket) {
		super.write(tag, clientPacket);
		tag.putInt("temperature", temperature);
		tag.putFloat("progress", progress);
		tag.putString("status", status.name());
		tag.putInt("pinnedTemperature", pinnedTemperature);
		if (activeRecipe != null) {
			tag.putString("activeRecipe", activeRecipe.toString());
		}
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		super.read(tag, clientPacket);
		temperature = Math.max(AMBIENT_TEMP, tag.getInt("temperature"));
		progress = tag.getFloat("progress");
		pinnedTemperature = tag.contains("pinnedTemperature") ? tag.getInt("pinnedTemperature") : -1;
		if (tag.contains("status")) {
			try {
				status = FurnaceStatus.valueOf(tag.getString("status"));
			} catch (IllegalArgumentException ignored) {
				status = FurnaceStatus.NOT_ASSEMBLED;
			}
		}
		activeRecipe = tag.contains("activeRecipe") ? ResourceLocation.tryParse(tag.getString("activeRecipe")) : null;
	}

	// ------------------------------------------------------------- goggles HUD

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		String spacing = " ";
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("block.chemicaladdon.furnace_controller")));
		ChatFormatting heatColor = temperature >= 800 ? ChatFormatting.RED
			: temperature >= 400 ? ChatFormatting.GOLD : ChatFormatting.GRAY;
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.temperature", temperature))
			.withStyle(ChatFormatting.WHITE));
		tooltip.add(Component.literal(spacing).append(Component.translatable(heatTierKey())).withStyle(heatColor));

		ChatFormatting statusColor = switch (status) {
			case CALCINING -> ChatFormatting.GREEN;
			case UNDERHEATED -> ChatFormatting.GOLD;
			case OVERHEATED -> ChatFormatting.RED;
			case OUTPUT_FULL, NOT_ASSEMBLED -> ChatFormatting.RED;
			case NO_RECIPE -> ChatFormatting.GRAY;
		};
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.status"))
			.append(Component.translatable("status.chemicaladdon." + status.name().toLowerCase()))
			.withStyle(statusColor));

		// charge bed + product
		tooltip.add(Component.literal(spacing).append(Component.translatable("goggles.chemicaladdon.items")));
		ItemStack bed = items.getStackInSlot(0);
		ItemStack product = items.getStackInSlot(1);
		tooltip.add(Component.literal(spacing + " ")
			.append(Component.literal((bed.isEmpty() ? "-" : bed.getHoverName().getString() + " x" + bed.getCount())))
			.withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.literal(spacing + " ")
			.append(Component.literal(product.isEmpty() ? "-" : product.getHoverName().getString() + " x" + product.getCount()))
			.withStyle(ChatFormatting.DARK_GREEN));

		// kiln gas volume
		int total = tank.getTotalAmount();
		tooltip.add(Component.literal(spacing).append(Component.translatable("goggles.chemicaladdon.contents")));
		tooltip.add(Component.literal(spacing + " ")
			.append(Component.literal(total + " mB / " + tank.getTankCapacity(0) + " mB"))
			.withStyle(ChatFormatting.AQUA));

		if (activeRecipe != null && progress > 0) {
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

	/** Controller block of the furnace. */
	public static class FurnaceControllerBlock extends Block implements EntityBlock {

		public FurnaceControllerBlock(Properties properties) {
			super(properties);
		}

		@Override
		public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
			return new FurnaceControllerBlockEntity(pos, state);
		}

		@Nullable
		@Override
		public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
			if (level.isClientSide) {
				return null;
			}
			return (lvl, pos, st, be) -> {
				if (be instanceof FurnaceControllerBlockEntity furnace) {
					furnace.tick();
				}
			};
		}

		@Override
		public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
			if (level.isClientSide) {
				return InteractionResult.SUCCESS;
			}
			if (level.getBlockEntity(pos) instanceof FurnaceControllerBlockEntity furnace) {
				if (!furnace.isAssembled()) {
					boolean ok = furnace.tryAssemble().ok();
					player.displayClientMessage(Component.literal(ok
						? "§a煅烧炉成型！"
						: "§c结构不完整：需要化工砖空心壳（3×3~7×7，高最多 12 环，顶封），控制器嵌在壁中"),
						false);
				} else {
					ItemStack held = player.getItemInHand(hand);
					if (!held.isEmpty() && !player.isShiftKeyDown()) {
						ItemStack remainder = furnace.bedPort.resolve()
							.map(handler -> handler.insertItem(0, held.copy(), false))
							.orElseGet(held::copy);
						int inserted = held.getCount() - remainder.getCount();
						if (inserted > 0) {
							held.shrink(inserted);
							player.setItemInHand(hand, held);
						}
						// Consume the interaction even when the bed is full or the held item
						// cannot join its current stack; never place it against the controller.
						return InteractionResult.SUCCESS;
					}
					player.displayClientMessage(Component.literal(String.format(
						"§7煅烧炉（%s，%d°C，床 %s，产品 %s，炉气 %d mB）",
						furnace.getStatus(), furnace.getTemperature(),
						furnace.getItems().getStackInSlot(0).isEmpty() ? "-"
							: furnace.getItems().getStackInSlot(0).getHoverName().getString(),
						furnace.getItems().getStackInSlot(1).isEmpty() ? "-"
							: furnace.getItems().getStackInSlot(1).getHoverName().getString(),
						furnace.getTank().getTotalAmount())), false);
				}
			}
			return InteractionResult.sidedSuccess(level.isClientSide);
		}
	}
}
