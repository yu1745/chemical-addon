package com.yu1745.chemicaladdon.reactor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * Renders the vessel's item buffer inside the hollow interior, Create Basin
 * style: items hover in a slowly rotating ring, stacked for larger counts
 * with a deterministic random scatter. This is the in-world replacement for
 * the removed item-slot GUI — what you put into the vessel is visible.
 */
public class ReactorControllerRenderer extends SmartBlockEntityRenderer<ReactorControllerBlockEntity> {

	public ReactorControllerRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected void renderSafe(ReactorControllerBlockEntity reactor, float partialTicks, PoseStack ms,
		MultiBufferSource buffer, int light, int overlay) {
		super.renderSafe(reactor, partialTicks, ms, buffer, light, overlay);

		Direction inward = reactor.getInward();
		if (inward == null || !reactor.isAssembled()) {
			return; // not assembled (or client hasn't received the structure yet)
		}
		int height = reactor.getHeight();
		float centerY = height / 2f;

		// light is sampled at the vessel interior (the controller block sits in
		// the wall and is nearly unlit — items must use the light of the spot
		// they actually occupy: open vessels get full skylight, sealed ones go dark)
		BlockPos centerPos = reactor.getBlockPos().offset(inward.getStepX(), height / 2, inward.getStepZ());
		light = LightTexture.pack(reactor.getLevel().getBrightness(LightLayer.BLOCK, centerPos),
			reactor.getLevel().getBrightness(LightLayer.SKY, centerPos));

		int itemCount = 0;
		for (int i = 0; i < reactor.getItems().getSlots(); i++) {
			if (!reactor.getItems().getStackInSlot(i).isEmpty()) {
				itemCount++;
			}
		}
		if (itemCount == 0) {
			return;
		}

		ms.pushPose();
		// vessel interior center, relative to the controller block origin
		ms.translate(0.5 + inward.getStepX(), centerY, 0.5 + inward.getStepZ());

		// slow whole-ring rotation (driven by render time, no BE tick needed)
		float angle = (AnimationTickHolder.getRenderTime(reactor.getLevel()) * 4) % 360;
		ms.mulPose(Axis.YP.rotationDegrees(angle));

		RandomSource r = RandomSource.create(reactor.getBlockPos().hashCode());
		Vec3 baseVector = itemCount == 1 ? new Vec3(0, 0, 0) : new Vec3(0.25, 0, 0);
		float anglePartition = 360f / itemCount;

		for (int slot = 0; slot < reactor.getItems().getSlots(); slot++) {
			ItemStack stack = reactor.getItems().getStackInSlot(slot);
			if (stack.isEmpty()) {
				continue;
			}
			ms.pushPose();

			Vec3 itemPosition = VecHelper.rotate(baseVector, anglePartition * itemCount, Direction.Axis.Y);
			float bob = Mth.sin(AnimationTickHolder.getRenderTime(reactor.getLevel()) / 12f + itemCount) * 1 / 32f;
			ms.translate(itemPosition.x, itemPosition.y + bob, itemPosition.z);
			ms.mulPose(Axis.YP.rotationDegrees(anglePartition * itemCount + 35));
			ms.mulPose(Axis.XP.rotationDegrees(65));

			// visually stack large counts like a heap on the ground
			for (int i = 0; i <= stack.getCount() / 8; i++) {
				ms.pushPose();
				Vec3 scatter = VecHelper.offsetRandomly(Vec3.ZERO, r, 1 / 16f);
				ms.translate(scatter.x, scatter.y, scatter.z);
				Minecraft.getInstance().getItemRenderer()
					.renderStatic(stack, ItemDisplayContext.GROUND, light, overlay, ms, buffer,
						Minecraft.getInstance().level, 0);
				ms.popPose();
			}
			ms.popPose();
			itemCount--;
		}
		ms.popPose();
	}
}
