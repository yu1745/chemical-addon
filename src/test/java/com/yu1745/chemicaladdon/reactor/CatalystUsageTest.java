package com.yu1745.chemicaladdon.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** B3 catalyst tray lifetime accounting (pure math, no MC types). */
class CatalystUsageTest {

	@Test
	void normalizeCollapsesNonsenseToEmpty() {
		assertEquals(0, CatalystUsage.normalize(-3, 50).count());
		assertEquals(0, CatalystUsage.normalize(0, 99).used());
		assertEquals(0, CatalystUsage.normalize(5, -1).used());
		assertEquals(CatalystUsage.BATCHES_PER_ITEM - 1, CatalystUsage.normalize(5, 9999).used());
	}

	@Test
	void itemSurvivesExactlyNinetyNineCharges() {
		int count = 1;
		int used = 0;
		for (int i = 0; i < CatalystUsage.BATCHES_PER_ITEM - 1; i++) {
			CatalystUsage.State s = CatalystUsage.advance(count, used);
			count = s.count();
			used = s.used();
		}
		assertEquals(1, count);
		assertEquals(CatalystUsage.BATCHES_PER_ITEM - 1, used);
	}

	@Test
	void hundredthBatchConsumesTheItem() {
		CatalystUsage.State after99 = new CatalystUsage.State(1, 99);
		CatalystUsage.State consumed = CatalystUsage.advance(after99.count(), after99.used());
		assertEquals(0, consumed.count());
		assertEquals(0, consumed.used());
		assertEquals(0, CatalystUsage.remaining(consumed.count(), consumed.used()));
	}

	@Test
	void stackDrainsItemByItem() {
		int count = 3;
		int used = 0;
		int batches = 0;
		while (count > 0) {
			CatalystUsage.State s = CatalystUsage.advance(count, used);
			count = s.count();
			used = s.used();
			batches++;
		}
		assertEquals(3 * CatalystUsage.BATCHES_PER_ITEM, batches);
	}

	@Test
	void emptyTrayNeverConsumesAnything() {
		CatalystUsage.State s = CatalystUsage.advance(0, 0);
		assertEquals(0, s.count());
		assertEquals(0, s.used());
		assertTrue(CatalystUsage.remaining(0, 42) == 0);
	}

	@Test
	void remainingCountsFrontItemOnly() {
		assertEquals(CatalystUsage.BATCHES_PER_ITEM, CatalystUsage.remaining(2, 0));
		assertEquals(1, CatalystUsage.remaining(4, CatalystUsage.BATCHES_PER_ITEM - 1));
	}
}
