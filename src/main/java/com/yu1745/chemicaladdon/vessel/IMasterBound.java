package com.yu1745.chemicaladdon.vessel;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A block entity that can be bound to a multiblock vessel master (U3: any
 * {@link VesselBlockEntity}). Implemented by the shell blocks (brick BE covers
 * brick/glass/decant port) and by the wall-form gauges, so the assembly code
 * can bind a shell cell to its controller regardless of which concrete block
 * fills the wall position.
 */
public interface IMasterBound {

	/** Called by the master on assembly / disassembly; null unbinds. */
	void setMaster(@Nullable BlockPos masterPos);

	/** The master position this block is bound to (or null if stray/unbound). */
	@Nullable
	BlockPos getMasterPos();

	/** The live master block entity if still valid (assembled), else null. */
	@Nullable
	BlockEntity getValidMaster();
}
