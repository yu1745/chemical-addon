/*
 * Vendored from Mantle (https://github.com/SlimeKnights/Mantle, 1.20 branch),
 * slimeknights.mantle.client.model.builder.ConnectedModelBuilder, trimmed: extends
 * CustomLoaderBuilder directly (no ColoredModelBuilder / tint support).
 * Copyright (c) SlimeKnights — MIT License. Attribution notice in THIRD_PARTY.md.
 */
package com.yu1745.chemicaladdon.client.connected;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.Direction;
import net.minecraftforge.client.model.generators.CustomLoaderBuilder;
import net.minecraftforge.client.model.generators.ModelBuilder;
import net.minecraftforge.common.data.ExistingFileHelper;

import java.util.EnumSet;
import java.util.Set;

/** Datagen builder for {@link ConnectedModel}. */
public class ConnectedModelBuilder<T extends ModelBuilder<T>> extends CustomLoaderBuilder<T> {
	private final JsonObject connectedTextures = new JsonObject();
	private Set<Direction> sides = null;
	private String predicate = null;

	public ConnectedModelBuilder(T parent, ExistingFileHelper existingFileHelper) {
		super(ConnectedModel.loaderId(), parent, existingFileHelper);
	}

	/**
	 * Makes the given texture connected using the given connection type.
	 * @param name  Name of the texture from the textures list, not the full path
	 * @param type  Connection type, see {@link ConnectedModelRegistry}
	 */
	public ConnectedModelBuilder<T> connected(String name, String type) {
		connectedTextures.addProperty(name, type);
		return this;
	}

	/** Sets the sides of the block that receive connected textures */
	public ConnectedModelBuilder<T> setSides(Direction first, Direction... other) {
		this.sides = EnumSet.of(first, other);
		return this;
	}

	/** Sets the connection predicate, must be registered with the {@link ConnectedModelRegistry} */
	public ConnectedModelBuilder<T> setPredicate(String predicate) {
		this.predicate = predicate;
		return this;
	}

	@Override
	public JsonObject toJson(JsonObject json) {
		json = super.toJson(json);
		JsonObject data = new JsonObject();
		json.add("connection", data);
		data.add("textures", connectedTextures);
		if (sides != null) {
			JsonArray sideArray = new JsonArray();
			for (Direction side : this.sides) {
				sideArray.add(side.getSerializedName());
			}
			data.add("sides", sideArray);
		}
		if (predicate != null) {
			data.addProperty("predicate", predicate);
		}
		return json;
	}
}
