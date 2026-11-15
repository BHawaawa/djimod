package com.example.avatadrone.mixin;

import com.mojang.datafixers.util.Either;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.util.concurrent.CompletableFuture;

@Mixin(ServerChunkManager.class)
public interface ServerChunkManagerAccessor {
    // The public SyncOnMainThread wrapper pumps tasks until completion, so camera
    // generation must call the nonblocking implementation from the server thread.
    @Invoker("getChunkFuture")
    CompletableFuture<Either<Chunk, ChunkHolder.Unloaded>> avatadrone$requestChunk(int x, int z, ChunkStatus status, boolean create);
}
