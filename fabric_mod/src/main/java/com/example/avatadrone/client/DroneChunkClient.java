package com.example.avatadrone.client;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.item.Rc2Item;
import com.example.avatadrone.network.DroneChunkStreaming;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** A separate cache avoids overwriting the terrain underneath the stationary pilot. */
public final class DroneChunkClient {
    private static final Map<Long, WorldChunk> chunks = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> vanillaLookup = ThreadLocal.withInitial(() -> false);
    private static volatile ClientWorld world;
    private static UUID requested;
    private static long session = -1;
    private static int centerX, centerZ, radius = -1, ticks;
    private static boolean receiving;
    private DroneChunkClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(DroneChunkStreaming.DATA, (client, handler, buf, response) -> {
            int type = buf.readUnsignedByte();
            long id = buf.readLong();
            var dimension = buf.readIdentifier();
            UUID aircraft = buf.readUuid();
            int x = type == 0 ? buf.readInt() : 0, z = type == 0 ? buf.readInt() : 0;
            int range = type == 0 ? buf.readVarInt() : 0;
            ChunkDataS2CPacket packet = type == 1 ? new ChunkDataS2CPacket(buf) : null;
            client.execute(() -> {
                if (client.getNetworkHandler() != handler || client.world == null
                        || !client.world.getRegistryKey().getValue().equals(dimension)
                        || !Objects.equals(requested, aircraft)) return;
                if (type == 0) {
                    if (world != client.world || session != id) clear();
                    world = client.world; session = id; centerX = x; centerZ = z;
                    radius = Math.max(0, Math.min(range, DroneChunkStreaming.MAX_RADIUS));
                    prune();
                } else if (session == id && type == 1 && wanted(packet.getX(), packet.getZ())) {
                    receiving = true;
                    try { handler.onChunkData(packet); } finally { receiving = false; }
                } else if (session == id && type == 2) clear();
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (world != null && world != client.world) clear();
            UUID next = null;
            if (client.player != null && Rc2View.isVisible()) {
                var rc = client.player.getMainHandStack().isOf(AvataDrone.DJI_RC2)
                        ? client.player.getMainHandStack() : client.player.getOffHandStack();
                if (rc.isOf(AvataDrone.DJI_RC2) && rc.hasNbt() && rc.getNbt().containsUuid(Rc2Item.DRONE))
                    next = rc.getNbt().getUuid(Rc2Item.DRONE);
            }
            boolean changed = !Objects.equals(next, requested);
            if (changed) { clear(); requested = next; }
            if (ClientPlayNetworking.canSend(DroneChunkStreaming.VIEW) && (changed || next != null && ++ticks % 10 == 0)) {
                var buf = PacketByteBufs.create(); buf.writeBoolean(next != null);
                if (next != null) { buf.writeUuid(next); buf.writeVarInt(client.options.getViewDistance().getValue()); }
                ClientPlayNetworking.send(DroneChunkStreaming.VIEW, buf);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { clear(); requested = null; });
    }

    public static boolean isReceiving(ClientWorld candidate) { return receiving && world == candidate; }
    public static int viewRadius() {
        return radius >= 2 ? radius : Math.min(DroneChunkStreaming.MAX_RADIUS,
                MinecraftClient.getInstance().options.getClampedViewDistance());
    }
    private static boolean wanted(int x, int z) { return radius >= 0 && Math.abs(x-centerX) <= radius && Math.abs(z-centerZ) <= radius; }
    public static WorldChunk extra(ClientWorld candidate, int x, int z) {
        return candidate == world && !vanillaLookup.get() ? chunks.get(ChunkPos.toLong(x,z)) : null;
    }
    public static boolean retains(ClientWorld candidate, WorldChunk chunk) {
        return candidate == world && chunks.get(chunk.getPos().toLong()) == chunk;
    }
    public static boolean retains(ClientWorld candidate, int x, int z) {
        return candidate == world && chunks.containsKey(ChunkPos.toLong(x,z));
    }
    private static WorldChunk normal(ClientWorld candidate, int x, int z) {
        vanillaLookup.set(true);
        try { return candidate.getChunkManager().getChunk(x, z, ChunkStatus.FULL, false); }
        finally { vanillaLookup.set(false); }
    }
    public static WorldChunk load(int x, int z, PacketByteBuf buf, NbtCompound heightmap,
                                  Consumer<ChunkData.BlockEntityVisitor> entities) {
        long key = ChunkPos.toLong(x,z);
        WorldChunk chunk = normal(world, x, z);
        if (chunk == null) chunk = chunks.get(key);
        if (chunk == null) chunk = new WorldChunk(world, new ChunkPos(x,z));
        chunk.loadFromPacket(buf, heightmap, entities);
        WorldChunk old = chunks.put(key, chunk);
        // Another cache now owns this column: clear only the old block entities,
        // not the shared lighting column or entity-ticking state.
        if (old != null && old != chunk) old.clear();
        world.resetChunkColor(chunk.getPos());
        // The early return in ClientChunkManager bypasses Sodium's normal load hook.
        SodiumChunkCompatibility.loaded(world, x, z);
        return chunk;
    }
    public static void receivedNormal(ClientWorld candidate, int x, int z, WorldChunk chunk) {
        if (receiving || candidate != world || chunk == null || !chunks.containsKey(ChunkPos.toLong(x,z))) return;
        WorldChunk old = chunks.put(ChunkPos.toLong(x,z), chunk);
        if (old != null && old != chunk) old.clear();
    }
    private static void prune() {
        for (long key : Set.copyOf(chunks.keySet())) {
            ChunkPos pos = new ChunkPos(key);
            if (!wanted(pos.x, pos.z)) release(key);
        }
    }
    private static void release(long key) {
        WorldChunk removed = chunks.remove(key);
        if (removed == null || world == null) return;
        ChunkPos pos = removed.getPos();
        WorldChunk local = normal(world, pos.x, pos.z);
        if (local == null) SodiumChunkCompatibility.unloaded(world, pos.x, pos.z);
        if (local == null) world.unloadBlockEntities(removed);
        else if (local != removed) removed.clear();
        var client = MinecraftClient.getInstance();
        if (local == null && client.world == world && client.getNetworkHandler() != null)
            client.getNetworkHandler().onUnloadChunk(new UnloadChunkS2CPacket(pos.x, pos.z));
    }
    private static void clear() {
        radius = -1;
        for (long key : Set.copyOf(chunks.keySet())) release(key);
        chunks.clear(); world = null; session = -1;
    }
}
