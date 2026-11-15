package com.example.avatadrone.network;

import com.example.avatadrone.signal.FourGNetwork;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.util.Identifier;

/** Sends the persistent index, including transmitters in unloaded chunks, for the player's dimension. */
public final class FlightMapNetworking {
    public static final Identifier ID = new Identifier("djimod", "flight_map_stations");
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ID, (server, player, handler, buf, sender) -> {
            server.execute(() -> {
                var stations = FourGNetwork.get(player.getServerWorld()).snapshot();
                // Batches keep even large transmitter networks below the custom payload limit.
                var entries = new java.util.ArrayList<>(stations.entrySet());
                for (int start = 0; start < Math.max(1, entries.size()); start += 2048) {
                    var packet = PacketByteBufs.create();
                    packet.writeIdentifier(player.getWorld().getRegistryKey().getValue());
                    packet.writeBoolean(start == 0);
                    int end = Math.min(start + 2048, entries.size());
                    packet.writeVarInt(end - start);
                    for (int i = start; i < end; i++) {
                        packet.writeBlockPos(entries.get(i).getKey());
                        packet.writeEnumConstant(entries.get(i).getValue());
                    }
                    ServerPlayNetworking.send(player, ID, packet);
                }
            });
        });
    }
}
