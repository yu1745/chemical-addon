package com.yu1745.chemicaladdon;

import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.yu1745.chemicaladdon.composition.SpeciesManager;
import com.yu1745.chemicaladdon.recipe.AllRecipeTypes;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.registry.AllBlocks;
import com.yu1745.chemicaladdon.registry.AllContainers;
import com.yu1745.chemicaladdon.registry.AllCreativeModeTabs;
import com.yu1745.chemicaladdon.registry.AllFluids;
import com.yu1745.chemicaladdon.registry.AllItems;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import org.slf4j.Logger;

@Mod(ChemicalAddon.MODID)
public class ChemicalAddon {

	public static final String MODID = "chemicaladdon";
	public static final Logger LOGGER = LogUtils.getLogger();

	/** Block tag of every block that can form a vessel shell (brick, glass, ... — Tinkers seared-series pattern). */
	public static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> VESSEL_WALLS =
		net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
			new net.minecraft.resources.ResourceLocation(MODID, "vessel_walls"));

	private static final CreateRegistrate REGISTRATE = CreateRegistrate.create(MODID);

	public ChemicalAddon() {
		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

		AllCreativeModeTabs.register(modBus);
		AllRecipeTypes.register(modBus);
		AllBlockEntities.register(modBus);
		AllFluids.register();
		AllItems.register();
		AllBlocks.register();
		AllContainers.register();
		REGISTRATE.registerEventListeners(modBus);

		// datagen: extra English lang keys (after Registrate's own listener)
		modBus.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST, ChemicalDataGen::gatherData);

		// client: block entity renderers (runs after registries are populated)
		modBus.addListener((FMLClientSetupEvent event) -> ChemicalAddonClient.init());

		// Datapack-driven species definitions (composition system, M0 skeleton)
		MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(SpeciesManager.RELOADER));

		LOGGER.info("Chemical Addon initialised");
	}

	public static CreateRegistrate registrate() {
		return REGISTRATE;
	}
}
