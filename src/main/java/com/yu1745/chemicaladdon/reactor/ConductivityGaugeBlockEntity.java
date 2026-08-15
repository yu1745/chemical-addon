package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.vessel.IMasterBound;
import com.yu1745.chemicaladdon.vessel.VesselBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;

/**
 * The full-cube conductivity gauge (方块形式): a vessel shell block (in the
 * {@code vessel_walls} tag) that doubles as a conductivity gauge — the S03
 * wall-gauge pattern, reading ionic strength instead of pressure.
 */
public class ConductivityGaugeBlockEntity extends AbstractConductivityGaugeBlockEntity implements IMasterBound {

	@Nullable
	private BlockPos masterPos;

	public ConductivityGaugeBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.CONDUCTIVITY_GAUGE.get(), pos, state);
	}

	@Override
	public void setMaster(@Nullable BlockPos masterPos) {
		this.masterPos = masterPos;
		setChanged();
		if (level instanceof ServerLevel serverLevel) {
			ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(this);
			serverLevel.getServer().getPlayerList()
				.broadcast(null, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), 64.0,
					serverLevel.dimension(), packet);
		}
	}

	@Override
	@Nullable
	public BlockPos getMasterPos() {
		return masterPos;
	}

	@Override
	@Nullable
	public BlockEntity getValidMaster() {
		if (masterPos == null || level == null) {
			return null;
		}
		BlockEntity master = level.getBlockEntity(masterPos);
		if (master instanceof VesselBlockEntity vessel && vessel.isAssembled()) {
			return vessel;
		}
		return null;
	}

	@Override
	@Nullable
	protected ReactorControllerBlockEntity findReactor() {
		BlockEntity master = getValidMaster();
		return master instanceof ReactorControllerBlockEntity reactor ? reactor : null;
	}

	@Override
	protected boolean isValueBoxSide(BlockState state, Direction side) {
		return true; // a full cube: the dial can be scrolled from any face
	}

	@Override
	protected float dialOffset() {
		return 0.5f; // full cube: the dial is drawn on the block surface
	}

	@Override
	protected void write(CompoundTag tag, boolean clientPacket) {
		super.write(tag, clientPacket);
		if (masterPos != null) {
			tag.putLong("masterPos", masterPos.asLong());
		}
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		super.read(tag, clientPacket);
		masterPos = tag.contains("masterPos") ? BlockPos.of(tag.getLong("masterPos")) : null;
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
		if (cap == ForgeCapabilities.FLUID_HANDLER && side == Direction.UP) {
			return LazyOptional.empty();
		}
		BlockEntity master = getValidMaster();
		if (master != null && (cap == ForgeCapabilities.FLUID_HANDLER || cap == ForgeCapabilities.ITEM_HANDLER)) {
			return master.getCapability(cap, side);
		}
		return super.getCapability(cap, side);
	}
}
