package com.yu1745.chemicaladdon.reactor;

/**
 * Pure dose-domain arithmetic of the B4 metering inlet (no MC types: the JUnit
 * composition-layer suite can exercise it without booting Minecraft).
 *
 * <p>The dose is configured in coarse steps of {@link #DOSE_STEP_MB} so the
 * Create value-settings board stays small (160 steps, not 16000). Everything
 * else (admitted counter, remaining budget) works in raw mB.</p>
 */
public final class MeteringInletMath {

	/** mB per scroll step. */
	public static final int DOSE_STEP_MB = 100;
	/** Minimum dose: 1 step = 100 mB. */
	public static final int MIN_STEPS = 1;
	/** Maximum dose: 160 steps = 16000 mB. */
	public static final int MAX_STEPS = 160;
	/** Default dose: 10 steps = 1000 mB (one bucket). */
	public static final int DEFAULT_STEPS = 10;

	private MeteringInletMath() {
	}

	/** Clamp a raw step count onto the legal board range. */
	public static int clampSteps(int steps) {
		return Math.max(MIN_STEPS, Math.min(MAX_STEPS, steps));
	}

	/** The default configured dose in mB. */
	public static int defaultDoseMb() {
		return DEFAULT_STEPS * DOSE_STEP_MB;
	}

	/** mB still admitted before the batch reaches its configured dose. */
	public static int remainingMb(int doseMb, int admittedMb) {
		return Math.max(0, doseMb - Math.max(0, admittedMb));
	}

	/** The batch has reached its dose. */
	public static boolean isDone(int doseMb, int admittedMb) {
		return admittedMb >= doseMb;
	}
}
