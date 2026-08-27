package com.yu1745.chemicaladdon.vessel;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.DoubleSupplier;

import net.minecraft.resources.ResourceLocation;

/** Immutable snapshot of an assembled vessel's topology and shell parts. */
public final class StructureCapabilities {
	private static final StructureCapabilities UNASSEMBLED = new StructureCapabilities(
		EnumSet.noneOf(ProcessCapability.class), 0, 0, 0, 0, 0, Set.of(), Set.of(), () -> 0d);

	private final Set<ProcessCapability> capabilities;
	private final int capacityMb;
	private final int size;
	private final int height;
	private final int ringLayer;
	private final int interiorVolumeBlocks;
	private final Set<ResourceLocation> installedParts;
	private final Set<ResourceLocation> boundParts;
	private final DoubleSupplier agitation;

	private StructureCapabilities(Set<ProcessCapability> capabilities, int capacityMb, int size, int height,
		int ringLayer, int interiorVolumeBlocks, Set<ResourceLocation> installedParts,
		Set<ResourceLocation> boundParts, DoubleSupplier agitation) {
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
		this.boundParts = Collections.unmodifiableSet(new LinkedHashSet<>(boundParts));
		this.agitation = agitation;
	}

	public static StructureCapabilities of(Set<ProcessCapability> capabilities, int capacityMb, int size,
		int height, int ringLayer) {
		return of(capabilities, capacityMb, size, height, ringLayer, Set.of(), Set.of(), () -> 0d);
	}

	/** Source-compatible factory where all supplied parts are both installed and bound. */
	public static StructureCapabilities of(Set<ProcessCapability> capabilities, int capacityMb, int size,
		int height, int ringLayer, Set<ResourceLocation> installedParts, DoubleSupplier agitation) {
		return of(capabilities, capacityMb, size, height, ringLayer, installedParts, installedParts, agitation);
	}

	/** Build a snapshot with effective installed parts and all structurally bound parts. */
	public static StructureCapabilities of(Set<ProcessCapability> capabilities, int capacityMb, int size,
		int height, int ringLayer, Set<ResourceLocation> installedParts, Set<ResourceLocation> boundParts,
		DoubleSupplier agitation) {
		int interiorWidth = Math.max(0, size - 2);
		int volume = interiorWidth * interiorWidth * Math.max(0, height);
		return new StructureCapabilities(capabilities, capacityMb, size, height, ringLayer, volume,
			installedParts, boundParts, agitation == null ? () -> 0d : agitation);
	}

	public static StructureCapabilities unassembled() {
		return UNASSEMBLED;
	}

	public Set<ProcessCapability> capabilities() { return capabilities; }
	public boolean has(ProcessCapability capability) { return capabilities.contains(capability); }
	/** Installed and currently effective shell part ids. */
	public Set<ResourceLocation> installedParts() { return installedParts; }
	public boolean hasPart(ResourceLocation part) { return installedParts.contains(part); }
	/** Shell parts bound into the structure, including ineffective parts. */
	public Set<ResourceLocation> boundParts() { return boundParts; }
	public boolean hasBoundPart(ResourceLocation part) { return boundParts.contains(part); }

	public float agitation() {
		double value = agitation.getAsDouble();
		if (!Double.isFinite(value) || value <= 0d) return 0f;
		return (float) Math.min(1d, value);
	}
	public int capacityMb() { return capacityMb; }
	public int size() { return size; }
	public int height() { return height; }
	public int ringLayer() { return ringLayer; }
	public int interiorVolumeBlocks() { return interiorVolumeBlocks; }

	@Override
	public String toString() {
		return "StructureCapabilities[capabilities=" + capabilities + ", capacityMb=" + capacityMb
			+ ", size=" + size + ", height=" + height + ", ringLayer=" + ringLayer
			+ ", interiorVolumeBlocks=" + interiorVolumeBlocks + ", parts=" + installedParts
			+ ", boundParts=" + boundParts + "]";
	}
}
