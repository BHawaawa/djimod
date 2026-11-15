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
            target = "Lnet/minecraft/client/render/GameRenderer;renderWorld(FJLnet/minecraft/client/util/math/MatrixStack;)V"))
    private void avatadrone$capture(GameRenderer renderer, float tickDelta, long limitTime,
            MatrixStack matrices, Operation<Void> original) {
        if (Rc2View.isVisible()) {
            // Keep the full renderWorld lifecycle, including Iris and Sodium hooks.
            Rc2View.capture(tickDelta, () -> original.call(renderer, tickDelta, limitTime, matrices));
        } else {
            Rc2View.close();
            original.call(renderer, tickDelta, limitTime, matrices);
        }
    }

    @Inject(method = "renderWorld", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;update(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V",
            shift = At.Shift.AFTER))
    private void avatadrone$camera(float tickDelta, long limitTime, MatrixStack matrices, CallbackInfo ci) {
        if (Rc2View.isRenderingFeed())
            Rc2View.configureFeedCamera(((GameRenderer)(Object)this).getCamera(), matrices);
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

    @WrapOperation(method="renderWorld", at=@At(value="INVOKE", target="Lnet/minecraft/client/render/WorldRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;FJZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lnet/minecraft/client/render/LightmapTextureManager;Lorg/joml/Matrix4f;)V"), require=0)
    private void avatadrone$assistWorldRender(WorldRenderer worldRenderer, MatrixStack matrixStack, float f, long l, boolean bl, Camera camera, GameRenderer gameRenderer, LightmapTextureManager lightmapTextureManager, Matrix4f matrix4f, Operation<Void> operation) {
        if (!Rc2View.isRenderingAssist() || Rc2View.drone() == null) {
            operation.call(worldRenderer, matrixStack, Float.valueOf(f), l, bl, camera, gameRenderer, lightmapTextureManager, matrix4f);
            return;
        }
        Camera camera2 = new Camera();
        camera2.update(MinecraftClient.getInstance().world, Rc2View.drone(), false, false, f);
        MatrixStack matrixStack2 = new MatrixStack();
        Rc2View.configureFeedCamera(camera2, matrixStack2);
        matrixStack2.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera2.getPitch()));
        matrixStack2.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera2.getYaw() + 180.0f));
        Matrix4f matrix4f2 = Rc2View.feedProjection();
        worldRenderer.setupFrustum(matrixStack2, camera2.getPos(), matrix4f2);
        RenderSystem.setInverseViewRotationMatrix(new Matrix3f(matrixStack2.peek().getNormalMatrix()).invert());
        operation.call(worldRenderer, matrixStack2, Float.valueOf(f), l, bl, camera2, gameRenderer, lightmapTextureManager, matrix4f2);
    }
}
