package com.yu1745.chemicaladdon.reactor;

/** Pure B2 gas-distributor maths shared by runtime code and JUnit. */
public final class GasDistributorMath {

	public static final double MINIMUM_IMMERSION = 0.25d;
	public static final int WINDOW_TICKS = 10;
	public static final int WINDOW_LIMIT_MB = 250;

	private GasDistributorMath() {
	}

	/** True when the outlet has at least the required vertical liquid cover. */
	public static boolean isSubmerged(double liquidSurfaceY, double outletY) {
		return Double.isFinite(liquidSurfaceY) && Double.isFinite(outletY)
			&& liquidSurfaceY - outletY >= MINIMUM_IMMERSION;
	}

	/**
	 * Pure allowance calculation for one rate-limit window. A missing, future or
	 * expired window is treated as unused; callers decide whether to commit the
	 * returned amount to persistent state.
	 */
	public static int available(long now, long windowStart, int acceptedInWindow, int requestMb) {
		if (requestMb <= 0) {
			return 0;
		}
		long elapsed = now - windowStart;
		boolean fresh = windowStart == Long.MIN_VALUE || now < windowStart || elapsed >= WINDOW_TICKS;
		int used = fresh ? 0 : Math.max(0, Math.min(WINDOW_LIMIT_MB, acceptedInWindow));
		return Math.min(requestMb, Math.max(0, WINDOW_LIMIT_MB - used));
	}
}
