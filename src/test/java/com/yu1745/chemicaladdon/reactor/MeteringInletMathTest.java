package com.yu1745.chemicaladdon.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** B4 metering inlet dose-domain arithmetic (pure math, no MC types). */
class MeteringInletMathTest {

	@Test
	void stepsClampOntoTheLegalBoardRange() {
		assertEquals(MeteringInletMath.MIN_STEPS, MeteringInletMath.clampSteps(0));
		assertEquals(MeteringInletMath.MIN_STEPS, MeteringInletMath.clampSteps(-5));
		assertEquals(50, MeteringInletMath.clampSteps(50));
		assertEquals(MeteringInletMath.MAX_STEPS, MeteringInletMath.clampSteps(1000));
	}

	@Test
	void defaultDoseIsOneThousandMillibuckets() {
		assertEquals(1000, MeteringInletMath.defaultDoseMb());
		assertEquals(100, MeteringInletMath.DOSE_STEP_MB);
		// 100–16000 mB in 100 mB steps
		assertEquals(100, MeteringInletMath.MIN_STEPS * MeteringInletMath.DOSE_STEP_MB);
		assertEquals(16000, MeteringInletMath.MAX_STEPS * MeteringInletMath.DOSE_STEP_MB);
	}

	@Test
	void remainingNeverGoesNegativeOrBelowZeroAdmitted() {
		assertEquals(1000, MeteringInletMath.remainingMb(1000, 0));
		assertEquals(300, MeteringInletMath.remainingMb(1000, 700));
		assertEquals(0, MeteringInletMath.remainingMb(1000, 1000));
		// a dose scrolled BELOW the already-admitted amount saturates, never inverts
		assertEquals(0, MeteringInletMath.remainingMb(500, 900));
		// nonsense admitted values collapse to zero budget
		assertEquals(1000, MeteringInletMath.remainingMb(1000, -50));
	}

	@Test
	void doneMeansAdmittedReachedTheDose() {
		assertFalse(MeteringInletMath.isDone(1000, 999));
		assertTrue(MeteringInletMath.isDone(1000, 1000));
		assertTrue(MeteringInletMath.isDone(1000, 1200));
	}
}
