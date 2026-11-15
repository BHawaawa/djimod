package com.example.avatadrone.mixin.client;

import com.example.avatadrone.client.Rc2View;
import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public abstract class MouseMixin {
    @Shadow private double x;
    @Shadow private double y;

    @Inject(method = "lockCursor", at = @At("HEAD"), cancellable = true)
    private void avatadrone$keepCursorFree(CallbackInfo ci) {
        if (Rc2View.isControllerActive()) ci.cancel();
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void avatadrone$handleControllerClick(long window, int button, int action, int mods, CallbackInfo ci) {
        if (window != MinecraftClient.getInstance().getWindow().getHandle()) return;
        if (com.example.avatadrone.client.charger.ChargingHud.slotMouse(window,button,action)) { ci.cancel(); return; }
        if (Rc2View.onMouseButton(button, action, x, y)) ci.cancel();
    }

    @Inject(method = "onCursorPos", at = @At("HEAD"))
    private void avatadrone$moveVirtualStick(long window, double x, double y, CallbackInfo ci) {
        if (window != MinecraftClient.getInstance().getWindow().getHandle()) return;
        Rc2View.onMouseMove(x, y);
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void avatadrone$adjustStickAmount(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (Rc2View.onMouseScroll(vertical)) ci.cancel();
    }

}
