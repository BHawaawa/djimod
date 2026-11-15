package com.example.avatadrone.compat;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public final class ClientPlayNetworking {
    @FunctionalInterface public interface Receiver {
        void receive(MinecraftClient client, ClientPlayNetworkHandler handler, PacketByteBuf buf, PacketSender sender);
    }
    private ClientPlayNetworking() {}
    public static void registerGlobalReceiver(Identifier id, Receiver receiver) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(DronePackets.id(id),
                (payload, context) -> {
                    PacketByteBuf buf = payload.buffer();
                    try { receiver.receive(context.client(), context.client().getNetworkHandler(), buf, context.responseSender()); }
                    finally { buf.release(); }
                });
    }
    public static boolean canSend(Identifier id) {
        return net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(DronePackets.id(id));
    }
    public static void send(Identifier id, PacketByteBuf buf) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(DronePackets.copy(id, buf));
    }
}
