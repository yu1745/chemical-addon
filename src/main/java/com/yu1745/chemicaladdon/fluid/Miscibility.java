package com.yu1745.chemicaladdon.fluid;

import com.yu1745.chemicaladdon.composition.Solution;
import com.yu1745.chemicaladdon.composition.Species;
import com.yu1745.chemicaladdon.composition.SpeciesManager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * D18 phase model: liquid-liquid miscibility is decided by a declared
 * solvent-family tag ({@link Species#miscibilityGroup}), not by structural
 * features. Same-group liquids merge into one {@link Mixture}; cross-group
 * liquids stay separate phases; gases ({@link #isGas}) never merge with any
 * liquid. Liquids with no declared group are {@link #UNKNOWN} (fail-closed —
 * immiscible with everything).
 */
public final class Miscibility {

	/** The water-based solvent family (water + all aqueous solutions/slurries). */
	public static final String AQUEOUS = "aqueous";
	/** Non-polar organic liquids (thermal oil). */
	public static final String NONPOLAR = "nonpolar";
	/** Fallback for a liquid with no declared group: immiscible with everything. */
	public static final String UNKNOWN = "unknown";

	private Miscibility() {}

	/** True when the registered chemical fluid explicitly represents a gas phase. */
	public static boolean isGas(FluidStack stack) {
		return !stack.isEmpty() && stack.getFluid().getFluidType() instanceof ChemFluidType chemical
			&& chemical.isGas();
	}

	/**
	 * The liquid miscibility group of a stack. A mixture is always aqueous (its
	 * solvent is water); vanilla water is aqueous (it has no species JSON); a pure
	 * liquid declares its group via its species JSON; anything undeclared is
	 * {@link #UNKNOWN}.
	 */
	public static String groupOf(FluidStack stack) {
		if (Mixture.isMixture(stack)) {
			return AQUEOUS;
		}
		ResourceLocation id = ForgeRegistries.FLUIDS.getKey(sourceOf(stack.getFluid()));
		if (id == null) {
			return UNKNOWN;
		}
		if (id.equals(Solution.WATER)) {
			return AQUEOUS; // vanilla water is the aqueous solvent
		}
		Species species = SpeciesManager.get(id);
		return species != null && species.miscibilityGroup() != null
			? species.miscibilityGroup()
			: UNKNOWN;
	}

	/** Density used for ordering within a phase; gas identity does not depend on density. */
	public static int densityOf(FluidStack stack) {
		return stack.getFluid().getFluidType().getDensity();
	}

	private static Fluid sourceOf(Fluid fluid) {
		return fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid;
	}
}
