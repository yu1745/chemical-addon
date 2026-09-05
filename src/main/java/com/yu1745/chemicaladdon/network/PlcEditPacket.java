package com.yu1745.chemicaladdon.network;

import java.util.function.Supplier;

import com.yu1745.chemicaladdon.control.PlcControllerBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public record PlcEditPacket(BlockPos pos,int action,PlcControllerBlockEntity.ProgramMode mode,String source) {
	public static void encode(PlcEditPacket p,FriendlyByteBuf b){b.writeBlockPos(p.pos);b.writeVarInt(p.action);b.writeEnum(p.mode);b.writeUtf(p.source,8192);}
	public static PlcEditPacket decode(FriendlyByteBuf b){return new PlcEditPacket(b.readBlockPos(),b.readVarInt(),b.readEnum(PlcControllerBlockEntity.ProgramMode.class),b.readUtf(8192));}
	public static void handle(PlcEditPacket p,Supplier<NetworkEvent.Context> supplier){NetworkEvent.Context ctx=supplier.get();ctx.enqueueWork(()->{ServerPlayer player=ctx.getSender();if(player==null||player.distanceToSqr(p.pos.getX()+.5,p.pos.getY()+.5,p.pos.getZ()+.5)>64)return;if(!(player.level().getBlockEntity(p.pos)instanceof PlcControllerBlockEntity plc))return;switch(p.action){case 0->plc.applyProgram(p.mode,p.source,false);case 1->{if(!plc.source().equals(p.source)||plc.programMode()!=p.mode)plc.applyProgram(p.mode,p.source,false);plc.setRunning(true);}case 2->plc.setRunning(false);default->{}}});ctx.setPacketHandled(true);}
}
