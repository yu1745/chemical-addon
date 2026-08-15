package com.yu1745.chemicaladdon.reactor;

import java.util.List;

import javax.annotation.Nullable;

import com.yu1745.chemicaladdon.composition.Analyte;
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
 * The S17 turbidity gauge (浊度计, plans/12 §2): reads the suspended-solid
 * volume fraction of the aqueous phase in four bins — 清 / 微浑 / 浑 / 浆
 * ({@link Analyte#turbidityBin}; the settled crystal bed is excluded). The
 * phase-state endpoint of the precipitation-type family, and the
 * <b>first-clouding alarm</b>: a turbid bin on a vessel that should be
 * precipitating clean means an impurity broke through — cut the feed
 * (an after-the-fact signal; the metastable zone and slow kinetics are the
 * stop-loss window).
 */
public abstract class AbstractTurbidityGaugeBlockEntity extends AbstractVesselGaugeBlockEntity {

	public static final int THRESHOLD_STEP = 1;       // 1 bin per scroll unit
	public static final int THRESHOLD_MAX_STEPS = 3;  // bins 0–3
	public static final int THRESHOLD_MILESTONE = 1;  // every bin is a milestone at this size
	public static final int DEFAULT_THRESHOLD_STEPS = 1; // 微浑: the first-clouding alarm

	protected AbstractTurbidityGaugeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	protected String thresholdLabelKey() {
		return "turbidity_gauge.chemicaladdon.threshold";
	}

	@Override
	protected String thresholdUnit() {
		return "";
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
		return turbidityOf(reactor.getTank());
	}

	@Override
	protected int ambientValue() {
		return 0; // unattached reads clear
	}

	@Override
	protected int analogZero() {
		return 0; // 12 o'clock = clear
	}

	@Override
	protected int needleTint() {
		return 0xFF96854E; // the baked dial art's olive needle
	}

	// ------------------------------------------------------- 4-bin scale

	@Override
	protected float needleTargetAngle() {
		// four fixed positions: 清 at 12 o'clock, 微浑 90°, 浑 180°, 浆 full sweep
		return getValue() * NEEDLE_SWEEP / 3f;
	}

	@Override
	public int analogSignal() {
		// 4 bins onto 0/5/10/15 — a bin never straddles levels
		return isAttached() ? Math.max(0, Math.min(3, getValue())) * 5 : 0;
	}

	/** The last-read turbidity bin 0–3; 0 when not attached. */
	public int getTurbidity() {
		return getValue();
	}

	/**
	 * The aqueous phase's turbidity bin from the tank's unit domains:
	 * {@code Suspended} units over water units. The {@code Sediment} bed does
	 * not cloud the reading — clear liquor over a settled bed reads clear.
	 */
	public static int turbidityOf(ReactorTank tank) {
		long suspended = 0;
		long water = 0;
		for (FluidStack stack : tank.getFluids()) {
			if (Mixture.isMixture(stack)) {
				for (int v : Mixture.deriveUnitSuspendedAmounts(stack).values()) {
					suspended += v;
				}
				water += (long) Mixture.deriveUnitAmounts(stack).getOrDefault(Solution.WATER, 0);
			} else if (stack.getFluid() == Fluids.WATER) {
				water += (long) stack.getAmount() * Chemistry.UNIT_PER_MB;
			}
		}
		return Analyte.turbidityBin(suspended, water);
	}

	/** The turbidity gauge BE at {@code pos}, or null — shared redstone helper (both forms). */
	@Nullable
	public static AbstractTurbidityGaugeBlockEntity at(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
		if (level instanceof net.minecraft.world.level.Level l) {
			BlockEntity be = l.getBlockEntity(pos);
			return be instanceof AbstractTurbidityGaugeBlockEntity gauge ? gauge : null;
		}
		return null;
	}

	/** The localised bin name (goggles HUD / paper-style readout). */
	public static Component binName(int bin) {
		return Component.translatable("goggles.chemicaladdon.turbidity_bin_" + Math.max(0, Math.min(3, bin)));
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		String spacing = " ";
		tooltip.add(Component.literal(spacing).append(getBlockState().getBlock().getName()));
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.turbidity", binName(getTurbidity())))
			.withStyle(ChatFormatting.AQUA));
		tooltip.add(Component.literal(spacing)
			.append(Component.translatable("goggles.chemicaladdon.turbidity_gauge_threshold", binName(getThreshold())))
			.withStyle(ChatFormatting.GRAY));
		if (!isAttached()) {
			tooltip.add(Component.literal(spacing)
				.append(Component.translatable("goggles.chemicaladdon.turbidity_gauge_no_vessel"))
				.withStyle(ChatFormatting.RED));
		} else if (isAlarm()) {
			tooltip.add(Component.literal(spacing)
				.append(Component.translatable("goggles.chemicaladdon.turbidity_gauge_alarm"))
				.withStyle(ChatFormatting.GREEN));
		}
		return true;
	}
}
