package com.yu1745.chemicaladdon.reactor;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yu1745.chemicaladdon.fluid.Mixture;
import com.yu1745.chemicaladdon.fluid.Miscibility;
import com.yu1745.chemicaladdon.vessel.VesselBlockEntity;

import net.createmod.catnip.platform.ForgeCatnipServices;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

/** Shared vessel contents pass: sediment at the floor, liquids above it and gas at the top. */
public final class VesselFluidRenderer {

	private VesselFluidRenderer() {}

	/** @return top of the non-gas region, relative to the interior floor. */
	public static float render(VesselBlockEntity vessel, float x1, float z1, float x2, float z2,
			float levelHeight, PoseStack ms, MultiBufferSource buffer, int light) {
		var fluids = vessel.getTank().getFluids();
		int total = vessel.getTank().getTotalAmount();
		if (fluids.isEmpty() || total <= 0) return 0;

		float ix1 = x1 + 1 / 32f, ix2 = x2 - 1 / 32f;
		float iz1 = z1 + 1 / 32f, iz2 = z2 - 1 / 32f;
		Map<ResourceLocation, Long> sediment = new LinkedHashMap<>();
		int sedimentAmount = 0;
		for (FluidStack fluid : fluids) {
			if (!Mixture.isMixture(fluid)) continue;
			for (var entry : Mixture.getSediment(fluid).entrySet())
				sediment.merge(entry.getKey(), entry.getValue(), Long::sum);
			for (int amount : Mixture.deriveSedimentAmounts(fluid).values()) sedimentAmount += amount;
		}
		float sedimentHeight = levelHeight * sedimentAmount / total;
		if (sedimentHeight > 1 / 1024f) {
			renderTinted(Mixture.blendColorLong(Map.of(), Map.of(), sediment),
				ix1, 0, iz1, ix2, sedimentHeight, iz2, ms, buffer, light);
		}

		float y = sedimentHeight;
		for (FluidStack fluid : fluids) {
			if (Miscibility.isGas(fluid)) continue;
			int amount = fluid.getAmount();
			if (Mixture.isMixture(fluid))
				for (int solid : Mixture.deriveSedimentAmounts(fluid).values()) amount -= solid;
			float height = levelHeight * amount / total;
			renderFluid(fluid, ix1, y, iz1, ix2, y + height, iz2, ms, buffer, light);
			y += height;
		}
		float gasTop = levelHeight;
		for (FluidStack fluid : fluids) {
			if (!Miscibility.isGas(fluid)) continue;
			float height = levelHeight * fluid.getAmount() / total;
			renderFluid(fluid, ix1, gasTop - height, iz1, ix2, gasTop, iz2, ms, buffer, light);
			gasTop -= height;
		}
		return y;
	}

	private static void renderFluid(FluidStack fluid, float x1, float y1, float z1, float x2, float y2,
			float z2, PoseStack ms, MultiBufferSource buffer, int light) {
		FluidStack rendered;
		if (fluid.getFluid() == Fluids.WATER) {
			rendered = new FluidStack(Mixture.fluid(), fluid.getAmount());
		} else if (Mixture.isMixture(fluid) && !Mixture.getSuspended(fluid).isEmpty()) {
			int color = Mixture.blendColorLong(Map.of(), Map.of(), Mixture.getSuspended(fluid)) | 0xFF000000;
			rendered = new FluidStack(Mixture.fluid(), fluid.getAmount());
			rendered.getOrCreateTag().putInt(Mixture.KEY_COLOR, color);
		} else {
			rendered = fluid;
		}
		ForgeCatnipServices.FLUID_RENDERER.renderFluidBox(rendered, x1, y1, z1, x2, y2, z2,
			buffer, ms, light, false, false);
	}

	private static void renderTinted(int color, float x1, float y1, float z1, float x2, float y2,
			float z2, PoseStack ms, MultiBufferSource buffer, int light) {
		FluidStack rendered = new FluidStack(Mixture.fluid(), 1000);
		rendered.getOrCreateTag().putInt(Mixture.KEY_COLOR, color);
		ForgeCatnipServices.FLUID_RENDERER.renderFluidBox(rendered, x1, y1, z1, x2, y2, z2,
			buffer, ms, light, false, false);
	}
}
