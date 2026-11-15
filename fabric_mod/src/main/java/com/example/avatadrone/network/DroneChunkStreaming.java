package com.example.avatadrone.network;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.entity.AvataDroneEntity;
import com.example.avatadrone.item.Rc2Item;
import com.mojang.datafixers.util.Either;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/** A second, bounded chunk subscription for the camera; the pilot's subscription stays intact. */
public final class DroneChunkStreaming {
    public static final Identifier VIEW = new Identifier(AvataDrone.MOD_ID, "drone_chunk_view");
    public static final Identifier DATA = new Identifier(AvataDrone.MOD_ID, "drone_chunk_data");
    /** Minecraft's maximum configurable view distance. The camera feed follows
     * the player's render-distance option instead of imposing a smaller cap. */
    public static final int MAX_RADIUS = 32;
    private static final int MAX_PENDING = 8;
    private static final ChunkTicketType<UUID> TICKET = ChunkTicketType.create("avatadrone_camera", UUID::compareTo);
    private static final Map<UUID, Session> sessions = new HashMap<>();
    private static long nextId;

    private static final class Session {
        final ServerPlayerEntity player;
        final ServerWorld world;
        final UUID droneId;
        final long id = ++nextId;
        int radius, lastHeartbeat, centerX = Integer.MIN_VALUE, centerZ;
        final Set<Long> desired = new HashSet<>(), tickets = new HashSet<>(), sent = new HashSet<>();
        final Map<Long, CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>>> pending = new LinkedHashMap<>();
        Session(ServerPlayerEntity player, UUID droneId, int radius, int tick) {
            this.player = player; this.world = player.getServerWorld(); this.droneId = droneId;
            this.radius = radius; this.lastHeartbeat = tick;
        }
    }

    private DroneChunkStreaming() {}
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(VIEW, (server, player, handler, buf, response) -> {
            boolean active = buf.readBoolean();
            UUID id = active ? buf.readUuid() : null;
            int distance = active ? buf.readVarInt() : 0;
            server.execute(() -> request(server, player, id, distance));
        });
        ServerTickEvents.END_SERVER_TICK.register(DroneChunkStreaming::tick);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> close(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> sessions.clear());
    }

    private static AvataDroneEntity target(ServerPlayerEntity player, UUID id) {
        if (!player.isAlive() || player.isSpectator()) return null;
        ItemStack rc = player.getMainHandStack().isOf(AvataDrone.DJI_RC2) ? player.getMainHandStack() : player.getOffHandStack();
        if (!Rc2Item.hasPower(rc) || !rc.hasNbt() || !rc.getNbt().containsUuid(Rc2Item.DRONE)
                || !id.equals(rc.getNbt().getUuid(Rc2Item.DRONE))
                || !player.getWorld().getRegistryKey().getValue().toString().equals(rc.getNbt().getString(Rc2Item.DIMENSION))) return null;
        var entity = player.getServerWorld().getEntity(id);
        return entity instanceof AvataDroneEntity drone && drone.isAlive() && drone.hasControlSignal(player) ? drone : null;
    }

    private static void request(MinecraftServer server, ServerPlayerEntity player, UUID id, int distance) {
        if (id == null || target(player, id) == null || !ServerPlayNetworking.canSend(player, DATA)) { close(player); return; }
        int radius = Math.max(2, Math.min(MAX_RADIUS, Math.min(distance, server.getPlayerManager().getViewDistance())));
        Session s = sessions.get(player.getUuid());
        if (s != null && (s.world != player.getServerWorld() || !s.droneId.equals(id) || s.radius != radius)) {
            close(player); s = null;
        }
        if (s == null) { s = new Session(player, id, radius, server.getTicks()); sessions.put(player.getUuid(), s); }
        s.lastHeartbeat = server.getTicks();
    }

    private static PacketByteBuf message(Session s, int type) {
        var buf = PacketByteBufs.create();
        buf.writeByte(type); buf.writeLong(s.id);
        buf.writeIdentifier(s.world.getRegistryKey().getValue());
        buf.writeUuid(s.droneId);
        return buf;
    }

    private static void tick(MinecraftServer server) {
        for (Session s : List.copyOf(sessions.values())) {
            AvataDroneEntity drone = s.player.getServerWorld() == s.world ? target(s.player, s.droneId) : null;
            if (s.player.isDisconnected() || server.getTicks() - s.lastHeartbeat > 40 || drone == null) { close(s.player); continue; }
            ChunkPos center = drone.getChunkPos();
            if (center.x != s.centerX || center.z != s.centerZ) move(s, center);
            var manager = s.world.getChunkManager();
            int sentThisTick = 0;
            var iterator = s.pending.entrySet().iterator();
            while (iterator.hasNext() && sentThisTick < 2) {
                var entry = iterator.next();
                var future = entry.getValue();
                if (!future.isDone()) continue;
                iterator.remove();
                if (future.isCompletedExceptionally() || future.isCancelled()) continue;
                Chunk chunk = future.getNow(ChunkHolder.UNLOADED_CHUNK).left().orElse(null);
                if (!(chunk instanceof WorldChunk worldChunk) || !s.desired.contains(entry.getKey())) continue;
                var buf = message(s, 1);
                new ChunkDataS2CPacket(worldChunk, manager.getLightingProvider(), null, null).write(buf);
                ServerPlayNetworking.send(s.player, DATA, buf);
                s.sent.add(entry.getKey()); sentThisTick++;
            }
            // Nearest first, with bounded asynchronous generation. Never join a world-generation future.
            var candidates = s.desired.stream().filter(p -> !s.sent.contains(p) && !s.pending.containsKey(p))
                    .sorted(Comparator.comparingLong(p -> distanceSquared(new ChunkPos(p), center))).toList();
            int started = 0;
            for (long key : candidates) {
                if (s.pending.size() >= MAX_PENDING || started++ >= 2) break;
                ChunkPos pos = new ChunkPos(key);
                if (s.tickets.add(key)) manager.addTicket(TICKET, pos, 0, s.player.getUuid());
                s.pending.put(key, ((com.example.avatadrone.mixin.ServerChunkManagerAccessor) manager)
                        .avatadrone$requestChunk(pos.x, pos.z, ChunkStatus.FULL, true));
            }
            // Refresh vanilla entity tracking as remote chunks become visible, even when the pilot stands still.
            if (sentThisTick > 0 || server.getTicks() % 10 == 0)
                manager.threadedAnvilChunkStorage.updatePosition(s.player);
        }
    }

    private static long distanceSquared(ChunkPos a, ChunkPos b) {
        long x = a.x - b.x, z = a.z - b.z;
        return x*x + z*z;
    }
    private static void move(Session s, ChunkPos center) {
        s.centerX = center.x; s.centerZ = center.z;
        s.desired.clear();
        for (int x = center.x-s.radius; x <= center.x+s.radius; x++)
            for (int z = center.z-s.radius; z <= center.z+s.radius; z++) s.desired.add(ChunkPos.toLong(x,z));
        for (long key : Set.copyOf(s.tickets)) if (!s.desired.contains(key)) {
            s.world.getChunkManager().removeTicket(TICKET, new ChunkPos(key), 0, s.player.getUuid());
            s.tickets.remove(key); s.pending.remove(key); s.sent.remove(key);
        }
        var buf = message(s, 0);
        buf.writeInt(center.x); buf.writeInt(center.z); buf.writeVarInt(s.radius);
        ServerPlayNetworking.send(s.player, DATA, buf);
    }
    private static void close(ServerPlayerEntity player) {
        Session s = sessions.remove(player.getUuid());
        if (s == null) return;
        for (long key : s.tickets) s.world.getChunkManager().removeTicket(TICKET, new ChunkPos(key), 0, player.getUuid());
        s.pending.clear();
        if (!player.isDisconnected()) {
            ServerPlayNetworking.send(player, DATA, message(s, 2));
            if (player.getServerWorld() == s.world) s.world.getChunkManager().threadedAnvilChunkStorage.updatePosition(player);
        }
    }
    public static boolean watches(ServerPlayerEntity player, ChunkPos pos) {
        Session s = sessions.get(player.getUuid());
        return s != null && s.world == player.getServerWorld() && s.sent.contains(pos.toLong());
    }
    public static List<ServerPlayerEntity> viewers(ServerWorld world, ChunkPos pos) {
        return sessions.values().stream().filter(s -> s.world == world && s.sent.contains(pos.toLong())).map(s -> s.player).toList();
    }
}
