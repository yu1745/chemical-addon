package com.yu1745.chemicaladdon.reactor;

import java.util.List;

import javax.annotation.Nullable;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * S02 thermometer (温度计): a face-mounted instrument that reads the temperature of
 * the reactor it is mounted on (a shell brick or the controller directly behind its
 * mounting face). World-in and GUI-free, per AGENTS.md:
 * <ul>
 *   <li>the goggles HUD shows the temperature, the alarm threshold and the alarm state
 *       ({@link IHaveGoggleInformation});</li>
 *   <li>the threshold is set by aiming at the dial and scrolling
 *       ({@link ScrollValueBehaviour} — world-in value, no GUI);</li>
 *   <li>a comparator reads an analog temperature signal and the block emits a strong
 *       redstone signal once the temperature reaches the threshold (alarm).</li>
 * </ul>
 *
 * <p>Mounting: {@code FACING} points away from the vessel (toward the player); the
 * vessel is the block directly at {@code FACING.getOpposite()}. Reading follows the
 * Create FluidTank / brick-proxy pattern: a brick delegates to its master controller.
 */
public class ThermometerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

	public static final int AMBIENT_TEMP = 20;
	public static final int DEFAULT_THRESHOLD = 400; // °C — Create HEATED tier
	public static final int MAX_THRESHOLD = 1000; // °C — matches the reactor MAX_TEMP

	private ScrollValueBehaviour threshold;
	private int temperature = AMBIENT_TEMP;
	private boolean alarm = false;

	public ThermometerBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.THERMOMETER.get(), pos, state);
	}

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		CenteredSideValueBoxTransform slot = new CenteredSideValueBoxTransform(
			(state, side) -> state.getValue(ThermometerBlock.FACING) == side);
		threshold = new ScrollValueBehaviour(Component.translatable("thermometer.chemicaladdon.threshold"), this, slot);
		threshold.between(0, MAX_THRESHOLD);
		threshold.value = DEFAULT_THRESHOLD;
		threshold.withFormatter(i -> i + "°C");
		behaviours.add(threshold);
	}

	/** The alarm threshold (°C), as set by world-in scrolling. */
	public int getThreshold() {
		return threshold != null ? threshold.getValue() : DEFAULT_THRESHOLD;
	}

	/** The last-read vessel temperature (°C); ambient when not attached. */
	public int getTemperature() {
		return temperature;
	}

	/** true when the vessel is hot enough to trip the alarm (temperature ≥ threshold). */
	public boolean isAlarm() {
		return alarm;
	}

	/** The reactor this thermometer reads: the block it is mounted on (a brick or the controller). */
	@Nullable
	private ReactorControllerBlockEntity findReactor() {
		if (level == null) {
			return null;
		}
		Direction facing = getBlockState().getValue(ThermometerBlock.FACING);
		BlockPos behind = worldPosition.relative(facing.getOpposite());
		BlockEntity be = level.getBlockEntity(behind);
		if (be instanceof ReactorControllerBlockEntity reactor) {
			return reactor;
		}
		if (be instanceof ChemicalBrickBlockEntity brick) {
			BlockEntity master = brick.getValidMaster();
			if (master instanceof ReactorControllerBlockEntity reactor) {
				return reactor;
			}
		}
		return null;
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
				// alarm edge: push the redstone change out to neighbours
				level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
			}
		}
	}

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		String spacing = " ";
		tooltip.add(Component.literal(spacing).append(Component.translatable("block.chemicaladdon.thermometer")));

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
}
