package com.example.avatadrone.mixin.client;

import com.example.avatadrone.client.DroneChunkClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public abstract class DroneClientWorldMixin {
    @Inject(method = "unloadBlockEntities", at = @At("HEAD"), cancellable = true)
    private void retainRemoteBlockEntities(WorldChunk chunk, CallbackInfo ci) {
        if (DroneChunkClient.retains((ClientWorld)(Object)this, chunk)) ci.cancel();
    }
}
