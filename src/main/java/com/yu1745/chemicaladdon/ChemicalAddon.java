package com.yu1745.chemicaladdon;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.yu1745.chemicaladdon.composition.SpeciesManager;
import com.yu1745.chemicaladdon.network.AssaySyncPacket;
import com.yu1745.chemicaladdon.recipe.AllRecipeTypes;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.registry.AllBlocks;
import com.yu1745.chemicaladdon.registry.AllContainers;
import com.yu1745.chemicaladdon.registry.AllCreativeModeTabs;
import com.yu1745.chemicaladdon.registry.AllFluids;
import com.yu1745.chemicaladdon.registry.AllItems;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import org.slf4j.Logger;

@Mod(ChemicalAddon.MODID)
public class ChemicalAddon {

	public static final String MODID = "chemicaladdon";
	public static final Logger LOGGER = LogUtils.getLogger();

	/** Block tag of every block that can form a vessel shell (brick, glass, ... — Tinkers seared-series pattern). */
	public static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> VESSEL_WALLS =
		net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
			new net.minecraft.resources.ResourceLocation(MODID, "vessel_walls"));

	/** Client-side: whether the goggles HUD shows the dev assay (ion/fluid) breakdown. */
	public static boolean ASSAY_ON = false;

	/** Server-side: players who have toggled the assay overlay on. */
	private static final Set<UUID> ASSAY_PLAYERS = new HashSet<>();

	private static final SimpleChannel ASSAY_CHANNEL = NetworkRegistry.newSimpleChannel(
		new net.minecraft.resources.ResourceLocation(MODID, "assay"), () -> "1", s -> true, s -> true);

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
		// eager preload of the built-in species so startup consumers (creative tab,
		// JEI) can resolve species before a world's datapack reload runs
		SpeciesManager.loadBuiltin();

		// dev assay overlay network channel
		ASSAY_CHANNEL.messageBuilder(AssaySyncPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
			.encoder(AssaySyncPacket::encode)
			.decoder(AssaySyncPacket::decode)
			.consumerMainThread(AssaySyncPacket::handle)
			.add();

		// /chemicaladdon assay (and /ca assay) — creative/op only, toggles the assay overlay
		MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> registerAssayCommand(event.getDispatcher()));

		// re-sync the assay state when a player joins
		MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
			if (event.getEntity() instanceof ServerPlayer sp) {
				ASSAY_CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
					new AssaySyncPacket(ASSAY_PLAYERS.contains(sp.getUUID())));
			}
		});

		LOGGER.info("Chemical Addon initialised");
	}

	private static void registerAssayCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("chemicaladdon")
			.then(Commands.literal("assay").executes(ctx -> toggleAssay(ctx.getSource()))));
		dispatcher.register(Commands.literal("ca")
			.then(Commands.literal("assay").executes(ctx -> toggleAssay(ctx.getSource()))));
	}

	private static int toggleAssay(CommandSourceStack source) {
		if (!(source.getEntity() instanceof ServerPlayer player)) {
			source.sendFailure(Component.literal("Only players can run this command"));
			return 0;
		}
		if (!player.isCreative() && !player.hasPermissions(2)) {
			source.sendFailure(Component.literal("Only creative or operators can toggle the assay overlay"));
			return 0;
		}
		UUID id = player.getUUID();
		boolean on = !ASSAY_PLAYERS.contains(id);
		if (on) {
			ASSAY_PLAYERS.add(id);
		} else {
			ASSAY_PLAYERS.remove(id);
		}
		ASSAY_CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new AssaySyncPacket(on));
		source.sendSuccess(() -> Component.literal("Assay overlay: " + (on ? "ON" : "OFF")), false);
		return 1;
	}

	public static CreateRegistrate registrate() {
		return REGISTRATE;
	}
}
