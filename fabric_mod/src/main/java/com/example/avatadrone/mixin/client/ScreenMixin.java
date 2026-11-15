package com.example.avatadrone.mixin.client;

import com.example.avatadrone.client.Rc2View;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void avatadrone$exitOnEscape(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (com.example.avatadrone.client.DroneKeys.EXIT.matchesKey(keyCode,scanCode) && Rc2View.onEscape()) cir.setReturnValue(true);
    }

    @Inject(method = "shouldPause", at = @At("HEAD"), cancellable = true)
    private void avatadrone$keepWorldRunning(CallbackInfoReturnable<Boolean> cir) {
        if (Rc2View.isFlightView()) cir.setReturnValue(false);
    }
}
