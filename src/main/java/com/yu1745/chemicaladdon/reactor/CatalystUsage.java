package com.yu1745.chemicaladdon.reactor;

/**
 * Pure catalyst-consumption accounting for the B3 catalyst tray (no MC types —
 * JUnit-testable like {@code GasDistributorMath}).
 *
 * <p>Each catalyst item pays for {@link #BATCHES_PER_ITEM} successful
 * catalyst-required recipe batches. The tray tracks only the number of batches
 * the CURRENT front item has already paid for; consumption happens exactly at
 * the 100th batch, never while a batch is still running — a failed or
 * interrupted reaction never spends catalyst.</p>
 */
public final class CatalystUsage {

	/** Successful catalyst-required batches one catalyst item survives. */
	public static final int BATCHES_PER_ITEM = 100;

	/** Immutable charge state: stack count + batches used by the front item. */
	public record State(int count, int used) {
	}

	private CatalystUsage() {
	}

	/** Clamp helper: any nonsense input collapses to "no catalyst". */
	public static State normalize(int count, int used) {
		int c = Math.max(0, count);
		int u = c <= 0 ? 0 : Math.max(0, Math.min(BATCHES_PER_ITEM - 1, used));
		return new State(c, u);
	}

	/**
	 * One successful catalyst-required batch completed: increment the usage of
	 * the front item; when it reaches {@link #BATCHES_PER_ITEM} the item is
	 * consumed and the next item (if any) starts fresh at 0.
	 */
	public static State advance(int count, int used) {
		State s = normalize(count, used);
		if (s.count() <= 0) {
			return s; // nothing to charge: no silent consumption of nothing
		}
		int next = s.used() + 1;
		if (next >= BATCHES_PER_ITEM) {
			return new State(s.count() - 1, 0);
		}
		return new State(s.count(), next);
	}

	/** Batches the current front item still covers (0 when empty). */
	public static int remaining(int count, int used) {
		State s = normalize(count, used);
		return s.count() <= 0 ? 0 : BATCHES_PER_ITEM - s.used();
	}
}
