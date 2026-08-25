package com.yu1745.chemicaladdon.reactor;

import java.util.List;

import javax.annotation.Nullable;

import com.yu1745.chemicaladdon.composition.Chemistry;
import com.yu1745.chemicaladdon.composition.Solution;
import com.yu1745.chemicaladdon.fluid.Mixture;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraft.ChatFormatting;

/**
 * The S18 conductivity gauge (电导率计, plans/04 §9.2): reads the vessel's ionic
 * strength — Σ ion units per water unit on the declared scale (1 mS per 0.1
 * concentration) — the washing endpoint instrument (U16.5). Molecular solutes
 * carry no charge and do not conduct: ammonia water reads near zero while its
 * ammonium salt reads high, which is exactly the distinction a hydrometer
 * cannot make.
 *
 * <p>The alarm direction is <b>inverted</b> versus S02/S03: the threshold is a
 * cleanliness setpoint, and the signal means "conductivity has fallen to or
 * below it" — wash until the filtrate's conductivity stops dropping, then set
 * the gauge just above that reading: the redstone signal is the
 * washing-complete event for the feeding circuitry.
 */
public abstract class AbstractConductivityGaugeBlockEntity extends AbstractVesselGaugeBlockEntity {

	public static final int THRESHOLD_STEP = 1;          // mS per scroll unit
	public static final int THRESHOLD_MAX_STEPS = 100;   // 100 mS board headroom
	public static final int THRESHOLD_MILESTONE = 10;    // a tick every 10 mS on the board
	public static final int DEFAULT_THRESHOLD_STEPS = 5; // 5 mS — a plainly washed cake

	protected AbstractConductivityGaugeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	protected String thresholdLabelKey() {
		return "conductivity_gauge.chemicaladdon.threshold";
	}

	@Override
	protected String thresholdUnit() {
		return " mS";
	}

	@Override
	protected int thresholdStep() {
		return THRESHOLD_STEP;
	}

	@Override
	protected int thresholdMaxSteps() {
		return THRESHOLD_MAX_STEPS;
	}

	@Override
	protected int thresholdMilestoneSteps() {
		return THRESHOLD_MILESTONE;
	}

	@Override
	protected int defaultThresholdSteps() {
		return DEFAULT_THRESHOLD_STEPS;
	}

	@Override
	protected int readValue(ReactorControllerBlockEntity reactor) {
		return conductivityOf(reactor.getTank());
	}

	@Override
	protected int ambientValue() {
		return 0; // unattached / pure water: nothing dissolved, nothing conducting
	}

	@Override
	protected int analogZero() {
		return 0; // 12 o'clock = 0 mS (pure water)
	}

	@Override
	protected boolean alarmWhenBelow() {
		return true; // the threshold is a cleanliness setpoint, not a limit
	}

	@Override
	protected int needleTint() {
		return 0xFF3E9E6E; // the baked dial art's green needle
	}

	/** The last-read vessel conductivity (mS); 0 when not attached. */
	public int getConductivity() {
		return getValue();
	}

	/**
	 * The vessel's ionic strength in declared mS: 10 × (ion units / water units)
	 * across the aqueous phase (mixture stacks + any plain water). Pure water
	 * reads 0; saturated brine (~0.36) reads ~4; dense mother liquors read
	 * higher. Molecular solutes contribute nothing (non-conducting).
	 */
	public static int conductivityOf(ReactorTank tank) {
		long ions = 0;
		long water = 0;
		for (FluidStack stack : tank.getFluids()) {
			if (Mixture.isMixture(stack)) {
				for (int v : Mixture.deriveUnitIonAmounts(stack).values()) {
					ions += v;
				}
				water += (long) Mixture.deriveUnitAmounts(stack).getOrDefault(Solution.WATER, 0);
			} else if (stack.getFluid() == Fluids.WATER) {
				water += (long) stack.getAmount() * Chemistry.UNIT_PER_MB;
			}
		}
		if (water <= 0) {
			return 0;
		}
		return (int) Math.round(ions * 10.0 / water);
	}

	/** The conductivity gauge BE at {@code pos}, or null — shared redstone helper (both forms). */
	@Nullable
	public static AbstractConductivityGaugeBlockEntity at(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
		if (level instanceof net.minecraft.world.level.Level l) {
			BlockEntity be = l.getBlockEntity(pos);
			return be instanceof AbstractConductivityGaugeBlockEntity gauge ? gauge : null;
		}
		return null;
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		String spacing = " ";
		int conductivity = getConductivity();
		tooltip.add(Component.literal(spacing).append(getBlockState().getBlock().getName()));
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.conductivity", conductivity))
			.withStyle(ChatFormatting.AQUA));
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.conductivity_gauge_threshold", getThreshold()))
			.withStyle(ChatFormatting.GRAY));
		if (!isAttached()) {
			tooltip.add(Component.literal(spacing)
				.append(Component.translatable("goggles.chemicaladdon.conductivity_gauge_no_vessel"))
				.withStyle(ChatFormatting.RED));
		} else if (isAlarm()) {
			tooltip.add(Component.literal(spacing)
				.append(Component.translatable("goggles.chemicaladdon.conductivity_gauge_clean"))
				.withStyle(ChatFormatting.GREEN));
		}
		return true;
	}
}
