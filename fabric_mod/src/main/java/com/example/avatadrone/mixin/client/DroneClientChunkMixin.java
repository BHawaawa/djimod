package com.example.avatadrone.mixin.client;

import com.example.avatadrone.client.DroneChunkClient;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.world.chunk.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.function.Consumer;

@Mixin(ClientChunkManager.class)
public abstract class DroneClientChunkMixin {
    @Shadow @Final private ClientWorld world;
    @Shadow @Final private WorldChunk emptyChunk;

    @Inject(method = "unload", at = @At("TAIL"))
    private void avatadrone$retainRendererColumn(int x, int z, CallbackInfo ci) {
        if (DroneChunkClient.retains(world, x, z))
            com.example.avatadrone.client.SodiumChunkCompatibility.loaded(world, x, z);
    }

    @Inject(method = "getChunk(IILnet/minecraft/world/chunk/ChunkStatus;Z)Lnet/minecraft/world/chunk/WorldChunk;", at = @At("RETURN"), cancellable = true)
    private void extraChunk(int x, int z, ChunkStatus status, boolean create, CallbackInfoReturnable<WorldChunk> cir) {
        if (cir.getReturnValue() != null && cir.getReturnValue() != emptyChunk) return;
        WorldChunk extra = DroneChunkClient.extra(world, x, z);
        if (extra != null) cir.setReturnValue(extra);
    }
    @Inject(method = "loadChunkFromPacket", at = @At("HEAD"), cancellable = true)
    private void loadRemote(int x, int z, PacketByteBuf buf, NbtCompound heightmap,
            Consumer<ChunkData.BlockEntityVisitor> entities, CallbackInfoReturnable<WorldChunk> cir) {
        if (DroneChunkClient.isReceiving(world)) cir.setReturnValue(DroneChunkClient.load(x,z,buf,heightmap,entities));
    }
    @Inject(method = "loadChunkFromPacket", at = @At("RETURN"))
    private void keepLatestNormal(int x, int z, PacketByteBuf buf, NbtCompound heightmap,
            Consumer<ChunkData.BlockEntityVisitor> entities, CallbackInfoReturnable<WorldChunk> cir) {
        DroneChunkClient.receivedNormal(world,x,z,cir.getReturnValue());
    }
    @Inject(method = "onChunkBiomeData", at = @At("HEAD"), cancellable = true)
    private void remoteBiome(int x, int z, PacketByteBuf buf, CallbackInfo ci) {
        WorldChunk extra = DroneChunkClient.extra(world,x,z);
        if (extra != null) { extra.loadBiomeFromPacket(buf); world.resetChunkColor(extra.getPos()); ci.cancel(); }
    }
}
