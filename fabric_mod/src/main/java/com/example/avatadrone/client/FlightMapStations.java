package com.example.avatadrone.client;

import com.example.avatadrone.network.FlightMapNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FlightMapStations {
    private static final Map<BlockPos, Direction> stations = new LinkedHashMap<>();
    private static Identifier dimension;
    private static long nextRequest;
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(FlightMapNetworking.ID, (client, handler, buf, sender) -> {
            Identifier dim = buf.readIdentifier();
            boolean reset = buf.readBoolean();
            int count = buf.readVarInt();
            Map<BlockPos, Direction> batch = new LinkedHashMap<>();
            for (int i = 0; i < count; i++) batch.put(buf.readBlockPos(), buf.readEnumConstant(Direction.class));
            client.execute(() -> {
                if (client.world == null || !client.world.getRegistryKey().getValue().equals(dim)) return;
                if (reset || !dim.equals(dimension)) stations.clear();
                dimension = dim;
                stations.putAll(batch);
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            stations.clear(); dimension = null; nextRequest = 0;
        });
    }
    public static Map<BlockPos, Direction> current() {
        var client = MinecraftClient.getInstance();
        if (client.world == null) return Map.of();
        Identifier dim = client.world.getRegistryKey().getValue();
        if (!dim.equals(dimension)) { stations.clear(); dimension = dim; nextRequest = 0; }
        long now = System.nanoTime();
        if (now >= nextRequest && ClientPlayNetworking.canSend(FlightMapNetworking.ID)) {
            nextRequest = now + 2_000_000_000L;
            ClientPlayNetworking.send(FlightMapNetworking.ID, PacketByteBufs.create());
        }
        return stations;
    }
}
