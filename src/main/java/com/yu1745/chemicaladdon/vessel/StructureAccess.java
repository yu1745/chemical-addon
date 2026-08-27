package com.yu1745.chemicaladdon.vessel;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The read-only structure view exposed by a multiblock controller.
 *
 * <p>This deliberately contains geometry and lifecycle only.  Process
 * inventories and measurements belong to {@link LiquidProcessAccess} and
 * {@link ProcessReadings}; consumers such as gauges therefore do not need to
 * know which controller implementation they are attached to.</p>
 */
public interface StructureAccess {

	/** Whether the controller currently owns a valid assembled structure. */
	boolean isAssembled();

	/** Whether the assembled structure has an open top. */
	boolean isOpen();

	/** Shell footprint in blocks, or zero when no geometry is active. */
	int getSize();

	/** Interior ring-layer count, or zero when no geometry is active. */
	int getHeight();

	/** Controller's ring index measured from the floor. */
	int getRingLayer();

	/** Direction from the controller toward the structure interior. */
	@Nullable
	Direction getInward();

	/** Absolute controller position, useful to clients that only retain the view. */
	BlockPos getStructurePos();

	/** Immutable capability/geometry snapshot derived from the current structure. */
	StructureCapabilities getStructureCapabilities();
}
