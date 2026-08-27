package com.yu1745.chemicaladdon.reactor;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.recipe.ChemicalReactionRecipe;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.vessel.IMasterBound;
import com.yu1745.chemicaladdon.vessel.IShellPartEntity;
import com.yu1745.chemicaladdon.vessel.ProcessCapability;
import com.yu1745.chemicaladdon.vessel.VesselBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/**
 * B3 catalyst tray: a side-wall vessel shell block holding a one-slot catalyst
 * inventory. FACING points into the vessel; the opposite face is the sole Forge
 * {@code ITEM_HANDLER} endpoint (world insert/extract, no GUI). A bound,
 * correctly placed, non-empty tray publishes the part id
 * {@code chemicaladdon:catalyst_tray} and {@link ProcessCapability#CATALYST_BED};
 * recipes requiring them only match while catalyst remains. Each catalyst item
 * pays for {@link CatalystUsage#BATCHES_PER_ITEM} successful catalyst-required
 * recipe batches and is consumed only on the batch that completes them.
 */
public class CatalystTrayBlockEntity extends BlockEntity
	implements IMasterBound, IShellPartEntity, IHaveGoggleInformation {

	public static final ResourceLocation PART_ID = new ResourceLocation(ChemicalAddon.MODID, "catalyst_tray");

	/** Item tag a tray accepts as catalyst (single source: data/.../tags/items/catalysts.json). */
	public static final TagKey<Item> CATALYST_TAG =
		TagKey.create(Registries.ITEM, new ResourceLocation(ChemicalAddon.MODID, "catalysts"));

	public enum Status {
		UNBOUND,
		WRONG_POSITION_OR_FACING,
		EMPTY,
		ACTIVE
	}

	@Nullable
	private BlockPos masterPos;
	private Status status = Status.UNBOUND;
	/** Batches the front catalyst item has already paid for (CatalystUsage domain). */
	private int batchesUsed = 0;
	private final ItemStackHandler catalysts = new ItemStackHandler(1) {
		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return stack.is(CATALYST_TAG);
		}

		@Override
		protected void onContentsChanged(int slot) {
			setChanged();
			if (level instanceof ServerLevel) {
				sync();
			}
		}
	};
	private final LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> catalysts);

	public CatalystTrayBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.CATALYST_TRAY.get(), pos, state);
	}

	@Override
	public void onLoad() {
		super.onLoad();
		// same replacement/reload repair as B2: event-time rebind, never a tick scan
		if (level != null && !level.isClientSide && (masterPos == null || validVessel() == null)) {
			CatalystTrayBlock.tryReformNearby(level, worldPosition);
		}
	}

	@Override
	public ResourceLocation partId() {
		return PART_ID;
	}

	@Override
	public boolean isPartEffective() {
		return validVessel() != null && isInwardWallInstall() && !getCatalystStack().isEmpty();
	}

	@Override
	public Set<ProcessCapability> effectiveCapabilities() {
		return isPartEffective() ? Set.of(ProcessCapability.CATALYST_BED) : Set.of();
	}

	@Override
	public float effectiveAgitation() {
		return 0f;
	}

	@Override
	public CompoundTag getUpdateTag() {
		return saveWithoutMetadata(); // master binding, status, catalyst inventory, batches-used
	}

	@Nullable
	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void setMaster(@Nullable BlockPos masterPos) {
		this.masterPos = masterPos;
		setChanged();
		if (masterPos == null) {
			setStatus(Status.UNBOUND);
		} else if (level != null && !level.isClientSide) {
			setStatus(evaluate());
		}
		if (level instanceof ServerLevel serverLevel) {
			serverLevel.getServer().getPlayerList().broadcast(null, worldPosition.getX(), worldPosition.getY(),
				worldPosition.getZ(), 64.0, serverLevel.dimension(), ClientboundBlockEntityDataPacket.create(this));
		}
	}

	@Nullable
	@Override
	public BlockPos getMasterPos() {
		return masterPos;
	}

	@Nullable
	@Override
	public BlockEntity getValidMaster() {
		return validVessel();
	}

	@Nullable
	public VesselBlockEntity validVessel() {
		if (masterPos == null || level == null) {
			return null;
		}
		BlockEntity master = level.getBlockEntity(masterPos);
		return master instanceof VesselBlockEntity vessel && vessel.isAssembled() ? vessel : null;
	}

	private boolean isInwardWallInstall() {
		VesselBlockEntity vessel = validVessel();
		return vessel != null
			&& vessel.isCatalystTrayPosition(worldPosition, getBlockState().getValue(CatalystTrayBlock.FACING));
	}

	/** The outward face — the only side exposing the item endpoint. */
	public Direction outwardFace() {
		return getBlockState().getValue(CatalystTrayBlock.FACING).getOpposite();
	}

	public Status getStatus() {
		return status;
	}

	/** The front catalyst stack (empty when uncharged). */
	public ItemStack getCatalystStack() {
		return catalysts.getStackInSlot(0);
	}

	/** Batches the front item has already paid for (diagnostics/tests). */
	public int getBatchesUsed() {
		return batchesUsed;
	}

	public ItemStackHandler getCatalysts() {
		return catalysts;
	}

	/** Recompute the passive diagnostic on explicit player interaction. */
	public Status refreshDiagnostic() {
		if (level != null && !level.isClientSide) {
			setStatus(evaluate());
		}
		return status;
	}

	private Status evaluate() {
		VesselBlockEntity vessel = validVessel();
		if (vessel == null) {
			return Status.UNBOUND;
		}
		if (!isInwardWallInstall()) {
			return Status.WRONG_POSITION_OR_FACING;
		}
		return getCatalystStack().isEmpty() ? Status.EMPTY : Status.ACTIVE;
	}

	/**
	 * One successful catalyst-required recipe batch completed (B3 ledger hook).
	 * Charges the front item; when its {@link CatalystUsage#BATCHES_PER_ITEM}
	 * batches are reached the item is consumed (shrunk by one) and the next
	 * item starts fresh.
	 *
	 * @return true when this tray actually carried the charge (non-empty slot)
	 */
	@Override
	public boolean recordBatchCompletion() {
		ItemStack stack = catalysts.getStackInSlot(0);
		if (stack.isEmpty()) {
			return false;
		}
		CatalystUsage.State next = CatalystUsage.advance(stack.getCount(), batchesUsed);
		batchesUsed = next.used();
		stack.setCount(next.count());
		if (next.count() <= 0) {
			catalysts.setStackInSlot(0, ItemStack.EMPTY);
		} else {
			catalysts.setStackInSlot(0, stack);
		}
		setChanged();
		if (level != null && !level.isClientSide) {
			setStatus(evaluate());
			sync();
		}
		return true;
	}

	/** Whether a recipe demands this part (by part id or the CATALYST_BED capability). */
	public static boolean recipeRequiresCatalyst(ChemicalReactionRecipe recipe) {
		return recipe.getRequiredParts().contains(PART_ID)
			|| recipe.getRequiredCapabilities().contains(ProcessCapability.CATALYST_BED);
	}

	private void setStatus(Status next) {
		if (status == next) {
			return;
		}
		status = next;
		setChanged();
		if (level instanceof ServerLevel) {
			sync();
		}
	}

	private void sync() {
		if (level instanceof ServerLevel serverLevel) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
			serverLevel.getServer().getPlayerList().broadcast(null, worldPosition.getX(), worldPosition.getY(),
				worldPosition.getZ(), 64.0, serverLevel.dimension(), ClientboundBlockEntityDataPacket.create(this));
		}
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
		// ITEM_HANDLER only on the outward face — the tray never leaks the
		// inventory into the vessel interior or along the wall
		if (cap == ForgeCapabilities.ITEM_HANDLER && side == outwardFace()) {
			return itemCap.cast();
		}
		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		itemCap.invalidate();
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		if (masterPos != null) {
			tag.putLong("masterPos", masterPos.asLong());
		}
		tag.putString("status", status.name());
		tag.put("trayCatalysts", catalysts.serializeNBT());
		tag.putInt("catalystBatches", batchesUsed);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		masterPos = tag.contains("masterPos") ? BlockPos.of(tag.getLong("masterPos")) : null;
		try {
			status = tag.contains("status") ? Status.valueOf(tag.getString("status")) : Status.UNBOUND;
		} catch (IllegalArgumentException ignored) {
			status = Status.UNBOUND;
		}
		catalysts.deserializeNBT(tag.getCompound("trayCatalysts"));
		batchesUsed = CatalystUsage.normalize(
			catalysts.getStackInSlot(0).getCount(), tag.getInt("catalystBatches")).used();
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		tooltip.add(Component.literal(" ").append(Component.translatable("goggles.chemicaladdon.catalyst_tray")));
		ChatFormatting color = switch (status) {
			case ACTIVE -> ChatFormatting.GREEN;
			case EMPTY -> ChatFormatting.GOLD;
			case WRONG_POSITION_OR_FACING, UNBOUND -> ChatFormatting.RED;
		};
		tooltip.add(Component.literal(" ").append(Component.translatable(
			"catalyst_tray.chemicaladdon.status." + status.name().toLowerCase())).withStyle(color));
		ItemStack stack = getCatalystStack();
		if (stack.isEmpty()) {
			tooltip.add(Component.literal(" ")
				.append(Component.translatable("goggles.chemicaladdon.catalyst_tray.empty"))
				.withStyle(ChatFormatting.GRAY));
		} else {
			tooltip.add(Component.literal(" ").append(Component.translatable(
				"goggles.chemicaladdon.catalyst_tray.charge",
				stack.getHoverName(), stack.getCount(),
				CatalystUsage.remaining(stack.getCount(), batchesUsed)))
				.withStyle(ChatFormatting.GRAY));
		}
		return true;
	}
}
