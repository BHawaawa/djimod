package com.example.avatadrone.mixin.client;

import com.example.avatadrone.client.DroneChunkClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class DroneClientNetworkMixin {
    @Shadow private ClientWorld world;
    @Inject(method = "unloadChunk", at = @At("HEAD"), cancellable = true)
    private void retainRemoteLighting(UnloadChunkS2CPacket packet, CallbackInfo ci) {
        if (DroneChunkClient.retains(world,packet.getX(),packet.getZ())) ci.cancel();
    }
}
