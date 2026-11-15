package com.example.avatadrone.mixin.client;

import com.example.avatadrone.client.Rc2View;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/** Optional vanilla camera corrections; Sodium replaces terrain traversal entirely. */
@Mixin(value = WorldRenderer.class, priority = 900)
public abstract class WorldRendererMixin {
    @WrapOperation(method = "getAdjacentChunk", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/math/MathHelper;abs(I)I", ordinal = 1), require = 0)
    private int avatadrone$verticalTerrainDistance(int distance, Operation<Integer> original) {
        return original.call(Rc2View.isFlightView() && !Rc2View.isRenderingAssist() ? 0 : distance);
    }

    @WrapOperation(method = "setupTerrain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;getX()D"), require = 0)
    private double avatadrone$terrainX(ClientPlayerEntity player, Operation<Double> original) {
        double position = original.call(player);
        return Rc2View.isRenderingFeed() ? Rc2View.feedCameraPosition().x : position;
    }

    @WrapOperation(method = "setupTerrain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;getY()D"), require = 0)
    private double avatadrone$terrainY(ClientPlayerEntity player, Operation<Double> original) {
        double position = original.call(player);
        return Rc2View.isRenderingFeed() ? Rc2View.feedCameraPosition().y : position;
    }

    @WrapOperation(method = "setupTerrain", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;getZ()D"), require = 0)
    private double avatadrone$terrainZ(ClientPlayerEntity player, Operation<Double> original) {
        double position = original.call(player);
        return Rc2View.isRenderingFeed() ? Rc2View.feedCameraPosition().z : position;
    }

    @WrapOperation(method = "renderSky(Lnet/minecraft/client/util/math/MatrixStack;Lorg/joml/Matrix4f;FLnet/minecraft/client/render/Camera;ZLjava/lang/Runnable;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;getCameraPosVec(F)Lnet/minecraft/util/math/Vec3d;"), require = 0)
    private Vec3d avatadrone$skyPosition(ClientPlayerEntity player, float tickDelta, Operation<Vec3d> original) {
        Vec3d position = original.call(player, tickDelta);
        return Rc2View.isRenderingFeed() ? Rc2View.feedCameraPosition() : position;
    }
}

