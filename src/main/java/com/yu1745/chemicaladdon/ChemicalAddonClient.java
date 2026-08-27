package com.yu1745.chemicaladdon;

import com.yu1745.chemicaladdon.client.connected.ConnectedModel;
import com.yu1745.chemicaladdon.reactor.CatalystTrayRenderer;
import com.yu1745.chemicaladdon.reactor.DecantHoseRenderer;
import com.yu1745.chemicaladdon.reactor.ReactorControllerRenderer;
import com.yu1745.chemicaladdon.reactor.StirringHeadRenderer;
import com.yu1745.chemicaladdon.reactor.VesselGaugeRenderer;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.registry.AllContainers;
import com.yu1745.chemicaladdon.registry.AllItems;

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

	/** Registers the connected-texture geometry loader (chemicaladdon:connected) on the mod bus. */
	public static void registerGeometryLoaders(net.minecraftforge.client.event.ModelEvent.RegisterGeometryLoaders event) {
		event.register("connected", ConnectedModel.LOADER);
	}

	public static void init() {
		// render the vessel's item buffer + fluid surface inside the hollow interior
		BlockEntityRenderers.register(AllBlockEntities.REACTOR_CONTROLLER.get(), ReactorControllerRenderer::new);
		// render the decant hose's coil + hanging rope (Create hose-pulley look, surface-tracking)
		BlockEntityRenderers.register(AllBlockEntities.DECANT_HOSE.get(), DecantHoseRenderer::new);
		// B1 stirring head visuals: dynamic shaft + enlarged impeller hanging from the
		// roof base into the vessel (kinetic rotation, liquid-tracking depth) — must
		// init before ModelEvent.RegisterAdditional so the shaft/impeller partials
		// bake (same clinit-forcing pattern as VesselGaugeRenderer.init)
		StirringHeadRenderer.init();
		BlockEntityRenderers.register(AllBlockEntities.STIRRING_HEAD.get(), StirringHeadRenderer::new);
		// B3 catalyst tray visuals: a metal shelf projects into the vessel and the
		// loaded catalyst item is visible on the bed (world feedback, no GUI).
		CatalystTrayRenderer.init();
		BlockEntityRenderers.register(AllBlockEntities.CATALYST_TRAY.get(), CatalystTrayRenderer::new);
		// render the S02/S03 gauge dial needles (both forms: full-cube wall block + thin panel),
		// chasing the synced reading — one renderer for all four block entities
		// (must run before ModelEvent.RegisterAdditional so the gauge_needle partial
		// model gets registered & baked; a bare method reference stays lazy)
		VesselGaugeRenderer.init();
		BlockEntityRenderers.register(AllBlockEntities.THERMOMETER.get(), VesselGaugeRenderer::new);
		BlockEntityRenderers.register(AllBlockEntities.THERMOMETER_PANEL.get(), VesselGaugeRenderer::new);
		BlockEntityRenderers.register(AllBlockEntities.PRESSURE_GAUGE.get(), VesselGaugeRenderer::new);
		BlockEntityRenderers.register(AllBlockEntities.PRESSURE_GAUGE_PANEL.get(), VesselGaugeRenderer::new);
		BlockEntityRenderers.register(AllBlockEntities.CONDUCTIVITY_GAUGE.get(), VesselGaugeRenderer::new);
		BlockEntityRenderers.register(AllBlockEntities.CONDUCTIVITY_GAUGE_PANEL.get(), VesselGaugeRenderer::new);
		// U17 gauge trio: pH (S16, fixed center-zero dial), Baumé (S04), turbidity (S17, 4-bin)
		BlockEntityRenderers.register(AllBlockEntities.PH_GAUGE.get(), VesselGaugeRenderer::new);
		BlockEntityRenderers.register(AllBlockEntities.PH_GAUGE_PANEL.get(), VesselGaugeRenderer::new);
		BlockEntityRenderers.register(AllBlockEntities.BAUME_GAUGE.get(), VesselGaugeRenderer::new);
		BlockEntityRenderers.register(AllBlockEntities.BAUME_GAUGE_PANEL.get(), VesselGaugeRenderer::new);
		BlockEntityRenderers.register(AllBlockEntities.TURBIDITY_GAUGE.get(), VesselGaugeRenderer::new);
		BlockEntityRenderers.register(AllBlockEntities.TURBIDITY_GAUGE_PANEL.get(), VesselGaugeRenderer::new);
		BlockEntityRenderers.register(AllBlockEntities.LIQUID_LEVEL_GAUGE.get(), VesselGaugeRenderer::new);
		BlockEntityRenderers.register(AllBlockEntities.LIQUID_LEVEL_GAUGE_PANEL.get(), VesselGaugeRenderer::new);
		// B4 metering inlet: plain SmartBlockEntityRenderer — renders the
		// world-scroll dose value box on the outward face (no other visuals)
		BlockEntityRenderers.register(AllBlockEntities.METERING_INLET.get(),
			com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer::new);

		// per-stack fluid tint for every DynamicFluidContainerModel item: the sample
		// vial (any fluid with NBT) and every species bucket (still sprite + tint).
		DynamicFluidContainerModel.Colors fluidTint = new DynamicFluidContainerModel.Colors();
		Minecraft.getInstance().getItemColors().register(fluidTint, AllContainers.FLUID_VIAL.get());
		// the mixed residue's tint is a blend of its NBT composition (the one
		// physically observable signal it carries — plans/03 §12)
		Minecraft.getInstance().getItemColors().register(
			(stack, tintIndex) -> com.yu1745.chemicaladdon.item.MixedResidueItem.colorOf(stack),
			AllItems.MIXED_RESIDUE.get());
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
