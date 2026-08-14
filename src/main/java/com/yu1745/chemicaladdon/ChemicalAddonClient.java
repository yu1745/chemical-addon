package com.yu1745.chemicaladdon;

import com.yu1745.chemicaladdon.reactor.DecantHoseRenderer;
import com.yu1745.chemicaladdon.reactor.ReactorControllerRenderer;
import com.yu1745.chemicaladdon.reactor.VesselGaugeRenderer;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.registry.AllContainers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.model.DynamicFluidContainerModel;
import net.minecraftforge.registries.ForgeRegistries;

/** Client-only initialisation. */
public class ChemicalAddonClient {

	public static void init() {
		// render the vessel's item buffer + fluid surface inside the hollow interior
		BlockEntityRenderers.register(AllBlockEntities.REACTOR_CONTROLLER.get(), ReactorControllerRenderer::new);
		// render the decant hose's coil + hanging rope (Create hose-pulley look, surface-tracking)
		BlockEntityRenderers.register(AllBlockEntities.DECANT_HOSE.get(), DecantHoseRenderer::new);
		// render the S02/S03 gauge dial needles (both forms: full-cube wall block + thin panel),
		// chasing the synced reading — one renderer for all four block entities
		// (must run before ModelEvent.RegisterAdditional so the gauge_needle partial
		// model gets registered & baked; a bare method reference stays lazy)
		VesselGaugeRenderer.init();
		BlockEntityRenderers.register(AllBlockEntities.THERMOMETER.get(), VesselGaugeRenderer::new);
		BlockEntityRenderers.register(AllBlockEntities.THERMOMETER_PANEL.get(), VesselGaugeRenderer::new);
		BlockEntityRenderers.register(AllBlockEntities.PRESSURE_GAUGE.get(), VesselGaugeRenderer::new);
		BlockEntityRenderers.register(AllBlockEntities.PRESSURE_GAUGE_PANEL.get(), VesselGaugeRenderer::new);

		// per-stack fluid tint for every DynamicFluidContainerModel item: the sample
		// vial (any fluid with NBT) and every species bucket (still sprite + tint).
		DynamicFluidContainerModel.Colors fluidTint = new DynamicFluidContainerModel.Colors();
		Minecraft.getInstance().getItemColors().register(fluidTint, AllContainers.FLUID_VIAL.get());
		for (com.tterrag.registrate.util.entry.ItemEntry<com.yu1745.chemicaladdon.item.SolutionBucketItem> entry
			: AllContainers.SOLUTION_BUCKETS) {
			Minecraft.getInstance().getItemColors().register(fluidTint, entry.get());
		}
		for (com.tterrag.registrate.util.entry.ItemEntry<com.yu1745.chemicaladdon.item.SolutionBucketItem> entry
			: AllContainers.SLURRY_BUCKETS) {
			Minecraft.getInstance().getItemColors().register(fluidTint, entry.get());
		}
		for (Fluid fluid : ForgeRegistries.FLUIDS) {
			ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluid);
			if (id == null || !ChemicalAddon.MODID.equals(id.getNamespace())) {
				continue;
			}
			Item bucket = fluid.getBucket();
			if (bucket != Items.AIR) {
				Minecraft.getInstance().getItemColors().register(fluidTint, bucket);
			}
		}
	}
}
