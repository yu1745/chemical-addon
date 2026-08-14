package com.yu1745.chemicaladdon.item;

import java.util.function.Consumer;

import com.yu1745.chemicaladdon.reactor.VesselGaugeItemRenderer;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Block item for the S02/S03 gauge blocks (both dual forms): the item icon
 * draws the block model + the dial needle at its zero position through Forge's
 * dedicated itemstack-BE-renderer mechanism (a {@link BlockEntityWithoutLevelRenderer}).
 *
 * <p>Wiring: the item model JSON opts the item out of regular model rendering with
 * the {@code builtin/entity} parent ({@code BakedModel#isCustomRenderer() == true}),
 * and {@link #initializeClient(Consumer)} supplies the renderer via
 * {@link IClientItemExtensions#getCustomRenderer()} — the same hook Create uses for
 * its wrench / sand paper etc. ({@code WrenchItem#initializeClient}). The renderer
 * itself is created lazily on the first item render: this hook fires during item
 * registration, long before the {@code Minecraft} client instance exists.
 */
public class GaugeBlockItem extends BlockItem {

	public GaugeBlockItem(Block block, Item.Properties properties) {
		super(block, properties);
	}

	@Override
	@OnlyIn(Dist.CLIENT)
	public void initializeClient(Consumer<IClientItemExtensions> consumer) {
		consumer.accept(new IClientItemExtensions() {
			@Override
			public BlockEntityWithoutLevelRenderer getCustomRenderer() {
				return VesselGaugeItemRenderer.instance();
			}
		});
	}
}
