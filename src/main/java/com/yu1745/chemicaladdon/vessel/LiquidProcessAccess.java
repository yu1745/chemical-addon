package com.yu1745.chemicaladdon.vessel;

import java.util.List;

import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

/**
 * Narrow liquid-phase process view for consumers such as ports and gauges.
 *
 * <p>The contract intentionally does not expose {@code ReactorTank}: legacy
 * controllers may retain their concrete accessor, while new consumers depend
 * only on operations a future tower or basin can implement independently.</p>
 */
public interface LiquidProcessAccess {

	/** Read-only view of the current phase entries. */
	List<FluidStack> getFluidPhases();

	/** Total liquid-process capacity in mB. */
	int getLiquidCapacity();

	/** Render/world-space liquid surface height. */
	float getLiquidSurfaceY(float partialTicks);

	/** Drain a matching phase using the same semantics as the backing inventory. */
	FluidStack drain(FluidStack resource, FluidAction action);

	/** Drain clear liquid from the currently exposed phase. */
	FluidStack decantClear(int maxDrain, FluidAction action);
}
