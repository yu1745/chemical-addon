/*
 * Vendored from Mantle (https://github.com/SlimeKnights/Mantle, 1.20 branch),
 * slimeknights.mantle.client.model.util.ModelTextureIteratable (lombok removed).
 * Copyright (c) SlimeKnights — MIT License. Attribution notice in THIRD_PARTY.md.
 */
package com.yu1745.chemicaladdon.client.connected;

import com.mojang.datafixers.util.Either;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.resources.model.Material;
import net.minecraftforge.client.model.geometry.BlockGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

/** Iterable over a block model's texture maps, walking the parent chain. */
public class ModelTextureIteratable implements Iterable<Map<String, Either<Material, String>>> {
	@Nullable
	private final Map<String, Either<Material, String>> startMap;
	@Nullable
	private final BlockModel startModel;

	public ModelTextureIteratable(@Nullable Map<String, Either<Material, String>> startMap, @Nullable BlockModel startModel) {
		this.startMap = startMap;
		this.startModel = startModel;
	}

	public ModelTextureIteratable(BlockModel model) {
		this(null, model);
	}

	public static ModelTextureIteratable of(IGeometryBakingContext owner, SimpleBlockModel fallback) {
		if (owner instanceof BlockGeometryBakingContext blockOwner) {
			return new ModelTextureIteratable(null, blockOwner.owner);
		}
		return new ModelTextureIteratable(fallback.getTextures(), fallback.getParent());
	}

	@Override
	public MapIterator iterator() {
		return new MapIterator(startMap, startModel);
	}

	private static class MapIterator implements Iterator<Map<String, Either<Material, String>>> {
		@Nullable
		private Map<String, Either<Material, String>> initial;
		@Nullable
		private BlockModel model;

		MapIterator(@Nullable Map<String, Either<Material, String>> initial, @Nullable BlockModel model) {
			this.initial = initial;
			this.model = model;
		}

		@Override
		public boolean hasNext() {
			return initial != null || model != null;
		}

		@Override
		public Map<String, Either<Material, String>> next() {
			Map<String, Either<Material, String>> map;
			if (initial != null) {
				map = initial;
				initial = null;
			} else if (model != null) {
				map = model.textureMap;
				model = model.parent;
			} else {
				throw new NoSuchElementException();
			}
			return map;
		}
	}
}
