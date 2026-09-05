package com.yu1745.chemicaladdon.control;

/** Stable fault codes shown by the PLC HUD and persisted with the controller. */
public enum PlcFault {
	NONE,
	STOPPED,
	NO_PROGRAM,
	COMPILE_ERROR,
	RUNTIME_ERROR,
	WATCHDOG,
	DUPLICATE_CHANNEL,
	MULTIPLE_CONTROLLERS
}
