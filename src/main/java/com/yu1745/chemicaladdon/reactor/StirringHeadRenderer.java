package com.yu1745.chemicaladdon.reactor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import com.yu1745.chemicaladdon.ChemicalAddon;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The B1 stirring head's dynamic visuals (B1 visual layer): the roof base
 * stays the normal Create-scale static block model; this renderer hangs a
 * <b>vertical shaft</b> from its underside down into the vessel and spins an
 * <b>enlarged impeller</b> at its lower end (pattern: Create's
 * {@code MechanicalMixerRenderer} pole + head, the pulley's per-segment rope).
 * The shaft and impeller are emitted together by the reactor controller's solid
 * pass so neither can lose a cross-BER depth race with the translucent fluid.
 *
 * <ul>
 *   <li><b>Rotation</b> — Create's production kinetic speed via
 *       {@code StirringHeadBlockEntity#effectiveRotation()} (partial ticks and
 *       direction included, per-position phase offsets so stacked shaft
 *       segments read as one continuously twisted shaft), tinted by Create's
 *       overstress effect along the way ({@code kineticRotationTransform}).</li>
 *   <li><b>Depth</b> — the eased {@link StirringHeadBlockEntity#getRenderedDepth}
 *       follows the vessel's liquid level (lower portion of the liquor,
 *       clamped between roof and floor plates; retracts near the roof when
 *       empty — see {@link StirShaftMath}).</li>
 *   <li><b>Scale</b> — the impeller model is authored one block across and
 *       scaled to {@link StirringHeadBlockEntity#getImpellerDiameter()}
 *       (interior-width-derived, wall- and height-capped). Only the impeller
 *       is enlarged — never the base block or the shaft cross-section.</li>
 * </ul>
 *
 * <p>Everything here is BER-only eye candy: no collision, no structure
 * semantics (those stay the single roof shell block). Like the decant hose
 * renderer there is deliberately no flywheel {@code VisualizationManager}
 * early-return — flywheel knows nothing about these partials, so the BER is
 * the only producer of this geometry.</p>
 */
public class StirringHeadRenderer extends SmartBlockEntityRenderer<StirringHeadBlockEntity> {

	/** 4 px metal column, authored hanging below its origin (y in [-1, 0]) — repeated/scaled into shaft segments. */
	private static final PartialModel SHAFT =
		PartialModel.of(new ResourceLocation(ChemicalAddon.MODID, "block/stirring_shaft"));

	/** Hub + crossed blade pair, authored one block across, centred on (0.5, 0, 0.5). */
	private static final PartialModel IMPELLER =
		PartialModel.of(new ResourceLocation(ChemicalAddon.MODID, "block/stirring_impeller"));

	/** Below this remaining fraction a partial segment is skipped (sub-pixel slivers). */
	private static final float SEGMENT_EPSILON = 1f / 32f;

	/**
	 * Forces this class's {@code <clinit>} (registering {@link #SHAFT} and
	 * {@link #IMPELLER} with {@code PartialModel.ALL}) <b>before</b>
	 * {@code ModelEvent.RegisterAdditional} — the same clinit-forcing pattern
	 * as {@code VesselGaugeRenderer.init()} / Create's {@code AllPartialModels}.
	 */
	public static void init() {
		// static field initialisation is the only side effect we need
	}

	public StirringHeadRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public boolean shouldRenderOffScreen(StirringHeadBlockEntity be) {
		return true; // the shaft hangs far below the head's own 1×1 cell
	}

	@Override
	protected void renderSafe(StirringHeadBlockEntity be, float partialTicks, PoseStack ms,
		MultiBufferSource buffer, int light, int overlay) {
		// Dynamic geometry is coordinated by ReactorControllerRenderer so the whole
		// shaft, including its submerged section, shares the vessel's solid pass.
	}

	/**
	 * Emits the complete shaft and impeller into the reactor controller's solid
	 * buffer. The caller must have translated the pose to the stirring head's block
	 * origin.
	 */
	static void renderAssembly(StirringHeadBlockEntity be, float partialTicks, PoseStack ms, VertexConsumer vb) {
		if (!be.isOnRoofPlane()) {
			return;
		}
		float depth = be.getRenderedDepth(partialTicks);
		if (depth <= 0f) {
			return;
		}

		BlockState state = be.getBlockState();
		int fullSegments = (int) depth;
		for (int i = 0; i < fullSegments; i++) {
			renderShaftSegment(be, state, i, 1f, ms, vb);
		}
		float remainder = depth - fullSegments;
		if (remainder > SEGMENT_EPSILON) {
			renderShaftSegment(be, state, fullSegments, remainder, ms, vb);
		}
		renderImpeller(be, partialTicks, ms, vb);
	}

	/**
	 * Emits the impeller into the reactor controller's solid buffer. The caller
	 * must have translated the pose to the stirring head's block origin. Keeping
	 * this in the same BER invocation as the fluid body makes the vanilla
	 * block-entity pass deterministic: all solid geometry is flushed before the
	 * fluid's translucent buffer, without disabling depth testing.
	 */
	static void renderImpeller(StirringHeadBlockEntity be, float partialTicks, PoseStack ms, VertexConsumer vb) {
		if (!be.isOnRoofPlane()) {
			return;
		}
		float depth = be.getRenderedDepth(partialTicks);
		float diameter = be.getImpellerDiameter();
		if (depth <= 0f || diameter <= 0f) {
			return;
		}

		BlockPos impellerPos = be.getBlockPos().below(Math.max(1, Math.round(depth)));
		int impellerLight = LevelRenderer.getLightColor(be.getLevel(), be.getLevel().getBlockState(impellerPos),
			impellerPos);
		SuperByteBuffer impeller = CachedBuffers.partial(IMPELLER, be.getBlockState());
		// Call order = application order reversed (first call is outermost): the
		// depth translation must stay OUTSIDE the pivot-scale or it would be scaled
		// along with the model.
		impeller.translate(0, StirShaftMath.impellerCentreY(depth), 0)
			.translate(0.5f, 0, 0.5f)
			.scale(diameter, diameter, diameter)
			.translate(-0.5f, 0, -0.5f);
		KineticBlockEntityRenderer.kineticRotationTransform(impeller, be, Axis.Y, angleFor(be, be.getBlockPos()),
			impellerLight)
			.renderInto(ms, vb);
	}

	/**
	 * One shaft segment at anchor {@code -segmentTop(index)} (see
	 * {@link StirShaftMath#segmentTop}), stretched to {@code heightFraction}:
	 * {@code translate(0, -index, 0) · scale(1, f, 1)} over the hanging model —
	 * first call is the outermost transform, so the scale anchors at the
	 * segment's top edge (model y = 0) exactly where the previous segment ends.
	 */
	private static void renderShaftSegment(StirringHeadBlockEntity be, BlockState state, int segIndex, float heightFraction,
		PoseStack ms, VertexConsumer vb) {
		// the segment spans local [-(segIndex+1), -segIndex-fraction] -> world cell below(segIndex+1)
		BlockPos segPos = be.getBlockPos().below(segIndex + 1);
		int segLight = LevelRenderer.getLightColor(be.getLevel(), be.getLevel().getBlockState(segPos), segPos);
		SuperByteBuffer segment = CachedBuffers.partial(SHAFT, state);
		segment.translate(0, StirShaftMath.segmentTop(segIndex), 0)
			.scale(1, heightFraction, 1);
		KineticBlockEntityRenderer.kineticRotationTransform(segment, be, Axis.Y, angleFor(be, segPos), segLight)
			.renderInto(ms, vb);
	}

	/**
	 * Create's kinetic angle for a position, but driven by
	 * {@code effectiveRotation()} so the debug/test pin and the overstress
	 * zeroing match the agitation logic exactly ({@code getAngleForBe} would
	 * read the raw {@code getSpeed()} instead).
	 */
	private static float angleFor(StirringHeadBlockEntity be, BlockPos pos) {
		float time = AnimationTickHolder.getRenderTime(be.getLevel());
		float offset = KineticBlockEntityRenderer.getRotationOffsetForPosition(be, pos, Axis.Y);
		float speed = be.effectiveRotation();
		return ((time * speed * 3f / 10 + offset) % 360) / 180 * (float) Math.PI;
	}
}
