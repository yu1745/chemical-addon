package com.yu1745.chemicaladdon.registry;

import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.yu1745.chemicaladdon.ChemicalAddon;
import com.yu1745.chemicaladdon.client.connected.ConnectedModelBuilder;
import com.yu1745.chemicaladdon.item.GaugeBlockItem;
import com.yu1745.chemicaladdon.reactor.LiquidLevelGaugeBlock;
import com.yu1745.chemicaladdon.reactor.MeteringInletBlock;
import com.yu1745.chemicaladdon.reactor.BaumeGaugeBlock;
import com.yu1745.chemicaladdon.reactor.BaumeGaugePanelBlock;
import com.yu1745.chemicaladdon.reactor.LiquidLevelGaugePanelBlock;
import com.yu1745.chemicaladdon.reactor.ChemicalBrickBlock;
import com.yu1745.chemicaladdon.reactor.ChemicalGlassBlock;
import com.yu1745.chemicaladdon.reactor.CatalystTrayBlock;
import com.yu1745.chemicaladdon.reactor.ConductivityGaugeBlock;
import com.yu1745.chemicaladdon.reactor.ConductivityGaugePanelBlock;
import com.yu1745.chemicaladdon.reactor.CrystallizerControllerBlock;
import com.yu1745.chemicaladdon.reactor.DecantHoseBlock;
import com.yu1745.chemicaladdon.reactor.DecantPortBlock;
import com.yu1745.chemicaladdon.reactor.FilterPressBlock;
import com.yu1745.chemicaladdon.reactor.GasDistributorBlock;
import com.yu1745.chemicaladdon.reactor.PhGaugeBlock;
import com.yu1745.chemicaladdon.reactor.PhGaugePanelBlock;
import com.yu1745.chemicaladdon.reactor.PressureGaugeBlock;
import com.yu1745.chemicaladdon.reactor.PressureGaugePanelBlock;
import com.yu1745.chemicaladdon.reactor.ReactorControllerBlock;
import com.yu1745.chemicaladdon.reactor.SettlingBasinBlockEntity.SettlingBasinBlock;
import com.yu1745.chemicaladdon.reactor.StirringHeadBlock;
import com.yu1745.chemicaladdon.reactor.StatusPortBlock;
import com.yu1745.chemicaladdon.reactor.ThermometerBlock;
import com.yu1745.chemicaladdon.reactor.ThermometerPanelBlock;
import com.yu1745.chemicaladdon.reactor.TurbidityGaugeBlock;
import com.yu1745.chemicaladdon.reactor.TurbidityGaugePanelBlock;

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

	/** Transparent shell block ("seared glass"): same structural series, lets you watch the fluid.
	 *  Connected textures via the vendored Mantle {@code chemicaladdon:connected} loader, on
	 *  Tinkers' clear_glass texture set (attribution in THIRD_PARTY.md). */
	public static final BlockEntry<ChemicalGlassBlock> CHEMICAL_GLASS =
		REGISTRATE.block("chemical_glass", ChemicalGlassBlock::new)
			.properties(p -> p.mapColor(MapColor.NONE).strength(0.3f)
				.noOcclusion()
				.isRedstoneConductor((a, b, c) -> false)
				.isSuffocating((a, b, c) -> false)
				.isViewBlocking((a, b, c) -> false))
			.lang("Chemical Glass")
			.blockstate((ctx, prov) -> {
				// cube_all on our clear_glass texture + connected-texture custom loader
				// ("cornerless_full": edges drop where neighbours of the same block connect).
				// Concrete BlockModelBuilder (not ModelBuilder<?>): javac cannot infer the
				// self-referential ConnectedModelBuilder<T extends ModelBuilder<T>> bound
				// through a wildcard capture.
				net.minecraftforge.client.model.generators.BlockModelBuilder model =
					prov.models().cubeAll(ctx.getName(), prov.modLoc("block/clear_glass"));
				model.customLoader(ConnectedModelBuilder::new).connected("all", "cornerless_full");
				prov.simpleBlock(ctx.get(), model.renderType(prov.mcLoc("cutout")));
			})
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
			.model((ctx, prov) -> prov.getBuilder(ctx.getName())
	.parent(new ModelFile.UncheckedModelFile("builtin/entity")))
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
			.model((ctx, prov) -> prov.getBuilder(ctx.getName())
	.parent(new ModelFile.UncheckedModelFile("builtin/entity")))
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
			.model((ctx, prov) -> prov.getBuilder(ctx.getName())
	.parent(new ModelFile.UncheckedModelFile("builtin/entity")))
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
			.model((ctx, prov) -> prov.getBuilder(ctx.getName())
	.parent(new ModelFile.UncheckedModelFile("builtin/entity")))
			.build()
			.register();

	/** S18 conductivity gauge (电导率计, 方块形式, U16.5): vessel shell block reading ionic strength. */
	public static final BlockEntry<ConductivityGaugeBlock> CONDUCTIVITY_GAUGE =
		REGISTRATE.block("conductivity_gauge", ConductivityGaugeBlock::new)
			.properties(p -> p.mapColor(MapColor.METAL).strength(2.0f, 6.0f))
			.lang("Conductivity Gauge")
			.blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(),
				prov.models().cubeAll(ctx.getName(), prov.modLoc("block/" + ctx.getName()))))
			.item(GaugeBlockItem::new)
			.model((ctx, prov) -> prov.getBuilder(ctx.getName())
	.parent(new ModelFile.UncheckedModelFile("builtin/entity")))
			.build()
			.register();

	/** S18 conductivity gauge (电导率计, 薄板形式, U16.5): thin face-mounted plate reading ionic strength. */
	public static final BlockEntry<ConductivityGaugePanelBlock> CONDUCTIVITY_GAUGE_PANEL =
			REGISTRATE.block("conductivity_gauge_panel", ConductivityGaugePanelBlock::new)
				.properties(p -> p.mapColor(MapColor.METAL).strength(1.5f, 4.0f)
					.noOcclusion()
					.isRedstoneConductor((a, b, c) -> false)
					.isSuffocating((a, b, c) -> false)
					.isViewBlocking((a, b, c) -> false))
				.lang("Conductivity Gauge Panel")
				.blockstate((ctx, prov) -> plateVariants(prov, ctx.get(), ctx.getName()))
				.item(GaugeBlockItem::new)
				.model((ctx, prov) -> prov.getBuilder(ctx.getName())
					.parent(new ModelFile.UncheckedModelFile("builtin/entity")))
				.build()
				.register();

	/** S16 pH gauge (pH 计, 方块形式, U17): vessel shell block reading H⁺ activity (Kw on the alkaline side). */
	public static final BlockEntry<PhGaugeBlock> PH_GAUGE =
			REGISTRATE.block("ph_gauge", PhGaugeBlock::new)
				.properties(p -> p.mapColor(MapColor.METAL).strength(2.0f, 6.0f))
				.lang("pH Gauge")
				.blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(),
					prov.models().cubeAll(ctx.getName(), prov.modLoc("block/" + ctx.getName()))))
				.item(GaugeBlockItem::new)
				.model((ctx, prov) -> prov.getBuilder(ctx.getName())
					.parent(new ModelFile.UncheckedModelFile("builtin/entity")))
				.build()
				.register();

	/** S16 pH gauge (pH 计, 薄板形式, U17): thin face-mounted plate reading H⁺ activity. */
	public static final BlockEntry<PhGaugePanelBlock> PH_GAUGE_PANEL =
			REGISTRATE.block("ph_gauge_panel", PhGaugePanelBlock::new)
				.properties(p -> p.mapColor(MapColor.METAL).strength(1.5f, 4.0f)
					.noOcclusion()
					.isRedstoneConductor((a, b, c) -> false)
					.isSuffocating((a, b, c) -> false)
					.isViewBlocking((a, b, c) -> false))
				.lang("pH Gauge Panel")
				.blockstate((ctx, prov) -> plateVariants(prov, ctx.get(), ctx.getName()))
				.item(GaugeBlockItem::new)
				.model((ctx, prov) -> prov.getBuilder(ctx.getName())
					.parent(new ModelFile.UncheckedModelFile("builtin/entity")))
				.build()
				.register();

	/** S04 Baumé gauge (波美计, 方块形式, U17): vessel shell block reading dissolved-solids density. */
	public static final BlockEntry<BaumeGaugeBlock> BAUME_GAUGE =
			REGISTRATE.block("baume_gauge", BaumeGaugeBlock::new)
				.properties(p -> p.mapColor(MapColor.METAL).strength(2.0f, 6.0f))
				.lang("Baumé Gauge")
				.blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(),
					prov.models().cubeAll(ctx.getName(), prov.modLoc("block/" + ctx.getName()))))
				.item(GaugeBlockItem::new)
				.model((ctx, prov) -> prov.getBuilder(ctx.getName())
					.parent(new ModelFile.UncheckedModelFile("builtin/entity")))
				.build()
				.register();

	/** S04 Baumé gauge (波美计, 薄板形式, U17): thin face-mounted plate reading dissolved-solids density. */
	public static final BlockEntry<BaumeGaugePanelBlock> BAUME_GAUGE_PANEL =
			REGISTRATE.block("baume_gauge_panel", BaumeGaugePanelBlock::new)
				.properties(p -> p.mapColor(MapColor.METAL).strength(1.5f, 4.0f)
					.noOcclusion()
					.isRedstoneConductor((a, b, c) -> false)
					.isSuffocating((a, b, c) -> false)
					.isViewBlocking((a, b, c) -> false))
				.lang("Baumé Gauge Panel")
				.blockstate((ctx, prov) -> plateVariants(prov, ctx.get(), ctx.getName()))
				.item(GaugeBlockItem::new)
				.model((ctx, prov) -> prov.getBuilder(ctx.getName())
					.parent(new ModelFile.UncheckedModelFile("builtin/entity")))
				.build()
				.register();

	/** S17 turbidity gauge (浊度计, 方块形式, U17): vessel shell block reading suspended solids in 4 bins. */
	public static final BlockEntry<TurbidityGaugeBlock> TURBIDITY_GAUGE =
			REGISTRATE.block("turbidity_gauge", TurbidityGaugeBlock::new)
				.properties(p -> p.mapColor(MapColor.METAL).strength(2.0f, 6.0f))
				.lang("Turbidity Gauge")
				.blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(),
					prov.models().cubeAll(ctx.getName(), prov.modLoc("block/" + ctx.getName()))))
				.item(GaugeBlockItem::new)
				.model((ctx, prov) -> prov.getBuilder(ctx.getName())
					.parent(new ModelFile.UncheckedModelFile("builtin/entity")))
				.build()
				.register();

	/** S17 turbidity gauge (浊度计, 薄板形式, U17): thin face-mounted plate reading suspended solids in 4 bins. */
	public static final BlockEntry<TurbidityGaugePanelBlock> TURBIDITY_GAUGE_PANEL =
			REGISTRATE.block("turbidity_gauge_panel", TurbidityGaugePanelBlock::new)
				.properties(p -> p.mapColor(MapColor.METAL).strength(1.5f, 4.0f)
					.noOcclusion()
					.isRedstoneConductor((a, b, c) -> false)
					.isSuffocating((a, b, c) -> false)
					.isViewBlocking((a, b, c) -> false))
				.lang("Turbidity Gauge Panel")
				.blockstate((ctx, prov) -> plateVariants(prov, ctx.get(), ctx.getName()))
				.item(GaugeBlockItem::new)
				.model((ctx, prov) -> prov.getBuilder(ctx.getName())
					.parent(new ModelFile.UncheckedModelFile("builtin/entity")))
				.build()
				.register();

	/** S11 liquid-level gauge (液位计, 方块形式): vessel shell block reading the liquid fill percent. */
	public static final BlockEntry<LiquidLevelGaugeBlock> LIQUID_LEVEL_GAUGE =
			REGISTRATE.block("liquid_level_gauge", LiquidLevelGaugeBlock::new)
				.properties(p -> p.mapColor(MapColor.METAL).strength(2.0f, 6.0f))
				.lang("Liquid Level Gauge")
				.blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(),
					prov.models().cubeAll(ctx.getName(), prov.modLoc("block/" + ctx.getName()))))
				.item(GaugeBlockItem::new)
				.model((ctx, prov) -> prov.getBuilder(ctx.getName())
					.parent(new ModelFile.UncheckedModelFile("builtin/entity")))
				.build()
				.register();

	/** S11 liquid-level gauge (液位计, 薄板形式): thin face-mounted plate reading the liquid fill percent. */
	public static final BlockEntry<LiquidLevelGaugePanelBlock> LIQUID_LEVEL_GAUGE_PANEL =
			REGISTRATE.block("liquid_level_gauge_panel", LiquidLevelGaugePanelBlock::new)
				.properties(p -> p.mapColor(MapColor.METAL).strength(1.5f, 4.0f)
					.noOcclusion()
					.isRedstoneConductor((a, b, c) -> false)
					.isSuffocating((a, b, c) -> false)
					.isViewBlocking((a, b, c) -> false))
				.lang("Liquid Level Gauge Panel")
				.blockstate((ctx, prov) -> plateVariants(prov, ctx.get(), ctx.getName()))
				.item(GaugeBlockItem::new)
				.model((ctx, prov) -> prov.getBuilder(ctx.getName())
					.parent(new ModelFile.UncheckedModelFile("builtin/entity")))
				.build()
				.register();

	/** M08 endpoint crystalliser (终点结晶器, U17): reactor multiblock + Baumé setpoint + condensate recovery. */
	public static final BlockEntry<CrystallizerControllerBlock> CRYSTALLIZER_CONTROLLER =
			REGISTRATE.block("crystallizer_controller", CrystallizerControllerBlock::new)
				.properties(p -> p.mapColor(MapColor.METAL).strength(3.0f, 6.0f))
				.lang("Crystallizer Controller")
				.blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(), prov.models()
					.cubeAll(ctx.getName(), prov.modLoc("block/" + ctx.getName()))))
				.simpleItem()
				.register();

	/** B2 gas distributor: directional shell inlet; FACING points into the vessel,
	 * and only the opposite external face exposes the one-way gas capability. */
	public static final BlockEntry<GasDistributorBlock> GAS_DISTRIBUTOR =
			REGISTRATE.block("gas_distributor", GasDistributorBlock::new)
				.properties(p -> p.mapColor(MapColor.METAL).strength(3.0f, 6.0f))
				.lang("Gas Distributor")
				.blockstate((ctx, prov) -> {
					ModelFile model = prov.models().getBuilder(ctx.getName())
						.parent(new ModelFile.UncheckedModelFile("minecraft:block/cube"))
						.texture("down", prov.modLoc("block/gas_distributor_side"))
						.texture("up", prov.modLoc("block/gas_distributor_side"))
						.texture("north", prov.modLoc("block/gas_distributor_back"))
						.texture("south", prov.modLoc("block/gas_distributor_front"))
						.texture("east", prov.modLoc("block/gas_distributor_side"))
						.texture("west", prov.modLoc("block/gas_distributor_side"));
					prov.getVariantBuilder(ctx.get()).forAllStates(state -> {
						Direction facing = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
						// The base model's front is SOUTH.  These rotations carry that
						// face to FACING, including the vertical bottom/top placements.
						int x = facing == Direction.UP ? 270 : facing == Direction.DOWN ? 90 : 0;
						int y = facing.getAxis().isVertical() ? 0 : (int) facing.toYRot();
						return net.minecraftforge.client.model.generators.ConfiguredModel.builder()
							.modelFile(model)
							.rotationX(x)
							.rotationY(y)
							.build();
					});
				})
				.simpleItem()
				.register();

	/** B1 stirring head (搅拌头): a roof shell block taking rotation from a vertical
	 *  shaft above (Create KineticBlock, Y axis, shaft towards UP) and mapping its
	 *  effective |RPM| onto the vessel's stirring coefficient. In the vessel_walls
	 *  tag, so it seals a roof like a brick; the hand-authored static model is the
	 *  fixed Create-scale roof base (shaft coupling on top, stub bore below) — the
	 *  dynamic shaft + vessel-sized impeller are BE-rendered partials
	 *  (StirringHeadRenderer). Datagen only links to the static model. */
	public static final BlockEntry<StirringHeadBlock> STIRRING_HEAD =
			REGISTRATE.block("stirring_head", StirringHeadBlock::new)
				.properties(p -> p.mapColor(MapColor.METAL).strength(3.0f, 6.0f))
				.lang("Stirring Head")
				.blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(),
					prov.models().getExistingFile(prov.modLoc("block/" + ctx.getName()))))
				.simpleItem()
				.register();

	/** B3 catalyst tray (催化托盘): directional side-wall shell block; FACING points
	 * into the vessel, the opposite face is the sole item endpoint (world
	 * insert/extract, no GUI). */
	public static final BlockEntry<CatalystTrayBlock> CATALYST_TRAY =
			REGISTRATE.block("catalyst_tray", CatalystTrayBlock::new)
				.properties(p -> p.mapColor(MapColor.METAL).strength(3.0f, 6.0f))
				.lang("Catalyst Tray")
				.blockstate((ctx, prov) -> {
					ModelFile model = prov.models().getBuilder(ctx.getName())
						.parent(new ModelFile.UncheckedModelFile("minecraft:block/cube"))
						.texture("down", prov.modLoc("block/catalyst_tray_side"))
						.texture("up", prov.modLoc("block/catalyst_tray_side"))
						.texture("north", prov.modLoc("block/catalyst_tray_back"))
						.texture("south", prov.modLoc("block/catalyst_tray_front"))
						.texture("east", prov.modLoc("block/catalyst_tray_side"))
						.texture("west", prov.modLoc("block/catalyst_tray_side"));
					prov.getVariantBuilder(ctx.get()).forAllStates(state -> {
						Direction facing = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
						// base model front is SOUTH (B2 convention)
						int x = facing == Direction.UP ? 270 : facing == Direction.DOWN ? 90 : 0;
						int y = facing.getAxis().isVertical() ? 0 : (int) facing.toYRot();
							return net.minecraftforge.client.model.generators.ConfiguredModel.builder()
								.modelFile(model)
								.rotationX(x)
								.rotationY(y)
								.build();
						});
				})
				.simpleItem()
				.register();

	/** B · status port (状态口, wall form only): a fixed-function vessel_walls shell
	 *  brick publishing the master's process status — right-click reads it out, goggles
	 *  show status + progress, redstone encodes it for batch interlocks (see
	 *  StatusPortBlockEntity). No dial, no panel form, no renderer. */
	public static final BlockEntry<StatusPortBlock> STATUS_PORT =
			REGISTRATE.block("status_port", StatusPortBlock::new)
				.properties(p -> p.mapColor(MapColor.METAL).strength(2.5f, 6.0f))
				.lang("Status Port")
				.blockstate((ctx, prov) -> prov.simpleBlock(ctx.get(),
					prov.models().cubeAll(ctx.getName(), prov.modLoc("block/" + ctx.getName()))))
				.simpleItem()
				.register();

	/** B4 directional batch-metered liquid inlet. FACING points into the vessel. */
	public static final BlockEntry<MeteringInletBlock> METERING_INLET =
			REGISTRATE.block("metering_inlet", MeteringInletBlock::new)
				.properties(p -> p.mapColor(MapColor.METAL).strength(3.0f, 6.0f))
				.lang("Metering Inlet")
				.blockstate((ctx, prov) -> {
					ModelFile model = prov.models().getBuilder(ctx.getName())
						.parent(new ModelFile.UncheckedModelFile("minecraft:block/cube"))
						.texture("down", prov.modLoc("block/metering_inlet_side"))
						.texture("up", prov.modLoc("block/metering_inlet_side"))
						.texture("north", prov.modLoc("block/metering_inlet_back"))
						.texture("south", prov.modLoc("block/metering_inlet_front"))
						.texture("east", prov.modLoc("block/metering_inlet_side"))
						.texture("west", prov.modLoc("block/metering_inlet_side"));
					prov.getVariantBuilder(ctx.get()).forAllStates(state -> {
						Direction facing = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING);
						int x = facing == Direction.UP ? 270 : facing == Direction.DOWN ? 90 : 0;
						int y = facing.getAxis().isVertical() ? 0 : (int) facing.toYRot();
						return net.minecraftforge.client.model.generators.ConfiguredModel.builder()
							.modelFile(model).rotationX(x).rotationY(y).build();
					});
				})
				.simpleItem()
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
