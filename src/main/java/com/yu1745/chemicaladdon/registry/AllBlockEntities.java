package com.yu1745.chemicaladdon.registry;

import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.reactor.ChemicalBrickBlockEntity;
import com.yu1745.chemicaladdon.reactor.ConductivityGaugeBlockEntity;
import com.yu1745.chemicaladdon.reactor.ConductivityGaugePanelBlockEntity;
import com.yu1745.chemicaladdon.reactor.DecantHoseBlockEntity;
import com.yu1745.chemicaladdon.reactor.DecantPortBlockEntity;
import com.yu1745.chemicaladdon.reactor.FilterPressBlockEntity;
import com.yu1745.chemicaladdon.reactor.PressureGaugeBlockEntity;
import com.yu1745.chemicaladdon.reactor.PressureGaugePanelBlockEntity;
import com.yu1745.chemicaladdon.reactor.ReactorControllerBlockEntity;
import com.yu1745.chemicaladdon.reactor.SettlingBasinBlockEntity;
import com.yu1745.chemicaladdon.reactor.ThermometerBlockEntity;
import com.yu1745.chemicaladdon.reactor.ThermometerPanelBlockEntity;

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

	public static final RegistryObject<BlockEntityType<ThermometerPanelBlockEntity>> THERMOMETER_PANEL =
		REGISTER.register("thermometer_panel",
			() -> BlockEntityType.Builder.of(ThermometerPanelBlockEntity::new, AllBlocks.THERMOMETER_PANEL.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<PressureGaugeBlockEntity>> PRESSURE_GAUGE =
		REGISTER.register("pressure_gauge",
			() -> BlockEntityType.Builder.of(PressureGaugeBlockEntity::new, AllBlocks.PRESSURE_GAUGE.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<PressureGaugePanelBlockEntity>> PRESSURE_GAUGE_PANEL =
		REGISTER.register("pressure_gauge_panel",
			() -> BlockEntityType.Builder.of(PressureGaugePanelBlockEntity::new, AllBlocks.PRESSURE_GAUGE_PANEL.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<ConductivityGaugeBlockEntity>> CONDUCTIVITY_GAUGE =
		REGISTER.register("conductivity_gauge",
			() -> BlockEntityType.Builder.of(ConductivityGaugeBlockEntity::new, AllBlocks.CONDUCTIVITY_GAUGE.get())
				.build(null));

	public static final RegistryObject<BlockEntityType<ConductivityGaugePanelBlockEntity>> CONDUCTIVITY_GAUGE_PANEL =
		REGISTER.register("conductivity_gauge_panel",
			() -> BlockEntityType.Builder.of(ConductivityGaugePanelBlockEntity::new,
				AllBlocks.CONDUCTIVITY_GAUGE_PANEL.get())
				.build(null));

	public static void register(IEventBus modEventBus) {
		REGISTER.register(modEventBus);
	}
}
