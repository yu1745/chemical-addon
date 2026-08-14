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
import net.minecraft.nbt.CompoundTag;
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
 * <p>The reading is computed <b>server-side</b> (where the full brick → master →
 * reactor chain is available) and the {@code attached}/{@code temperature} fields
 * are synced to the client, so the goggles HUD never has to re-resolve the master
 * chain client-side (which broke when the panel was mounted on a shell brick).
 *
 * <p>The alarm threshold is stored in <b>coarse units</b> ({@link #THRESHOLD_STEP} °C
 * each) so the Create value-settings board stays small enough to fit on screen — a
 * 0–1000°C range with 1° steps builds a ~1400px-wide board that overflows the
 * viewport. The board and the in-world value box both re-scale the raw unit back
 * to °C for display.
 */
public abstract class AbstractThermometerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

	public static final int AMBIENT_TEMP = 20;
	public static final int THRESHOLD_STEP = 2;            // °C per scroll unit
	public static final int THRESHOLD_MAX_STEPS = 500;     // 1000°C / 2
	public static final int THRESHOLD_MILESTONE = 50;      // a tick every 100°C on the board
	public static final int DEFAULT_THRESHOLD_STEPS = 200; // 400°C (Create HEATED tier)

	protected ScrollValueBehaviour threshold;
	private boolean attached = false;
	private int temperature = AMBIENT_TEMP;
	private boolean lastAlarm = false; // server-side, redstone transition tracking

	protected AbstractThermometerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		CenteredSideValueBoxTransform slot = new CenteredSideValueBoxTransform(this::isValueBoxSide);
		threshold = new ScrollValueBehaviour(Component.translatable("thermometer.chemicaladdon.threshold"), this, slot) {
			@Override
			public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
				// show °C (rescaled) on the board instead of the raw 0..500 unit index
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

	/** The reactor this thermometer reads, or null when not attached to one (server-side). */
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
		return attached && temperature >= getThreshold();
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide) {
			return; // reading is server-side only; the client gets it via write/read
		}
		ReactorControllerBlockEntity reactor = findReactor();
		boolean newAttached = reactor != null;
		int newTemp = newAttached ? reactor.getTemperature() : AMBIENT_TEMP;
		boolean changed = newAttached != attached || newTemp != temperature;
		attached = newAttached;
		temperature = newTemp;

		boolean newAlarm = isAlarm();
		if (newAlarm != lastAlarm) {
			lastAlarm = newAlarm;
			level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
		}
		if (changed) {
			setChanged();
			sendData();
		}
	}

	@Override
	protected void write(CompoundTag tag, boolean clientPacket) {
		super.write(tag, clientPacket);
		tag.putBoolean("attached", attached);
		tag.putInt("temperature", temperature);
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		super.read(tag, clientPacket);
		attached = tag.getBoolean("attached");
		temperature = tag.contains("temperature") ? tag.getInt("temperature") : AMBIENT_TEMP;
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

		if (!attached) {
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
