package com.yu1745.chemicaladdon.reactor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.AllPartialModels;
import com.simibubi.create.AllSpriteShifts;
import com.simibubi.create.content.contraptions.pulley.AbstractPulleyRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;

import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Renders the decant hose by copying Create's {@link AbstractPulleyRenderer} verbatim
 * (coil + hanging rope + magnet, reusing Create's hose_pulley partial models and the
 * coil scroll sprite shift). The one deliberate difference: the hose length is NOT
 * driven by rotation speed — it auto-drops to the vessel's liquid surface and follows
 * it, so there is no manual lowering/retraction. The block body is Create's own hose
 * pulley model (see the {@code decant_hose} blockstate), so the whole assembly reads
 * as "a Create hose pulley that tracks the liquid surface".
 */
public class DecantHoseRenderer extends SafeBlockEntityRenderer<DecantHoseBlockEntity> {

	public DecantHoseRenderer(BlockEntityRendererProvider.Context context) {}

	@Override
	protected void renderSafe(DecantHoseBlockEntity be, float partialTicks, PoseStack ms,
		MultiBufferSource buffer, int light, int overlay) {
		float offset = be.getInterpolatedOffset(partialTicks);
		Level world = be.getLevel();
		BlockState blockState = be.getBlockState();
		BlockPos pos = be.getBlockPos();
		VertexConsumer vb = buffer.getBuffer(RenderType.solid());

		// coil — the drum the hose spools around. Create's N/S-facing pulley spins about
		// the X axis, so its coil faces EAST; our simple block has no facing, so we fix it
		// to the same orientation (drum axis along X, matching the body model).
		SuperByteBuffer coil = CachedBuffers.partialFacing(AllPartialModels.HOSE_COIL, blockState, Direction.EAST);
		AbstractPulleyRenderer.scrollCoil(coil, AllSpriteShifts.HOSE_PULLEY_COIL, offset, 1)
			.light(light)
			.renderInto(ms, vb);

		SuperByteBuffer halfMagnet = CachedBuffers.partial(AllPartialModels.HOSE_HALF_MAGNET, blockState);
		SuperByteBuffer halfRope = CachedBuffers.partial(AllPartialModels.HOSE_HALF, blockState);
		SuperByteBuffer magnet = CachedBuffers.partial(AllPartialModels.HOSE_MAGNET, blockState);
		SuperByteBuffer rope = CachedBuffers.partial(AllPartialModels.HOSE, blockState);

		// the tip (magnet) — tucked under the pulley when there is no vessel below
		AbstractPulleyRenderer.renderAt(world, offset > .25f ? magnet : halfMagnet, offset, pos, ms, vb);

		float f = offset % 1;
		if (offset > .75f && (f < .25f || f > .75f))
			AbstractPulleyRenderer.renderAt(world, halfRope, f > .75f ? f - 1 : f, pos, ms, vb);

		// rope segments from the pulley down to the tip
		for (int i = 0; i < offset - 1.25f; i++)
			AbstractPulleyRenderer.renderAt(world, rope, offset - i - 1, pos, ms, vb);
	}

	@Override
	public boolean shouldRenderOffScreen(DecantHoseBlockEntity be) {
		return true; // the hose hangs down out of the block's 1×1 cell
	}
}
