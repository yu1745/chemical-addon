package com.yu1745.chemicaladdon.reactor;

import java.util.List;

import javax.annotation.Nullable;

import com.yu1745.chemicaladdon.composition.Analyte;
import com.yu1745.chemicaladdon.composition.Chemistry;
import com.yu1745.chemicaladdon.composition.Solution;
import com.yu1745.chemicaladdon.fluid.Mixture;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraft.ChatFormatting;

/**
 * The S16 pH gauge (pH 计, plans/12 §2): reads H⁺ activity —
 * {@code pH = −log₁₀[H⁺]}, alkaline side via {@code [H⁺] = Kw/[OH⁻]}
 * ({@link Chemistry#KW}, the "Kw entry" unlocked for pH in the reading layer).
 * The glass electrode of the titration-endpoint instrument family: 三酸两碱 and
 * Solvay carbonisation are all chemical-potential endpoints.
 *
 * <p>Unlike S02/S03 the dial is a <b>fixed center-zero scale</b>: pH 0–14 maps
 * linearly onto the full sweep with pH 7 at 12 o'clock (a logarithmic scale is
 * binned linearly with correct semantics — 1 comparator level = 1 pH), so the
 * threshold does NOT define the dial range here. The alarm direction is
 * <b>player-toggled in-world</b> (right-click with an empty hand): both
 * endpoints exist — "pH fell to/below the setpoint" (carbonisation done,
 * phenolphthalein's historical job) and "pH rose above it" (basification done).
 */
public abstract class AbstractPhGaugeBlockEntity extends AbstractVesselGaugeBlockEntity {

	public static final int THRESHOLD_STEP = 1;        // 1 pH per scroll unit
	public static final int THRESHOLD_MAX_STEPS = 14;  // pH 0–14
	public static final int THRESHOLD_MILESTONE = 7;   // neutral tick mid-board
	public static final int DEFAULT_THRESHOLD_STEPS = 8; // pH 8.2 ≈ 8: carbonisation endpoint

	/** Which crossing raises the alarm; right-click with an empty hand toggles it. */
	private boolean triggerBelow = true;

	protected AbstractPhGaugeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	protected String thresholdLabelKey() {
		return "ph_gauge.chemicaladdon.threshold";
	}

	@Override
	protected String thresholdUnit() {
		return " pH";
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
		return phOf(reactor.getTank());
	}

	@Override
	protected int ambientValue() {
		return 7; // unattached: the electrode reads neutral water
	}

	@Override
	protected int analogZero() {
		return 7; // unused by the overridden needle/comparator mappings, kept for the family contract
	}

	@Override
	protected boolean alarmWhenBelow() {
		return triggerBelow;
	}

	@Override
	protected int needleTint() {
		return 0xFFAA3C78; // the baked dial art's magenta needle
	}

	// ------------------------------------------------ fixed center-zero scale

	@Override
	protected float needleTargetAngle() {
		// pH 0 = rest (0°), pH 7 = 12 o'clock + 135°, pH 14 = full sweep — a fixed
		// scale, unlike the threshold-defined dynamic range of S02/S03/S18
		return getValue() * NEEDLE_SWEEP / 14f;
	}

	@Override
	public int analogSignal() {
		// 1 level = 1 pH, 0–14 (the 15th slot stays empty — plans/12 §2)
		return isAttached() ? Math.max(0, Math.min(14, getValue())) : 0;
	}

	// ------------------------------------------------------------- toggle

	/** Flip the alarm direction (right-click with an empty hand, world-in — no GUI). */
	public void toggleTriggerDirection() {
		triggerBelow = !triggerBelow;
		setChanged();
		sendData();
		if (level != null) {
			level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
		}
	}

	/** true when the alarm fires on "pH fell to/below the setpoint". */
	public boolean triggersBelow() {
		return triggerBelow;
	}

	@Override
	protected void write(CompoundTag tag, boolean clientPacket) {
		super.write(tag, clientPacket);
		tag.putBoolean("triggerBelow", triggerBelow);
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		super.read(tag, clientPacket);
		triggerBelow = !tag.contains("triggerBelow") || tag.getBoolean("triggerBelow");
	}

	// -------------------------------------------------------------- reading

	/** The last-read vessel pH (0–14); 7 when not attached. */
	public int getPh() {
		return getValue();
	}

	/**
	 * The aqueous phase's pH from the tank's unit domains: H⁺ / OH⁻ / water
	 * summed across mixture stacks plus any plain water ({@link Analyte#ph}).
	 *
	 * <p>P3 读数源开关（-Dchemengine.readings=engine）：内核侧 IPhreeqc 求解的
	 * pH（经 EngineReadings 缓存快照）；无快照/求解失败回退 legacy 路径。
	 */
	public static int phOf(ReactorTank tank) {
		if (com.yu1745.chemicaladdon.composition.parity.ChemEngineConfig.ENGINE_READINGS) {
			com.yu1745.chemicaladdon.composition.parity.EngineReadings.Snapshot s =
					com.yu1745.chemicaladdon.composition.parity.EngineReadings.peek();
			if (s.valid) {
				return com.yu1745.chemicaladdon.composition.parity.EngineReadings.phSteps(s);
			}
		}
		long h = 0;
		long oh = 0;
		long water = 0;
		for (FluidStack stack : tank.getFluids()) {
			if (Mixture.isMixture(stack)) {
				h += (long) Mixture.deriveUnitIonAmounts(stack).getOrDefault("H+1", 0);
				oh += (long) Mixture.deriveUnitIonAmounts(stack).getOrDefault("OH-1", 0);
				water += (long) Mixture.deriveUnitAmounts(stack).getOrDefault(Solution.WATER, 0);
			} else if (stack.getFluid() == Fluids.WATER) {
				water += (long) stack.getAmount() * Chemistry.UNIT_PER_MB;
			}
		}
		return Analyte.ph(h, oh, water);
	}

	/** The pH gauge BE at {@code pos}, or null — shared redstone helper (both forms). */
	@Nullable
	public static AbstractPhGaugeBlockEntity at(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
		if (level instanceof net.minecraft.world.level.Level l) {
			BlockEntity be = l.getBlockEntity(pos);
			return be instanceof AbstractPhGaugeBlockEntity gauge ? gauge : null;
		}
		return null;
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		String spacing = " ";
		tooltip.add(Component.literal(spacing).append(getBlockState().getBlock().getName()));
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.ph", getPh()))
			.withStyle(ChatFormatting.AQUA));
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.ph_gauge_threshold", getThreshold(),
				Component.translatable(triggerBelow
					? "goggles.chemicaladdon.ph_gauge_below"
					: "goggles.chemicaladdon.ph_gauge_above")))
			.withStyle(ChatFormatting.GRAY));
		if (!isAttached()) {
			tooltip.add(Component.literal(spacing)
				.append(Component.translatable("goggles.chemicaladdon.ph_gauge_no_vessel"))
				.withStyle(ChatFormatting.RED));
		} else if (isAlarm()) {
			tooltip.add(Component.literal(spacing)
				.append(Component.translatable("goggles.chemicaladdon.ph_gauge_endpoint"))
				.withStyle(ChatFormatting.GREEN));
		}
		return true;
	}
}
