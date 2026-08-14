package com.yu1745.chemicaladdon.reactor;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.ChatFormatting;

/**
 * The S03 pressure gauge: a {@link AbstractVesselGaugeBlockEntity} reading the
 * vessel's gauge pressure (kPa, see {@link ReactorControllerBlockEntity#getPressure()}).
 * Threshold steps are coarse (25 kPa) so the value-settings board fits on
 * screen; the full scale (1500 kPa) doubles as the comparator mapping and
 * leaves headroom for the U11 high-pressure line (compressor, relief valve,
 * material ratings) without rescaling existing installations.
 */
public abstract class AbstractPressureGaugeBlockEntity extends AbstractVesselGaugeBlockEntity {

	public static final int THRESHOLD_STEP = 25;           // kPa per scroll unit
	public static final int THRESHOLD_MAX_STEPS = 60;      // 1500 kPa full scale
	public static final int THRESHOLD_MILESTONE = 4;       // a tick every 100 kPa on the board
	public static final int DEFAULT_THRESHOLD_STEPS = 10;  // 250 kPa — comfortable chemical-brick service
	public static final int FULL_SCALE_KPA = THRESHOLD_MAX_STEPS * THRESHOLD_STEP;

	protected AbstractPressureGaugeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	protected String thresholdLabelKey() {
		return "pressure_gauge.chemicaladdon.threshold";
	}

	@Override
	protected String thresholdUnit() {
		return " kPa";
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
		return reactor.getPressure();
	}

	@Override
	protected int ambientValue() {
		return 0; // unattached / vented: gauge pressure is zero
	}

	@Override
	protected int analogFullScale() {
		return FULL_SCALE_KPA;
	}

	/** The last-read vessel gauge pressure (kPa); 0 when not attached. */
	public int getPressure() {
		return getValue();
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		String spacing = " ";
		int pressure = getPressure();
		tooltip.add(Component.literal(spacing).append(getBlockState().getBlock().getName()));

		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.pressure", pressure))
			.withStyle(ChatFormatting.AQUA));
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.pressure_gauge_threshold", getThreshold()))
			.withStyle(ChatFormatting.GRAY));

		if (!isAttached()) {
			tooltip.add(Component.literal(spacing)
				.append(Component.translatable("goggles.chemicaladdon.pressure_gauge_no_vessel"))
				.withStyle(ChatFormatting.RED));
		} else if (isAlarm()) {
			tooltip.add(Component.literal(spacing)
				.append(Component.translatable("goggles.chemicaladdon.pressure_gauge_alarm"))
				.withStyle(ChatFormatting.RED));
		}
		return true;
	}

	/** The pressure gauge BE at {@code pos}, or null — shared redstone helper (both forms). */
	@Nullable
	public static AbstractPressureGaugeBlockEntity at(BlockGetter level, BlockPos pos) {
		if (level instanceof Level l) {
			BlockEntity be = l.getBlockEntity(pos);
			return be instanceof AbstractPressureGaugeBlockEntity gauge ? gauge : null;
		}
		return null;
	}
}
