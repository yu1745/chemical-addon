package com.yu1745.chemicaladdon.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GasDistributorMathTest {

	@Test
	void immersionBoundaryIsInclusive() {
		assertTrue(GasDistributorMath.isSubmerged(10.25, 10.0));
		assertFalse(GasDistributorMath.isSubmerged(10.249999, 10.0));
		assertFalse(GasDistributorMath.isSubmerged(Double.NaN, 10.0));
	}

	@Test
	void freshWindowAllowsExactlyTwoHundredFifty() {
		assertEquals(250, GasDistributorMath.available(100, Long.MIN_VALUE, 0, 500));
		assertEquals(0, GasDistributorMath.available(100, 100, 250, 1));
	}

	@Test
	void tenthTickStartsANewWindow() {
		assertEquals(50, GasDistributorMath.available(109, 100, 200, 100));
		assertEquals(100, GasDistributorMath.available(110, 100, 200, 100));
	}

	@Test
	void malformedUsageCannotIncreaseAllowance() {
		assertEquals(0, GasDistributorMath.available(100, 100, 250, 100));
		assertEquals(250, GasDistributorMath.available(100, 100, -20, 250));
		assertEquals(0, GasDistributorMath.available(100, 100, 0, 0));
	}

	@Test
	void particleRateScalesWithExecutedFlowAndIsCapped() {
		assertEquals(0d, GasDistributorMath.particleRate(0));
		assertTrue(GasDistributorMath.particleRate(1) > 0d);
		assertTrue(GasDistributorMath.particleRate(125) < GasDistributorMath.particleRate(250));
		assertEquals(3d, GasDistributorMath.particleRate(250), 1.0e-9);
		assertEquals(3d, GasDistributorMath.particleRate(1000), 1.0e-9);
	}
}
