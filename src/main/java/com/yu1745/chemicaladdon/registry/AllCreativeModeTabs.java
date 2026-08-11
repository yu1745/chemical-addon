package com.yu1745.chemicaladdon.registry;

import com.yu1745.chemicaladdon.ChemicalAddon;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class AllCreativeModeTabs {

	private static final DeferredRegister<CreativeModeTab> REGISTER =
		DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ChemicalAddon.MODID);

	public static final RegistryObject<CreativeModeTab> BASE = REGISTER.register("chemical",
		() -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.chemicaladdon"))
			.withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
			.icon(() -> new ItemStack(AllItems.SODA_ASH.get()))
			.displayItems((params, output) -> {
				for (Item item : BuiltInRegistries.ITEM) {
					ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
					if (ChemicalAddon.MODID.equals(key.getNamespace())) {
						output.accept(item);
					}
				}
			})
			.build());

	public static void register(IEventBus modEventBus) {
		REGISTER.register(modEventBus);
	}
}
