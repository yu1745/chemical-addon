package com.yu1745.chemicaladdon.reactor;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import com.yu1745.chemicaladdon.registry.AllMenuTypes;

/**
 * Control panel menu for the reaction vessel. M1: no slots; the screen reads
 * BE state (temperature / contents / structure) directly from the client BE.
 */
public class ReactorMenu extends AbstractContainerMenu {

	private final BlockPos pos;

	public ReactorMenu(int id, Inventory playerInventory, BlockPos pos) {
		super(AllMenuTypes.REACTOR.get(), id);
		this.pos = pos;
	}

	public BlockPos getBlockPos() {
		return pos;
	}

	public static ReactorMenu fromNetwork(int id, Inventory playerInventory, FriendlyByteBuf buf) {
		return new ReactorMenu(id, playerInventory, buf.readBlockPos());
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		return ItemStack.EMPTY;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}
}
