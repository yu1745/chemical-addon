package com.yu1745.chemicaladdon.reactor;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.vessel.IMasterBound;
import com.yu1745.chemicaladdon.vessel.ProcessCapability;
import com.yu1745.chemicaladdon.vessel.IShellPartEntity;
import com.yu1745.chemicaladdon.vessel.VesselBlockEntity;
import com.yu1745.chemicaladdon.vessel.StructureCapabilities;

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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/** B2 one-way, submerged gas inlet mounted in a vessel shell. */
public class GasDistributorBlockEntity extends BlockEntity implements IMasterBound, IShellPartEntity, IHaveGoggleInformation {

	public static final ResourceLocation PART_ID = new ResourceLocation(ChemicalAddon.MODID, "gas_distributor");

	public enum Status {
		UNBOUND,
		WRONG_POSITION_OR_FACING,
		NOT_SUBMERGED,
		NON_GAS,
		NO_CAPACITY,
		RATE_LIMITED,
		ACCEPTING
	}

	@Nullable
	private BlockPos masterPos;
	private Status status = Status.UNBOUND;
	private long windowStart = Long.MIN_VALUE;
	private int acceptedInWindow;
	private final LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(() -> new InletHandler());

	public GasDistributorBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.GAS_DISTRIBUTOR.get(), pos, state);
	}

	@Override
	public void onLoad() {
		super.onLoad();
		// LevelChunk invokes the new block's onPlace before creating its BE. A
		// replacement therefore cannot bind here in the block callback. Retry once
		// after this BE is registered; this is an event/reload repair, never a tick
		// scan. It also repairs old saves produced by the broken replacement path.
		if (level != null && !level.isClientSide && (masterPos == null || validVessel() == null)) {
			GasDistributorBlock.tryReformNearby(level, worldPosition);
		}
	}

	public void tick() {
		if (level != null && !level.isClientSide && level.getGameTime() % GasDistributorMath.WINDOW_TICKS == 0) {
			setStatus(evaluate(null));
		}
	}

	@Override
	public ResourceLocation partId() {
		return PART_ID;
	}

	@Override
	public boolean isPartEffective() {
		return validVessel() != null && inputFace() != null && isSubmerged(validVessel());
	}

	@Override
	public Set<ProcessCapability> effectiveCapabilities() {
		return isPartEffective() ? Set.of(ProcessCapability.GAS_DISPERSED) : Set.of();
	}

	@Override
	public float effectiveAgitation() {
		return 0f;
	}

	@Override
	public CompoundTag getUpdateTag() {
		return saveWithoutMetadata(); // master binding, status, rate window
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
			setStatus(evaluate(null));
		}
		if (level instanceof ServerLevel serverLevel) {
			ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(this);
			serverLevel.getServer().getPlayerList().broadcast(null, worldPosition.getX(), worldPosition.getY(),
				worldPosition.getZ(), 64.0, serverLevel.dimension(), packet);
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

	/** The one external face that accepts gas, or null when not a valid install. */
	@Nullable
	public Direction inputFace() {
		VesselBlockEntity vessel = validVessel();
		if (vessel == null || !vessel.isGasDistributorPosition(worldPosition, getBlockState().getValue(GasDistributorBlock.FACING))) {
			return null;
		}
		return getBlockState().getValue(GasDistributorBlock.FACING).getOpposite();
	}

	public Status getStatus() {
		return status;
	}

	/** Recompute the passive diagnostic on an explicit player interaction. */
	public Status refreshDiagnostic() {
		if (level != null && !level.isClientSide) {
			setStatus(evaluate(null));
		}
		return status;
	}

	public long getWindowStart() {
		return windowStart;
	}

	public int getAcceptedInWindow() {
		return acceptedInWindow;
	}

	private boolean isSubmerged(VesselBlockEntity vessel) {
		Direction facing = getBlockState().getValue(GasDistributorBlock.FACING);
		double outletY = facing == Direction.UP ? worldPosition.getY() + 1.0 : worldPosition.getY() + 0.5;
		return GasDistributorMath.isSubmerged((double) vessel.getLiquidSurfaceY(1.0f), outletY);
	}

	private boolean isGas(FluidStack stack) {
		// The project-wide gas classification is FluidType lighter-than-air. This
		// deliberately does not inspect a fluid id or maintain a whitelist.
		return !stack.isEmpty() && stack.getFluid().getFluidType().isLighterThanAir();
	}

	private Status evaluate(@Nullable FluidStack resource) {
		VesselBlockEntity vessel = validVessel();
		if (vessel == null) {
			return Status.UNBOUND;
		}
		if (inputFace() == null) {
			return Status.WRONG_POSITION_OR_FACING;
		}
		if (!isSubmerged(vessel)) {
			return Status.NOT_SUBMERGED;
		}
		if (resource != null && !isGas(resource)) {
			return Status.NON_GAS;
		}
		if (vessel.getTank().getTotalAmount() >= vessel.getTank().getTankCapacity(0)) {
			return Status.NO_CAPACITY;
		}
		if (resource != null && GasDistributorMath.available(now(), windowStart, acceptedInWindow,
			resource.getAmount()) <= 0) {
			return Status.RATE_LIMITED;
		}
		return Status.ACCEPTING;
	}

	private long now() {
		return level == null ? 0L : level.getGameTime();
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

	/** Execute-only state sync; SIMULATE never reaches this method. */
	private void sync() {
		if (level instanceof ServerLevel serverLevel) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
			serverLevel.getServer().getPlayerList().broadcast(null, worldPosition.getX(), worldPosition.getY(),
				worldPosition.getZ(), 64.0, serverLevel.dimension(), ClientboundBlockEntityDataPacket.create(this));
		}
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
		if (cap == ForgeCapabilities.FLUID_HANDLER && side != null && side == inputFace()) {
			return fluidCap.cast();
		}
		return LazyOptional.empty();
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		fluidCap.invalidate();
	}

	private final class InletHandler implements IFluidHandler {
		@Override
		public int getTanks() {
			return 1;
		}

		@Override
		public FluidStack getFluidInTank(int tank) {
			return FluidStack.EMPTY;
		}

		@Override
		public int getTankCapacity(int tank) {
			VesselBlockEntity vessel = validVessel();
			return vessel == null ? 0 : vessel.getTank().getTankCapacity(0);
		}

		@Override
		public boolean isFluidValid(int tank, FluidStack stack) {
			return isGas(stack);
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			if (resource.isEmpty()) {
				return 0;
			}
			VesselBlockEntity vessel = validVessel();
			Status reason = evaluate(resource);
			if (reason != Status.ACCEPTING) {
				if (action.execute()) {
					setStatus(reason);
				}
				return 0;
			}
			long time = now();
			int allowed = GasDistributorMath.available(time, windowStart, acceptedInWindow, resource.getAmount());
			if (allowed <= 0 || vessel == null) {
				if (action.execute()) {
					setStatus(Status.RATE_LIMITED);
				}
				return 0;
			}
			FluidStack limited = resource.copy();
			limited.setAmount(allowed);
			int accepted = vessel.getTank().fill(limited, action);
			if (!action.execute()) {
				return accepted;
			}
			if (accepted > 0) {
				if (windowStart == Long.MIN_VALUE || time < windowStart
					|| time - windowStart >= GasDistributorMath.WINDOW_TICKS) {
					windowStart = time;
					acceptedInWindow = 0;
				}
				acceptedInWindow = Math.min(GasDistributorMath.WINDOW_LIMIT_MB, acceptedInWindow + accepted);
				setChanged();
			}
			Status after = accepted < allowed ? Status.NO_CAPACITY
				: allowed < resource.getAmount() ? Status.RATE_LIMITED : Status.ACCEPTING;
			setStatus(after);
			if (time % GasDistributorMath.WINDOW_TICKS == 0) {
				sync(); // low-frequency replication of the persistent safety window
			}
			return accepted;
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			return FluidStack.EMPTY;
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			return FluidStack.EMPTY;
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		if (masterPos != null) {
			tag.putLong("masterPos", masterPos.asLong());
		}
		tag.putString("status", status.name());
		tag.putLong("gasWindowStart", windowStart);
		tag.putInt("gasAcceptedInWindow", acceptedInWindow);
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
		windowStart = tag.contains("gasWindowStart") ? tag.getLong("gasWindowStart") : Long.MIN_VALUE;
		acceptedInWindow = Math.max(0, Math.min(GasDistributorMath.WINDOW_LIMIT_MB,
			tag.getInt("gasAcceptedInWindow")));
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		tooltip.add(Component.literal(" ").append(Component.translatable("goggles.chemicaladdon.gas_distributor")));
		ChatFormatting color = switch (status) {
			case ACCEPTING -> ChatFormatting.GREEN;
			case RATE_LIMITED -> ChatFormatting.GOLD;
			case NO_CAPACITY, NON_GAS, NOT_SUBMERGED, WRONG_POSITION_OR_FACING, UNBOUND -> ChatFormatting.RED;
		};
		tooltip.add(Component.literal(" ").append(Component.translatable(
			"gas_distributor.chemicaladdon.status." + status.name().toLowerCase())).withStyle(color));
		tooltip.add(Component.literal(" ").append(Component.translatable("goggles.chemicaladdon.gas_distributor.rate",
			acceptedInWindow, GasDistributorMath.WINDOW_LIMIT_MB)).withStyle(ChatFormatting.GRAY));
		return true;
	}
}
