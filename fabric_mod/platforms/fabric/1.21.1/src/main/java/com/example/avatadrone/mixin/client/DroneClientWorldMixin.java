package com.example.avatadrone.mixin.client;

import com.example.avatadrone.client.DroneChunkClient;
import com.example.avatadrone.client.Rc2View;
import com.example.avatadrone.entity.AvataDroneEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public abstract class DroneClientWorldMixin {
    @Unique private AvataDroneEntity avatadrone$camera;
    @Unique private int avatadrone$age;

    @Inject(method = "unloadBlockEntities", at = @At("HEAD"), cancellable = true)
    private void retainRemoteBlockEntities(WorldChunk chunk, CallbackInfo ci) {
        if (DroneChunkClient.retains((ClientWorld)(Object)this, chunk)) ci.cancel();
    }

    @Inject(method = "tickEntities", at = @At("HEAD"))
    private void recordCameraTick(CallbackInfo ci) {
        avatadrone$camera = Rc2View.isVisible() ? Rc2View.drone() : null;
        if (avatadrone$camera != null) avatadrone$age = avatadrone$camera.age;
    }

    @Inject(method = "tickEntities", at = @At("TAIL"))
    private void tickRemoteCamera(CallbackInfo ci) {
        AvataDroneEntity camera = avatadrone$camera;
        avatadrone$camera = null;
        ClientWorld world = (ClientWorld)(Object)this;
        // ClientEntityManager only ticks sections loaded by the pilot. A distant
        // drone must finish network interpolation even while its camera chunks arrive.
        // Checking age prevents a second tick when vanilla already handled it.
        if (camera != null && camera.getWorld() == world && camera.age == avatadrone$age
                && !camera.isRemoved() && !camera.hasVehicle() && !world.getTickManager().shouldSkipTick(camera)) {
            world.tickEntity(camera);
        }
    }
}
