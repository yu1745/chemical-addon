package com.yu1745.chemicaladdon.network;

import java.util.function.Supplier;

import com.yu1745.chemicaladdon.ChemicalAddon;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * S2C: the server tells the client whether the assay (dev ion/fluid breakdown)
 * overlay is on for the local player. Kept deliberately tiny — the client just
 * flips {@link ChemicalAddon#ASSAY_ON}, which the goggles HUD reads.
 */
public record AssaySyncPacket(boolean on) {

	public static void encode(AssaySyncPacket msg, FriendlyByteBuf buf) {
		buf.writeBoolean(msg.on());
	}

	public static AssaySyncPacket decode(FriendlyByteBuf buf) {
		return new AssaySyncPacket(buf.readBoolean());
	}

	public static void handle(AssaySyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> ChemicalAddon.ASSAY_ON = msg.on());
		ctx.get().setPacketHandled(true);
	}
}
