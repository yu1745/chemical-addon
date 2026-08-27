package com.yu1745.chemicaladdon.vessel;

/**
 * Agitation maths for stirred vessels (construction package B1).
 *
 * <ul>
 *   <li><b>Normalized agitation</b> ∈ [0,1] — {@code |RPM| / REFERENCE_RPM},
 *       clamped. This is the value the structure snapshot publishes
 *       ({@link StructureCapabilities#agitation()}) and recipes compare
 *       against ({@code conditions.agitation}).</li>
 *   <li><b>Stirring coefficient</b> ∈ [1, {@link #MAX_COEFFICIENT}] —
 *       {@code 1 + normalized}. A vessel without an effectively rotating head
 *       stays at 1.0 (the pre-B1 baseline every existing rate tune targets);
 *       a powered head can only ever speed a process up, and 2.0 is the
 *       documented hard cap reached at 256 RPM shaft input.</li>
 * </ul>
 *
 * <p>Both values are derived from Create's kinetics
 * ({@code KineticBlockEntity.getSpeed()}); an overstressed network reads as
 * zero there, so an overstressed head simply stops agitating.</p>
 */
public final class Agitation {

	/**
	 * |RPM| that maps to fully normalized agitation: Create's standard maximum
	 * shaft speed ({@code kinetics.maxRotationSpeed} default 256).
	 */
	public static final float REFERENCE_RPM = 256.0f;

	/** Hard cap of the stirring coefficient a powered head can reach. */
	public static final float MAX_COEFFICIENT = 2.0f;

	private Agitation() {
	}

	/** Speed magnitude → normalized agitation in [0,1] (sign-independent, clamped, non-finite safe). */
	public static float normalized(float speedMagnitude) {
		if (!Float.isFinite(speedMagnitude) || speedMagnitude <= 0f) {
			return 0f;
		}
		return Math.min(1f, speedMagnitude / REFERENCE_RPM);
	}

	/** Normalized agitation → stirring coefficient in [1,MAX_COEFFICIENT]. */
	public static float coefficient(float normalized) {
		return Math.min(MAX_COEFFICIENT, 1.0f + Math.max(0f, normalized));
	}
}
