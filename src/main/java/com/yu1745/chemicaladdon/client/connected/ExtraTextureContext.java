/*
 * Vendored from Mantle (https://github.com/SlimeKnights/Mantle, 1.20 branch),
 * slimeknights.mantle.client.model.util.ExtraTextureContext.
 * Copyright (c) SlimeKnights — MIT License. Attribution notice in THIRD_PARTY.md.
 */
package com.yu1745.chemicaladdon.client.connected;

import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;

import java.util.Map;

/**
 * Model configuration wrapper to add in an extra set of textures.
 */
public class ExtraTextureContext extends GeometryContextWrapper {
	private final Map<String, Material> textures;

	/**
	 * Creates a new wrapper: any textures in this map take precedence over those in the base configuration.
	 */
	public ExtraTextureContext(IGeometryBakingContext base, Map<String, Material> textures) {
		super(base);
		this.textures = textures;
	}

	public ExtraTextureContext(IGeometryBakingContext base, String name, ResourceLocation texture) {
		super(base);
		this.textures = Map.of(name, new Material(InventoryMenu.BLOCK_ATLAS, texture));
	}

	@Override
	public Material getMaterial(String name) {
		Material connected = textures.get(name);
		if (connected != null) {
			return connected;
		}
		return super.getMaterial(name);
	}

	@Override
	public boolean hasMaterial(String name) {
		return textures.containsKey(name) || super.hasMaterial(name);
	}
}
