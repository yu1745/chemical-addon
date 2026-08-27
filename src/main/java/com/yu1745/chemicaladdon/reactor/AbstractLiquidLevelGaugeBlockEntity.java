package com.yu1745.chemicaladdon.reactor;

import java.util.List;

import javax.annotation.Nullable;

import com.yu1745.chemicaladdon.fluid.Miscibility;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraft.ChatFormatting;

/**
 * The S11 liquid-level gauge (液位计, plans/03 §metrics): liquid-only fill
 * percent 0–100 of the vessel — <b>liquid</b> phases only, classified the
 * project-wide way (a FluidStack is gas iff its FluidType is
 * lighter-than-air, {@link Miscibility#isGas}). A gas headspace must never
 * raise the level: a pressure vessel brim-full of gas still reads 0 %. The
 * batch-automation workhorse: feed cut-off on high level, pump stop on
 * drain-empty, both from one dial.
 */
public abstract class AbstractLiquidLevelGaugeBlockEntity extends AbstractVesselGaugeBlockEntity {

	public static final int THRESHOLD_STEP = 1;        // 1 % per scroll unit
	public static final int THRESHOLD_MAX_STEPS = 100; // 0–100 %
	public static final int THRESHOLD_MILESTONE = 10;  // a tick every 10 % on the board
	public static final int DEFAULT_THRESHOLD_STEPS = 80; // 80 %: leave a gas headspace

	protected AbstractLiquidLevelGaugeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	protected String thresholdLabelKey() {
		return "liquid_level_gauge.chemicaladdon.threshold";
	}

	@Override
	protected String thresholdUnit() {
		return "%";
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
		return liquidPercentOf(reactor.getTank());
	}

	@Override
	protected int ambientValue() {
		return 0; // an empty / unattached vessel reads empty
	}

	@Override
	protected int analogZero() {
		return 0; // 12 o'clock = empty
	}

	@Override
	protected int needleTint() {
		return 0xFF3CA0BE; // the baked dial art's cyan needle
	}

	/** The last-read liquid fill percent 0–100; 0 when not attached. */
	public int getLiquidPercent() {
		return getValue();
	}

	/**
	 * The liquid-only fill percent: Σ amounts of the non-gas phases over the
	 * tank capacity, ×100 (integer truncation). Gas phases (lighter-than-air
	 * FluidTypes) are skipped entirely — they are headspace, not level.
	 */
	public static int liquidPercentOf(ReactorTank tank) {
		long capacity = tank.getTankCapacity(0);
		if (capacity <= 0) {
			return 0;
		}
		long liquid = 0;
		for (FluidStack stack : tank.getFluids()) {
			if (!Miscibility.isGas(stack)) {
				liquid += stack.getAmount();
			}
		}
		return (int) Math.min(100, liquid * 100 / capacity);
	}

	/** The liquid-level gauge BE at {@code pos}, or null — shared redstone helper (both forms). */
	@Nullable
	public static AbstractLiquidLevelGaugeBlockEntity at(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
		if (level instanceof net.minecraft.world.level.Level l) {
			BlockEntity be = l.getBlockEntity(pos);
			return be instanceof AbstractLiquidLevelGaugeBlockEntity gauge ? gauge : null;
		}
		return null;
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		String spacing = " ";
		tooltip.add(Component.literal(spacing).append(getBlockState().getBlock().getName()));
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.liquid_level", getLiquidPercent()))
			.withStyle(ChatFormatting.AQUA));
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.liquid_level_gauge_threshold", getThreshold()))
			.withStyle(ChatFormatting.GRAY));
		if (!isAttached()) {
			tooltip.add(Component.literal(spacing)
				.append(Component.translatable("goggles.chemicaladdon.liquid_level_gauge_no_vessel"))
				.withStyle(ChatFormatting.RED));
		} else if (isAlarm()) {
			tooltip.add(Component.literal(spacing)
				.append(Component.translatable("goggles.chemicaladdon.liquid_level_gauge_alarm"))
				.withStyle(ChatFormatting.GREEN));
		}
		return true;
	}
}
