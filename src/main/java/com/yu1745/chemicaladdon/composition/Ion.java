package com.yu1745.chemicaladdon.composition;

import java.util.Objects;

/**
 * A chemical ion identity (e.g. Na+, Ca2+, SO4 2-). Ions are simulation-internal
 * degrees of freedom of an aqueous solution: they live only in the rules engine's
 * transient {@link Solution} snapshot and are <b>never</b> registered as species,
 * fluids or items — a dissolved ion is not transportable by Create pipes, only the
 * molecular species that releases it is.
 */
public final class Ion {

	private final String symbol; // element/group, e.g. "Na", "Ca", "SO4", "OH", "NH4", "CO3"
	private final int charge;    // +1, +2, -1, -2 ...

	public Ion(String symbol, int charge) {
		this.symbol = symbol;
		this.charge = charge;
	}

	public String symbol() {
		return symbol;
	}

	public int charge() {
		return charge;
	}

	/** Canonical id used as a map key, e.g. "Ca+2", "SO4-2", "Na+1". */
	public String id() {
		return charge >= 0 ? symbol + "+" + charge : symbol + charge;
	}

	/**
	 * Parse the charge out of a canonical ion id (e.g. "H+1" → +1, "SO4-2" → −2).
	 * The sign sits just before the trailing magnitude; no sign ⇒ charge 0.
	 */
	public static int chargeOf(String ionId) {
		int idx = -1;
		char sign = 0;
		for (int i = ionId.length() - 1; i >= 0; i--) {
			char c = ionId.charAt(i);
			if (c == '+' || c == '-') {
				idx = i;
				sign = c;
				break;
			}
		}
		if (idx < 0) {
			return 0;
		}
		String mag = ionId.substring(idx + 1);
		int m = mag.isEmpty() ? 1 : Integer.parseInt(mag);
		return sign == '+' ? m : -m;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Ion ion)) {
			return false;
		}
		return charge == ion.charge && symbol.equals(ion.symbol);
	}

	@Override
	public int hashCode() {
		return Objects.hash(symbol, charge);
	}

	@Override
	public String toString() {
		return id();
	}
}
