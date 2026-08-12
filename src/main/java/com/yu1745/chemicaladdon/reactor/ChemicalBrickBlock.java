package com.yu1745.chemicaladdon.reactor;

import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Structural block of the reaction vessel / settling basin shell. Has a light
 * BE that proxies fluid/item capabilities to the assembled master (Create
 * FluidTank pattern). When broken, any nearby controller is notified so the
 * structure de-assembles.
 *
 * The block uses Create's FluidTank position-aware model scheme: TOP/BOTTOM
 * (which plate layer this brick is in) and SHAPE (windowed or plain wall)
 * are written by the master when the multiblock forms, so the shell shows the
 * matching hollow-wall model variant (top/middle/bottom/single) and its
 * connected-texture windows adapt as the vessel grows.
 */
public class ChemicalBrickBlock extends Block implements EntityBlock {

	private static final int SEARCH_RADIUS = 7; // n up to 7 -> corner brick ~5.2 blocks from controller

	public static final BooleanProperty TOP = BooleanProperty.create("top");
	public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom");
	public static final EnumProperty<Shape> SHAPE = EnumProperty.create("shape", Shape.class);

	/** Wall window layout of a single brick (mirrors Create's FluidTankBlock.Shape). */
	public enum Shape implements StringRepresentable {
		PLAIN, WINDOW;

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public ChemicalBrickBlock(Properties properties) {
		super(properties);
		registerDefaultState(defaultBlockState().setValue(TOP, true).setValue(BOTTOM, true).setValue(SHAPE, Shape.WINDOW));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		super.createBlockStateDefinition(builder);
		builder.add(TOP, BOTTOM, SHAPE);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new ChemicalBrickBlockEntity(pos, state);
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
		// placing (or replacing) a brick re-forms nearby unassembled structures
		// automatically — Create FluidTank-style state recovery (no right-click
		// needed after rebuilding a broken shell)
		if (oldState.getBlock() == state.getBlock() || moved || level.isClientSide) {
			return;
		}
		for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
			for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
				for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
					BlockEntity be = level.getBlockEntity(pos.offset(dx, dy, dz));
					if (be instanceof ReactorControllerBlockEntity controller && !controller.isAssembled()) {
						controller.tryAssemble();
					} else if (be instanceof SettlingBasinBlockEntity basin && !basin.isAssembled()) {
						basin.tryAssemble();
					}
				}
			}
		}
	}

	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
		if (!state.is(newState.getBlock())) {
			for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
				for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
					for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
						BlockEntity be = level.getBlockEntity(pos.offset(dx, dy, dz));
						if (be instanceof ReactorControllerBlockEntity controller) {
							controller.invalidateStructure(pos);
						}
						if (be instanceof SettlingBasinBlockEntity basin) {
							basin.invalidateStructure(pos);
						}
					}
				}
			}
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}
}
