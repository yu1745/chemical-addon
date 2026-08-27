package com.yu1745.chemicaladdon.vessel;

/**
 * Read-only, player-facing measurements published by a process controller.
 * Values are already reduced to the instrument scales; this interface does not
 * expose controller classes or chemistry implementation details.
 */
public interface ProcessReadings {

	int getTemperature();

	int getPressure();

	int getPh();

	int getTurbidity();

	int getBaume();

	int getConductivity();

	/** Stable diagnostic identifier, e.g. {@code REACTING} or {@code NO_RECIPE}. */
	String getProcessStatus();

	float getProcessProgress();
}
