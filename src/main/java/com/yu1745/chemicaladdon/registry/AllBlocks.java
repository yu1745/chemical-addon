package com.yu1745.chemicaladdon.registry;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.reactor.ChemicalBrickBlock;
import com.yu1745.chemicaladdon.reactor.ChemicalGlassBlock;
import com.yu1745.chemicaladdon.reactor.FilterPressBlock;
import com.yu1745.chemicaladdon.reactor.ReactorControllerBlock;
import com.yu1745.chemicaladdon.reactor.SettlingBasinBlockEntity.SettlingBasinBlock;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class AllBlocks {

	public static final CreateRegistrate REGISTRATE = ChemicalAddon.registrate();

	/**
	 * Opaque shell block of the vessels (Tinkers "seared bricks" of the series).
	 * The multiblock validates any block in the {@code chemicaladdon:vessel_walls}
	 * tag, so brick is solid and {@link #CHEMICAL_GLASS} is transparent — the
	 * player chooses where the shell is see-through.
	 */
	public static final BlockEntry<ChemicalBrickBlock> CHEMICAL_BRICK =
		REGISTRATE.block("chemical_brick", ChemicalBrickBlock::new)
			.properties(p -> p.mapColor(MapColor.STONE).strength(2.0f, 6.0f))
			.lang("Chemical Brick")
			.simpleItem()
			.register();

	/** Transparent shell block ("seared glass"): same structural series, lets you watch the fluid. */
	public static final BlockEntry<ChemicalGlassBlock> CHEMICAL_GLASS =
		REGISTRATE.block("chemical_glass", ChemicalGlassBlock::new)
			.properties(p -> p.mapColor(MapColor.NONE).strength(0.3f)
				.noOcclusion()
				.isRedstoneConductor((a, b, c) -> false)
				.isSuffocating((a, b, c) -> false)
				.isViewBlocking((a, b, c) -> false))
			.lang("Chemical Glass")
			.blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(),
				prov.models().cubeAll(ctx.getName(), prov.models().mcLoc("block/glass"))))
			.addLayer(() -> RenderType::cutoutMipped)
			.simpleItem()
			.register();

	public static final BlockEntry<ReactorControllerBlock> REACTOR_CONTROLLER =
		REGISTRATE.block("reactor_controller", ReactorControllerBlock::new)
			.properties(p -> p.mapColor(MapColor.METAL).strength(3.0f, 6.0f))
			.lang("Reactor Controller")
			.blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(), prov.models()
				.cubeAll(ctx.getName(), prov.modLoc("block/" + ctx.getName()))))
			.simpleItem()
			.register();

	public static final BlockEntry<FilterPressBlock> FILTER_PRESS =
		REGISTRATE.block("filter_press", FilterPressBlock::new)
			.properties(p -> p.mapColor(MapColor.METAL).strength(3.0f, 6.0f))
			.lang("Filter Press")
			.blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(), prov.models()
				.cubeAll(ctx.getName(), prov.modLoc("block/" + ctx.getName()))))
			.simpleItem()
			.register();

	public static final BlockEntry<SettlingBasinBlock> SETTLING_BASIN =
		REGISTRATE.block("settling_basin", SettlingBasinBlock::new)
			.properties(p -> p.mapColor(MapColor.METAL).strength(3.0f, 6.0f))
			.lang("Settling Basin")
			.blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(), prov.models()
				.cubeAll(ctx.getName(), prov.modLoc("block/" + ctx.getName()))))
			.simpleItem()
			.register();

	public static void register() {
	}
}
