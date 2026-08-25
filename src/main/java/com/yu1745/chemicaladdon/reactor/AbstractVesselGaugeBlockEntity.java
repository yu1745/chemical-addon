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

import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

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
 *
 * <p><b>Dynamic range:</b> the alarm threshold <i>is</i> the dial's full scale.
 * The needle reaches full deflection exactly at the threshold and the comparator
 * maps {@link #analogZero()}..threshold onto 0..15, so the whole range follows the
 * scrollable threshold. The rest position ({@link #analogZero()}) is a fixed
 * physical zero (0 °C / 0 kPa) — never ambient — so below-ambient readings
 * (compressors, cooling crystallisation) sweep below 12 o'clock.
 */
public abstract class AbstractVesselGaugeBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

	protected ScrollValueBehaviour threshold;
	private boolean attached = false;
	private int value;
	private boolean lastAlarm = false; // server-side, redstone transition tracking

	/** Full dial sweep: the dynamic span ({@link #dynamicSpan()} = threshold − rest
	 *  reading) maps to this many degrees clockwise from 12 o'clock
	 *  ({@link VesselGaugeRenderer}). */
	protected static final float NEEDLE_SWEEP = 270;
	/** The needle never leaves this arc (rest = 12 o'clock, 0°). */
	private static final float NEEDLE_MIN_ANGLE = -45;
	private static final float NEEDLE_MAX_ANGLE = 270;

	/** Client-side dial needle angle (°), chasing the synced reading (see {@link #tick}).
	 *  Never serialised — it is a pure client animation of the already-synced {@code value}. */
	private final LerpedFloat needleAngle = LerpedFloat.angular();

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

	/** The reading at which the needle rests at 12 o'clock — the zero of the
	 *  dynamic scale (the thermometer's ambient temperature, the pressure gauge's 0 kPa). */
	protected abstract int analogZero();

	/** The dynamic span = threshold − rest reading. The alarm threshold IS the full
	 *  scale, so the whole dial range follows the scrollable threshold. */
	protected int dynamicSpan() {
		return getThreshold() - analogZero();
	}

	/** Target dial needle angle (° clockwise from 12 o'clock) for the current reading.
	 *  Full deflection (270°) is reached exactly at the alarm threshold — the gauge's
	 *  range is dynamic and defined entirely by the set threshold. A non-positive span
	 *  collapses the range: the needle sits at rest or pegs full-scale when alarming. */
	protected float needleTargetAngle() {
		int span = dynamicSpan();
		if (span <= 0) {
			return isAlarm() ? NEEDLE_SWEEP : 0;
		}
		return (getValue() - analogZero()) * NEEDLE_SWEEP / span;
	}

	/** The needle's resting tint (ARGB) — the dial art's needle colour for this gauge type. */
	protected abstract int needleTint();

	/** The dial face's offset from the block centre along the FACING/face normal:
	 *  a full-cube wall gauge draws on the block surface ({@code +0.5}), a thin
	 *  panel hangs 2px inside the cell ({@code -3/8}). Shared by the needle renderer
	 *  and the value-box placement so the two stay on the same plane. */
	protected abstract float dialOffset();

	// ---------------------------------------------------------------- behaviour

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		// The value box is pinned to the BOTTOM of the dial face (not the centre):
		// the needle sweeps from the face centre and would be occluded by a centred
		// label. It also sits on the SAME plane as the dial — a thin panel's dial is
		// inset 3/8 inside the cell, so a centred value box would float a full block
		// off the wall. dialOffset() keeps it flush with the baked dial art.
		CenteredSideValueBoxTransform slot = new CenteredSideValueBoxTransform(this::isValueBoxSide) {
			@Override
			public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
				Direction side = getSide();
				double along = dialOffset() - 0.5 / 16.0;
				double x = 0.5 + side.getStepX() * along;
				double y = 2.0 / 16.0;
				double z = 0.5 + side.getStepZ() * along;
				return new Vec3(x, y, z);
			}
		};
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

	/** The dial needle angle (°), smoothed client-side — 0 = 12 o'clock. */
	public float getNeedleAngle(float partialTicks) {
		return needleAngle.getValue(partialTicks);
	}

	/** Which crossing raises the alarm. Gauges default to "reading at/above the
	 *  threshold" (over-limit alarms: overtemperature, overpressure); a gauge may
	 *  invert it — e.g. the conductivity gauge signals "reading has fallen to/below
	 *  the setpoint", the washing-complete / water-clean endpoint (plans/04 §9.3). */
	protected boolean alarmWhenBelow() {
		return false;
	}

	/** true when the reading crossed the alarm threshold (attached vessels only). */
	public boolean isAlarm() {
		return attached && (alarmWhenBelow() ? value <= getThreshold() : value >= getThreshold());
	}

	/** true when the gauge currently sees a vessel to read. */
	public boolean isAttached() {
		return attached;
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null) {
			return;
		}
		if (level.isClientSide) {
			// chase the last-synced reading (write/read pushes it to the client): the
			// needle eases toward the target instead of snapping — the same chased-anim
			// pattern as the reactor's fluid surface (ReactorControllerBlockEntity)
			needleAngle.chase(Mth.clamp(needleTargetAngle(), NEEDLE_MIN_ANGLE, NEEDLE_MAX_ANGLE),
				0.06f, LerpedFloat.Chaser.EXP);
			needleAngle.tickChaser();
			return; // the reading itself is computed server-side; see the write/read pair
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

	/** Comparator output: the reading scaled onto 0..15 against the dynamic span
	 *  (0 at the rest reading, 15 at the alarm threshold). */
	public int analogSignal() {
		if (!attached) {
			return 0;
		}
		int span = dynamicSpan();
		return span <= 0 ? 0 : Mth.clamp((value - analogZero()) * 15 / span, 0, 15);
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
