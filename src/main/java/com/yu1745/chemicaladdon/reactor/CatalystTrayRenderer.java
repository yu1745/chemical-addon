package com.yu1745.chemicaladdon.reactor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.yu1745.chemicaladdon.ChemicalAddon;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * B3 visual layer. The shell block remains a normal full vessel-wall cube;
 * this renderer adds the actual metal tray projecting into the vessel and
 * renders the loaded catalyst on its bed. It is visual only: collision,
 * inventory and process capability remain on the shell block entity.
 */
public class CatalystTrayRenderer implements BlockEntityRenderer<CatalystTrayBlockEntity> {

	/** Authored facing south (+Z), extending twelve pixels into the vessel cell. */
	private static final PartialModel INTERNAL_TRAY = PartialModel.of(
		new ResourceLocation(ChemicalAddon.MODID, "block/catalyst_tray_internal"));

	/** Force partial-model registration before ModelEvent.RegisterAdditional. */
	public static void init() {
	}

	public CatalystTrayRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public boolean shouldRenderOffScreen(CatalystTrayBlockEntity be) {
		return true;
	}

	@Override
	public void render(CatalystTrayBlockEntity be, float partialTicks, PoseStack ms,
		MultiBufferSource buffer, int light, int overlay) {
		Direction inward = be.getBlockState().getValue(CatalystTrayBlock.FACING);
		if (inward.getAxis().isVertical()) {
			return; // invalid installs stay a plain shell block and diagnose as misplaced
		}

		int interiorLight = light;
		if (be.getLevel() != null) {
			BlockPos sample = be.getBlockPos().relative(inward);
			interiorLight = LevelRenderer.getLightColor(be.getLevel(), sample);
		}

		ms.pushPose();
		ms.translate(0.5, 0.5, 0.5);
		ms.mulPose(Axis.YP.rotationDegrees(inward.toYRot()));
		ms.translate(-0.5, -0.5, -0.5);
		SuperByteBuffer tray = CachedBuffers.partial(INTERNAL_TRAY, be.getBlockState());
		tray.light(interiorLight).renderInto(ms, buffer.getBuffer(RenderType.solid()));
		ms.popPose();

		ItemStack catalyst = be.getCatalystStack();
		if (catalyst.isEmpty()) {
			return;
		}

		// One representative pile is enough: stack count and remaining batches are
		// reported by goggles, while this world render answers the physical question
		// "is the tray charged?" without opening a GUI.
		ms.pushPose();
		ms.translate(0.5 + inward.getStepX() * 0.82, 0.54, 0.5 + inward.getStepZ() * 0.82);
		ms.mulPose(Axis.YP.rotationDegrees(inward.toYRot()));
		ms.scale(0.72f, 0.72f, 0.72f);
		Minecraft.getInstance().getItemRenderer().renderStatic(catalyst, ItemDisplayContext.GROUND,
			interiorLight, overlay, ms, buffer, be.getLevel(), (int) be.getBlockPos().asLong());
		ms.popPose();
	}
}
