package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.vessel.Agitation;
import com.yu1745.chemicaladdon.vessel.IMasterBound;
import com.yu1745.chemicaladdon.vessel.IShellPartEntity;
import com.yu1745.chemicaladdon.vessel.VesselBlockEntity;

import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;

/**
 * Kinetic block entity of the stirring head (B1): a roof shell block bound to
 * its vessel master ({@link IMasterBound}, ChemicalBrickBlockEntity pattern —
 * capability proxy included) whose rotation is Create's to give. The vessel's
 * structure snapshot lists {@link #PART_ID} and publishes the AGITATED
 * capability only while this head is bound, <b>sitting on its master's roof
 * plane</b> (a roof-penetration drive — a wall-position head is a decorative
 * shell block) and effectively rotating; the live |RPM| normalizes to [0,1]
 * agitation (see {@link Agitation}) which the reactor maps onto its stirring
 * coefficient.
 *
 * <p><b>Visual layer</b> (client-only, no gameplay effect): the roof base stays
 * the normal Create-scale static block; {@code StirringHeadRenderer} hangs a
 * rotating shaft from the underside, while the controller renderer owns the
 * vessel-sized impeller pass so it can be ordered with the translucent fluid,
 * easing toward {@link #getShaftTargetDepth()} (liquid-tracking, clamped between the roof
 * and floor plates — pure maths in {@link StirShaftMath}). The eased depth is
 * transient client state ({@code LerpedFloat}, never serialized); the server
 * master/kinetic path is untouched.</p>
 */
public class StirringHeadBlockEntity extends KineticBlockEntity
	implements IMasterBound, IShellPartEntity {

	/** Stable part identity exposed through the structure snapshot and recipe {@code requiredParts}. */
	public static final ResourceLocation PART_ID = new ResourceLocation(ChemicalAddon.MODID, "stirring_head");

	@Nullable
	private BlockPos masterPos;

	/**
	 * Client-only eased shaft depth (blocks below this block's bottom face) the
	 * renderer reads — chases {@link #getShaftTargetDepth()} every client tick
	 * (LerpedFloat/EXP, the decant-hose pattern). Null until first client use so
	 * the animation starts AT the true depth (no drop-from-roof pop on chunk
	 * load). Never serialized: purely transient animation state.
	 */
	@Nullable
	private LerpedFloat renderedDepth;

	/** Exponential chase speed for the eased shaft depth (higher = snappier follow). */
	private static final float DEPTH_CHASE_SPEED = 0.35f;

	/** Worst-case shaft reach below the head (reactor max rings = 5; +1 headroom). */
	private static final double RENDER_REACH_BELOW = 8;

	/**
	 * Debug/test rotation pin (-1 = unpinned). The production path always reads
	 * Create's live {@code getSpeed()}; the pin exists because GameTests cannot
	 * bootstrap a full kinetic network deterministically (Create's periodic
	 * validation would clear a sourceless speed). An overstressed head reports
	 * zero either way, so the overstress semantics stay the Create ones.
	 */
	private float pinnedSpeed = -1;

	public StirringHeadBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	public StirringHeadBlockEntity(BlockPos pos, BlockState state) {
		this(AllBlockEntities.STIRRING_HEAD.get(), pos, state);
	}

	@Override
	public void tick() {
		super.tick(); // kinetic bookkeeping first — the server master/kinetic path is untouched
		if (level == null || !level.isClientSide) {
			return;
		}
		if (renderedDepth == null) {
			renderedDepth = LerpedFloat.linear().startWithValue(getShaftTargetDepth());
		}
		renderedDepth.chase(getShaftTargetDepth(), DEPTH_CHASE_SPEED, LerpedFloat.Chaser.EXP);
		renderedDepth.tickChaser();
	}

	/**
	 * The shaft/impeller depth the renderer should draw toward (blocks below this
	 * block's bottom face). Pure function of the master's geometry + live liquid
	 * surface (see {@link StirShaftMath}); 0 when there is nothing to stir — a
	 * stray/unbound head, a disassembled master or a wall-position head draw the
	 * static shell block only. Works on both sides: on the server the vessel's
	 * rendered level falls back to the exact fill state (GameTests read this).
	 */
	public float getShaftTargetDepth() {
		if (!(getValidMaster() instanceof VesselBlockEntity vessel) || !isOnVesselRoofPlane()) {
			return 0f;
		}
		float interiorHeight = vessel.getHeight();
		if (interiorHeight <= 0f) {
			return 0f;
		}
		float floorY = vessel.getBlockPos().getY() + vessel.getInteriorBottomRelY();
		// liquid-only surface (gases excluded, empty rests on the floor) — same
		// source the decant hose tracks, so the impeller and the hose agree
		float liquidLevel = vessel.getLiquidSurfaceY(1.0f) - floorY;
		return StirShaftMath.shaftDepth(interiorHeight, liquidLevel, StirShaftMath.impellerHalfHeight(getImpellerDiameter()));
	}

	/**
	 * Visual impeller diameter in blocks: derived from the master's interior
	 * width and capped by this head's clearance to the nearest inner wall face
	 * and by the interior height (see {@link StirShaftMath#impellerDiameter});
	 * 0 when the head is not an installed roof drive (nothing to scale).
	 */
	public float getImpellerDiameter() {
		if (!(getValidMaster() instanceof VesselBlockEntity vessel) || !isOnVesselRoofPlane()) {
			return 0f;
		}
		int w = vessel.getSize();
		if (w < 3) {
			return 0f;
		}
		return StirShaftMath.impellerDiameter(w - 2, wallClearance(vessel), vessel.getHeight());
	}

	/** Eased shaft depth for the renderer (client-only interpolation). */
	public float getRenderedDepth(float partialTicks) {
		return renderedDepth == null ? getShaftTargetDepth() : renderedDepth.getValue(partialTicks);
	}

	/**
	 * Distance in blocks from this head column's centre to the nearest inner
	 * wall face of the master's interior (derived from the master's controller
	 * frame — {@code inward}/{@code side} and the shell footprint, the same
	 * frame {@code VesselBlockEntity#cell} builds the shell from). A centred
	 * head in an interior of width n gets n/2; a head hugging a wall gets less,
	 * so its impeller shrinks to fit.
	 */
	private float wallClearance(VesselBlockEntity vessel) {
		Direction inward = vessel.getInward();
		if (inward == null || inward.getAxis().isVertical()) {
			return 0f;
		}
		Direction side = inward.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
		int w = vessel.getSize();
		int sStart = -(w - 1) / 2;
		// opposite corners of the interior column square: (s=sStart+1, d=1) and
		// (s=sStart+w-2, d=w-2) — the interior s-range is [sStart+1, sStart+w-2]
		BlockPos a = vessel.getBlockPos().offset(
			side.getStepX() * (sStart + 1) + inward.getStepX(), 0,
			side.getStepZ() * (sStart + 1) + inward.getStepZ());
		BlockPos b = vessel.getBlockPos().offset(
			side.getStepX() * (sStart + w - 2) + inward.getStepX() * (w - 2), 0,
			side.getStepZ() * (sStart + w - 2) + inward.getStepZ() * (w - 2));
		int minX = Math.min(a.getX(), b.getX());
		int maxX = Math.max(a.getX(), b.getX());
		int minZ = Math.min(a.getZ(), b.getZ());
		int maxZ = Math.max(a.getZ(), b.getZ());
		float centreX = worldPosition.getX() + 0.5f;
		float centreZ = worldPosition.getZ() + 0.5f;
		float dx = Math.min(centreX - minX, maxX + 1 - centreX);
		float dz = Math.min(centreZ - minZ, maxZ + 1 - centreZ);
		return Math.min(dx, dz);
	}

	/**
	 * Public roof-plane probe for the renderer (visual only): whether this head
	 * sits on its bound master's ceiling layer and should draw a shaft at all.
	 * Not the same as {@link #isPartEffective()} — a stalled head keeps its
	 * shaft in the liquor, it just stops agitating.
	 */
	public boolean isOnRoofPlane() {
		return isOnVesselRoofPlane();
	}

	/**
	 * The shaft and enlarged impeller render far below this 1×1 roof cell (and
	 * the impeller spans past it horizontally). MC would frustum-cull them the
	 * moment the head's own cell leaves the viewport — expand the box down the
	 * worst-case shaft reach and out to the worst-case impeller radius, the
	 * Create pulley/mixer pattern ({@code expandTowards}).
	 */
	@Override
	protected AABB createRenderBoundingBox() {
		return new AABB(worldPosition).expandTowards(0, -RENDER_REACH_BELOW, 0).inflate(2, 0, 2);
	}

	// ------------------------------------------------------------- shell part

	@Override
	public ResourceLocation partId() {
		return PART_ID;
	}

	/** B1: a roof-penetration drive — only ceiling-layer cells install this part. */
	@Override
	public boolean requiresRoofPlane() {
		return true;
	}

	@Override
	public boolean isPartEffective() {
		return isOnVesselRoofPlane() && effectiveRotation() != 0f;
	}

	/**
	 * B1 placement rule, evaluated against the live master: the head must sit
	 * on the master's ceiling layer (controller Y + roof plane). Mirrors the
	 * vessel-side bookkeeping filter so even a direct BE query (goggles,
	 * diagnostics) never reports a wall-position head as an installed part.
	 */
	private boolean isOnVesselRoofPlane() {
		if (!(getValidMaster() instanceof VesselBlockEntity vessel)) {
			return false;
		}
		return worldPosition.getY() == vessel.getBlockPos().getY() + vessel.getRoofRelY();
	}

	@Override
	public float effectiveAgitation() {
		return isPartEffective() ? Agitation.normalized(Math.abs(effectiveRotation())) : 0f;
	}

	/**
	 * The rotation the agitation math reads: Create's live {@code getSpeed()}
	 * (zero when overstressed or halted), or the debug pin when one is set.
	 */
	public float effectiveRotation() {
		if (pinnedSpeed >= 0f) {
			return isOverStressed() ? 0f : pinnedSpeed;
		}
		return getSpeed();
	}

	/** Debug/dev: pin the head's rotation (negative clears the pin back to the live network). */
	public void setPinnedSpeed(float rpm) {
		this.pinnedSpeed = rpm;
		setChanged();
	}

	// --------------------------------------------------------- master binding

	/** Called by the master controller on assembly / disassembly. */
	@Override
	public void setMaster(@Nullable BlockPos masterPos) {
		this.masterPos = masterPos;
		setChanged();
		// The client must learn the master pointer for client-side lookups (same
		// contract as ChemicalBrickBlockEntity — assembly happens in-view, so push
		// the update out instead of waiting for a chunk resend).
		if (level instanceof ServerLevel serverLevel) {
			ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(this);
			serverLevel.getServer().getPlayerList()
				.broadcast(null, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), 64.0,
					serverLevel.dimension(), packet);
		}
	}

	/**
	 * The master this head was last bound to (or {@code null} when stray /
	 * unbound). Kept after the master disassembles — used by
	 * {@code StirringHeadBlock.onRemove} to decide whether a breaking head is
	 * structural (notify its master) or a stray.
	 */
	@Nullable
	@Override
	public BlockPos getMasterPos() {
		return masterPos;
	}

	/** Returns the master vessel controller BE if still valid (assembled), else null. */
	@Nullable
	@Override
	public BlockEntity getValidMaster() {
		if (masterPos == null || level == null) {
			return null;
		}
		BlockEntity master = level.getBlockEntity(masterPos);
		if (master instanceof VesselBlockEntity vessel && vessel.isAssembled()) {
			return vessel;
		}
		return null; // master missing or disassembled -> no proxying, no part
	}

	// ------------------------------------------------------ capability proxy

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
		// Parity with ChemicalBrickBlockEntity: the head is a shell block, so pipes
		// on its sides reach the vessel — but the vessel's top face (the head's UP,
		// where the shaft goes) never accepts a pipe.
		if (cap == ForgeCapabilities.FLUID_HANDLER && side == Direction.UP) {
			return LazyOptional.empty();
		}
		BlockEntity master = getValidMaster();
		if (master != null && (cap == ForgeCapabilities.FLUID_HANDLER || cap == ForgeCapabilities.ITEM_HANDLER)) {
			return master.getCapability(cap, side);
		}
		return super.getCapability(cap, side);
	}

	// ----------------------------------------------------------- serialization

	@Override
	protected void write(CompoundTag tag, boolean clientPacket) {
		super.write(tag, clientPacket);
		if (masterPos != null) {
			tag.putLong("masterPos", masterPos.asLong());
		}
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		super.read(tag, clientPacket);
		masterPos = tag.contains("masterPos") ? BlockPos.of(tag.getLong("masterPos")) : null;
	}
}
