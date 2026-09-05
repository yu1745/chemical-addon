package com.yu1745.chemicaladdon.control;

import net.minecraft.util.Mth;

/** Shared live-zero signal conventions used by instruments, PLCs and I/O. */
public final class ControlSignal {
	public enum Type { ANALOG, DIGITAL, EVENT, ENUM, RAW }

	public static final int INVALID = 0;
	public static final int LIVE_ZERO = 1;
	public static final int FULL_SCALE = 15;

	private ControlSignal() {}

	/** Linear live-zero mapping: invalid is reserved for a missing/broken source. */
	public static int analog(double value, double minimum, double maximum) {
		if (!Double.isFinite(value) || !Double.isFinite(minimum) || !Double.isFinite(maximum)
			|| maximum <= minimum) {
			return INVALID;
		}
		double fraction = Mth.clamp((value - minimum) / (maximum - minimum), 0.0, 1.0);
		return LIVE_ZERO + (int) Math.round(14.0 * fraction);
	}

	/** Logarithmic live-zero mapping for trace analytes. Both bounds must be positive. */
	public static int logarithmic(double value, double minimum, double maximum) {
		if (!Double.isFinite(value) || !Double.isFinite(minimum) || !Double.isFinite(maximum)
			|| minimum <= 0 || maximum <= minimum) {
			return INVALID;
		}
		if (value <= minimum) return LIVE_ZERO;
		if (value >= maximum) return FULL_SCALE;
		double fraction = Math.log(value / minimum) / Math.log(maximum / minimum);
		return LIVE_ZERO + (int) Math.round(14.0 * fraction);
	}

	public static int digital(boolean value) {
		return value ? FULL_SCALE : LIVE_ZERO;
	}

	public static int clamp(int value) {
		return Mth.clamp(value, INVALID, FULL_SCALE);
	}
}
