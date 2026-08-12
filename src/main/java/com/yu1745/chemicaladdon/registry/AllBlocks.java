package com.yu1745.chemicaladdon.registry;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.providers.RegistrateBlockstateProvider;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.reactor.ChemicalBrickBlock;
import com.yu1745.chemicaladdon.reactor.ChemicalTankModel;
import com.yu1745.chemicaladdon.reactor.FilterPressBlock;
import com.yu1745.chemicaladdon.reactor.ReactorControllerBlock;
import com.yu1745.chemicaladdon.reactor.SettlingBasinBlockEntity.SettlingBasinBlock;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.client.model.generators.ConfiguredModel;

public class AllBlocks {

	public static final CreateRegistrate REGISTRATE = ChemicalAddon.registrate();

	/**
	 * Shell block of the vessels. Mirrors Create's FluidTank registration: a
	 * cutoutMipped hollow-wall model per (top/bottom/shape) variant, position-
	 * aware CTM via ChemicalTankModel, and Create's tank sprite shifts — so the
	 * shell is transparent (windows) and connects seamlessly as it grows. The
	 * metal-grey tint is applied client-side (ChemicalAddonClient).
	 */
	public static final BlockEntry<ChemicalBrickBlock> CHEMICAL_BRICK =
		REGISTRATE.block("chemical_brick", ChemicalBrickBlock::new)
			.properties(p -> p.mapColor(MapColor.STONE).strength(2.0f, 6.0f)
				.noOcclusion()
				.isRedstoneConductor((a, b, c) -> true))
			.lang("Chemical Brick")
			.blockstate(AllBlocks::chemicalBrickState)
			.onRegister(CreateRegistrate.blockModel(() -> ChemicalTankModel::standard))
			.addLayer(() -> RenderType::cutoutMipped)
			.item()
			.model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/chemical_brick/block_single_window")))
			.build()
			.register();

	/** Blockstate variant table: top x bottom x shape -> wall model (Create FluidTank layout). */
	private static void chemicalBrickState(DataGenContext<Block, ChemicalBrickBlock> ctx,
		RegistrateBlockstateProvider prov) {
		prov.getVariantBuilder(ctx.get()).forAllStates(state -> {
			String variant = state.getValue(ChemicalBrickBlock.TOP)
				? state.getValue(ChemicalBrickBlock.BOTTOM) ? "single" : "top"
				: state.getValue(ChemicalBrickBlock.BOTTOM) ? "bottom" : "middle";
			if (state.getValue(ChemicalBrickBlock.SHAPE) == ChemicalBrickBlock.Shape.WINDOW) {
				variant += "_window";
			}
			// model files are named block_single/top/bottom/middle[_window].json
			return ConfiguredModel.builder()
				.modelFile(prov.models().getExistingFile(prov.modLoc("block/chemical_brick/block_" + variant)))
				.build();
		});
	}

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
