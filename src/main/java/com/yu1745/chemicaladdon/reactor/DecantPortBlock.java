package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The decant port (分液口): a structural wall block that doubles as a one-way
 * drain for the vessel's bottom (densest) phase. Rendered as a flush wall block
 * (no protruding faucet), so it sits cleanly in the shell; its BE carries the
 * "heaviest-phase-only" fluid handler. Placement / breakage re-form and de-assemble
 * the structure exactly like {@link ChemicalBrickBlock}.
 */
public class DecantPortBlock extends ChemicalBrickBlock {

	public DecantPortBlock(Properties properties) {
		super(properties);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new DecantPortBlockEntity(pos, state);
	}
}
