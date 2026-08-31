package com.yu1745.chemicaladdon.reactor;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.vessel.IMasterBound;
import com.yu1745.chemicaladdon.vessel.IShellPartEntity;
import com.yu1745.chemicaladdon.vessel.ProcessCapability;
import com.yu1745.chemicaladdon.vessel.VesselBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;

/**
 * Compressor (施工包 F3 / plans/07 §2.4): a side-wall shell part that holds a
 * SEALED vessel at process pressure while it has FE. It publishes the
 * {@link ProcessCapability#PRESSURIZED} capability through the structure
 * snapshot when effective — recipes gate on {@code requiredCapabilities:
 * ["pressurized"]}. The numeric vessel-pressure model (U1, content-derived)
 * stays untouched: this is the <i>equipment</i> axis, that is the <i>reading</i>
 * axis.
 *
 * <p>Maintenance draw: {@link #FE_PER_STEP} per 10-tick step while effective —
 * running a pressurized recipe is a continuous energy commitment; run the FE
 * dry and the capability (and every recipe requiring it) drops.
 */
public class CompressorBlockEntity extends BlockEntity
	implements IMasterBound, IShellPartEntity, IHaveGoggleInformation {

	public static final ResourceLocation PART_ID = new ResourceLocation(ChemicalAddon.MODID, "compressor");
	public static final int ENERGY_CAPACITY = 20000;
	public static final int ENERGY_TRANSFER = 2000;
	public static final int FE_PER_STEP = 400;
	private static final int STEP_TICK = 10;

	/** Why the compressor is (not) holding pressure. */
	public enum Status {
		UNBOUND, VESSEL_NOT_SEALED, NO_POWER, PRESSURIZING
	}

	@Nullable
	private BlockPos masterPos;
	private Status status = Status.UNBOUND;
	private final DirtyEnergyStorage energy = new DirtyEnergyStorage(ENERGY_CAPACITY, ENERGY_TRANSFER,
		ENERGY_CAPACITY, this::onEnergyChanged);
	private LazyOptional<net.minecraftforge.energy.IEnergyStorage> energyCap = LazyOptional.of(() -> energy);
	private int tickCounter = 0;

	public CompressorBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.COMPRESSOR.get(), pos, state);
	}

	private void onEnergyChanged() {
		setChanged();
		sync();
	}

	@Override
	public void onLoad() {
		super.onLoad();
		// a shell-part replacement lands AFTER the old brick's onRemove re-bound the
		// shell — the new BE did not exist yet (the B2 lesson). Retry the bind once
		// after registration; event-time repair, never a tick scan.
		if (level != null && !level.isClientSide && masterPos == null) {
			GasDistributorBlock.tryReformNearby(level, worldPosition);
		}
	}

	public void tick() {
		if (level == null || level.isClientSide || ++tickCounter % STEP_TICK != 0) {
			return;
		}
		boolean wasEffective = isPartEffective();
		if (masterPos != null && vessel() != null && !vessel().isOpen()) {
			if (energy.getEnergyStored() >= FE_PER_STEP) {
				energy.extractEnergy(FE_PER_STEP, false);
				setStatus(Status.PRESSURIZING);
			} else {
				setStatus(Status.NO_POWER);
			}
		} else {
			setStatus(masterPos == null ? Status.UNBOUND : Status.VESSEL_NOT_SEALED);
		}
		if (wasEffective != isPartEffective()) {
			// the snapshot is derived live, but the vessel should re-sync its
			// consumers when the capability set changes
			if (vessel() != null) {
				vessel().setChanged();
			}
			sync();
		}
	}

	@Nullable
	private VesselBlockEntity vessel() {
		if (level == null || masterPos == null) {
			return null;
		}
		return level.getBlockEntity(masterPos) instanceof VesselBlockEntity vb ? vb : null;
	}

	@Override
	public BlockEntity getValidMaster() {
		VesselBlockEntity vessel = vessel();
		return vessel != null && vessel.isAssembled() ? vessel : null;
	}

	// ---------------------------------------------------------- shell part API

	@Override
	public ResourceLocation partId() {
		return PART_ID;
	}

	@Override
	public boolean isPartEffective() {
		VesselBlockEntity vessel = vessel();
		return vessel != null && vessel.isAssembled() && !vessel.isOpen()
			&& energy.getEnergyStored() >= FE_PER_STEP;
	}

	@Override
	public Set<ProcessCapability> effectiveCapabilities() {
		return isPartEffective() ? Set.of(ProcessCapability.PRESSURIZED) : Set.of();
	}

	@Override
	public float effectiveAgitation() {
		return 0f;
	}

	@Override
	public CompoundTag getUpdateTag() {
		return saveWithoutMetadata();
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
		}
		sync();
	}

	private void setStatus(Status value) {
		if (status != value) {
			status = value;
			setChanged();
			sync();
		}
	}

	private void sync() {
		if (level instanceof ServerLevel serverLevel) {
			ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(this);
			serverLevel.getServer().getPlayerList().broadcast(null, worldPosition.getX(), worldPosition.getY(),
				worldPosition.getZ(), 64.0, serverLevel.dimension(), packet);
		}
	}

	public Status getStatus() {
		return status;
	}

	@Nullable
	@Override
	public BlockPos getMasterPos() {
		return masterPos;
	}

	public DirtyEnergyStorage getEnergy() {
		return energy;
	}

	// ------------------------------------------------------------- capability

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
		if (cap == ForgeCapabilities.ENERGY) {
			return energyCap.cast();
		}
		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		energyCap.invalidate();
	}

	@Override
	public void reviveCaps() {
		super.reviveCaps();
		energyCap = LazyOptional.of(() -> energy);
	}

	// ---------------------------------------------------------- serialization

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		if (masterPos != null) {
			tag.putLong("master", masterPos.asLong());
		}
		tag.putInt("energy", energy.getEnergyStored());
		tag.putString("status", status.name());
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		masterPos = tag.contains("master") ? BlockPos.of(tag.getLong("master")) : null;
		if (tag.contains("energy")) {
			energy.setEnergyStored(tag.getInt("energy"));
		}
		if (tag.contains("status")) {
			try {
				status = Status.valueOf(tag.getString("status"));
			} catch (IllegalArgumentException ignored) {
				status = Status.UNBOUND;
			}
		}
	}

	// ------------------------------------------------------------- goggles HUD

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		String spacing = " ";
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("block.chemicaladdon.compressor")));
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.energy", energy.getEnergyStored(), ENERGY_CAPACITY))
			.withStyle(ChatFormatting.RED));
		ChatFormatting color = switch (status) {
			case PRESSURIZING -> ChatFormatting.GREEN;
			case NO_POWER, VESSEL_NOT_SEALED -> ChatFormatting.GOLD;
			case UNBOUND -> ChatFormatting.RED;
		};
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.status"))
			.append(Component.translatable("status.chemicaladdon.compressor_" + status.name().toLowerCase()))
			.withStyle(color));
		return true;
	}
}
