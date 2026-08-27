package com.yu1745.chemicaladdon.reactor;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.vessel.IMasterBound;
import com.yu1745.chemicaladdon.vessel.IShellPartEntity;
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
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * B4 metering inlet: a directional side-wall vessel shell block admitting
 * liquid into the vessel only until a world-scroll-configured batch dose is
 * reached. FACING points into the vessel; the opposite outward face is the
 * sole Forge {@code FLUID_HANDLER} inlet (never gases — lighter-than-air
 * fluids belong to the B2 gas distributor). The admitted counter counts only
 * actual EXECUTE fills and is reset by an empty-hand right click (the
 * physical reset for the next batch).
 *
 * <p>The dose is a Create {@link ScrollValueBehaviour} value box on the
 * outward face (world interaction first, no GUI): 100–16000 mB in 100 mB
 * steps, default 1000 mB. Redstone: strong 15 once the batch is DONE; the
 * comparator reports the admitted fraction of the dose (0 when unbound or
 * misplaced). The inlet publishes the stable part id
 * {@code chemicaladdon:metering_inlet} but intentionally no process
 * capability — generic batch automation arrives with the status port.</p>
 */
public class MeteringInletBlockEntity extends SmartBlockEntity
	implements IMasterBound, IShellPartEntity, IHaveGoggleInformation {

	public static final ResourceLocation PART_ID = new ResourceLocation(ChemicalAddon.MODID, "metering_inlet");

	public enum Status {
		UNBOUND,
		MISPLACED,
		NON_LIQUID,
		DONE,
		NO_CAPACITY,
		METERING,
		READY
	}

	@Nullable
	private BlockPos masterPos;
	private Status status = Status.UNBOUND;
	/** mB admitted into the vessel for the current batch (EXECUTE fills only). */
	private int admittedMb = 0;
	/** Last redstone DONE state (server-side transition tracking). */
	private boolean lastDone = false;

	private ScrollValueBehaviour dose;
	private final LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(InletHandler::new);

	public MeteringInletBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.METERING_INLET.get(), pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		// World-scroll dose value box pinned to the outward (pipe) face. The dose
		// is stored in coarse 100 mB steps so the value-settings board stays
		// small; the board rescales back to mB for display.
		CenteredSideValueBoxTransform slot = new CenteredSideValueBoxTransform(this::isValueBoxSide);
		dose = new ScrollValueBehaviour(Component.translatable("metering_inlet.chemicaladdon.dose"), this, slot) {
			@Override
			public ValueSettingsBoard createBoard(net.minecraft.world.entity.player.Player player,
				net.minecraft.world.phys.BlockHitResult hitResult) {
				return new ValueSettingsBoard(label, MeteringInletMath.MAX_STEPS, MeteringInletMath.MAX_STEPS / 10,
					ImmutableList.of(Component.literal("mB")),
					new ValueSettingsFormatter(
						v -> Component.literal((v.value() * MeteringInletMath.DOSE_STEP_MB) + " mB")));
			}
		};
		dose.between(MeteringInletMath.MIN_STEPS, MeteringInletMath.MAX_STEPS);
		dose.value = MeteringInletMath.DEFAULT_STEPS;
		dose.withFormatter(i -> i * MeteringInletMath.DOSE_STEP_MB + " mB");
		behaviours.add(dose);
	}

	private boolean isValueBoxSide(BlockState state, Direction side) {
		return side == outwardFace();
	}

	@Override
	public void onLoad() {
		super.onLoad();
		// same event-time replacement/reload repair as B2/B3: never a tick scan
		if (level != null && !level.isClientSide && (masterPos == null || validVessel() == null)) {
			MeteringInletBlock.tryReformNearby(level, worldPosition);
		}
	}

	// ------------------------------------------------------------ part contract

	@Override
	public ResourceLocation partId() {
		return PART_ID;
	}

	@Override
	public boolean isPartEffective() {
		return validVessel() != null && isInwardWallInstall();
	}

	@Override
	public Set<com.yu1745.chemicaladdon.vessel.ProcessCapability> effectiveCapabilities() {
		// stable part id only — no generic process capability until the batch
		// automation layer lands (status port / recipe gating)
		return Set.of();
	}

	@Override
	public float effectiveAgitation() {
		return 0f;
	}

	// ------------------------------------------------------------ master binding

	@Override
	public void setMaster(@Nullable BlockPos masterPos) {
		this.masterPos = masterPos;
		setChanged();
		if (masterPos == null) {
			setStatus(Status.UNBOUND);
		} else if (level != null && !level.isClientSide) {
			setStatus(evaluate(null));
		}
		sync();
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
			&& vessel.isMeteringInletPosition(worldPosition, getBlockState().getValue(MeteringInletBlock.FACING));
	}

	/** The outward face — the only side exposing the metering fluid endpoint. */
	public Direction outwardFace() {
		return getBlockState().getValue(MeteringInletBlock.FACING).getOpposite();
	}

	// ------------------------------------------------------------ batch state

	/** The configured batch dose in mB (world-scroll value box). */
	public int getDoseMb() {
		return (dose != null ? dose.getValue() : MeteringInletMath.DEFAULT_STEPS) * MeteringInletMath.DOSE_STEP_MB;
	}

	/** Test/diagnostic setter that mirrors one scroll step change. */
	public void setDoseSteps(int steps) {
		if (dose != null) {
			dose.setValue(MeteringInletMath.clampSteps(steps));
		}
	}

	/** mB admitted into the vessel for the current batch. */
	public int getAdmittedMb() {
		return admittedMb;
	}

	public Status getStatus() {
		return status;
	}

	/** Physical reset: clear the current batch counter (empty-hand right click). */
	public void resetBatch() {
		admittedMb = 0;
		lastDone = false;
		if (level != null && !level.isClientSide) {
			setChanged();
			setStatus(evaluate(null));
			sync();
			level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
		}
	}

	/** Recompute the passive diagnostic on an explicit player interaction. */
	public Status refreshDiagnostic() {
		if (level != null && !level.isClientSide) {
			setStatus(evaluate(null));
		}
		return status;
	}

	private boolean isLiquid(FluidStack stack) {
		// project-wide gas classification: FluidType lighter-than-air. The inlet
		// is the liquid counterpart of the B2 gas distributor on purpose.
		return !stack.isEmpty() && !stack.getFluid().getFluidType().isLighterThanAir();
	}

	private Status evaluate(@Nullable FluidStack resource) {
		VesselBlockEntity vessel = validVessel();
		if (vessel == null) {
			return Status.UNBOUND;
		}
		if (!isInwardWallInstall()) {
			return Status.MISPLACED;
		}
		if (resource != null && !isLiquid(resource)) {
			return Status.NON_LIQUID;
		}
		if (MeteringInletMath.isDone(getDoseMb(), admittedMb)) {
			return Status.DONE;
		}
		if (vessel.getTank().getTotalAmount() >= vessel.getTank().getTankCapacity(0)) {
			return Status.NO_CAPACITY;
		}
		return admittedMb > 0 ? Status.METERING : Status.READY;
	}

	private void setStatus(Status next) {
		if (status == next) {
			return;
		}
		status = next;
		setChanged();
		sync();
	}

	private void sync() {
		if (level instanceof ServerLevel serverLevel) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
			serverLevel.getServer().getPlayerList().broadcast(null, worldPosition.getX(), worldPosition.getY(),
				worldPosition.getZ(), 64.0, serverLevel.dimension(), ClientboundBlockEntityDataPacket.create(this));
		}
	}

	// -------------------------------------------------------------- redstone

	/** Strong redstone: 15 once the batch reached its dose, else 0. */
	public int doneSignal() {
		return isDoneForRedstone() ? 15 : 0;
	}

	private boolean isDoneForRedstone() {
		return validVessel() != null && isInwardWallInstall()
			&& MeteringInletMath.isDone(getDoseMb(), admittedMb);
	}

	/** Comparator: admitted/dose scaled onto 0..15 (0 when unbound/misplaced). */
	public int analogSignal() {
		if (validVessel() == null || !isInwardWallInstall()) {
			return 0;
		}
		int doseMb = getDoseMb();
		if (doseMb <= 0) {
			return 0;
		}
		return net.minecraft.util.Mth.clamp(admittedMb * 15 / doseMb, 0, 15);
	}

	// ------------------------------------------------------------ capabilities

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
		// The outward face is the ONLY fluid endpoint (B2 convention: a
		// directional inlet part deliberately does not proxy the vessel tank on
		// its other faces — an unmetered fill path would defeat the metering
		// contract; ordinary wall bricks already proxy).
		if (cap == ForgeCapabilities.FLUID_HANDLER && side != null && side == outwardFace()
			&& isInwardWallInstall() && validVessel() != null) {
			return fluidCap.cast();
		}
		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		fluidCap.invalidate();
	}

	/** The one-way metering endpoint exposed on the outward face. */
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
			return isLiquid(stack);
		}

		@Override
		public int fill(FluidStack resource, FluidAction action) {
			if (resource.isEmpty()) {
				return 0;
			}
			VesselBlockEntity vessel = validVessel();
			if (vessel == null) {
				if (action.execute()) {
					setStatus(Status.UNBOUND);
				}
				return 0;
			}
			if (!isInwardWallInstall()) {
				if (action.execute()) {
					setStatus(Status.MISPLACED);
				}
				return 0;
			}
			if (!isLiquid(resource)) {
				if (action.execute()) {
					setStatus(Status.NON_LIQUID);
				}
				return 0;
			}
			int allowed = MeteringInletMath.remainingMb(getDoseMb(), admittedMb);
			if (allowed <= 0) {
				if (action.execute()) {
					setStatus(Status.DONE);
				}
				return 0;
			}
			FluidStack limited = resource.copy();
			limited.setAmount(Math.min(resource.getAmount(), allowed));
			int accepted = vessel.getTank().fill(limited, action);
			if (!action.execute()) {
				return accepted; // SIMULATE never touches the admitted counter
			}
			if (accepted > 0) {
				admittedMb += accepted;
				setChanged();
				updateRedstone();
			}
			setStatus(evaluate(null));
			sync();
			return accepted;
		}

		@Override
		public FluidStack drain(FluidStack resource, FluidAction action) {
			return FluidStack.EMPTY; // one-way inlet, never drains
		}

		@Override
		public FluidStack drain(int maxDrain, FluidAction action) {
			return FluidStack.EMPTY;
		}
	}

	/** Neighbor update on the DONE redstone transition only. */
	private void updateRedstone() {
		boolean done = isDoneForRedstone();
		if (done != lastDone) {
			lastDone = done;
			if (level != null && !level.isClientSide) {
				level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
			}
		}
	}

	// ------------------------------------------------------------ serialization

	@Override
	public CompoundTag getUpdateTag() {
		return saveWithoutMetadata(); // master binding, status, dose, admitted counter
	}

	@Nullable
	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	protected void write(CompoundTag tag, boolean clientPacket) {
		super.write(tag, clientPacket); // SmartBlockEntity persists behaviours (dose)
		if (masterPos != null) {
			tag.putLong("masterPos", masterPos.asLong());
		}
		tag.putString("status", status.name());
		tag.putInt("admittedMb", admittedMb);
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		super.read(tag, clientPacket);
		masterPos = tag.contains("masterPos") ? BlockPos.of(tag.getLong("masterPos")) : null;
		try {
			status = tag.contains("status") ? Status.valueOf(tag.getString("status")) : Status.UNBOUND;
		} catch (IllegalArgumentException ignored) {
			status = Status.UNBOUND;
		}
		admittedMb = Math.max(0, tag.getInt("admittedMb"));
		lastDone = status == Status.DONE;
	}

	// ------------------------------------------------------------ goggles

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		tooltip.add(Component.literal(" ").append(Component.translatable("goggles.chemicaladdon.metering_inlet")));
		ChatFormatting color = switch (status) {
			case READY, METERING -> status == Status.READY ? ChatFormatting.GREEN : ChatFormatting.AQUA;
			case DONE -> ChatFormatting.GOLD;
			case UNBOUND, MISPLACED, NON_LIQUID, NO_CAPACITY -> ChatFormatting.RED;
		};
		tooltip.add(Component.literal(" ").append(Component.translatable(
			"metering_inlet.chemicaladdon.status." + status.name().toLowerCase())).withStyle(color));
		tooltip.add(Component.literal(" ").append(Component.translatable(
			"metering_inlet.chemicaladdon.progress", admittedMb, getDoseMb(),
			MeteringInletMath.remainingMb(getDoseMb(), admittedMb))).withStyle(ChatFormatting.GRAY));
		return true;
	}
}
