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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Shared guts of every S-series vessel gauge (S02 thermometer, S03 pressure
 * gauge, future S04 concentration meter — the S02 dual-form pattern): a
 * world-in scrollable alarm threshold (no GUI), a server-side reading synced
 * to the client, the alarm state and the redstone helpers.
 *
 * <p>The reading is computed <b>server-side</b> (where the full brick → master →
 * reactor chain is available) and the {@code attached}/{@code value} fields are
 * synced to the client, so the goggles HUD never has to re-resolve the master
 * chain client-side (which broke when a panel was mounted on a shell brick).
 *
 * <p>The alarm threshold is stored in <b>coarse units</b> ({@link #thresholdStep()}
 * each) so the Create value-settings board stays small enough to fit on screen —
 * a fine-grained range builds a board that overflows the viewport. The board and
 * the in-world value box both re-scale the raw unit back for display.
 */
public abstract class AbstractVesselGaugeBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

	protected ScrollValueBehaviour threshold;
	private boolean attached = false;
	private int value;
	private boolean lastAlarm = false; // server-side, redstone transition tracking

	protected AbstractVesselGaugeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	// ---------------------------------------------------------- gauge parameters

	/** Translation key of the scrollable threshold label. */
	protected abstract String thresholdLabelKey();

	/** Displayed physical unit of one coarse threshold step (e.g. "°C", "kPa"). */
	protected abstract String thresholdUnit();

	/** Physical units per scroll step (coarse on purpose — see the class doc). */
	protected abstract int thresholdStep();

	/** Full scale of the scroll board, in steps. */
	protected abstract int thresholdMaxSteps();

	/** Milestone tick of the value-settings board, in steps. */
	protected abstract int thresholdMilestoneSteps();

	/** Default threshold, in steps. */
	protected abstract int defaultThresholdSteps();

	/** The reactor this gauge reads, or null when not attached to one (server-side). */
	@Nullable
	protected abstract ReactorControllerBlockEntity findReactor();

	/** The reading taken from an attached reactor. */
	protected abstract int readValue(ReactorControllerBlockEntity reactor);

	/** The reading reported when not attached (ambient / zero). */
	protected abstract int ambientValue();

	/** Comparator full scale: the reading that maps onto signal 15. */
	protected abstract int analogFullScale();

	// ---------------------------------------------------------------- behaviour

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		CenteredSideValueBoxTransform slot = new CenteredSideValueBoxTransform(this::isValueBoxSide);
		threshold = new ScrollValueBehaviour(Component.translatable(thresholdLabelKey()), this, slot) {
			@Override
			public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
				// show the physical unit (rescaled) on the board instead of the raw step index
				return new ValueSettingsBoard(label, thresholdMaxSteps(), thresholdMilestoneSteps(),
					ImmutableList.of(Component.literal(thresholdUnit())),
					new ValueSettingsFormatter(v -> Component.literal((v.value() * thresholdStep()) + thresholdUnit())));
			}
		};
		threshold.between(0, thresholdMaxSteps());
		threshold.value = defaultThresholdSteps();
		threshold.withFormatter(i -> (i * thresholdStep()) + thresholdUnit());
		behaviours.add(threshold);
	}

	/** On which face(s) the scroll value box appears (form-specific). */
	protected abstract boolean isValueBoxSide(BlockState state, Direction side);

	// -------------------------------------------------------------------- state

	/** The alarm threshold in physical units, as set by world-in scrolling. */
	public int getThreshold() {
		return (threshold != null ? threshold.getValue() : defaultThresholdSteps()) * thresholdStep();
	}

	/** The last-read vessel value (physical units); {@link #ambientValue()} when not attached. */
	public int getValue() {
		return value;
	}

	/** true when the reading reached the alarm threshold (attached vessels only). */
	public boolean isAlarm() {
		return attached && value >= getThreshold();
	}

	/** true when the gauge currently sees a vessel to read. */
	public boolean isAttached() {
		return attached;
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null || level.isClientSide) {
			return; // reading is server-side only; the client gets it via write/read
		}
		ReactorControllerBlockEntity reactor = findReactor();
		boolean newAttached = reactor != null;
		int newValue = newAttached ? readValue(reactor) : ambientValue();
		boolean changed = newAttached != attached || newValue != value;
		attached = newAttached;
		value = newValue;

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
		tag.putInt("value", value);
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		super.read(tag, clientPacket);
		attached = tag.getBoolean("attached");
		value = tag.contains("value") ? tag.getInt("value") : ambientValue();
	}

	// --------------------------------------------------------------- redstone

	/** Strong redstone output: 15 on alarm, else 0. */
	public int alarmSignal() {
		return isAlarm() ? 15 : 0;
	}

	/** Comparator output: the reading scaled onto 0..15 (0 when not attached). */
	public int analogSignal() {
		return attached ? Mth.clamp(value * 15 / analogFullScale(), 0, 15) : 0;
	}

	/** The vessel gauge BE at {@code pos}, or null — shared redstone helper (all forms). */
	@Nullable
	public static AbstractVesselGaugeBlockEntity at(BlockGetter level, BlockPos pos) {
		if (level instanceof Level l) {
			BlockEntity be = l.getBlockEntity(pos);
			return be instanceof AbstractVesselGaugeBlockEntity gauge ? gauge : null;
		}
		return null;
	}
}
