/*
 * Vendored from Mantle (https://github.com/SlimeKnights/Mantle, 1.20 branch),
 * slimeknights.mantle.client.model.util.SimpleBlockModel, trimmed for our use:
 * access-transformer-dependent code replaced with public API equivalents.
 * Copyright (c) SlimeKnights — MIT License. Attribution notice in THIRD_PARTY.md.
 */
package com.yu1745.chemicaladdon.client.connected;

import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.datafixers.util.Either;
import com.mojang.math.Transformation;
import com.yu1745.chemicaladdon.ChemicalAddon;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.client.RenderTypeGroup;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.QuadTransformers;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;
import net.minecraftforge.client.model.geometry.UnbakedGeometryHelper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Simpler version of {@link BlockModel} for use in an {@link IUnbakedGeometry},
 * as the owner handles most block model properties.
 */
public class SimpleBlockModel implements IUnbakedGeometry<SimpleBlockModel> {
	private static final Logger LOGGER = LoggerFactory.getLogger("chemicaladdon/connected");

	/** Location used for baking dynamic models, name does not matter so just using a constant */
	static final ResourceLocation BAKE_LOCATION = new ResourceLocation(ChemicalAddon.MODID, "dynamic_model_baking");

	/** Parent model location, used to fetch parts and for textures if the owner is not a block model */
	@Nullable
	private ResourceLocation parentLocation;
	/** Model parts for baked model, if empty uses parent parts */
	private final List<BlockElement> parts;
	/** Fallback textures in case the owner does not contain a block model */
	private final Map<String, Either<Material, String>> textures;
	private BlockModel parent;

	public SimpleBlockModel(@Nullable ResourceLocation parentLocation, Map<String, Either<Material, String>> textures, List<BlockElement> parts) {
		this.parts = parts;
		this.textures = textures;
		this.parentLocation = parentLocation;
	}

	public SimpleBlockModel(SimpleBlockModel base) {
		this.parts = base.parts;
		this.textures = base.textures;
		this.parentLocation = base.parentLocation;
		this.parent = base.parent;
	}

	public List<BlockElement> getElements() {
		return parts.isEmpty() && parent != null ? parent.getElements() : parts;
	}

	public Map<String, Either<Material, String>> getTextures() {
		return textures;
	}

	public BlockModel getParent() {
		return parent;
	}

	/* Textures */

	@Override
	public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext owner) {
		// no work if no parent or the parent is fetched already
		if (parent != null || parentLocation == null) {
			return;
		}

		Set<UnbakedModel> chain = Sets.newLinkedHashSet();

		parent = getParentModel(modelGetter, chain, parentLocation, owner.getModelName());
		if (parent == null) {
			parent = getMissing(modelGetter);
			parentLocation = ModelBakery.MISSING_MODEL_LOCATION;
		}

		// loop through each parent, adding in parents
		for (BlockModel link = parent; link.getParentLocation() != null && link.parent == null; link = link.parent) {
			chain.add(link);

			link.parent = getParentModel(modelGetter, chain, link.getParentLocation(), link.name);
			if (link.parent == null) {
				link.parent = getMissing(modelGetter);
			}
		}
	}

	@Nullable
	private static BlockModel getParentModel(Function<ResourceLocation, UnbakedModel> modelGetter, Set<UnbakedModel> chain, ResourceLocation location, String name) {
		UnbakedModel unbaked = modelGetter.apply(location);
		if (unbaked == null) {
			LOGGER.warn("No parent '{}' while loading model '{}'", location, name);
			return null;
		}
		if (chain.contains(unbaked)) {
			LOGGER.warn("Found 'parent' loop while loading model '{}' in chain: {} -> {}", name,
				chain.stream().map(Object::toString).collect(Collectors.joining(" -> ")), location);
			return null;
		}
		if (!(unbaked instanceof BlockModel)) {
			throw new IllegalStateException("BlockModel parent has to be a block model.");
		}
		return (BlockModel) unbaked;
	}

	@Nonnull
	private static BlockModel getMissing(Function<ResourceLocation, UnbakedModel> modelGetter) {
		UnbakedModel model = modelGetter.apply(ModelBakery.MISSING_MODEL_LOCATION);
		if (!(model instanceof BlockModel)) {
			throw new IllegalStateException("Failed to load missing model");
		}
		return (BlockModel) model;
	}

	/* Baking */

	public static SimpleBakedModel.Builder bakedBuilder(IGeometryBakingContext owner, ItemOverrides overrides) {
		return new SimpleBakedModel.Builder(owner.useAmbientOcclusion(), owner.useBlockLight(), owner.isGui3d(), owner.getTransforms(), overrides);
	}

	/**
	 * Bakes a list of block part elements into a model
	 * @param owner         Model configuration
	 * @param elements      Model elements
	 * @param spriteGetter  Sprite getter instance
	 * @param transform     Model transform
	 * @param overrides     Model overrides
	 * @param location      Model bake location
	 * @return Baked model
	 */
	public static BakedModel bakeModel(IGeometryBakingContext owner, List<BlockElement> elements, Function<Material, TextureAtlasSprite> spriteGetter, ModelState transform, ItemOverrides overrides, ResourceLocation location) {
		TextureAtlasSprite particle = spriteGetter.apply(owner.getMaterial("particle"));
		SimpleBakedModel.Builder builder = bakedBuilder(owner, overrides).particle(particle);
		IQuadTransformer quadTransformer = applyTransform(transform, owner.getRootTransform());
		for (BlockElement part : elements) {
			bakePart(builder, owner, part, spriteGetter, transform, quadTransformer, location);
		}
		return builder.build(getRenderTypeGroup(owner));
	}

	private static void bakePart(SimpleBakedModel.Builder builder, IGeometryBakingContext owner, BlockElement part, Function<Material, TextureAtlasSprite> spriteGetter, ModelState transform, IQuadTransformer quadTransformer, ResourceLocation location) {
		for (Direction direction : part.faces.keySet()) {
			BlockElementFace face = part.faces.get(direction);
			String texture = face.texture;
			if (texture.charAt(0) == '#') {
				texture = texture.substring(1);
			}
			TextureAtlasSprite sprite = spriteGetter.apply(owner.getMaterial(texture));
			BakedQuad bakedQuad = BlockModel.bakeFace(part, face, sprite, direction, transform, location);
			quadTransformer.processInPlace(bakedQuad);
			if (face.cullForDirection == null) {
				builder.addUnculledFace(bakedQuad);
			} else {
				builder.addCulledFace(Direction.rotate(transform.getRotation().getMatrix(), face.cullForDirection), bakedQuad);
			}
		}
	}

	public static RenderTypeGroup getRenderTypeGroup(IGeometryBakingContext owner) {
		ResourceLocation renderTypeHint = owner.getRenderTypeHint();
		return renderTypeHint != null ? owner.getRenderType(renderTypeHint) : RenderTypeGroup.EMPTY;
	}

	public static IQuadTransformer applyTransform(ModelState modelState, Transformation transformation) {
		if (transformation.isIdentity()) {
			return QuadTransformers.empty();
		}
		return UnbakedGeometryHelper.applyRootTransform(modelState, transformation);
	}

	@Override
	public BakedModel bake(IGeometryBakingContext owner, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState transform, ItemOverrides overrides, ResourceLocation location) {
		return bakeModel(owner, this.getElements(), spriteGetter, transform, overrides, location);
	}

	/**
	 * Same as {@link #bake}, but passes in sensible defaults for values unneeded in dynamic models.
	 */
	public BakedModel bakeWithElements(IGeometryBakingContext owner, List<BlockElement> elements, ModelState transform) {
		return bakeModel(owner, elements, Material::sprite, transform, ItemOverrides.EMPTY, BAKE_LOCATION);
	}

	/* Deserializing */

	/**
	 * Deserializes a SimpleBlockModel from JSON.
	 */
	public static SimpleBlockModel deserialize(JsonObject json, JsonDeserializationContext context) {
		String parentName = GsonHelper.getAsString(json, "parent", "");
		ResourceLocation parent = parentName.isEmpty() ? null : new ResourceLocation(parentName);

		Map<String, Either<Material, String>> textureMap;
		if (json.has("textures")) {
			ResourceLocation atlas = InventoryMenu.BLOCK_ATLAS;
			JsonObject textures = GsonHelper.getAsJsonObject(json, "textures");
			Map<String, Either<Material, String>> builder = new HashMap<>(textures.size());
			for (Entry<String, JsonElement> entry : textures.entrySet()) {
				builder.put(entry.getKey(), parseTextureLocationOrReference(atlas, entry.getValue().getAsString()));
			}
			textureMap = Map.copyOf(builder);
		} else {
			textureMap = Map.of();
		}

		List<BlockElement> parts;
		if (json.has("elements")) {
			parts = getModelElements(context, GsonHelper.getAsJsonArray(json, "elements"), "elements");
		} else {
			parts = List.of();
		}
		return new SimpleBlockModel(parent, textureMap, parts);
	}

	/** Inline of Mantle's AT-exposed {@code BlockModel.Deserializer.parseTextureLocationOrReference} */
	private static Either<Material, String> parseTextureLocationOrReference(ResourceLocation atlas, String name) {
		if (name.charAt(0) == '#') {
			return Either.right(name.substring(1));
		}
		return Either.left(new Material(atlas, new ResourceLocation(name)));
	}

	public static List<BlockElement> getModelElements(JsonDeserializationContext context, JsonElement element, String name) {
		if (element.isJsonObject()) {
			return List.of((BlockElement) context.deserialize(element.getAsJsonObject(), BlockElement.class));
		}
		if (element.isJsonArray()) {
			JsonArray array = element.getAsJsonArray();
			List<BlockElement> builder = new ArrayList<>(array.size());
			for (JsonElement json : array) {
				builder.add((BlockElement) context.deserialize(json, BlockElement.class));
			}
			return List.copyOf(builder);
		}

		throw new JsonSyntaxException("Missing " + name + ", expected to find a JsonArray or JsonObject");
	}
}
