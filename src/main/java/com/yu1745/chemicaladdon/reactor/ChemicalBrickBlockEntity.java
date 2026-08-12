package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;

/**
 * Lightweight BE for structural bricks of multiblock vessels (reactor /
 * settling basin). It carries no data of its own; it proxies FLUID_HANDLER
 * and ITEM_HANDLER capabilities to the assembled master controller, following
 * the Create FluidTank pattern (every structural block is a BE that delegates
 * to the controller; master position is stored and validated lazily).
 */
public class ChemicalBrickBlockEntity extends BlockEntity {

	@Nullable
	private BlockPos masterPos;

	public ChemicalBrickBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.CHEMICAL_BRICK.get(), pos, state);
	}

	/** Called by the master controller on assembly / disassembly. */
	public void setMaster(@Nullable BlockPos masterPos) {
		this.masterPos = masterPos;
		setChanged();
	}

	/** Position of the master controller this brick belongs to, or null. */
	@Nullable
	public BlockPos getMasterPos() {
		return masterPos;
	}

	/**
	 * True when both brick block entities belong to the same assembled master —
	 * the connectivity check used by the CTM shell model (two bricks connect
	 * their textures only when they are part of the same multiblock).
	 */
	public static boolean isConnected(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockPos other) {
		BlockEntity a = level.getBlockEntity(pos);
		BlockEntity b = level.getBlockEntity(other);
		if (!(a instanceof ChemicalBrickBlockEntity brickA) || !(b instanceof ChemicalBrickBlockEntity brickB)) {
			return false;
		}
		BlockPos masterA = brickA.masterPos;
		return masterA != null && masterA.equals(brickB.masterPos);
	}

	/** Returns the master controller BE if still valid (assembled), else null. */
	@Nullable
	public BlockEntity getValidMaster() {
		if (masterPos == null || level == null) {
			return null;
		}
		BlockEntity master = level.getBlockEntity(masterPos);
		if (master instanceof ReactorControllerBlockEntity reactor && reactor.isAssembled()) {
			return reactor;
		}
		if (master instanceof SettlingBasinBlockEntity basin && basin.isAssembled()) {
			return basin;
		}
		return null; // master missing or disassembled -> no capability
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
		BlockEntity master = getValidMaster();
		if (master != null && (cap == ForgeCapabilities.FLUID_HANDLER || cap == ForgeCapabilities.ITEM_HANDLER)) {
			return master.getCapability(cap, side);
		}
		return super.getCapability(cap, side);
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		if (masterPos != null) {
			tag.putLong("masterPos", masterPos.asLong());
		}
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		masterPos = tag.contains("masterPos") ? BlockPos.of(tag.getLong("masterPos")) : null;
	}
}
