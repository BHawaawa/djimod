package com.example.avatadrone.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.world.ClientWorld;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/** Bridges extra camera columns into Sodium 0.5's chunk readiness tracker. */
public final class SodiumChunkCompatibility {
    private static boolean initialized, disabled;
    private static Method tracker, addStatus, removeStatus;

    private SodiumChunkCompatibility() {}

    public static void loaded(ClientWorld world, int x, int z) { update(world, x, z, true); }
    public static void unloaded(ClientWorld world, int x, int z) { update(world, x, z, false); }

    private static void update(ClientWorld world, int x, int z, boolean loaded) {
        if (disabled) return;
        try {
            if (!initialized) {
                initialized = true;
                if (!FabricLoader.getInstance().isModLoaded("sodium")) { disabled = true; return; }
                // These are Sodium-owned names; no Minecraft method names are reflected.
                Class<?> holder = Class.forName("me.jellysquid.mods.sodium.client.render.chunk.map.ChunkTrackerHolder");
                tracker = holder.getMethod("get", ClientWorld.class);
                Class<?> type = tracker.getReturnType();
                addStatus = type.getMethod("onChunkStatusAdded", int.class, int.class, int.class);
                removeStatus = type.getMethod("onChunkStatusRemoved", int.class, int.class, int.class);
            }
            // Chunk data is bit 1. Vanilla's light packet handling supplies bit 2.
            // On eviction remove both, since the column has no other owner.
            (loaded ? addStatus : removeStatus).invoke(tracker.invoke(null, world), x, z, loaded ? 1 : 3);
        } catch (ReflectiveOperationException | LinkageError error) {
            disabled = true;
            LoggerFactory.getLogger("avatadrone/compat").warn(
                    "Sodium chunk tracker bridge unavailable; remote camera terrain may be incomplete", error);
        }
    }
}
