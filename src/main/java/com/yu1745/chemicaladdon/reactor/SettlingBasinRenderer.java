package com.yu1745.chemicaladdon.reactor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/** Fluid and sludge-bed contents renderer for the open settling basin. */
public class SettlingBasinRenderer extends SmartBlockEntityRenderer<SettlingBasinBlockEntity> {

	public SettlingBasinRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	protected void renderSafe(SettlingBasinBlockEntity basin, float partialTicks, PoseStack ms,
			MultiBufferSource buffer, int light, int overlay) {
		Direction inward = basin.getInward();
		if (inward == null || basin.getTank().getTotalAmount() <= 0) return;
		Direction side = inward.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
		int size = basin.getSize(), half = (size - 1) / 2;
		int sMin = -half + 1, sMax = -half + size - 2;
		Vec3 c1 = new Vec3(side.getStepX() * sMin + inward.getStepX(), 0,
			side.getStepZ() * sMin + inward.getStepZ());
		Vec3 c2 = new Vec3(side.getStepX() * sMax + inward.getStepX() * (size - 2), 0,
			side.getStepZ() * sMax + inward.getStepZ() * (size - 2));
		float x1 = (float) Math.min(c1.x, c2.x), x2 = (float) Math.max(c1.x, c2.x) + 1;
		float z1 = (float) Math.min(c1.z, c2.z), z2 = (float) Math.max(c1.z, c2.z) + 1;
		BlockPos center = basin.getBlockPos().offset((int) ((x1 + x2) / 2),
			basin.getInteriorBottomRelY() + basin.getHeight() / 2, (int) ((z1 + z2) / 2));
		light = LightTexture.pack(basin.getLevel().getBrightness(LightLayer.BLOCK, center),
			basin.getLevel().getBrightness(LightLayer.SKY, center));

		ms.pushPose();
		ms.translate(0, basin.getInteriorBottomRelY(), 0);
		VesselFluidRenderer.render(basin, x1, z1, x2, z2, basin.getRenderedLevel(partialTicks), ms, buffer, light);
		ms.popPose();
	}

	@Override
	public boolean shouldRenderOffScreen(SettlingBasinBlockEntity basin) {
		return true;
	}
}
