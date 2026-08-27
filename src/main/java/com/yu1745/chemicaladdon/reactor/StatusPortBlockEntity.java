package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.vessel.ProcessReadings;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import net.minecraft.ChatFormatting;

/**
 * The fixed-function reactor status port (施工包 B · 状态口, wall form only): a
 * vessel shell brick whose whole job is to publish the master's process status
 * to the world — right-click reads it out loud, goggles add the batch progress,
 * and redstone encodes it for batch interlocks.
 *
 * <p>The port never touches controller internals: the only read path is
 * {@link ProcessReadings} (status name + progress), resolved through the
 * IMasterBound binding exactly like every other wall gauge.
 *
 * <p><b>Redstone encoding</b> (fixed, no configuration):
 * <ul>
 * <li>unbound (or bound to a non-process vessel): strong 0, comparator 0 — silent;
 * <li>attached + REACTING: strong 0, comparator 4 — a running batch is NOT a
 * completion signal; the completion edge is the transition out of REACTING
 * (strong flips 0 → 15, e.g. to OUTPUT_FULL/NO_RECIPE);
 * <li>attached + any non-running status: strong 15, comparator fixed mapping
 * NOT_ASSEMBLED=0, TEMPERATURE=8, OUTPUT_FULL=12, NO_RECIPE=15.
 * </ul>
 *
 * <p><b>Sync</b>: masterPos / status / progress live in the update tag
 * ({@code getUpdateTag = saveWithoutMetadata}) and {@code setMaster} plus every
 * status change broadcast a block-entity data packet — the B2/B3 update-tag
 * defect (client silently reset to defaults) must not recur here.
 */
public class StatusPortBlockEntity extends ChemicalBrickBlockEntity implements IHaveGoggleInformation {

	/** Fixed comparator mapping for the non-running statuses (spec: 0/8/12/15) and
	 * REACTING=4 (running is "busy", never "done"). */
	private static final Map<String, Integer> COMPARATOR_BY_STATUS = Map.of(
		statusKey(ReactorControllerBlockEntity.ReactorStatus.NOT_ASSEMBLED), 0,
		statusKey(ReactorControllerBlockEntity.ReactorStatus.REACTING), 4,
		statusKey(ReactorControllerBlockEntity.ReactorStatus.TEMPERATURE), 8,
		statusKey(ReactorControllerBlockEntity.ReactorStatus.OUTPUT_FULL), 12,
		statusKey(ReactorControllerBlockEntity.ReactorStatus.NO_RECIPE), 15);

	/** Progress is re-synced only after it moved by this much — the goggles
	 * percentage does not need per-tick resolution. */
	private static final float PROGRESS_SYNC_STEP = 0.05f;

	@Nullable
	private String statusName; // null = unbound / master publishes no process status
	private float progress;
	private float lastSyncedProgress = -1;

	public StatusPortBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.STATUS_PORT.get(), pos, state);
	}

	private static String statusKey(ReactorControllerBlockEntity.ReactorStatus status) {
		return status.name().toLowerCase(Locale.ROOT);
	}

	/** The BE at {@code pos}, or null — redstone/interaction helper. */
	@Nullable
	public static StatusPortBlockEntity at(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
		if (level instanceof net.minecraft.world.level.Level l) {
			BlockEntity be = l.getBlockEntity(pos);
			return be instanceof StatusPortBlockEntity port ? port : null;
		}
		return null;
	}

	// ------------------------------------------------------------------ reading

	/** The master's process status, or null when unbound / no process master. */
	@Nullable
	public String getStatusName() {
		return statusName;
	}

	/** Localised status line for right-click / goggles. */
	public Component statusComponent() {
		return statusName != null
			? Component.translatable("status.chemicaladdon." + statusName)
			: Component.translatable("status_port.chemicaladdon.unbound");
	}

	/** true when the port currently sees an assembled process master. */
	public boolean isAttached() {
		return statusName != null;
	}

	public float getProgress() {
		return progress;
	}

	@Nullable
	private ProcessReadings readings() {
		return getValidMaster() instanceof ProcessReadings readings ? readings : null;
	}

	// -------------------------------------------------------------------- tick

	public void tick() {
		if (level == null || level.isClientSide) {
			return; // everything the client needs arrives via the update tag / data packet
		}
		ProcessReadings readings = readings();
		// masters publish the raw enum name (e.g. "REACTING"); normalise to the
		// lowercase lang-key form used everywhere in this class
		String newStatus = readings != null ? readings.getProcessStatus().toLowerCase(Locale.ROOT) : null;
		float newProgress = readings != null ? readings.getProcessProgress() : 0;
		boolean encodedChanged = !Objects.equals(newStatus, statusName);
		statusName = newStatus;
		progress = newProgress;
		if (encodedChanged) {
			setChanged();
			syncToClient();
			// neighbours only on encoded-state change: the redstone output is a
			// pure function of (attached, status), so mid-batch progress churn
			// must not spam updates
			level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
		} else if (readings != null && Math.abs(progress - lastSyncedProgress) >= PROGRESS_SYNC_STEP) {
			setChanged();
			syncToClient(); // goggles progress refresh only — no neighbour update
		}
	}

	private void syncToClient() {
		lastSyncedProgress = progress;
		if (level instanceof ServerLevel serverLevel) {
			ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(this);
			serverLevel.getServer().getPlayerList()
				.broadcast(null, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), 64.0,
					serverLevel.dimension(), packet);
		}
	}

	// ---------------------------------------------------------------- redstone

	/** Strong output: 0 when unbound or REACTING (busy ≠ done), 15 for every
	 * non-running attached status — the batch-completion edge. */
	public int strongSignal() {
		return isAttached() && !isReacting() ? 15 : 0;
	}

	/** Comparator output: fixed status mapping, 0 when unbound. */
	public int comparatorSignal() {
		return comparatorFor(statusName);
	}

	/** The fixed comparator encoding of a status name (0 = unbound/unknown).
	 *  Static so tests can lock the mapping without driving every status. */
	public static int comparatorFor(@Nullable String statusName) {
		return statusName != null ? COMPARATOR_BY_STATUS.getOrDefault(statusName, 0) : 0;
	}

	private boolean isReacting() {
		return statusKey(ReactorControllerBlockEntity.ReactorStatus.REACTING).equals(statusName);
	}

	// ------------------------------------------------------------------- sync

	@Override
	public void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		if (statusName != null) {
			tag.putString("status", statusName);
		}
		tag.putFloat("progress", progress);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		statusName = tag.contains("status") ? tag.getString("status") : null;
		progress = tag.getFloat("progress");
	}

	// ---------------------------------------------------------------- goggles

	@Override
	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		String spacing = " ";
		tooltip.add(Component.literal(spacing).append(getBlockState().getBlock().getName()));
		if (isAttached()) {
			tooltip.add(Component.literal(spacing)
				.append(Component.translatable("goggles.chemicaladdon.status_port", statusComponent()))
				.withStyle(ChatFormatting.GRAY));
			tooltip.add(Component.literal(spacing)
				.append(Component.translatable("goggles.chemicaladdon.status_port_progress",
					Math.round(progress * 100)))
				.withStyle(ChatFormatting.AQUA));
		} else {
			tooltip.add(Component.literal(spacing)
				.append(statusComponent())
				.withStyle(ChatFormatting.RED));
		}
		return true;
	}
}
