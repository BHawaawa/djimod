package com.example.avatadrone.mixin;

import com.example.avatadrone.network.DroneChunkStreaming;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ThreadedAnvilChunkStorage;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

/** Reuse vanilla block/light/biome deltas after the initial camera chunk snapshot. */
@Mixin(ThreadedAnvilChunkStorage.class)
public abstract class DroneChunkWatchMixin {
    @Shadow @Final private ServerWorld world;
    @Inject(method = "getPlayersWatchingChunk(Lnet/minecraft/util/math/ChunkPos;Z)Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private void includeCameraViewers(ChunkPos pos, boolean edgeOnly, CallbackInfoReturnable<List<ServerPlayerEntity>> cir) {
        List<ServerPlayerEntity> remote = DroneChunkStreaming.viewers(world, pos);
        if (remote.isEmpty()) return;
        List<ServerPlayerEntity> combined = new ArrayList<>(cir.getReturnValue());
        for (ServerPlayerEntity player : remote) if (!combined.contains(player)) combined.add(player);
        cir.setReturnValue(combined);
    }
}
