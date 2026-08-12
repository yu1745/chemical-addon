package com.yu1745.chemicaladdon.reactor;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.platform.ForgeCatnipServices;
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
import net.minecraftforge.fluids.FluidStack;

/**
 * Renders the vessel interior: the fluid surface (a semi-transparent "pot of
 * soup" — liquids layered upward from the floor, gases hanging from the top)
 * and the item buffer floating ON the surface, half-submerged with a gentle
 * bobbing. Handles any n x n x n shell size (interior (n-2)^3). Fluid rendering
 * reuses Create's catnip FluidRenderHelper, animated by a client-side LerpedFloat
 * chasing the synced fill state.
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
		int size = reactor.getSize();
		int height = reactor.getHeight();

		// interior footprint in controller-local coordinates (the (n-2)^2 hollow core)
		Direction side = inward.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
		int half = (size - 1) / 2;
		int sMin = -half + 1;
		int sMax = -half + size - 2;
		Vec3 c1 = new Vec3(side.getStepX() * sMin + inward.getStepX(), 0,
			side.getStepZ() * sMin + inward.getStepZ());
		Vec3 c2 = new Vec3(side.getStepX() * sMax + inward.getStepX() * (size - 2), 0,
			side.getStepZ() * sMax + inward.getStepZ() * (size - 2));
		float x1 = (float) Math.min(c1.x, c2.x);
		float x2 = (float) Math.max(c1.x, c2.x) + 1;
		float z1 = (float) Math.min(c1.z, c2.z);
		float z2 = (float) Math.max(c1.z, c2.z) + 1;
		float cx = (x1 + x2) / 2f;
		float cz = (z1 + z2) / 2f;

		// light is sampled at the vessel interior (the controller block sits in
		// the wall and is nearly unlit — contents must use the light of the spot
		// they actually occupy: open vessels get full skylight, sealed ones go dark)
		BlockPos centerPos = reactor.getBlockPos().offset((int) cx, height / 2, (int) cz);
		light = LightTexture.pack(reactor.getLevel().getBrightness(LightLayer.BLOCK, centerPos),
			reactor.getLevel().getBrightness(LightLayer.SKY, centerPos));

		float renderTime = AnimationTickHolder.getRenderTime(reactor.getLevel());

		// --- fluid pass: layered soup surface rising from the interior floor ---
		float levelHeight = reactor.getRenderedLevel(partialTicks) * height;
		float liquidSurface = 0;
		if (levelHeight > 1 / 1024f) {
			liquidSurface = renderFluid(reactor, x1, z1, x2, z2, levelHeight, ms, buffer, light);
		}

		// --- item pass: float on the fluid surface, half-submerged, bobbing ---
		int itemCount = 0;
		for (int i = 0; i < reactor.getItems().getSlots(); i++) {
			if (!reactor.getItems().getStackInSlot(i).isEmpty()) {
				itemCount++;
			}
		}
		if (itemCount == 0) {
			return;
		}

		// item ring centre sits on the liquid surface (below any gas layer);
		// with no fluid, items rest near the floor
		float surfaceY = Math.max(liquidSurface, 0.125f);

		ms.pushPose();
		ms.translate(cx, surfaceY - 0.1f, cz);

		// slow whole-ring rotation (driven by render time, no BE tick needed)
		float angle = (renderTime * 4) % 360;
		ms.mulPose(Axis.YP.rotationDegrees(angle));

		RandomSource r = RandomSource.create(reactor.getBlockPos().hashCode());
		float ringRadius = (size - 2) * 0.3f; // scales with the interior footprint
		Vec3 baseVector = itemCount == 1 ? new Vec3(0, 0, 0) : new Vec3(ringRadius, 0, 0);
		float anglePartition = 360f / itemCount;
		int remaining = itemCount;
		boolean floating = reactor.getFillState() > 0;

		for (int slot = 0; slot < reactor.getItems().getSlots(); slot++) {
			ItemStack stack = reactor.getItems().getStackInSlot(slot);
			if (stack.isEmpty()) {
				continue;
			}
			ms.pushPose();

			Vec3 itemPosition = VecHelper.rotate(baseVector, anglePartition * remaining, Direction.Axis.Y);
			// bob only when floating on fluid (Basin/Vat style); still on the floor otherwise
			float bob = 0;
			if (floating) {
				bob = (Mth.sin(renderTime / 12f + remaining) + 1.5f) * 1 / 32f;
			}
			ms.translate(itemPosition.x, itemPosition.y + bob, itemPosition.z);
			ms.mulPose(Axis.YP.rotationDegrees(anglePartition * remaining + 35));
			ms.mulPose(Axis.XP.rotationDegrees(65));

			// visually stack large counts like a heap on the surface
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
			remaining--;
		}
		ms.popPose();
	}

	/**
	 * Renders the layered fluid body from the interior floor up to {@code levelHeight}
	 * (local y). Non-gas fluids build the bottom layers, gases hang from the top of
	 * the level (the lighter-than-air layer of the "soup"). Returns the top of the
	 * liquid (non-gas) region — the surface items float on.
	 */
	private float renderFluid(ReactorControllerBlockEntity reactor, float x1, float z1, float x2, float z2,
		float levelHeight, PoseStack ms, MultiBufferSource buffer, int light) {
		List<FluidStack> fluids = reactor.getTank().getFluids();
		int total = reactor.getTank().getTotalAmount();
		if (fluids.isEmpty() || total <= 0) {
			return 0;
		}

		// inset 1/32 from the shell walls
		float ix1 = x1 + 1 / 32f;
		float ix2 = x2 - 1 / 32f;
		float iz1 = z1 + 1 / 32f;
		float iz2 = z2 - 1 / 32f;

		int liquidAmount = 0;
		for (FluidStack f : fluids) {
			if (!isLighterThanAir(f)) {
				liquidAmount += f.getAmount();
			}
		}
		float liquidHeight = levelHeight * liquidAmount / total;

		// liquids: bottom-up
		float y = 0;
		for (FluidStack f : fluids) {
			if (isLighterThanAir(f)) {
				continue;
			}
			float h = levelHeight * f.getAmount() / total;
			renderBox(f, ix1, y, iz1, ix2, y + h, iz2, ms, buffer, light);
			y += h;
		}
		// gases: hang from the level top downward
		float gasTop = levelHeight;
		for (FluidStack f : fluids) {
			if (!isLighterThanAir(f)) {
				continue;
			}
			float h = levelHeight * f.getAmount() / total;
			renderBox(f, ix1, gasTop - h, iz1, ix2, gasTop, iz2, ms, buffer, light);
			gasTop -= h;
		}
		return liquidHeight;
	}

	private void renderBox(FluidStack fluid, float x1, float y1, float z1, float x2, float y2, float z2,
		PoseStack ms, MultiBufferSource buffer, int light) {
		// no bottom face (the vessel floor is brick), no gas flipping (we layer gases on top ourselves)
		ForgeCatnipServices.FLUID_RENDERER.renderFluidBox(fluid, x1, y1, z1, x2, y2, z2, buffer, ms, light, false, false);
	}

	private static boolean isLighterThanAir(FluidStack fluid) {
		return fluid.getFluid().getFluidType().isLighterThanAir();
	}

	@Override
	public boolean shouldRenderOffScreen(ReactorControllerBlockEntity reactor) {
		// the interior (fluid + items) can extend up to 7 blocks from the controller
		// block, beyond the default per-block render culling
		return true;
	}
}
