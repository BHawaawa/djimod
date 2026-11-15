package com.example.avatadrone.mixin.client;

import com.example.avatadrone.client.Rc2View;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.RotationAxis;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix3f;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/GameRenderer;renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V"))
    private void avatadrone$capture(GameRenderer renderer, RenderTickCounter counter, Operation<Void> original) {
        if (Rc2View.isVisible()) {
            // Keep the full renderWorld lifecycle, including Iris and Sodium hooks.
            Rc2View.capture(counter.getTickDelta(false), () -> original.call(renderer, counter));
        } else {
            Rc2View.close();
            original.call(renderer, counter);
        }
    }

    @Inject(method = "renderWorld", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;update(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V",
            shift = At.Shift.AFTER))
    private void avatadrone$camera(RenderTickCounter counter, CallbackInfo ci) {
        if (Rc2View.isRenderingFeed())
            Rc2View.configureFeedCamera(((GameRenderer)(Object)this).getCamera(), new MatrixStack());
    }

    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true)
    private void avatadrone$feedFov(Camera camera, float tickDelta, boolean changingFov,
            CallbackInfoReturnable<Double> cir) {
        if (Rc2View.isRenderingFeed()) {
            ((GameRendererAccessor)this).avatadrone$setViewDistance(Rc2View.feedDistance());
            cir.setReturnValue(Rc2View.feedFov());
        }
    }

    @Inject(method = "getBasicProjectionMatrix", at = @At("HEAD"), cancellable = true)
    private void avatadrone$feedProjection(double fov, CallbackInfoReturnable<Matrix4f> cir) {
        if (Rc2View.isRenderingFeed()) cir.setReturnValue(Rc2View.feedProjection());
    }

    @Inject(method = {"tiltViewWhenHurt", "bobView", "renderHand"}, at = @At("HEAD"), cancellable = true)
    private void avatadrone$stableCamera(CallbackInfo ci) {
        if (Rc2View.isRenderingFeed()) ci.cancel();
    }

    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void avatadrone$hideSelection(CallbackInfoReturnable<Boolean> cir) {
        if (Rc2View.isRenderingFeed()) cir.setReturnValue(false);
    }

    // 1.21 uses the camera quaternion to construct the view matrix. Configuring the
    // camera above handles both the main feed and the obstacle-assistance pass.
}
