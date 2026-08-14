package com.yu1745.chemicaladdon.registry;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.item.GaugeBlockItem;
import com.yu1745.chemicaladdon.reactor.ChemicalBrickBlock;
import com.yu1745.chemicaladdon.reactor.ChemicalGlassBlock;
import com.yu1745.chemicaladdon.reactor.DecantHoseBlock;
import com.yu1745.chemicaladdon.reactor.DecantPortBlock;
import com.yu1745.chemicaladdon.reactor.FilterPressBlock;
import com.yu1745.chemicaladdon.reactor.PressureGaugeBlock;
import com.yu1745.chemicaladdon.reactor.PressureGaugePanelBlock;
import com.yu1745.chemicaladdon.reactor.ReactorControllerBlock;
import com.yu1745.chemicaladdon.reactor.SettlingBasinBlockEntity.SettlingBasinBlock;
import com.yu1745.chemicaladdon.reactor.ThermometerBlock;
import com.yu1745.chemicaladdon.reactor.ThermometerPanelBlock;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.generators.ModelFile;
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

	/** Decant hose (分液软管): a transient conversion of Create's Hose Pulley that drains the lightest phase. */
	public static final BlockEntry<DecantHoseBlock> DECANT_HOSE =
		REGISTRATE.block("decant_hose", DecantHoseBlock::new)
			.properties(p -> p.mapColor(MapColor.METAL).strength(2.0f, 6.0f).noOcclusion())
			.lang("Decant Hose")
			// reuse Create's hose pulley model (the visible block is the BE-rendered
			// coil anyway). UncheckedModelFile: datagen's existence check cannot see
			// into the slim Create jar (no assets), the model resolves at runtime.
			.blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(),
				new ModelFile.UncheckedModelFile(new ResourceLocation("create", "block/hose_pulley/block"))))
			.register();

	/** One-way drain port (分液口): a shell block whose FLUID_HANDLER drains only the densest phase. */
	public static final BlockEntry<DecantPortBlock> DECANT_PORT =
		REGISTRATE.block("decant_port", DecantPortBlock::new)
			.properties(p -> p.mapColor(MapColor.METAL).strength(2.0f, 6.0f))
			.lang("Decant Port")
			.blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(), prov.models()
				.cubeAll(ctx.getName(), prov.modLoc("block/" + ctx.getName()))))
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

	/** S02 thermometer (温度计, 方块形式): a full-cube shell block that doubles as a temperature gauge.
	 *  In the {@code vessel_walls} tag, so it can fill a wall position of the reactor and reads its own vessel. */
	public static final BlockEntry<ThermometerBlock> THERMOMETER =
		REGISTRATE.block("thermometer", ThermometerBlock::new)
			.properties(p -> p.mapColor(MapColor.METAL).strength(2.0f, 6.0f))
			.lang("Thermometer")
			.blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(), prov.models()
				.cubeAll(ctx.getName(), prov.modLoc("block/" + ctx.getName()))))
			// gaugeBlockItem: the icon is a custom-rendered itemstack (builtin/entity
			// model + BEWLR drawing the block model with the needle at its zero position)
			.item(GaugeBlockItem::new)
			.model((ctx, prov) -> prov.withExistingParent(ctx.getName(), prov.mcLoc("builtin/entity")))
			.build()
			.register();

	/** S02 thermometer (温度计, 薄板形式): a thin face-mounted plate reading the reactor behind it. */
	public static final BlockEntry<ThermometerPanelBlock> THERMOMETER_PANEL =
		REGISTRATE.block("thermometer_panel", ThermometerPanelBlock::new)
			.properties(p -> p.mapColor(MapColor.METAL).strength(1.5f, 4.0f)
				.noOcclusion()
				.isRedstoneConductor((a, b, c) -> false)
				.isSuffocating((a, b, c) -> false)
				.isViewBlocking((a, b, c) -> false))
			.lang("Thermometer Panel")
			// FACING-aware variants; the 2px plate model itself is hand-written in
			// main resources (custom elements — datagen only links to it)
			.blockstate((ctx, prov) -> plateVariants(prov, ctx.get(), ctx.getName()))
			.item(GaugeBlockItem::new)
			.model((ctx, prov) -> prov.withExistingParent(ctx.getName(), prov.mcLoc("builtin/entity")))
			.build()
			.register();

	/** S03 pressure gauge (压力表, 方块形式): a full-cube shell block that doubles as a pressure gauge.
	 *  In the {@code vessel_walls} tag, so it can fill a wall position of the reactor and reads its own vessel. */
	public static final BlockEntry<PressureGaugeBlock> PRESSURE_GAUGE =
		REGISTRATE.block("pressure_gauge", PressureGaugeBlock::new)
			.properties(p -> p.mapColor(MapColor.METAL).strength(2.0f, 6.0f))
			.lang("Pressure Gauge")
			.blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(),
				prov.models().cubeAll(ctx.getName(), prov.modLoc("block/" + ctx.getName()))))
			.item(GaugeBlockItem::new)
			.model((ctx, prov) -> prov.withExistingParent(ctx.getName(), prov.mcLoc("builtin/entity")))
			.build()
			.register();

	/** S03 pressure gauge (压力表, 薄板形式): a thin face-mounted plate reading the reactor behind it. */
	public static final BlockEntry<PressureGaugePanelBlock> PRESSURE_GAUGE_PANEL =
		REGISTRATE.block("pressure_gauge_panel", PressureGaugePanelBlock::new)
			.properties(p -> p.mapColor(MapColor.METAL).strength(1.5f, 4.0f)
				.noOcclusion()
				.isRedstoneConductor((a, b, c) -> false)
				.isSuffocating((a, b, c) -> false)
				.isViewBlocking((a, b, c) -> false))
			.lang("Pressure Gauge Panel")
			// same dual-form pattern as the thermometer panel (S02)
			.blockstate((ctx, prov) -> plateVariants(prov, ctx.get(), ctx.getName()))
			.item(GaugeBlockItem::new)
			.model((ctx, prov) -> prov.withExistingParent(ctx.getName(), prov.mcLoc("builtin/entity")))
			.build()
			.register();

	public static void register() {
	}

	/**
	 * FACING-aware blockstate variants for a thin wall plate (the S02/S03 gauge
	 * panels): north is the un-rotated model, horizontals y-rotate, up/down
	 * x-rotate — the geometry the hand-authored 2px plate model is drawn for.
	 * (Forge's {@code directionalBlock} would x-rotate horizontals too: it
	 * assumes a floor-standing model, a plate is not one.)
	 */
	private static void plateVariants(com.tterrag.registrate.providers.RegistrateBlockstateProvider prov,
		net.minecraft.world.level.block.Block block, String name) {
		ModelFile model = prov.models().getExistingFile(prov.modLoc("block/" + name));
		prov.getVariantBuilder(block).forAllStates(state -> {
			Direction facing = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
			int x = facing == Direction.UP ? 270 : facing == Direction.DOWN ? 90 : 0;
			// north-anchored yaw (N=0/E=90/S=180/W=270): toYRot() is south-anchored,
			// so shift by 180 or the plate renders flipped into the wall
			int y = facing.getAxis().isVertical() ? 0 : Math.floorMod((int) facing.toYRot() + 180, 360);
			return net.minecraftforge.client.model.generators.ConfiguredModel.builder()
				.modelFile(model)
				.rotationX(x)
				.rotationY(y)
				.build();
		});
	}
}
