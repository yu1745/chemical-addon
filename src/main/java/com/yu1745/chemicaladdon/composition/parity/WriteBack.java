package com.yu1745.chemicaladdon.composition.parity;

import java.util.List;

import com.yu1745.chemicaladdon.composition.parity.TickDriver.Step;
import com.yu1745.chemicaladdon.fluid.Mixture;

import net.minecraftforge.fluids.FluidStack;

/**
 * Transaction commit boundary.  It writes only the next canonical engine
 * state.  Molecules, ions, and solid fields are display projections owned by
 * the adapter and are never used here as chemical input or charge balancing.
 */
public final class WriteBack {
	private WriteBack() {}

	public static boolean stateOf(FluidStack stack, Step step) {
		if (!step.valid || step.state == null || !Mixture.isMixture(stack)) return false;
		Mixture.setEngineSolution(stack, step.state);
		return true;
	}

	/** Commit to the mixed vessel entry. Tank adaptation must subsequently render its derived view. */
	public static boolean firstOf(List<FluidStack> fluids, Step step) {
		if (!step.valid || step.state == null) return false;
		for (FluidStack stack : fluids) if (Mixture.isMixture(stack)) return stateOf(stack, step);
		return false;
	}
}
