package com.example.avatadrone.mixin.client;

import com.example.avatadrone.client.Rc2View;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={WorldRenderer.class})
public abstract class AssistRenderMixin {
    @Inject(method={"render"}, at={@At(value="HEAD")})
    private void avatadrone$forceAssistCamera(MatrixStack matrixStack, float f, long l, boolean bl, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f matrix4f, CallbackInfo callbackInfo) {
        if (Rc2View.isRenderingAssist()) {
            Rc2View.configureFeedCamera(camera, matrixStack);
        }
    }

    @Inject(method="renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V", at=@At("HEAD"), cancellable=true)
    private void avatadrone$noAssistSky(MatrixStack matrixStack, Matrix4f matrix4f, float f, Camera camera, boolean bl, Runnable runnable, CallbackInfo callbackInfo) {
        if (Rc2View.isRenderingAssist()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method={"renderClouds"}, at={@At(value="HEAD")}, cancellable=true)
    private void avatadrone$noAssistClouds(MatrixStack matrixStack, Matrix4f matrix4f, float f, double d, double d2, double d3, CallbackInfo callbackInfo) {
        if (Rc2View.isRenderingAssist()) {
            callbackInfo.cancel();
        }
    }
}

