package com.yu1745.chemicaladdon.vessel;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Immutable snapshot of a controller's assembled structure.
 *
 * <p>The snapshot intentionally contains only topology capabilities and
 * structured geometry values.  It is derived from the live structure state in
 * A2, so no additional NBT keys or migration step are needed.</p>
 */
public final class StructureCapabilities {
	private static final StructureCapabilities UNASSEMBLED = new StructureCapabilities(
		EnumSet.noneOf(ProcessCapability.class), 0, 0, 0, 0, 0);

	private final Set<ProcessCapability> capabilities;
	private final int capacityMb;
	private final int size;
	private final int height;
	private final int ringLayer;
	private final int interiorVolumeBlocks;

	private StructureCapabilities(Set<ProcessCapability> capabilities, int capacityMb, int size, int height,
		int ringLayer, int interiorVolumeBlocks) {
		EnumSet<ProcessCapability> copy = capabilities.isEmpty()
			? EnumSet.noneOf(ProcessCapability.class)
			: EnumSet.copyOf(capabilities);
		this.capabilities = Collections.unmodifiableSet(copy);
		this.capacityMb = Math.max(0, capacityMb);
		this.size = Math.max(0, size);
		this.height = Math.max(0, height);
		this.ringLayer = Math.max(0, ringLayer);
		this.interiorVolumeBlocks = Math.max(0, interiorVolumeBlocks);
	}

	/** Build a snapshot for an assembled structure. */
	public static StructureCapabilities of(Set<ProcessCapability> capabilities, int capacityMb, int size,
		int height, int ringLayer) {
		int interiorWidth = Math.max(0, size - 2);
		int volume = interiorWidth * interiorWidth * Math.max(0, height);
		return new StructureCapabilities(capabilities, capacityMb, size, height, ringLayer, volume);
	}

	/** Snapshot for a controller without a valid assembled structure. */
	public static StructureCapabilities unassembled() {
		return UNASSEMBLED;
	}

	public Set<ProcessCapability> capabilities() {
		return capabilities;
	}

	public boolean has(ProcessCapability capability) {
		return capabilities.contains(capability);
	}

	public int capacityMb() {
		return capacityMb;
	}

	/** Shell footprint (W × W). */
	public int size() {
		return size;
	}

	/** Interior ring-layer count. */
	public int height() {
		return height;
	}

	/** Controller's ring index measured upward from the floor. */
	public int ringLayer() {
		return ringLayer;
	}

	/** Number of interior blocks represented by this snapshot. */
	public int interiorVolumeBlocks() {
		return interiorVolumeBlocks;
	}

	@Override
	public String toString() {
		return "StructureCapabilities[capabilities=" + capabilities + ", capacityMb=" + capacityMb
			+ ", size=" + size + ", height=" + height + ", ringLayer=" + ringLayer
			+ ", interiorVolumeBlocks=" + interiorVolumeBlocks + "]";
	}
}
