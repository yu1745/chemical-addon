package com.yu1745.chemicaladdon.reactor;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.yu1745.chemicaladdon.composition.Analyte;
import com.yu1745.chemicaladdon.composition.Chemistry;
import com.yu1745.chemicaladdon.composition.Solution;
import com.yu1745.chemicaladdon.fluid.Mixture;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraft.ChatFormatting;

/**
 * The S04 Baumé gauge (波美计, plans/04 §9.2 — redefined from the old
 * "concentration meter"): a hydrometer reading <b>density as a function of
 * total dissolved solids</b> — {@code Σ dissolved units / water units} on the
 * declared °Bé scale ({@link Analyte#baume}). Species-blind by design: it
 * says how much is dissolved, never what (a second salt at the same
 * concentration reads the same — that is the instrument's honesty). The
 * mass-concentration endpoint of the concentration-type family: salt-works
 * saturation, caustic concentration, brine make-up.
 */
public abstract class AbstractBaumeGaugeBlockEntity extends AbstractVesselGaugeBlockEntity {

	public static final int THRESHOLD_STEP = 2;        // °Bé per scroll unit (1 级 = 2°Bé)
	public static final int THRESHOLD_MAX_STEPS = 15;  // 0–30 °Bé
	public static final int THRESHOLD_MILESTONE = 5;   // a tick every 10 °Bé on the board
	public static final int DEFAULT_THRESHOLD_STEPS = 12; // 24 °Bé: near-saturated brine

	protected AbstractBaumeGaugeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	protected String thresholdLabelKey() {
		return "baume_gauge.chemicaladdon.threshold";
	}

	@Override
	protected String thresholdUnit() {
		return "°Bé";
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
		return baumeOf(reactor.getTank());
	}

	@Override
	protected int ambientValue() {
		return 0; // unattached / pure water: a hydrometer floats at 0 °Bé
	}

	@Override
	protected int analogZero() {
		return 0; // 12 o'clock = 0 °Bé
	}

	@Override
	protected int needleTint() {
		return 0xFFC47C2C; // the baked dial art's amber needle
	}

	/** The last-read vessel density (°Bé); 0 when not attached. */
	public int getBaume() {
		return getValue();
	}

	/**
	 * The aqueous phase's °Bé from the tank's unit domains: dissolved = all ion
	 * units + non-water molecular solute units (sugar-like solutes raise
	 * density too), water = solvent units ({@link Analyte#baume}).
	 */
	public static int baumeOf(ReactorTank tank) {
		long dissolved = 0;
		long water = 0;
		for (FluidStack stack : tank.getFluids()) {
			if (Mixture.isMixture(stack)) {
				for (Map.Entry<ResourceLocation, Integer> e : Mixture.deriveUnitAmounts(stack).entrySet()) {
					if (!e.getKey().equals(Solution.WATER)) {
						dissolved += e.getValue();
					}
				}
				for (int v : Mixture.deriveUnitIonAmounts(stack).values()) {
					dissolved += v;
				}
				water += (long) Mixture.deriveUnitAmounts(stack).getOrDefault(Solution.WATER, 0);
			} else if (stack.getFluid() == Fluids.WATER) {
				water += (long) stack.getAmount() * Chemistry.UNIT_PER_MB;
			}
		}
		return Analyte.baume(dissolved, water);
	}

	/** The Baumé gauge BE at {@code pos}, or null — shared redstone helper (both forms). */
	@Nullable
	public static AbstractBaumeGaugeBlockEntity at(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
		if (level instanceof net.minecraft.world.level.Level l) {
			BlockEntity be = l.getBlockEntity(pos);
			return be instanceof AbstractBaumeGaugeBlockEntity gauge ? gauge : null;
		}
		return null;
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		String spacing = " ";
		tooltip.add(Component.literal(spacing).append(getBlockState().getBlock().getName()));
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.baume", getBaume()))
			.withStyle(ChatFormatting.AQUA));
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.baume_gauge_threshold", getThreshold()))
			.withStyle(ChatFormatting.GRAY));
		if (!isAttached()) {
			tooltip.add(Component.literal(spacing)
				.append(Component.translatable("goggles.chemicaladdon.baume_gauge_no_vessel"))
				.withStyle(ChatFormatting.RED));
		} else if (isAlarm()) {
			tooltip.add(Component.literal(spacing)
				.append(Component.translatable("goggles.chemicaladdon.baume_gauge_endpoint"))
				.withStyle(ChatFormatting.GREEN));
		}
		return true;
	}
}
