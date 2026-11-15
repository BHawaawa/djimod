package com.example.avatadrone.compat;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

public final class ServerPlayNetworking {
    @FunctionalInterface public interface Receiver {
        void receive(MinecraftServer server, ServerPlayerEntity player, ServerPlayNetworkHandler handler,
                     PacketByteBuf buf, PacketSender sender);
    }
    private ServerPlayNetworking() {}
    public static void registerGlobalReceiver(Identifier id, Receiver receiver) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(DronePackets.id(id),
                (payload, context) -> {
                    PacketByteBuf buf = payload.buffer();
                    try {
                        receiver.receive(context.server(), context.player(), context.player().networkHandler,
                                buf, context.responseSender());
                    } finally { buf.release(); }
                });
    }
    public static boolean canSend(ServerPlayerEntity player, Identifier id) {
        return net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player, DronePackets.id(id));
    }
    public static void send(ServerPlayerEntity player, Identifier id, PacketByteBuf buf) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, DronePackets.copy(id, buf));
    }
}
