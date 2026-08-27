package com.yu1745.chemicaladdon.reactor;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Itemstack renderer for the S02/S03 gauge items: the unrotated block model
 * (the dial art) plus the {@link VesselGaugeRenderer} needle parked at its zero
 * position (12 o'clock) — so the inventory icon, the held item and the dropped
 * item all show the needle the same way a fresh gauge does in-world.
 *
 * <p>Forge's dedicated mechanism for rendering BE-style content on an item
 * stack: the item model uses the {@code builtin/entity} parent, which makes the
 * baked model report {@code isCustomRenderer() == true}; {@code ItemRenderer}
 * then hands the whole render off to the {@code IClientItemExtensions}
 * supplied by {@link com.yu1745.chemicaladdon.item.GaugeBlockItem#initializeClient}.
 *
 * <p>When this method runs, {@code ItemRenderer} has already applied the item
 * model's display transform (identity for {@code builtin/entity}, matching the
 * old block-parented item model) and the −½/−½/−½ centring, so the pose is the
 * authored block space — the same space the needle pose lives in. The dial art
 * visible to the GUI camera sits on the +Z face of the authored model (the
 * unrotated panel plate hangs at z ∈ [14,16]; the cube bakes the art onto every
 * face), so the needle is drawn on the SOUTH face at {@code dialOffset = +½},
 * one pixel proud of the dial plane.
 */
public class VesselGaugeItemRenderer extends BlockEntityWithoutLevelRenderer {

	/** Lazy holder: {@link #instance()} is first called by the item render path,
	 *  by which time the Minecraft client (dispatcher + entity models) exists —
	 *  {@code initializeClient} fires during registration, far too early to
	 *  touch {@code Minecraft.getInstance()}. */
	private static final class Holder {
		private static final VesselGaugeItemRenderer INSTANCE = new VesselGaugeItemRenderer();
	}

	private VesselGaugeItemRenderer() {
		super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
	}

	public static VesselGaugeItemRenderer instance() {
		return Holder.INSTANCE;
	}

	@Override
	public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack ms, MultiBufferSource buffer,
		int light, int overlay) {
		if (!(stack.getItem() instanceof BlockItem blockItem)) {
			return;
		}
		Block block = blockItem.getBlock();
		int tint = needleTintOf(block);
		if (tint == -1) {
			return; // not a vessel gauge item
		}

		// Draw the unrotated block model exactly like the old parented block-item
		// icon did (same render-pass / render-type loop as ItemRenderer#render).
		ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
		BakedModel dial = Minecraft.getInstance().getBlockRenderer().getBlockModel(block.defaultBlockState());
		for (RenderType type : dial.getRenderTypes(stack, false)) {
			itemRenderer.renderModelLists(dial, stack, light, overlay, ms,
				ItemRenderer.getFoilBufferDirect(buffer, type, true, stack.hasFoil()));
		}

		// The needle, at rest (0° = 12 o'clock), in the gauge's dial-art colour.
		VesselGaugeRenderer.renderNeedle(ms, buffer, block.defaultBlockState(), Direction.SOUTH, 0.5f, 0.0f, tint,
			light);
	}

	/** The resting needle tint of the item's gauge form, or −1 when the block is
	 *  not a vessel gauge (values mirror the block entities' {@code needleTint()}). */
	private static int needleTintOf(Block block) {
		if (block instanceof ThermometerBlock || block instanceof ThermometerPanelBlock) {
			return 0xFFC42C2C; // the thermometer dial art's red needle
		}
		if (block instanceof PressureGaugeBlock || block instanceof PressureGaugePanelBlock) {
			return 0xFF486CBC; // the pressure dial art's blue needle
		}
		if (block instanceof LiquidLevelGaugeBlock || block instanceof LiquidLevelGaugePanelBlock) {
			return 0xFF3CA0BE; // S11 liquid-level dial's cyan needle
		}
		return -1;
	}
}
