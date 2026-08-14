package com.yu1745.chemicaladdon.reactor;

import java.util.List;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Shared guts of the S02 thermometer (both the wall form and the thin-panel form):
 * a world-in alarm threshold (scrollable, no GUI), the temperature reading, the
 * alarm state, the goggles HUD and the redstone signal helpers.
 *
 * <p>The alarm threshold is stored in <b>coarse units</b> ({@link #THRESHOLD_STEP} °C
 * each) so the Create value-settings board stays small enough to fit on screen —
 * a 0–1000°C range with 1° steps would build a ~1400px-wide board that overflows
 * the viewport. The board and the in-world value box both re-scale the raw unit
 * back to °C for display.
 */
public abstract class AbstractThermometerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

	public static final int AMBIENT_TEMP = 20;
	public static final int THRESHOLD_STEP = 25;          // °C per scroll unit
	public static final int THRESHOLD_MAX_STEPS = 40;     // 1000°C / 25
	public static final int THRESHOLD_MILESTONE = 4;      // a tick every 100°C on the board
	public static final int DEFAULT_THRESHOLD_STEPS = 16; // 400°C (Create HEATED tier)

	protected ScrollValueBehaviour threshold;
	private int temperature = AMBIENT_TEMP;
	private boolean alarm = false;

	protected AbstractThermometerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		CenteredSideValueBoxTransform slot = new CenteredSideValueBoxTransform(this::isValueBoxSide);
		threshold = new ScrollValueBehaviour(Component.translatable("thermometer.chemicaladdon.threshold"), this, slot) {
			@Override
			public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
				// show °C (rescaled) on the board instead of the raw 0..40 unit index
				return new ValueSettingsBoard(label, THRESHOLD_MAX_STEPS, THRESHOLD_MILESTONE,
					ImmutableList.of(Component.literal("°C")),
					new ValueSettingsFormatter(v -> Component.literal((v.value() * THRESHOLD_STEP) + "°C")));
			}
		};
		threshold.between(0, THRESHOLD_MAX_STEPS);
		threshold.value = DEFAULT_THRESHOLD_STEPS;
		threshold.withFormatter(i -> (i * THRESHOLD_STEP) + "°C");
		behaviours.add(threshold);
	}

	/** On which face(s) the scroll value box appears (form-specific). */
	protected abstract boolean isValueBoxSide(BlockState state, Direction side);

	/** The reactor this thermometer reads, or null when not attached to one. */
	@Nullable
	protected abstract ReactorControllerBlockEntity findReactor();

	/** The alarm threshold in °C, as set by world-in scrolling. */
	public int getThreshold() {
		return threshold != null ? threshold.getValue() * THRESHOLD_STEP : DEFAULT_THRESHOLD_STEPS * THRESHOLD_STEP;
	}

	/** The last-read vessel temperature (°C); ambient when not attached. */
	public int getTemperature() {
		return temperature;
	}

	/** true when the vessel is hot enough to trip the alarm (temperature ≥ threshold). */
	public boolean isAlarm() {
		return alarm;
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null) {
			return;
		}
		ReactorControllerBlockEntity reactor = findReactor();
		temperature = reactor != null ? reactor.getTemperature() : AMBIENT_TEMP;
		boolean newAlarm = reactor != null && temperature >= getThreshold();
		if (newAlarm != alarm) {
			alarm = newAlarm;
			if (!level.isClientSide) {
				level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
			}
		}
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		String spacing = " ";
		tooltip.add(Component.literal(spacing).append(getBlockState().getBlock().getName()));

		ChatFormatting heatColor = temperature >= 800 ? ChatFormatting.RED
			: temperature >= 400 ? ChatFormatting.GOLD : ChatFormatting.GRAY;
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.temperature", temperature))
			.withStyle(ChatFormatting.WHITE));
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.thermometer_threshold", getThreshold()))
			.withStyle(ChatFormatting.GRAY));

		if (findReactor() == null) {
			tooltip.add(Component.literal(spacing)
				.append(Component.translatable("goggles.chemicaladdon.thermometer_no_vessel"))
				.withStyle(ChatFormatting.RED));
		} else if (alarm) {
			tooltip.add(Component.literal(spacing)
				.append(Component.translatable("goggles.chemicaladdon.thermometer_alarm"))
				.withStyle(ChatFormatting.RED));
		}
		return true;
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
