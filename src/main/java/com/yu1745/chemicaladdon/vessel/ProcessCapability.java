package com.yu1745.chemicaladdon.vessel;

import java.util.Locale;

import com.google.gson.JsonSyntaxException;

/**
 * Stable, finite vocabulary of structural process capabilities.
 *
 * <p>These names describe topology or equipment that a process may require;
 * numeric limits live in {@link StructureCapabilities}.  Keeping this as an
 * enum prevents recipe-facing capability checks from becoming an unbounded
 * string convention.</p>
 */
public enum ProcessCapability {
	MIXED_VOLUME,
	OPEN_TOP,
	SEALED,
	PRESSURIZED,
	AGITATED,
	GAS_DISPERSED,
	CATALYST_BED,
	SETTLING_AREA,
	CLEAR_OVERFLOW,
	SLUDGE_UNDERFLOW,
	SOLID_BED,
	REFRACTORY_CHAMBER,
	MOLTEN_BATH,
	ELECTROLYSIS;

	/**
	 * Stable recipe spelling. Keep this explicit rather than deriving it from
	 * {@link #name()}: enum constants are Java implementation details and may
	 * be renamed without changing the data format.
	 */
	public String jsonName() {
		return switch (this) {
		case MIXED_VOLUME -> "mixed_volume";
		case OPEN_TOP -> "open_top";
		case SEALED -> "sealed";
		case PRESSURIZED -> "pressurized";
		case AGITATED -> "agitated";
		case GAS_DISPERSED -> "gas_dispersed";
		case CATALYST_BED -> "catalyst_bed";
		case SETTLING_AREA -> "settling_area";
		case CLEAR_OVERFLOW -> "clear_overflow";
		case SLUDGE_UNDERFLOW -> "sludge_underflow";
		case SOLID_BED -> "solid_bed";
		case REFRACTORY_CHAMBER -> "refractory_chamber";
		case MOLTEN_BATH -> "molten_bath";
		case ELECTROLYSIS -> "electrolysis";
		};
	}

	/** Decode a bare snake_case name or the namespaced form used in design docs. */
	public static ProcessCapability fromJsonName(String value) {
		if (value == null) {
			throw new JsonSyntaxException("Capability name must not be null");
		}
		String name = value.trim().toLowerCase(Locale.ROOT);
		if (name.startsWith("chemicaladdon:")) {
			name = name.substring("chemicaladdon:".length());
		}
		for (ProcessCapability capability : values()) {
			if (capability.jsonName().equals(name)) {
				return capability;
			}
		}
		throw new JsonSyntaxException("Unknown chemical process capability '" + value + "'");
	}
}
