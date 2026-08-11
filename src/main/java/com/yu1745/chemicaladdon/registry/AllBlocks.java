package com.yu1745.chemicaladdon.registry;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.reactor.ChemicalBrickBlock;
import com.yu1745.chemicaladdon.reactor.ReactorControllerBlock;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class AllBlocks {

	public static final CreateRegistrate REGISTRATE = ChemicalAddon.registrate();

	public static final BlockEntry<ChemicalBrickBlock> CHEMICAL_BRICK =
		REGISTRATE.block("chemical_brick", ChemicalBrickBlock::new)
			.properties(p -> p.mapColor(MapColor.STONE).strength(2.0f, 6.0f))
			.simpleItem()
			.register();

	public static final BlockEntry<ReactorControllerBlock> REACTOR_CONTROLLER =
		REGISTRATE.block("reactor_controller", ReactorControllerBlock::new)
			.properties(p -> p.mapColor(MapColor.METAL).strength(3.0f, 6.0f))
			.simpleItem()
			.register();

	public static void register() {
	}
}
