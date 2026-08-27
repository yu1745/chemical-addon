package com.yu1745.chemicaladdon.reactor;

/**
 * Pure placement maths for the B1 stirring head's visual shaft + impeller
 * (client rendering and the GameTest geometry checks share it — no Minecraft
 * types here so JUnit can exercise it without a runtime).
 *
 * <p>Model of the geometry: the head block occupies one roof cell; the interior
 * column below its bottom face is exactly {@code interiorHeight} blocks deep
 * (roof bottom to floor top). The impeller centre target rides
 * {@link #LIQUID_FRACTION} of the liquid column above the floor — the lower
 * portion of the liquor, where actual vessels run their impellers — and two
 * hard clamps keep the blades between the roof plate and the floor plate:</p>
 * <ul>
 *   <li><b>floor clamp</b> — the blade bottom never reaches the floor
 *       ({@code depth <= h - half - FLOOR_CLEARANCE});</li>
 *   <li><b>roof clamp</b> — the blade top never crosses the head
 *       ({@code depth >= half + ROOF_CLEARANCE}). When the two conflict
 *       (tall impeller in a very short vessel) the roof clamp wins, so the
 *       retracted parking position is always valid.</li>
 * </ul>
 *
 * <p>An empty (or practically empty) vessel does not continue the "follow the
 * liquor down" curve — it <b>retracts near the roof</b> (the same world-in
 * language as the mechanical mixer's pole): the eased descent/ascent between
 * the two regimes is the renderer's job ({@code LerpedFloat} chase), not this
 * function's.</p>
 *
 * <p>The impeller diameter is derived from the interior width (a continuous
 * {@link #DIAMETER_FRACTION} of it, capped at {@link #MAX_DIAMETER}) and then
 * further capped by the head's clearance to the nearest inner wall face — an
 * off-centre head simply gets a smaller impeller that fits between its shaft
 * and the wall — and by the interior height (blades must fit between the two
 * clamps at all).</p>
 */
public final class StirShaftMath {

	/** Liquid level (blocks) below which the vessel reads as empty → retract near the roof. */
	public static final float EMPTY_EPSILON = 1f / 16f;

	/** Fraction of the liquid column the impeller centre rides above the floor. */
	public static final float LIQUID_FRACTION = 0.30f;

	/** Blade-to-floor plate clearance (blocks). */
	public static final float FLOOR_CLEARANCE = 1f / 16f;

	/** Blade-to-roof plate clearance while retracted (blocks). */
	public static final float ROOF_CLEARANCE = 1f / 16f;

	/** Blade-tip-to-inner-wall-face clearance (blocks). */
	public static final float WALL_CLEARANCE = 1f / 16f;

	/** Impeller diameter as a fraction of the interior width (continuous derivation). */
	public static final float DIAMETER_FRACTION = 0.65f;

	/** Absolute impeller diameter cap (blocks) — a W=7 vessel tops out here. */
	public static final float MAX_DIAMETER = 3.25f;

	/** Absolute impeller diameter floor (blocks) — it never vanishes entirely. */
	public static final float MIN_DIAMETER = 0.5f;

	/** Vertical extent of the authored blade pair, in model pixels (model spans 16 px = 1 block). */
	public static final float BLADE_HEIGHT_PX = 6f;

	/** Authored model span in pixels (16 px = one block at scale 1). */
	public static final float MODEL_SPAN_PX = 16f;

	private StirShaftMath() {
	}

	/**
	 * Impeller centre depth below the head block's bottom face, in blocks
	 * (0..interiorHeight; the shaft and impeller render down to exactly this
	 * depth). {@code liquidLevel} is the <b>liquid-only</b> surface height in
	 * blocks above the interior floor (gases excluded, matching
	 * {@code VesselBlockEntity#getLiquidSurfaceY}); it is clamped here.
	 */
	public static float shaftDepth(float interiorHeight, float liquidLevel, float impellerHalfHeight) {
		float h = sanitize(interiorHeight, 0f, Float.MAX_VALUE);
		float level = sanitize(liquidLevel, 0f, h);
		float half = Math.max(impellerHalfHeight, 0f);

		float floorMax = h - half - FLOOR_CLEARANCE;
		float roofMin = half + ROOF_CLEARANCE;
		// conflict (tall impeller, very short interior): the roof clamp wins so
		// the retract position stays valid — the floor clamp yields first
		float upper = Math.max(floorMax, roofMin);

		if (level <= EMPTY_EPSILON) {
			// empty: park just under the roof (never below a degenerate zero-height interior)
			return Math.min(Math.min(roofMin, upper), h);
		}
		float depth = h - level * LIQUID_FRACTION; // LIQUID_FRACTION up the column, above the floor
		return Math.min(Math.max(depth, roofMin), upper);
	}

	/**
	 * Visual impeller diameter in blocks. {@code wallClearance} is the distance
	 * in blocks from the head column's centre to the nearest inner wall face
	 * (for a centred head: half the interior width); {@code interiorHeight}
	 * caps blades that could not fit between the roof and floor clamps.
	 */
	public static float impellerDiameter(float interiorWidth, float wallClearance, float interiorHeight) {
		float w = finiteOr(interiorWidth, 0f); // Math.max/min propagate NaN — guard explicitly
		float c = finiteOr(wallClearance, 0f);
		float h = finiteOr(interiorHeight, 0f);
		float byWidth = Math.min(Math.max(w, 0f) * DIAMETER_FRACTION, MAX_DIAMETER);
		float byWall = Math.max(0f, 2f * Math.max(c, 0f) - 2f * WALL_CLEARANCE);
		float byHeight = Math.max(0f, h - ROOF_CLEARANCE - FLOOR_CLEARANCE)
			* MODEL_SPAN_PX / BLADE_HEIGHT_PX;
		return Math.max(MIN_DIAMETER, Math.min(byWidth, Math.min(byWall, byHeight)));
	}

	/** Half of the blade pair's vertical extent at the given diameter (blocks). */
	public static float impellerHalfHeight(float diameter) {
		return Math.max(finiteOr(diameter, 0f), 0f) * BLADE_HEIGHT_PX / 2f / MODEL_SPAN_PX;
	}

	private static float finiteOr(float v, float fallback) {
		return Float.isFinite(v) ? v : fallback;
	}

	// ------------------------------------------------- renderer anchor conventions
	//
	// Both partials are authored to hang BELOW their model origin (the shaft
	// segment spans local y in [-1, 0]; the impeller is centred on (0.5, 0, 0.5)),
	// so the renderer's plain translate(0, -k, 0) / scale(1, f, 1) composition
	// keeps every anchor where the depth math expects it. These helpers pin that
	// convention in pure, testable form — the JUnit anchor tests assert them
	// against shaftDepth's outputs, and the renderer must keep using them.

	/**
	 * Local Y (head bottom face = 0, down = negative) of shaft segment
	 * {@code index}'s TOP anchor: {@code -index}. Segment 0 tops out exactly at
	 * the head's bottom face — never inside the head's own block cell.
	 */
	public static float segmentTop(int index) {
		return -index;
	}

	/**
	 * Local Y of shaft segment {@code index}'s BOTTOM at stretch fraction
	 * {@code fraction} (1 = full block): {@code -(index + fraction)}. Full
	 * segments tile downward with no gaps or overlaps, and the partial remainder
	 * stretches DOWN from the last full segment's bottom to exactly {@code -depth}.
	 */
	public static float segmentBottom(int index, float fraction) {
		return -(index + Math.max(fraction, 0f));
	}

	/** Local Y the impeller's geometric centre must sit at: exactly {@code -depth} (the shaft's lower end). */
	public static float impellerCentreY(float depth) {
		return -Math.max(depth, 0f);
	}

	private static float sanitize(float v, float min, float max) {
		if (!Float.isFinite(v)) {
			return min;
		}
		return Math.max(min, Math.min(max, v));
	}
}
