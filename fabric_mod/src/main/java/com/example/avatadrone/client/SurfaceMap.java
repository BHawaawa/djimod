package com.example.avatadrone.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkStatus;

/** Bounded fallback map of loaded terrain. Never requests or fabricates unseen chunks. */
public final class SurfaceMap {
    private record Sample(int color, long tick) {}
    private static final Long2ObjectLinkedOpenHashMap<Sample> CACHE = new Long2ObjectLinkedOpenHashMap<>();
    private static final BlockPos.Mutable SAMPLE_POS = new BlockPos.Mutable();
    private static ClientWorld cachedWorld;
    private static int budget;
    private SurfaceMap() {}
    public static void clear() { CACHE.clear(); cachedWorld = null; }
    public static void beginFrame() { budget = 700; }
    public static int colorAt(ClientWorld world, int x, int z) {
        if (world != cachedWorld) { CACHE.clear(); cachedWorld = world; }
        long key = ((long)x << 32) ^ (z & 0xffffffffL);
        Sample hit = CACHE.getAndMoveToLast(key);
        long now = world.getTime();
        if (hit != null && now >= hit.tick && now - hit.tick < 100) return hit.color;
        if (world.getChunkManager().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false) == null) return 0;
        if (budget-- <= 0) return hit == null ? 0 : hit.color;
        int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z) - 1;
        int color = 0;
        if (y >= world.getBottomY()) {
            BlockPos pos = SAMPLE_POS.set(x, y, z);
            var state = world.getBlockState(pos);
            int rgb = state.getMapColor(world, pos).color & 0xffffff;
            if (!state.isAir() && rgb != 0) color = 0xff000000 | rgb;
        }
        if (hit == null && CACHE.size() >= 60000) CACHE.removeFirst();
        // Cache empty loaded columns too, so void terrain cannot exhaust every
        // frame's sampling budget before the scan reaches visible land.
        CACHE.put(key, new Sample(color, now));
        return color;
    }
}
