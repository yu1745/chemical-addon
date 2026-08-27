package com.yu1745.chemicaladdon.vessel;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.DoubleSupplier;

import net.minecraft.resources.ResourceLocation;

/**
 * Immutable snapshot of a controller's assembled structure.
 *
 * <p>The snapshot intentionally contains only topology capabilities, structured
 * geometry values, the installed shell parts and a live agitation reading. It
 * is derived from the live structure state in A2, so no additional NBT keys or
 * migration step are needed.</p>
 *
 * <p>B1 split of state semantics: <b>discrete</b> structural state (part ids)
 * is captured when the snapshot is built, while the <b>continuous</b> physical
 * reading (agitation, driven by the kinetic network's speed) is kept live via
 * a supplier, so even a held snapshot tracks speed changes. Part installation
 * only changes on structure events (assembly / binding) which rebuild the
 * snapshot anyway.</p>
 */
public final class StructureCapabilities {
	private static final StructureCapabilities UNASSEMBLED = new StructureCapabilities(
		EnumSet.noneOf(ProcessCapability.class), 0, 0, 0, 0, 0, Set.of(), () -> 0d);

	private final Set<ProcessCapability> capabilities;
	private final int capacityMb;
	private final int size;
	private final int height;
	private final int ringLayer;
	private final int interiorVolumeBlocks;
	private final Set<ResourceLocation> installedParts;
	private final DoubleSupplier agitation;

	private StructureCapabilities(Set<ProcessCapability> capabilities, int capacityMb, int size, int height,
		int ringLayer, int interiorVolumeBlocks, Set<ResourceLocation> installedParts, DoubleSupplier agitation) {
		EnumSet<ProcessCapability> copy = capabilities.isEmpty()
			? EnumSet.noneOf(ProcessCapability.class)
			: EnumSet.copyOf(capabilities);
		this.capabilities = Collections.unmodifiableSet(copy);
		this.capacityMb = Math.max(0, capacityMb);
		this.size = Math.max(0, size);
		this.height = Math.max(0, height);
		this.ringLayer = Math.max(0, ringLayer);
		this.interiorVolumeBlocks = Math.max(0, interiorVolumeBlocks);
		this.installedParts = Collections.unmodifiableSet(new LinkedHashSet<>(installedParts));
		this.agitation = agitation;
	}

	/**
	 * Build a snapshot for an assembled structure without shell parts (A2
	 * factory, kept for source compatibility: reads as zero agitation).
	 */
	public static StructureCapabilities of(Set<ProcessCapability> capabilities, int capacityMb, int size,
		int height, int ringLayer) {
		return of(capabilities, capacityMb, size, height, ringLayer, Set.of(), () -> 0d);
	}

	/**
	 * Build a snapshot for an assembled structure that carries installed shell
	 * parts and a live agitation reading.
	 *
	 * @param installedParts
	 *            ids of the parts that currently contribute to the process
	 *            (already filtered to effective ones by the caller)
	 * @param agitation
	 *            live supplier of the normalized effective agitation (0..1)
	 */
	public static StructureCapabilities of(Set<ProcessCapability> capabilities, int capacityMb, int size,
		int height, int ringLayer, Set<ResourceLocation> installedParts, DoubleSupplier agitation) {
		int interiorWidth = Math.max(0, size - 2);
		int volume = interiorWidth * interiorWidth * Math.max(0, height);
		return new StructureCapabilities(capabilities, capacityMb, size, height, ringLayer, volume,
			installedParts, agitation == null ? () -> 0d : agitation);
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

	/** Installed (and currently effective) shell part ids. */
	public Set<ResourceLocation> installedParts() {
		return installedParts;
	}

	/** True when the named part is installed and currently effective. */
	public boolean hasPart(ResourceLocation part) {
		return installedParts.contains(part);
	}

	/**
	 * Live normalized effective agitation contributed by the installed parts,
	 * clamped to [0,1]. Zero for an unstirred (or halted/overstressed) vessel.
	 */
	public float agitation() {
		double value = agitation.getAsDouble();
		if (!Double.isFinite(value) || value <= 0d) {
			return 0f;
		}
		return (float) Math.min(1d, value);
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
			+ ", interiorVolumeBlocks=" + interiorVolumeBlocks + ", parts=" + installedParts + "]";
	}
}
