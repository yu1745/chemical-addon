package com.yu1745.chemicaladdon.reactor;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.ChatFormatting;

/**
 * The S02 thermometer: a {@link AbstractVesselGaugeBlockEntity} reading the
 * vessel temperature. Only the gauge parameters and the HUD live here — the
 * threshold behaviour, server-side reading sync, alarm and redstone helpers
 * are shared with the other vessel gauges (S03 pressure gauge, ...).
 */
public abstract class AbstractThermometerBlockEntity extends AbstractVesselGaugeBlockEntity {

	public static final int AMBIENT_TEMP = 20;
	public static final int THRESHOLD_STEP = 3;            // °C per scroll unit
	public static final int THRESHOLD_MAX_STEPS = 333;     // ~1000°C / 3
	public static final int THRESHOLD_MILESTONE = 50;      // a tick every 150°C on the board
	public static final int DEFAULT_THRESHOLD_STEPS = 133; // ~400°C (Create HEATED tier)

	protected AbstractThermometerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	protected String thresholdLabelKey() {
		return "thermometer.chemicaladdon.threshold";
	}

	@Override
	protected String thresholdUnit() {
		return "°C";
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
		return reactor.getTemperature();
	}

	@Override
	protected int ambientValue() {
		return AMBIENT_TEMP;
	}

	@Override
	protected int analogZero() {
		// 12 o'clock = 0°C (freezing point) — NOT ambient: compressors and cooling
		// crystallisation drive vessels below room temperature, and those readings
		// sweep below 12 o'clock into the negative-angle segment. The dial's rest
		// is a fixed physical zero; the threshold alone defines the full scale.
		return 0;
	}

	@Override
	protected int needleTint() {
		return 0xFFC42C2C; // the baked dial art's red needle
	}

	/** The last-read vessel temperature (°C); ambient when not attached. */
	public int getTemperature() {
		return getValue();
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		String spacing = " ";
		int temperature = getTemperature();
		tooltip.add(Component.literal(spacing).append(getBlockState().getBlock().getName()));

		ChatFormatting heatColor = temperature >= 800 ? ChatFormatting.RED
			: temperature >= 400 ? ChatFormatting.GOLD : ChatFormatting.GRAY;
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.temperature", temperature))
			.withStyle(ChatFormatting.WHITE));
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.thermometer_threshold", getThreshold()))
			.withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.literal(spacing).append(Component.translatable(heatTierKey())).withStyle(heatColor));

		if (!isAttached()) {
			tooltip.add(Component.literal(spacing)
				.append(Component.translatable("goggles.chemicaladdon.thermometer_no_vessel"))
				.withStyle(ChatFormatting.RED));
		} else if (isAlarm()) {
			tooltip.add(Component.literal(spacing)
				.append(Component.translatable("goggles.chemicaladdon.thermometer_alarm"))
				.withStyle(ChatFormatting.RED));
		}
		return true;
	}

	private String heatTierKey() {
		int temperature = getTemperature();
		if (temperature >= 800) {
			return "goggles.chemicaladdon.heat.superheated";
		}
		if (temperature >= 400) {
			return "goggles.chemicaladdon.heat.heated";
		}
		return "goggles.chemicaladdon.heat.none";
	}

	/** The thermometer BE at {@code pos}, or null — shared redstone helper (both forms). */
	@Nullable
	public static AbstractThermometerBlockEntity at(BlockGetter level, BlockPos pos) {
		if (level instanceof Level l) {
			BlockEntity be = l.getBlockEntity(pos);
			return be instanceof AbstractThermometerBlockEntity thermometer ? thermometer : null;
		}
		return null;
	}
}
