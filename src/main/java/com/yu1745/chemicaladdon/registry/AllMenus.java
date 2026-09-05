package com.yu1745.chemicaladdon.registry;

import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.control.PlcMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class AllMenus {
	private static final DeferredRegister<MenuType<?>> REGISTER=DeferredRegister.create(Registries.MENU,ChemicalAddon.MODID);
	public static final RegistryObject<MenuType<PlcMenu>> PLC=REGISTER.register("plc",()->IForgeMenuType.create(PlcMenu::client));
	private AllMenus(){}
	public static void register(IEventBus bus){REGISTER.register(bus);}
}
