package com.yu1745.chemicaladdon.control;

import com.yu1745.chemicaladdon.registry.AllMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class PlcMenu extends AbstractContainerMenu {
	private final BlockPos pos;
	private final PlcControllerBlockEntity plc;
	public PlcMenu(int id,Inventory inventory,PlcControllerBlockEntity plc){super(AllMenus.PLC.get(),id);this.pos=plc.getBlockPos();this.plc=plc;}
	public static PlcMenu client(int id,Inventory inventory,FriendlyByteBuf buf){BlockPos pos=buf.readBlockPos();return new PlcMenu(id,inventory,(PlcControllerBlockEntity)inventory.player.level().getBlockEntity(pos));}
	public PlcControllerBlockEntity plc(){return plc;}
	public BlockPos pos(){return pos;}
	@Override public boolean stillValid(Player player){return player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64&&player.level().getBlockEntity(pos)==plc;}
	@Override public ItemStack quickMoveStack(Player player,int index){return ItemStack.EMPTY;}
}
