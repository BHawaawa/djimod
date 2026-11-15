package com.example.avatadrone.mixin.client;

import com.example.avatadrone.client.Rc2View;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.client.render.FogShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {
    @Inject(method = "applyFog", at = @At("TAIL"))
    private static void aerialTerrainFog(Camera camera, BackgroundRenderer.FogType type,
            float distance, boolean thickFog, float tickDelta, CallbackInfo ci) {
        if (!Rc2View.isRenderingFeed() || type != BackgroundRenderer.FogType.FOG_TERRAIN
                || thickFog || camera.getSubmersionType() != CameraSubmersionType.NONE) return;
        // The ground is further away vertically, but horizontal chunk coverage has
        // not increased. Spherical distance keeps the ground visible while fading
        // the actual terrain boundary, instead of revealing missing chunk edges.
        float horizontalEnd = Math.max(32, (com.example.avatadrone.client.DroneChunkClient.viewRadius() - 1) * 16);
        double height = Rc2View.drone() == null ? 0 : Rc2View.drone().getGroundDistance();
        RenderSystem.setShaderFogShape(FogShape.SPHERE);
        RenderSystem.setShaderFogStart((float) Math.hypot(height, horizontalEnd * 0.7));
        RenderSystem.setShaderFogEnd((float) Math.hypot(height, horizontalEnd));
    }
}
