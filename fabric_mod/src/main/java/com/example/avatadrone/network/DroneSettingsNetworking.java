package com.example.avatadrone.network;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.DroneSettings;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/** Dedicated servers and integrated servers use the same validated settings path. */
public final class DroneSettingsNetworking {
    public static final Identifier ID = new Identifier(AvataDrone.MOD_ID, "flight_settings");
    public static volatile double clientRange = DroneSettings.DEFAULT_RC_RANGE;
    private DroneSettingsNetworking() {}

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.player));
        ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, handler, buf, sender) -> {
            if (buf.readableBytes() != Double.BYTES) return;
            double requested = buf.readDouble();
            server.execute(() -> {
                if (player.hasPermissionLevel(2) && Double.isFinite(requested)) {
                    DroneSettings.get(player.getServerWorld()).setRange(requested);
                    for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) sync(online);
                } else sync(player);
            });
        });
    }
    private static void sync(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, ID)) return;
        var buf = PacketByteBufs.create();
        buf.writeDouble(DroneSettings.get(player.getServerWorld()).range());
        buf.writeBoolean(player.hasPermissionLevel(2));
        ServerPlayNetworking.send(player, ID, buf);
    }
}
