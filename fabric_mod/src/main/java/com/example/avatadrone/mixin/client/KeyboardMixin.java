package com.example.avatadrone.mixin.client;

import com.example.avatadrone.client.Rc2View;
import net.minecraft.client.Keyboard;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public abstract class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void avatadrone$consumeControllerKeys(long window, int key, int scancode, int action,
            int modifiers, CallbackInfo ci) {
        if(window != net.minecraft.client.MinecraftClient.getInstance().getWindow().getHandle()) return;
        if (Rc2View.onScreenOnlyKey(key, action)) { ci.cancel(); return; }
        if (com.example.avatadrone.client.charger.ChargingHud.slotKey(window, key, scancode, action, modifiers)) {
            ci.cancel(); return;
        }
        if ((com.example.avatadrone.client.DroneKeys.ENTER.matchesKey(key,scancode)
                || com.example.avatadrone.client.DroneKeys.ENTER_ALT.matchesKey(key,scancode)) && action == GLFW.GLFW_PRESS
                && Rc2View.canEnterFromKey()) {
            Rc2View.requestEnterFromKey();
            ci.cancel();
            return;
        }
        if (action == GLFW.GLFW_PRESS && com.example.avatadrone.client.DroneKeys.DISCONNECT.matchesKey(key,scancode)
                && Rc2View.disconnectFromKey()) { ci.cancel(); return; }
        if (!Rc2View.isControllerActive()) return;
        if (com.example.avatadrone.client.DroneKeys.EXIT.matchesKey(key,scancode) && action == GLFW.GLFW_PRESS) {
            Rc2View.onEscape();
            ci.cancel();
            return;
        }
        if (com.example.avatadrone.client.DroneKeys.flightKey(key,scancode)) ci.cancel();
    }
}
