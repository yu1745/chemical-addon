package com.yu1745.chemicaladdon.registry;

import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.reactor.ReactorMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class AllMenuTypes {

	private static final DeferredRegister<MenuType<?>> REGISTER =
		DeferredRegister.create(Registries.MENU, ChemicalAddon.MODID);

	public static final RegistryObject<MenuType<ReactorMenu>> REACTOR =
		REGISTER.register("reactor", () -> IForgeMenuType.create(ReactorMenu::fromNetwork));

	public static void register(IEventBus modEventBus) {
		REGISTER.register(modEventBus);
	}
}
