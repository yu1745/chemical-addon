package com.yu1745.chemicaladdon.vessel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.reactor.ReactorTank;
import com.yu1745.chemicaladdon.reactor.SpillLogic;

import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

/**
 * Structure layer shared by every vessel multiblock (U3, extracted verbatim
 * from the M1 reactor controller): hollow W x W x rings shell validated by the
 * {@code chemicaladdon:vessel_walls} tag, master binding of the shell blocks
 * ({@link IMasterBound}), grow/shrink lifecycle, breach-level spill, geometry
 * accessors, fluid-surface render math and the capability proxy. The shape is
 * parameterised by the subclass ({@link #minSize()}/{@link #maxSize()}/
 * {@link #minRings()}/{@link #maxRings()}/{@link #roofMode()}); processing
 * (heating, reactions, filtering, ...) lives in {@link #vesselTick()}.
 *
 * <p>Everything here is a pure migration of the reactor's M1–U1 structure
 * code; the 72-test suite is the safety net. Subclasses must not rename the
 * public accessors — gauges, the decant hose, the renderer and the GameTests
 * call them directly.
 */
public abstract class VesselBlockEntity extends SmartBlockEntity {

	/**
	 * Interior internals allowlist (U3): extra block types allowed to occupy the
	 * hollow interior without failing validation as {@code INTERIOR_BLOCKED}.
	 * Empty by default — U6 packing/spargers and U4 kiln internals register
	 * here; GameTests add entries to cover the allowlist path.
	 */
	public static final Set<Block> INTERIOR_OVERRIDES = new HashSet<>();

	/** What exactly is wrong with an attempted assembly (for the failure message). */
	public enum AssembleIssue {
		BOTTOM_GAP, TOP_GAP, RING_GAP, INTERIOR_BLOCKED, TOO_SHORT, PARTIAL_TOP
	}

	/** Structured result of an assembly attempt: which face, which issue, where. */
	public record AssembleResult(boolean ok, @Nullable Direction face, @Nullable AssembleIssue issue,
		@Nullable BlockPos issuePos) {
		public static AssembleResult success() {
			return new AssembleResult(true, null, null, null);
		}
	}

	/** Whether the top layer above the wall rings may be sealed. */
	protected enum RoofMode {
		/** Full brick layer = sealed vessel, empty = open-topped, partial = error. */
		OPTIONAL,
		/** Roofless shape: the layer above the rings is not part of the structure. */
		FORBIDDEN
	}

	protected final ReactorTank tank;
	protected final ItemStackHandler items;
	private final LazyOptional<IFluidHandler> fluidCap;
	private final LazyOptional<IItemHandler> itemCap;

	protected boolean assembled = false;
	protected boolean open = false; // open-topped (interior visible) vs sealed
	protected int size = 0; // shell footprint W (W x W base, 0 = not assembled)
	protected int height = 0; // interior ring-layer count; interior is (W-2)^2 x height
	protected int ringLayer = 0; // which ring layer the controller sits on (0 = bottom)
	@Nullable
	protected Direction inward = null; // direction from the controller into the vessel
	// progressive fluid spill after structural breakage (one source per few ticks)
	private final List<FluidStack> pendingSpill = new ArrayList<>();
	@Nullable
	private BlockPos spillLeakPos = null;
	private int spillTimer = 0;

	/**
	 * Client-side fluid surface animation: chases the ABSOLUTE surface height in
	 * blocks (fill × interior height), NOT the fill fraction. Create's FluidTank
	 * can chase the fraction because its geometry never changes — a vessel's
	 * height/capacity changes on brick break/place (shrink/extend) while the
	 * amount stays put, and a fraction chase would blend the old fraction with
	 * the new height mid-transition, twitching a surface that never physically
	 * moved. Null until first client use so the animation starts AT the true
	 * surface instead of rising from the floor on chunk load.
	 */
	private LerpedFloat renderedLevel;

	protected VesselBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int initialCapacity,
		int itemSlots) {
		super(type, pos, state);
		this.tank = new ReactorTank(initialCapacity, this::onContentsChanged);
		this.items = new ItemStackHandler(itemSlots) {
			@Override
			protected void onContentsChanged(int slot) {
				VesselBlockEntity.this.onContentsChanged();
			}
		};
		this.fluidCap = LazyOptional.of(() -> tank);
		this.itemCap = LazyOptional.of(() -> items);
	}

	// ------------------------------------------------------------ shape hooks

	/** Shell footprint bounds (W x W base). */
	protected abstract int minSize();

	protected abstract int maxSize();

	/** Interior ring-layer bounds (shell height minus floor and ceiling). */
	protected abstract int minRings();

	protected abstract int maxRings();

	protected RoofMode roofMode() {
		return RoofMode.OPTIONAL;
	}

	/** Tank capacity in mB for an assembled shell of footprint w and ring count rings. */
	protected abstract int capacityFor(int w, int rings);

	/**
	 * May this state occupy the hollow interior without failing validation?
	 * Default: air or fluid (a fluid poured before sealing is absorbed, not a
	 * blocker). {@link #INTERIOR_OVERRIDES} adds block types on top.
	 */
	protected boolean isInteriorAllowed(BlockState state) {
		return state.isAir() || !state.getFluidState().isEmpty() || INTERIOR_OVERRIDES.contains(state.getBlock());
	}

	/** Legacy save migration: derive the footprint W from a pre-geometry capacity tag. */
	protected int legacySizeFromCapacity(int capacityMb) {
		return 0;
	}

	/** Called on every successful assembly adoption (initial, extension, shrink). */
	protected abstract void onAssembled();

	/** Called when the structure is invalidated (subclass resets process state). */
	protected abstract void onStructureInvalidated();

	/** Sync the controller blockstate's open/sealed variant after adoption. */
	protected void applyOpenState(boolean topOpen) {}

	// ------------------------------------------------------------------ tick

	@Override
	public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
		// no behaviours yet (gauges/ValueSettings live on their own blocks)
	}

	@Override
	public void tick() {
		super.tick();
		if (level == null) {
			return;
		}
		if (level.isClientSide) {
			if (renderedLevel == null) {
				// first frame after the client learned of the vessel: start AT the
				// true surface (no rise-from-floor on chunk load / dimension entry)
				renderedLevel = LerpedFloat.linear().startWithValue(targetRenderedLevel());
			}
			// chase the ABSOLUTE surface height (see renderedLevel): a capacity
			// change with the amount unchanged (shrink on brick break, regrow on
			// placement) leaves this target exactly where it was, so the surface
			// only eases when fluid actually moves
			renderedLevel.chase(targetRenderedLevel(), 0.5, LerpedFloat.Chaser.EXP);
			renderedLevel.tickChaser();
			return;
		}
		tickSpill();
		vesselTick();
	}

	/** Server-side processing of the assembled vessel (heat / reactions / filtering). */
	protected void vesselTick() {
	}

	/** One source block trickles out of the breach every few ticks. */
	private void tickSpill() {
		if (pendingSpill.isEmpty()) {
			return;
		}
		if (++spillTimer % 5 != 0) {
			return;
		}
		SpillLogic.tryPlaceOne(level, spillLeakPos != null ? spillLeakPos : worldPosition, pendingSpill);
	}

	protected void onContentsChanged() {
		setChanged();
		if (level != null && !level.isClientSide) {
			sync();
		}
	}

	protected void sync() {
		if (level != null && !level.isClientSide) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
			// sendBlockUpdated does NOT carry BE nbt; push an explicit update packet
			// so the client BE (tank contents, structure state) refreshes
			if (level instanceof ServerLevel serverLevel) {
				ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(this);
				serverLevel.getServer().getPlayerList()
					.broadcast(null, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), 64.0,
						serverLevel.dimension(), packet);
			}
		}
	}

	// -------------------------------------------------------- assembly engine

	/**
	 * Validates the hollow W x W x rings brick shell (largest complete cuboid
	 * wins — Tinkers smeltery style) and returns a structured result: on
	 * failure, the face that progressed furthest, the first broken spot on that
	 * face and its position. The controller sits in the middle of one wall and
	 * may be placed on ANY ring layer (the floor is k+1 layers below it); the
	 * interior is (W-2)^2 x rings and must be air, fluid or an allowlisted
	 * internal ({@link #isInteriorAllowed}).
	 */
	public AssembleResult tryAssemble() {
		return tryAssemble(maxRings(), Integer.MAX_VALUE, false);
	}

	/**
	 * {@link #tryAssemble()} with bounds used by the shrink path:
	 * {@code maxRings} caps the candidate interior height (a removed ceiling brick
	 * must shrink the vessel, not grow it), {@code ignoreAboveY} (controller-
	 * relative) marks the discarded top zone — layers at/above it are skipped
	 * entirely (their bricks become stray, outside the shell) and a candidate whose
	 * ceiling lies there is treated as open-topped. {@code allowShrink} gates
	 * adopting a SMALLER shell: only the removal path may shrink — a placement
	 * (sealing a half-finished ceiling) must never yank the vessel back down
	 * (that would flicker the height while building taller).
	 */
	private AssembleResult tryAssemble(int ringsCap, int ignoreAboveY, boolean allowShrink) {
		if (level == null || level.isClientSide) {
			return new AssembleResult(false, null, AssembleIssue.BOTTOM_GAP, null);
		}

		AssembleResult best = null;
		int bestProgress = -1;

		for (Direction inward : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST }) {
			Direction side = inward.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.EAST;

			// try the largest shell first: widest W first, then tallest ring count
			// (the shrink path caps the candidate rings via ringsCap)
			for (int w = maxSize(); w >= minSize(); w--) {
				int half = (w - 1) / 2;
				int sStart = -half;
				int sEnd = sStart + w - 1;
				for (int rings = Math.min(ringsCap, maxRings()); rings >= minRings(); rings--) {
					// the controller may sit on ANY ring layer (Tinkers-style): k = its
					// layer counting up from the floor, so the floor is k+1 below it
					for (int k = 0; k < rings; k++) {
						int bottomY = -k - 1;
						int ringY0 = -k;
						int ringY1 = rings - 1 - k;
						int topY = rings - k;

						boolean ok = true;
						int progress = 0;
						AssembleIssue firstIssue = null;
						BlockPos firstIssuePos = null;

						// bottom layer: full w x w of shell blocks
						for (int s = sStart; s <= sEnd && ok; s++) {
							for (int d = 0; d <= w - 1 && ok; d++) {
								BlockPos p = cell(s, d, bottomY, side, inward);
								if (!level.getBlockState(p).is(ChemicalAddon.VESSEL_WALLS)) {
									ok = false;
									firstIssue = AssembleIssue.BOTTOM_GAP;
									firstIssuePos = p;
								}
							}
						}
						if (ok) {
							progress++;
						}

						// ring layers ringY0..ringY1: the shell wall (s at either end, d=0/w-1)
						// must be a vessel block and the interior hollow; (y=0,s=0,d=0) is
						// the controller on its own layer
						for (int y = ringY0; y <= ringY1 && ok; y++) {
							if (y >= ignoreAboveY) {
								progress++; // discarded layer: counts as present, no checks
								continue;
							}
							boolean layerIsRing = true;
							for (int s = sStart; s <= sEnd && layerIsRing; s++) {
								for (int d = 0; d <= w - 1 && layerIsRing; d++) {
									if (y == 0 && s == 0 && d == 0) {
										continue; // the controller itself
									}
									BlockPos p = cell(s, d, y, side, inward);
									boolean wall = s == sStart || s == sEnd || d == 0 || d == w - 1;
									if (!wall) {
										BlockState interior = level.getBlockState(p);
										// interior must be hollow: only air, fluid or an
										// allowlisted internal is allowed. A fluid already sitting
										// inside (e.g. water poured before sealing) must NOT block
										// assembly — it is absorbed into the tank below.
										if (!isInteriorAllowed(interior)) {
											layerIsRing = false; // a solid block occupies the interior
											firstIssue = AssembleIssue.INTERIOR_BLOCKED;
											firstIssuePos = p;
										}
									} else if (!level.getBlockState(p).is(ChemicalAddon.VESSEL_WALLS)) {
											layerIsRing = false;
											firstIssue = AssembleIssue.RING_GAP;
											firstIssuePos = p;
										}
								}
							}
							if (layerIsRing) {
								progress++;
							} else {
								ok = false;
							}
						}

						// top layer: fully sealed (w*w blocks) or fully open — or not part
						// of the shape at all (FORBIDDEN roof), or discarded (shrink zone)
						boolean topOpen = false;
						if (ok) {
							if (roofMode() == RoofMode.FORBIDDEN || topY >= ignoreAboveY) {
								// roofless shape or ceiling in the discarded zone (shrink):
								// treat as open, no top-brick checks — bricks there are stray,
								// outside the shell
								topOpen = true;
								progress++;
							} else {
								int topBricks = 0;
								for (int s = sStart; s <= sEnd && ok; s++) {
									for (int d = 0; d <= w - 1 && ok; d++) {
										BlockPos p = cell(s, d, topY, side, inward);
										if (level.getBlockState(p).is(ChemicalAddon.VESSEL_WALLS)) {
											topBricks++;
										} else if (firstIssue == null) {
											firstIssue = AssembleIssue.PARTIAL_TOP;
											firstIssuePos = p;
										}
									}
								}
								if (topBricks == 0) {
									topOpen = true;
								} else if (topBricks != w * w) {
									ok = false; // partially sealed top
								}
								if (ok) {
									progress++;
								}
							}
						}

						if (ok) {
							// A re-validation of a LIVE vessel adopts only strictly larger
							// (extension) or — on the REMOVAL path only — strictly smaller
							// (shrink after a bound brick was removed) shells. A placement
							// must never shrink: sealing a half-finished ceiling would
							// momentarily match a shorter open vessel and yank the height
							// back down (the build flicker). A tie (same volume AND same
							// open state — even a different orientation) keeps the current
							// assembly untouched; an open/sealed change always takes effect
							// (sealing/opening the top is the player's intent). Initial
							// assembly (assembled == false) adopts the largest cuboid.
							int newVol = w * w * rings;
							int curVol = size * size * height;
							if (assembled && newVol == curVol && topOpen == open) {
								return AssembleResult.success(); // tie: keep current assembly
							}
							if (assembled && newVol < curVol && !allowShrink) {
								return AssembleResult.success(); // placement must never shrink
							}
							boolean wasAssembled = assembled;
							int oldSize = size;
							int oldHeight = height;
							assembled = true;
							this.inward = inward;
							this.open = topOpen;
							this.size = w;
							this.height = rings;
							this.ringLayer = k;
							onAssembled();
							tank.setCapacity(capacityFor(w, rings));
							if (wasAssembled) {
								// re-bind: clear old-shell masters first so bricks that fell OUT
								// of the (shrunk) shell stop proxying capabilities — bindBricks
								// below re-binds the new shell (extension re-binds harmlessly).
								// The radius must cover the shell's vertical extent too (floor
								// is ringLayer+1 below the controller, ceiling rings-ringLayer
								// above): a footprint-only radius misses old floor/ceiling
								// bricks on tall narrow vessels (height > size)
								clearShellMasters(Math.max(Math.max(oldSize, w), Math.max(oldHeight, rings)) + 1);
							} else {
								// coming back from a break: the shell is intact again — stop
								// any leftover trickle from the breach (a repaired vessel must
								// not keep leaking). An extension of a live vessel keeps its
								// (empty) spill state untouched.
								pendingSpill.clear();
								spillLeakPos = null;
								spillTimer = 0;
							}
							// §D: rebuilt smaller than the retained contents -> the excess is
							// turned back into physical fluid (progressive trickle from the new
							// interior top), so the vessel never sits wedged in a permanent
							// over-capacity OUTPUT_FULL state
							int newCap = tank.getTankCapacity(0);
							int nowTotal = tank.getTotalAmount();
							if (nowTotal > newCap) {
								int overflowMb = nowTotal - newCap;
								List<FluidStack> overflow = new ArrayList<>();
								for (FluidStack stack : new ArrayList<>(tank.getFluids())) {
									int take = (int) Math.floor(stack.getAmount() * (double) overflowMb / nowTotal);
									if (take > 0) {
										FluidStack out = stack.copy();
										out.setAmount(take);
										overflow.add(out);
										stack.shrink(take);
									}
								}
								tank.pruneEmpty();
								pendingSpill.addAll(SpillLogic.queueFluids(overflow));
								spillLeakPos = topCenter(w, rings, inward);
								spillTimer = 4;
								SpillLogic.tryPlaceOne(level, spillLeakPos, pendingSpill);
							}
							bindBricks(worldPosition, inward, side, w, rings);
							// absorb any fluid already sitting in the interior (e.g. water poured
							// before the last brick closed the shell) into the tank — source blocks
							// become tank contents, flowing fluids simply evaporate. Without this a
							// sealed vessel would trap fluid invisibly inside (open-top absorption
							// only runs while the top is open).
							absorbInteriorOnAssemble(side, inward, w, sStart, sEnd, ringY0, ringY1);
							applyOpenState(topOpen);
							setChanged();
							sync();
							return AssembleResult.success();
						}
						// keep the failure diagnostic that progressed furthest
						if (progress > bestProgress) {
							bestProgress = progress;
							best = new AssembleResult(false, inward, firstIssue, firstIssuePos);
						}
					}
				}
			}
		}
		return best != null ? best : new AssembleResult(false, Direction.NORTH, AssembleIssue.TOO_SHORT, null);
	}

	/** World position of a shell cell (s, d, y) relative to the controller. */
	private BlockPos cell(int s, int d, int y, Direction side, Direction inward) {
		return worldPosition.offset(side.getStepX() * s + inward.getStepX() * d, y,
			side.getStepZ() * s + inward.getStepZ() * d);
	}

	/**
	 * §A: re-validate after a structural block was placed near an assembled
	 * vessel. The placed block may have completed a larger shell — adopt the
	 * result only when it is strictly larger (grow, never shrink or re-orient);
	 * {@link #tryAssemble} enforces that, and never spills on success, so the
	 * contents carry over untouched. Blocks far outside the shell's reach are
	 * rejected cheaply (a stray brick must not trigger a full re-validation).
	 */
	public boolean tryExtend(BlockPos placedPos) {
		if (level == null || level.isClientSide || !assembled) {
			return false;
		}
		// fast reject: the placed block must be within one block of the current
		// shell's bounding box to be able to complete a larger cuboid
		int reach = Math.max(size, height) + 2;
		if (Math.abs(placedPos.getX() - worldPosition.getX()) > reach
			|| Math.abs(placedPos.getY() - worldPosition.getY()) > reach
			|| Math.abs(placedPos.getZ() - worldPosition.getZ()) > reach) {
			return false;
		}
		return tryAssemble().ok();
	}

	/**
	 * §D: leak point for an overflow after rebuilding smaller — the top-centre
	 * of the (new) shell's interior, so the excess pours over the rim (open top)
	 * or seeps from the seam (sealed top; {@code SpillLogic.findFreeSpot} walks
	 * outward from the occupied cap block).
	 */
	private BlockPos topCenter(int w, int rings, Direction inward) {
		int dMid = (w - 1) / 2;
		return worldPosition.offset(inward.getStepX() * dMid, rings - ringLayer, inward.getStepZ() * dMid);
	}

	/**
	 * A bound shell block was removed. Before giving up on the vessel, try to
	 * keep it going at the least destructive step:
	 * 1) any legal (smaller or open-changed) shell from a full re-validation;
	 * 2) a CEILING brick removed -> the vessel stays the SAME height and simply
	 *    becomes open-topped (the ceiling layer is discarded, its bricks become
	 *    stray) — the height of a vessel is its ring count, not its lid;
	 * 3) a top RING brick removed -> shrink one ring (lower the vessel);
	 * 4) otherwise the shell has no legal remainder -> full de-assembly (§B
	 *    breach-level spill). Contents survive 1-3 (over-capacity overflows, §D).
	 */
	public void handleStructuralBlockRemoved(BlockPos removedPos) {
		if (level == null || level.isClientSide || !assembled) {
			return;
		}
		// 1) any legal shell from a full re-validation (smaller, or tie-with-open-change)
		if (tryAssemble(maxRings(), Integer.MAX_VALUE, true).ok()) {
			return;
		}
		// 2) ceiling brick removed -> same height, open-topped (discard only the lid
		// layer, keep every ring); no height change, no de-assembly
		int ceilingLine = height - ringLayer; // controller-relative y of the ceiling layer
		if (tryAssemble(height, ceilingLine, true).ok()) {
			return;
		}
		// 3) top ring brick removed -> drop the ceiling + highest ring (lower by one)
		if (tryShrink()) {
			return;
		}
		// 4) nothing legal remains -> full de-assembly with breach-level spill
		invalidateStructure(removedPos);
	}

	/**
	 * Shrink the vessel by one interior ring: the ceiling layer and the highest
	 * ring layer are treated as discarded (their bricks become stray, out of the
	 * shell) and the remaining shell is re-validated as an open-topped vessel one
	 * ring shorter. Adopts the largest legal result (w unchanged usually), so
	 * removing a top brick lowers the vessel instead of destroying it.
	 */
	private boolean tryShrink() {
		if (height <= 1) {
			return false; // already minimal height (a 3x3x3 has a single ring)
		}
		// controller-relative y of the new ceiling; everything at/above is discarded
		int dropLine = height - 1 - ringLayer;
		return tryAssemble(height - 1, dropLine, true).ok();
	}

	/** Clears master pointers on every brick within a box around the controller. */
	private void clearShellMasters(int radius) {
		if (level == null) {
			return;
		}
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dy = -radius; dy <= radius; dy++) {
				for (int dz = -radius; dz <= radius; dz++) {
					if (level.getBlockEntity(worldPosition.offset(dx, dy, dz)) instanceof IMasterBound bound) {
						bound.setMaster(null);
					}
				}
			}
		}
	}

	/**
	 * Binds every structural shell block (any block in the vessel_walls tag —
	 * brick, glass, ...) to this controller so it proxies capabilities and can
	 * report breakage. The y range is controller-RELATIVE and must follow the
	 * controller's ring layer k: floor at -k-1, ceiling at rings-k. A hard-coded
	 * -1..rings (k=0 assumption) leaves the floor unbound when the controller is
	 * mounted higher — the decant hose scans down the interior column and falls
	 * through the unbound floor, never finding the vessel. Roofless shapes
	 * (FORBIDDEN) stop at the top of the wall ring: blocks above the rim are not
	 * part of the structure.
	 */
	private void bindBricks(BlockPos masterPos, Direction inward, Direction side, int w, int rings) {
		if (level == null) {
			return;
		}
		int half = (w - 1) / 2;
		int sStart = -half;
		int sEnd = sStart + w - 1;
		int yTop = roofMode() == RoofMode.FORBIDDEN ? rings - 1 - ringLayer : rings - ringLayer;
		for (int s = sStart; s <= sEnd; s++) {
			for (int d = 0; d <= w - 1; d++) {
				for (int y = -ringLayer - 1; y <= yTop; y++) {
					// skip only the controller cell itself — the shell blocks directly
					// above/below the controller (same s/d column, y != 0) are real wall
					// blocks and must be bound too (a gauge mounted there read nothing)
					if (s == 0 && d == 0 && y == 0) {
						continue;
					}
					bindBrick(cell(s, d, y, side, inward), masterPos);
				}
			}
		}
	}

	private void bindBrick(BlockPos pos, @Nullable BlockPos masterPos) {
		if (level == null) {
			return;
		}
		if (level.getBlockEntity(pos) instanceof IMasterBound bound) {
			bound.setMaster(masterPos);
		}
	}

	/**
	 * Absorbs fluid already sitting in the interior into the tank, called once on
	 * successful assembly. Source blocks become tank contents (1000 mB each);
	 * flowing (spreading) fluids carry no volume and are simply cleared to air so
	 * they don't linger invisibly inside a sealed shell. Allowlisted solid
	 * internals are skipped. Runs for both open and sealed vessels.
	 */
	private void absorbInteriorOnAssemble(Direction side, Direction inward, int w,
		int sStart, int sEnd, int ringY0, int ringY1) {
		if (level == null) {
			return;
		}
		for (int y = ringY0; y <= ringY1; y++) {
			for (int s = sStart + 1; s <= sEnd - 1; s++) { // interior columns (skip the two wall columns)
				for (int d = 1; d <= w - 2; d++) {          // interior depth  (skip the two wall layers)
					BlockPos p = cell(s, d, y, side, inward);
					BlockState bs = level.getBlockState(p);
					if (bs.isAir()) {
						continue;
					}
					net.minecraft.world.level.material.FluidState fs = bs.getFluidState();
					if (fs.isEmpty()) {
						continue; // allowlisted internal or a solid that slipped through; skip
					}
					if (fs.isSource()) {
						int filled = tank.fill(new FluidStack(fs.getType(), 1000), IFluidHandler.FluidAction.EXECUTE);
						if (filled == 1000) {
							level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
						}
					} else {
						// flowing fluid: no volume to absorb, just clear it
						level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
					}
				}
			}
		}
	}

	public void invalidateStructure(@Nullable BlockPos leakPos) {
		if (assembled) {
			assembled = false;
			int oldSize = size;
			// §C: keep size/height/inward as lastGeometry — the remaining lower shell
			// still stands and the residual fluid surface must keep rendering while
			// the vessel is de-assembled (see the renderer guard). All logical paths
			// (reactions, absorption, capability proxy) are gated on isAssembled(), so
			// the retained geometry only feeds rendering.
			onStructureInvalidated();
			// contents become physical again: items drop, fluids pour out of the breach.
			// §B: breach-level spill — only the fluid above the breach height pours
			// out; the portion below stays in the tank (auto-lowered surface, recovered
			// on rebuild). Breaking the controller itself keeps nothing: its NBT dies
			// with the block, so a retained remainder would silently vanish — fall
			// back to a full physical spill.
			BlockPos breach = leakPos != null ? leakPos : worldPosition;
			SpillLogic.spillItems(level, breach, items);
			pendingSpill.clear();
			int total = tank.getTotalAmount();
			if (total <= 0 || height <= 0 || breach.equals(worldPosition)) {
				// full spill: empty tank, no interior, or the controller itself broke
				pendingSpill.addAll(SpillLogic.queueFluids(tank)); // sub-bucket remainder lost by design
			} else {
				// interior ring the breach sits on (controller is on ringLayer; the ring
				// below it holds the fluid that survives — one full layer per ring)
				int ring = Math.max(0, Math.min(height, breach.getY() - worldPosition.getY() + ringLayer));
				int keepMb = (int) ((long) tank.getTankCapacity(0) * ring / height);
				int spillMb = Math.max(0, total - keepMb);
				if (spillMb >= total) {
					pendingSpill.addAll(SpillLogic.queueFluids(tank)); // bottom breach: drains everything
				} else if (spillMb > 0) {
					// proportional split preserves every phase's ratio (gases included)
					List<FluidStack> spilled = new ArrayList<>();
					for (FluidStack stack : new ArrayList<>(tank.getFluids())) {
						int take = (int) Math.floor(stack.getAmount() * (double) spillMb / total);
						if (take > 0) {
							FluidStack out = stack.copy();
							out.setAmount(take);
							spilled.add(out);
							stack.shrink(take);
						}
					}
					tank.pruneEmpty();
					pendingSpill.addAll(SpillLogic.queueFluids(spilled));
				}
				// spillMb == 0 (breach at/above the surface): keep everything
			}
			spillLeakPos = breach;
			spillTimer = 4; // first source appears almost immediately
			SpillLogic.tryPlaceOne(level, breach, pendingSpill);
			// clear master pointers on nearby shell blocks so they stop proxying
			// (radius covers the vertical extent too — height is retained as
			// lastGeometry here, so a tall vessel's floor/ceiling bricks are reached)
			clearShellMasters(Math.max(oldSize, height) + 1);
			setChanged();
			sync();
		}
	}

	// ------------------------------------------------------ geometry accessors

	public boolean isAssembled() {
		return assembled;
	}

	/** mB still queued to pour out of the breach (server-side spill state; tests/debug). */
	public int getPendingSpillAmount() {
		int total = 0;
		for (FluidStack f : pendingSpill) {
			total += f.getAmount();
		}
		return total;
	}

	/**
	 * The interior (fluid surface + floating items) renders up to 7 blocks away
	 * from the controller block. Without an expanded render bounding box, MC's
	 * frustum cull tests only the controller's own 1×1×1 cell — so the moment the
	 * controller leaves the viewport (even with the fluid surface still on
	 * screen) the whole BE is culled and the contents vanish. Cover the entire
	 * shell footprint (Create FluidTank pattern, FluidTankBlockEntity:372).
	 */
	@Override
	protected AABB createRenderBoundingBox() {
		// §C: a broken-but-not-empty vessel keeps rendering its residual surface
		// in the remaining shell, so the box must stay as large as the last
		// assembly (size/height/inward are retained on invalidation as lastGeometry).
		if ((!assembled && tank.getTotalAmount() <= 0) || size < minSize() || inward == null) {
			return super.createRenderBoundingBox();
		}
		// controller sits at the wall centre (s=0, d=0, k-th ring). The shell
		// spans s ∈ [-(half), +half] along the wall axis, d ∈ [0, size-1] inward,
		// and the controller can be on any ring layer k ∈ [0, rings-1] — so y may
		// extend up to (height+1) below and above the controller. Cover the worst
		// case in every axis; frustum tests are cheap.
		Direction side = inward.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
		int half = (size - 1) / 2;
		int reach = size - 1; // 0..size-1 blocks of shell in the inward/±side directions
		int dy = height + 1;  // floor below / ceiling above the controller's layer
		int minX = worldPosition.getX() - half;
		int maxX = worldPosition.getX() + half;
		int minZ = worldPosition.getZ() - half;
		int maxZ = worldPosition.getZ() + half;
		if (side.getAxis() == Direction.Axis.X) {
			minX = worldPosition.getX() - half;
			maxX = worldPosition.getX() + half;
			minZ = worldPosition.getZ();
			maxZ = worldPosition.getZ() + reach * inward.getStepZ();
			if (inward.getStepZ() < 0) { minZ = worldPosition.getZ() + reach * inward.getStepZ(); maxZ = worldPosition.getZ(); }
		} else {
			minX = worldPosition.getX();
			maxX = worldPosition.getX() + reach * inward.getStepX();
			if (inward.getStepX() < 0) { minX = worldPosition.getX() + reach * inward.getStepX(); maxX = worldPosition.getX(); }
			minZ = worldPosition.getZ() - half;
			maxZ = worldPosition.getZ() + half;
		}
		int minY = worldPosition.getY() - dy;
		int maxY = worldPosition.getY() + dy;
		return new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
	}

	/** Direction from the controller into the vessel interior (for item rendering). */
	@Nullable
	public Direction getInward() {
		return inward;
	}

	/** true when the vessel is open-topped (interior visible from above). */
	public boolean isOpen() {
		return open;
	}

	/** Shell footprint W of the assembled cuboid (W x W base; 0 when not assembled). */
	public int getSize() {
		return size;
	}

	/** Interior height in blocks (ring-layer count). */
	public int getHeight() {
		return Math.max(height, 0);
	}

	/** Y of the floor layer relative to the controller (negative; floor is ringLayer+1 below). */
	public int getFloorRelY() {
		return -ringLayer - 1;
	}

	/**
	 * Controller-relative Y where the fluid body's bottom rests: the TOP face of
	 * the floor blocks (= {@code -ringLayer}). The controller may sit on ANY ring
	 * layer, so this is NOT always 0 — rendering, the liquid-surface math and the
	 * absorb polling all measure from here, never from the controller's own layer.
	 */
	public int getInteriorBottomRelY() {
		return -ringLayer;
	}

	/** Y of the roof layer relative to the controller (positive). */
	public int getRoofRelY() {
		return height - ringLayer;
	}

	public ReactorTank getTank() {
		return tank;
	}

	public ItemStackHandler getItems() {
		return items;
	}

	/** Tank fill fraction (0..1); capacity is height-scaled, so this maps onto the interior height.
	 *  Clamped: an older save may hold more fluid than the current (smaller) capacity, and an
	 *  over-1 fraction would render the surface above the vessel rim. */
	public float getFillState() {
		int cap = tank.getTankCapacity(0);
		if (cap <= 0) {
			return 0;
		}
		float f = (float) tank.getTotalAmount() / cap;
		return Math.max(0, Math.min(1, f));
	}

	/**
	 * The height the client animates the fluid surface toward: fill × interior
	 * height, in blocks above the interior floor. Deliberately ABSOLUTE (not the
	 * bare fill fraction) so that a ring-count change (shrink/extend) with the
	 * amount unchanged produces the SAME target — the rendered surface stays put
	 * instead of dipping/spiking while the LerpedFloat re-converges.
	 */
	private float targetRenderedLevel() {
		return getFillState() * getHeight();
	}

	/** Animated fluid surface height in blocks (interpolated for smooth rendering; client only). */
	public float getRenderedLevel(float partialTicks) {
		return renderedLevel == null ? targetRenderedLevel() : renderedLevel.getValue(partialTicks);
	}

	/**
	 * World-space Y of the liquid (non-gas) surface — the height the decant hose
	 * tracks. Mirrors the vessel renderer's surface math (interpolated fill
	 * fraction scaled by the interior height, gases excluded) so the hose tip
	 * lands exactly on the rendered surface. Empty vessels report the floor.
	 */
	public float getLiquidSurfaceY(float partialTicks) {
		float levelHeight = getRenderedLevel(partialTicks);
		List<FluidStack> fluids = tank.getFluids();
		int total = tank.getTotalAmount();
		// measure from the interior floor, ringLayer below the controller (the
		// controller may be mounted on any ring — never from its own layer)
		int floorY = worldPosition.getY() + getInteriorBottomRelY();
		if (levelHeight <= 1 / 1024f || fluids.isEmpty() || total <= 0) {
			return floorY; // empty: surface rests on the interior floor
		}
		int liquidAmount = 0;
		for (FluidStack f : fluids) {
			if (!f.getFluid().getFluidType().isLighterThanAir()) {
				liquidAmount += f.getAmount();
			}
		}
		return floorY + levelHeight * liquidAmount / total;
	}

	// --------------------------------------------- capability + serialization

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
		if (cap == ForgeCapabilities.FLUID_HANDLER) {
			if (side == Direction.UP) {
				return LazyOptional.empty(); // vessel top never accepts a pipe (side + bottom only)
			}
			return fluidCap.cast();
		}
		if (cap == ForgeCapabilities.ITEM_HANDLER) {
			return itemCap.cast();
		}
		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		fluidCap.invalidate();
		itemCap.invalidate();
	}

	@Override
	protected void write(CompoundTag tag, boolean clientPacket) {
		super.write(tag, clientPacket);
		tag.putBoolean("assembled", assembled);
		tag.put("tank", tank.serializeNBT());
		tag.putInt("tankCapacity", tank.getTankCapacity(0)); // survive reloads (volume-scaled)
		tag.putInt("size", size);
		tag.putInt("height", height);
		tag.putInt("ringLayer", ringLayer);
		tag.put("items", items.serializeNBT());
		tag.putBoolean("open", open);
		if (inward != null) {
			tag.putString("inward", inward.getSerializedName());
		}
	}

	@Override
	protected void read(CompoundTag tag, boolean clientPacket) {
		super.read(tag, clientPacket);
		assembled = tag.getBoolean("assembled");
		tank.deserializeNBT(tag.getCompound("tank"));
		if (tag.contains("tankCapacity")) {
			tank.setCapacity(tag.getInt("tankCapacity"));
		}
		if (tag.contains("size")) {
			size = tag.getInt("size");
		} else {
			size = legacySizeFromCapacity(tag.getInt("tankCapacity"));
		}
		// legacy saves had no height (cube shells): height = size - 2
		height = tag.contains("height") ? tag.getInt("height") : (size > 0 ? size - 2 : 0);
		ringLayer = tag.getInt("ringLayer"); // legacy saves default 0 (bottom ring)
		items.deserializeNBT(tag.getCompound("items"));
		inward = tag.contains("inward") ? Direction.byName(tag.getString("inward")) : null;
		open = tag.getBoolean("open");
		// a sync packet may have changed structure geometry (size/height/inward/
		// assembled) — drop the cached render bounding box so createRenderBoundingBox
		// recomputes it against the new dimensions (Create FluidTank pattern).
		if (clientPacket && level != null && level.isClientSide) {
			invalidateRenderBoundingBox();
		}
	}
}
