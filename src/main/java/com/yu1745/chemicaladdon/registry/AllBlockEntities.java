package com.yu1745.chemicaladdon.registry;

import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.reactor.ReactorControllerBlockEntity;

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

	public static void register(IEventBus modEventBus) {
		REGISTER.register(modEventBus);
	}
}
