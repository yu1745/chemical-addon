package com.yu1745.chemicaladdon.registry;

import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.reactor.FilterPressBlockEntity;
import com.yu1745.chemicaladdon.reactor.ReactorControllerBlockEntity;
import com.yu1745.chemicaladdon.reactor.SettlingBasinBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class AllBlockEntities {

	private static final DeferredRegister<BlockEntityType<?>> REGISTER =
		DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ChemicalAddon.MODID);

	public static final RegistryObject<BlockEntityType<ReactorControllerBlockEntity>> REACTOR_CONTROLLER =
		REGISTER.register("reactor_controller",
			() -> BlockEntityType.Builder.of(ReactorControllerBlockEntity::new, AllBlocks.REACTOR_CONTROLLER.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<FilterPressBlockEntity>> FILTER_PRESS =
		REGISTER.register("filter_press",
			() -> BlockEntityType.Builder.of(FilterPressBlockEntity::new, AllBlocks.FILTER_PRESS.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<SettlingBasinBlockEntity>> SETTLING_BASIN =
		REGISTER.register("settling_basin",
			() -> BlockEntityType.Builder.of(SettlingBasinBlockEntity::new, AllBlocks.SETTLING_BASIN.get())
				.build(null));

	public static void register(IEventBus modEventBus) {
		REGISTER.register(modEventBus);
	}
}
