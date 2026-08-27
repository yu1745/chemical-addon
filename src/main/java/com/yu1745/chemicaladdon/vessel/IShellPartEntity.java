package com.yu1745.chemicaladdon.vessel;

import net.minecraft.resources.ResourceLocation;

/**
 * Block entity of a vessel shell block that installs an internal part
 * (construction package B1: the roof-penetrating stirring head; the B2 gas
 * distributor will follow the same contract).
 *
 * <p>The structure layer records part positions while it binds the shell
 * (assembly events) and re-derives them lazily after a reload — never by
 * per-tick whole-structure scans. A part contributes to
 * {@link StructureCapabilities} (installed part ids, {@code AGITATED},
 * agitation) only while {@link #isPartEffective()} reports true, so a recipe
 * gating on {@code requiredParts} expresses "a running part", not a
 * decorative block sitting in the shell.</p>
 */
public interface IShellPartEntity {

	/** Stable part identity used by recipe {@code requiredParts} and the structure snapshot. */
	ResourceLocation partId();

	/**
	 * Whether this part installs only on the vessel's roof plane (the ceiling
	 * layer). Roof-penetrating parts (B1 stirring head) are recorded by the
	 * structure bookkeeping only on ceiling cells; a wall/floor placement stays
	 * a bound shell block (proxy, breakage) but never becomes an installed part.
	 * Default false: future parts (e.g. a B2 gas distributor) accept any
	 * structural shell cell.
	 */
	default boolean requiresRoofPlane() {
		return false;
	}

	/**
	 * Whether this part currently contributes to its vessel's process. B1
	 * stirring head: bound to an assembled master, sitting on its roof plane
	 * AND effectively rotating (non-overstressed, non-zero speed).
	 */
	boolean isPartEffective();

	/**
	 * Live normalized agitation this part delivers (0..1; non-agitating parts
	 * report 0). Normalization and cap policy live in {@link Agitation}.
	 */
	float effectiveAgitation();
}
