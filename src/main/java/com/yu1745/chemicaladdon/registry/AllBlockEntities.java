package com.yu1745.chemicaladdon.registry;

import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.reactor.ChemicalBrickBlockEntity;
import com.yu1745.chemicaladdon.reactor.DecantHoseBlockEntity;
import com.yu1745.chemicaladdon.reactor.DecantPortBlockEntity;
import com.yu1745.chemicaladdon.reactor.FilterPressBlockEntity;
import com.yu1745.chemicaladdon.reactor.ReactorControllerBlockEntity;
import com.yu1745.chemicaladdon.reactor.SettlingBasinBlockEntity;
import com.yu1745.chemicaladdon.reactor.ThermometerBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class AllBlockEntities {

	private static final DeferredRegister<BlockEntityType<?>> REGISTER =
		DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ChemicalAddon.MODID);

	public static final RegistryObject<BlockEntityType<ChemicalBrickBlockEntity>> CHEMICAL_BRICK =
		REGISTER.register("chemical_brick",
			() -> BlockEntityType.Builder.of(ChemicalBrickBlockEntity::new,
				AllBlocks.CHEMICAL_BRICK.get(), AllBlocks.CHEMICAL_GLASS.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<DecantHoseBlockEntity>> DECANT_HOSE =
		REGISTER.register("decant_hose",
			() -> BlockEntityType.Builder.of(DecantHoseBlockEntity::new, AllBlocks.DECANT_HOSE.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<DecantPortBlockEntity>> DECANT_PORT =
		REGISTER.register("decant_port",
			() -> BlockEntityType.Builder.of(DecantPortBlockEntity::new, AllBlocks.DECANT_PORT.get())
				.build(null));

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

	public static final RegistryObject<BlockEntityType<ThermometerBlockEntity>> THERMOMETER =
		REGISTER.register("thermometer",
			() -> BlockEntityType.Builder.of(ThermometerBlockEntity::new, AllBlocks.THERMOMETER.get())
				.build(null));

	public static void register(IEventBus modEventBus) {
		REGISTER.register(modEventBus);
	}
}
