package com.example.avatadrone.mixin.client;

import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessor {
    @Invoker("setPos")
    void avatadrone$setPos(double x, double y, double z);
    @Invoker("setRotation")
    void avatadrone$setRotation(float yaw, float pitch);
}
