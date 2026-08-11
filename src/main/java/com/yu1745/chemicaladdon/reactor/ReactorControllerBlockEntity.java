package com.yu1745.chemicaladdon.reactor;

import javax.annotation.Nullable;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.yu1745.chemicaladdon.registry.AllBlockEntities;
import com.yu1745.chemicaladdon.registry.AllBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.IFluidHandler;

/**
 * Reaction vessel controller. Holds the multi-fluid tank (stream container),
 * the temperature field, structure state and the control panel menu.
 * M1 scope: 3x3x3 structure, fluid IO via Forge FLUID_HANDLER (Create pipes
 * connect directly), Blaze Burner heating, GUI display. Reactions come in M1c.
 */
public class ReactorControllerBlockEntity extends BlockEntity implements MenuProvider {

	public static final int TANK_CAPACITY = 16000; // mB (16 buckets), fixed for M1
	public static final int AMBIENT_TEMP = 20;

	private final ReactorTank tank = new ReactorTank(TANK_CAPACITY, this::onTankChanged);
	private final LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(() -> tank);

	private boolean assembled = false;
	private int temperature = AMBIENT_TEMP;
	private int tickCounter = 0;

	public ReactorControllerBlockEntity(BlockPos pos, BlockState state) {
		super(AllBlockEntities.REACTOR_CONTROLLER.get(), pos, state);
	}

	public void serverTick() {
		if (level == null || level.isClientSide) {
			return;
		}
		if (++tickCounter < 20) {
			return;
		}
		tickCounter = 0;

		// heating from a Blaze Burner directly below the vessel
		BlockState below = level.getBlockState(worldPosition.below());
		int target = switch (BlazeBurnerBlock.getHeatLevelOf(below)) {
			case KINDLED -> 500;
			case SEETHING -> 900;
			default -> AMBIENT_TEMP;
		};
		temperature += (target - temperature) / 10;

		level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
	}

	private void onTankChanged() {
		setChanged();
		if (level != null && !level.isClientSide) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/**
	 * Validates the 3x3x3 hollow brick shell. The controller must sit in the
	 * middle of one wall; the structure extends 2 blocks in the inward
	 * direction, ±1 along the wall, ±1 vertically. Tries all 4 wall faces.
	 */
	public boolean tryAssemble() {
		if (level == null || level.isClientSide) {
			return false;
		}
		BlockState brick = AllBlocks.CHEMICAL_BRICK.get().defaultBlockState();

		for (Direction inward : new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST }) {
			Direction side = inward.getAxis() == Direction.Axis.X ? Direction.NORTH : Direction.EAST;
			boolean ok = true;

			// bottom (y-1) and top (y+1) layers: full 3x3 of bricks
			for (int s = -1; s <= 1 && ok; s++) {
				for (int d = 0; d <= 2 && ok; d++) {
					BlockPos bottom = worldPosition.offset(
						side.getStepX() * s + inward.getStepX() * d, -1, side.getStepZ() * s + inward.getStepZ() * d);
					if (!level.getBlockState(bottom).is(brick.getBlock())) {
						ok = false;
					}
					BlockPos top = worldPosition.offset(
						side.getStepX() * s + inward.getStepX() * d, 1, side.getStepZ() * s + inward.getStepZ() * d);
					if (!level.getBlockState(top).is(brick.getBlock())) {
						ok = false;
					}
				}
			}

			// wall layer (y): ring of bricks, controller at its own spot, interior air
			for (int s = -1; s <= 1 && ok; s++) {
				for (int d = 0; d <= 2 && ok; d++) {
					if (s == 0 && d == 0) {
						continue; // the controller itself
					}
					BlockPos p = worldPosition.offset(
						side.getStepX() * s + inward.getStepX() * d, 0, side.getStepZ() * s + inward.getStepZ() * d);
					if (s == 0 && d == 1) {
						if (!level.getBlockState(p).isAir()) {
							ok = false; // interior must be hollow
						}
					} else if (!level.getBlockState(p).is(brick.getBlock())) {
						ok = false;
					}
				}
			}

			if (ok) {
				assembled = true;
				setChanged();
				level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
				return true;
			}
		}
		return false;
	}

	public void invalidateStructure() {
		if (assembled) {
			assembled = false;
			setChanged();
			if (level != null && !level.isClientSide) {
				level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
			}
		}
	}

	public boolean isAssembled() {
		return assembled;
	}

	public int getTemperature() {
		return temperature;
	}

	public ReactorTank getTank() {
		return tank;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.chemicaladdon.reactor_controller");
	}

	@Nullable
	@Override
	public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
		return new ReactorMenu(id, inventory, worldPosition);
	}

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
		if (cap == ForgeCapabilities.FLUID_HANDLER) {
			return fluidCap.cast();
		}
		return super.getCapability(cap, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		fluidCap.invalidate();
	}

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putBoolean("assembled", assembled);
		tag.putInt("temperature", temperature);
		tag.put("tank", tank.serializeNBT());
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		assembled = tag.getBoolean("assembled");
		temperature = tag.getInt("temperature");
		tank.deserializeNBT(tag.getCompound("tank"));
	}

	@Override
	public CompoundTag getUpdateTag() {
		CompoundTag tag = super.getUpdateTag();
		saveAdditional(tag);
		return tag;
	}
}
