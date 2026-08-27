package com.yu1745.chemicaladdon.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pure geometry checks for the B1 stirring head's shaft/impeller placement
 * (see {@link StirShaftMath}). Every case mirrors a GameTest scenario: empty,
 * shallow, half, full, tall and high-controller vessels plus the wall-distance
 * and height caps on the impeller diameter.
 */
class StirShaftMathTest {

	private static final float EPS = 1.0e-4f;

	/** Interior 3×3 (W=5), centred head: the flagship 5×5×5 reactor case. */
	private static final float D5 = StirShaftMath.impellerDiameter(3, 1.5f, 3);
	private static final float HALF5 = StirShaftMath.impellerHalfHeight(D5);

	@Test
	void emptyVesselRetractsNearRoof() {
		for (float h : new float[] { 1, 3, 5, 7 }) {
			float d = StirShaftMath.impellerDiameter(Math.min(h * 2 + 2, 5), 1.5f, h);
			float depth = StirShaftMath.shaftDepth(h, 0f, StirShaftMath.impellerHalfHeight(d));
			assertEquals(StirShaftMath.impellerHalfHeight(d) + StirShaftMath.ROOF_CLEARANCE, depth, EPS,
				"empty vessel of height " + h + " must park the impeller just under the roof");
			assertTrue(depth < 0.75f, "retracted impeller must sit near the roof, not mid-vessel");
		}
	}

	@Test
	void traceLiquidStillCountsAsEmpty() {
		// below the epsilon the head retracts (no visual stirring of a wet floor)
		float depth = StirShaftMath.shaftDepth(3, StirShaftMath.EMPTY_EPSILON / 2, HALF5);
		assertEquals(HALF5 + StirShaftMath.ROOF_CLEARANCE, depth, EPS);
	}

	@Test
	void shallowLiquidClampsAboveTheFloor() {
		// tall vessel, half a block of liquor: the 30%-up target overshoots the
		// floor clamp, which must win — blades never scrape the floor plate
		float h = 5;
		float d = StirShaftMath.impellerDiameter(1, 0.5f, h); // interior 1 wide
		float half = StirShaftMath.impellerHalfHeight(d);
		float depth = StirShaftMath.shaftDepth(h, 0.5f, half);
		assertEquals(h - half - StirShaftMath.FLOOR_CLEARANCE, depth, EPS, "floor clamp must bind");
		assertTrue(h - depth - half >= StirShaftMath.FLOOR_CLEARANCE - EPS,
			"blade bottom must clear the floor");
	}

	@Test
	void halfFullVesselOperatesLowInTheLiquid() {
		// interior height 3, liquid 1.5: centre rides 0.45 above the floor
		float depth = StirShaftMath.shaftDepth(3, 1.5f, HALF5);
		assertEquals(3 - 1.5f * StirShaftMath.LIQUID_FRACTION, depth, EPS);
		// ... and it is deeper than the retracted parking position
		assertTrue(depth > HALF5 + StirShaftMath.ROOF_CLEARANCE);
	}

	@Test
	void fullerVesselRaisesTheImpeller() {
		// the centre tracks 30% up the column, so filling rises it monotonically
		float half = StirShaftMath.shaftDepth(3, 1.5f, HALF5);
		float full = StirShaftMath.shaftDepth(3, 3, HALF5);
		assertEquals(3 - 3 * StirShaftMath.LIQUID_FRACTION, full, EPS);
		assertTrue(full < half, "a fuller vessel must raise the impeller centre");
		// a full vessel still submerges the blades entirely
		assertTrue(3 - full - HALF5 > 0, "blades stay inside the liquid body");
	}

	@Test
	void minimalVesselNeverCrossesEitherPlate() {
		// interior 1×1×1 (3×3×3 shell): everything must fit in one block
		float d = StirShaftMath.impellerDiameter(1, 0.5f, 1);
		float half = StirShaftMath.impellerHalfHeight(d);
		for (float level : new float[] { 0f, 0.25f, 0.5f, 1f }) {
			float depth = StirShaftMath.shaftDepth(1, level, half);
			assertTrue(depth - half >= StirShaftMath.ROOF_CLEARANCE - EPS, "roof clamp at level " + level);
			assertTrue(depth + half <= 1 + EPS, "floor clamp at level " + level);
		}
	}

	@Test
	void coordinatedImpellerPassKeepsEveryLiquidDepthInsideTheVessel() {
		// This is the non-pixel contract used by the controller-owned render pass:
		// moving the impeller into the solid pass must not change the existing
		// liquid-tracking envelope or let geometry cross either shell plate.
		for (int height : new int[] { 1, 3, 5, 7 }) {
			float diameter = StirShaftMath.impellerDiameter(3, 1.5f, height);
			float half = StirShaftMath.impellerHalfHeight(diameter);
			for (int i = 0; i <= 10; i++) {
				float liquid = height * i / 10f;
				float depth = StirShaftMath.shaftDepth(height, liquid, half);
				assertTrue(depth - half >= StirShaftMath.ROOF_CLEARANCE - EPS,
					"impeller top crossed the roof at h=" + height + ", liquid=" + liquid);
				assertTrue(depth + half <= height - StirShaftMath.FLOOR_CLEARANCE + EPS,
					"impeller bottom crossed the floor at h=" + height + ", liquid=" + liquid);
			}
		}
	}

	@Test
	void diameterDerivesFromInteriorWidthAndCaps() {
		// centred heads: continuous 0.65 × width, absolute cap at 3.25
		assertEquals(0.65f, StirShaftMath.impellerDiameter(1, 0.5f, 7), EPS);
		assertEquals(1.95f, StirShaftMath.impellerDiameter(3, 1.5f, 7), EPS);
		assertEquals(3.25f, StirShaftMath.impellerDiameter(5, 2.5f, 7), EPS, "W=7 tops out at the cap");
		// off-centre head in a W=5 interior: clearance 1 block to the near wall
		float cornerish = StirShaftMath.impellerDiameter(3, 1.0f, 7);
		assertEquals(2f * 1.0f - 2f * StirShaftMath.WALL_CLEARANCE, cornerish, EPS,
			"the wall clearance must cap the diameter");
		// very short interior: the blades must fit between the two clamps — a wide
		// W=7 interior in a 1-ring vessel is where the height cap (not the width) binds
		float shortVessel = StirShaftMath.impellerDiameter(5, 2.5f, 1);
		assertEquals((1 - 2f / 16f) * 16f / StirShaftMath.BLADE_HEIGHT_PX, shortVessel, EPS,
			"interior height must cap the diameter");
		// degenerate inputs floor at MIN_DIAMETER instead of collapsing to zero
		assertEquals(StirShaftMath.MIN_DIAMETER, StirShaftMath.impellerDiameter(0, 0, 1), EPS);
	}

	@Test
	void nonFiniteAndNegativeInputsAreSafe() {
		float depth = StirShaftMath.shaftDepth(Float.NaN, -3, 0.5f);
		assertTrue(depth >= 0f && Float.isFinite(depth), "degenerate input must stay finite and non-negative");
		assertEquals(0f, StirShaftMath.shaftDepth(0f, 1f, 0.5f), EPS,
			"a zero-height interior renders no shaft at all");
		assertEquals(StirShaftMath.MIN_DIAMETER, StirShaftMath.impellerDiameter(Float.NaN, -1, -1), EPS);
	}

	@Test
	void shaftSegmentsAnchorBelowTheHeadCell() {
		// the renderer draws full segments at anchor -k plus a stretched partial;
		// the anchors must tile exactly [0, -depth] with no gap and never reach
		// into the head's own block cell (the bug class: model authored in-cell,
		// segment 0 hidden inside the base, partial growing the wrong way)
		float depth = StirShaftMath.shaftDepth(3, 1.5f, HALF5); // 2.55 → full=2, partial=0.55
		int full = (int) depth;
		float rem = depth - full;
		assertTrue(full >= 1 && rem > 0.1f && rem < 0.9f, "case must exercise both full and partial segments");
		// segment 0 tops out exactly at the head's bottom face — never inside the head cell
		assertEquals(0f, StirShaftMath.segmentTop(0), EPS);
		// full segments tile downward with no gaps or overlaps
		for (int k = 0; k < full; k++) {
			assertEquals(StirShaftMath.segmentTop(k + 1), StirShaftMath.segmentBottom(k, 1f), EPS,
				"full segments must chain");
		}
		// the partial remainder stretches DOWN from the last full segment's bottom
		// to exactly -depth — anchored at its top, never shrinking upward with a gap
		assertEquals(-full, StirShaftMath.segmentTop(full), EPS, "partial tops where the last full segment ends");
		assertEquals(-depth, StirShaftMath.segmentBottom(full, rem), EPS, "partial bottoms out at -depth");
		// retracted stub (depth < 1): a single partial hanging straight off the head
		float stub = StirShaftMath.shaftDepth(3, 0f, HALF5);
		assertEquals(0f, StirShaftMath.segmentTop(0), EPS);
		assertEquals(-stub, StirShaftMath.segmentBottom(0, stub), EPS);
	}

	@Test
	void impellerCentreSitsAtTheShaftEnd() {
		for (float depth : new float[] { 0.2f, 1f, 2.55f, 4.815f }) {
			assertEquals(-depth, StirShaftMath.impellerCentreY(depth), EPS,
				"the impeller centre must park exactly at the shaft's lower end (not half a block above it)");
		}
		// blades stay inside the interior: centre ± half never crosses roof or floor
		float depth = StirShaftMath.shaftDepth(3, 3, HALF5);
		float centre = StirShaftMath.impellerCentreY(depth);
		assertTrue(centre - HALF5 >= -(3 + EPS), "blade bottom must not cross the floor");
		assertTrue(centre + HALF5 <= -EPS, "blade top must not cross the head's bottom face");
	}
}
