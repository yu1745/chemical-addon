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
 * bobbing. This is the in-world replacement for the removed item-slot GUI and
 * the visual half of the physical<->data spill/absorb loop.
 *
 * Fluid rendering reuses Create's catnip FluidRenderHelper
 * ({@code ForgeCatnipServices.FLUID_RENDERER.renderFluidBox}) — the same call
 * Create's own FluidTank/Basin renderers use. The fluid surface is animated by
 * a client-side LerpedFloat on the block entity chasing the synced fill state.
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

		// light is sampled at the vessel interior (the controller block sits in
		// the wall and is nearly unlit — contents must use the light of the spot
		// they actually occupy: open vessels get full skylight, sealed ones go dark)
		BlockPos centerPos = reactor.getBlockPos().offset(inward.getStepX(), height / 2, inward.getStepZ());
		light = LightTexture.pack(reactor.getLevel().getBrightness(LightLayer.BLOCK, centerPos),
			reactor.getLevel().getBrightness(LightLayer.SKY, centerPos));

		float renderTime = AnimationTickHolder.getRenderTime(reactor.getLevel());

		// --- fluid pass: layered soup surface rising from the interior floor ---
		// all coordinates below are in the controller block's local space; the
		// interior air column spans local y in [0, height], x/z one block inward
		float levelHeight = reactor.getRenderedLevel(partialTicks) * height;
		float liquidSurface = 0;
		if (levelHeight > 1 / 1024f) {
			liquidSurface = renderFluid(reactor, inward, levelHeight, ms, buffer, light);
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

		// item ring center sits on the liquid surface (below any gas layer);
		// with no fluid, items rest near the floor
		float surfaceY = Math.max(liquidSurface, 0.125f);

		ms.pushPose();
		ms.translate(0.5 + inward.getStepX(), surfaceY - 0.1f, 0.5 + inward.getStepZ());

		// slow whole-ring rotation (driven by render time, no BE tick needed)
		float angle = (renderTime * 4) % 360;
		ms.mulPose(Axis.YP.rotationDegrees(angle));

		RandomSource r = RandomSource.create(reactor.getBlockPos().hashCode());
		Vec3 baseVector = itemCount == 1 ? new Vec3(0, 0, 0) : new Vec3(0.25, 0, 0);
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
	private float renderFluid(ReactorControllerBlockEntity reactor, Direction inward, float levelHeight,
		PoseStack ms, MultiBufferSource buffer, int light) {
		List<FluidStack> fluids = reactor.getTank().getFluids();
		int total = reactor.getTank().getTotalAmount();
		if (fluids.isEmpty() || total <= 0) {
			return 0;
		}

		// interior is a single column: inset 1/32 from the brick walls
		float x1 = inward.getStepX() + 1 / 32f;
		float x2 = inward.getStepX() + 1 - 1 / 32f;
		float z1 = inward.getStepZ() + 1 / 32f;
		float z2 = inward.getStepZ() + 1 - 1 / 32f;

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
			renderBox(f, x1, y, z1, x2, y + h, z2, ms, buffer, light);
			y += h;
		}
		// gases: hang from the level top downward
		float gasTop = levelHeight;
		for (FluidStack f : fluids) {
			if (!isLighterThanAir(f)) {
				continue;
			}
			float h = levelHeight * f.getAmount() / total;
			renderBox(f, x1, gasTop - h, z1, x2, gasTop, z2, ms, buffer, light);
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
		// the interior (fluid + items) can extend up to MAX_HEIGHT blocks from the
		// controller block, beyond the default per-block render culling
		return true;
	}
}
