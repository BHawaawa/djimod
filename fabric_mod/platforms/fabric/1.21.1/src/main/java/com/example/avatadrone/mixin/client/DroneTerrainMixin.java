package com.example.avatadrone.mixin.client;

import com.example.avatadrone.client.Rc2View;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.ChunkRenderingDataPreparer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Vanilla moved terrain traversal out of WorldRenderer in 1.21. */
@Mixin(ChunkRenderingDataPreparer.class)
public abstract class DroneTerrainMixin {
    @WrapOperation(method = "method_52831", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/math/MathHelper;abs(I)I"), require = 1)
    private int cameraVerticalDistance(int distance, Operation<Integer> original) {
        return original.call(Rc2View.isFlightView() && !Rc2View.isRenderingAssist() ? 0 : distance);
    }
}
