package com.yu1745.chemicaladdon.reactor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.yu1745.chemicaladdon.ChemicalAddon;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Create-style moving pressure platen spanning from the drive into the plate pack. */
public class FilterPressRenderer extends KineticBlockEntityRenderer<FilterPressBlockEntity> {
	private static final PartialModel PLATEN = PartialModel.of(
		new ResourceLocation(ChemicalAddon.MODID, "block/filter_press/platen"));
	public static void init() {}
	public FilterPressRenderer(BlockEntityRendererProvider.Context context) { super(context); }

	@Override protected void renderSafe(FilterPressBlockEntity be, float partialTicks, PoseStack pose,
		MultiBufferSource buffer, int light, int overlay) {
		super.renderSafe(be, partialTicks, pose, buffer, light, overlay);
		if (!be.isStructureValid()) return;
		Direction facing = be.getBlockState().getValue(FilterPressBlock.HORIZONTAL_FACING);
		float extension = .18f + .42f * Mth.sin(be.getProgress() * Mth.PI);
		var normal = facing.getNormal();
		// The head crosses the controller's block boundary.  Sampling only the
		// controller used its enclosed light value and made the moving copper much
		// darker than the open plate pack; use the brighter of both occupied cells.
		int headLight = Math.max(light, LevelRenderer.getLightColor(be.getLevel(),
			be.getBlockState(), be.getBlockPos().relative(facing)));
		CachedBuffers.partialFacing(PLATEN, be.getBlockState(), facing)
			.translate(normal.getX() * extension, 0, normal.getZ() * extension)
			.light(headLight).renderInto(pose, buffer.getBuffer(RenderType.solid()));
	}

	@Override public boolean shouldRenderOffScreen(FilterPressBlockEntity be) { return true; }
}
