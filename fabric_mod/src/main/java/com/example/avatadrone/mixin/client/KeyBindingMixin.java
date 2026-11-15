package com.example.avatadrone.mixin.client;

import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla's single-entry key map drops other bindings sharing a physical key. */
@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin {
    @Shadow @Final private static Map<InputUtil.Key, KeyBinding> KEY_TO_BINDINGS;
    @Shadow private int timesPressed;

    @Inject(method = "setKeyPressed", at = @At("TAIL"))
    private static void avatadrone$sharedHeld(InputUtil.Key key, boolean pressed, CallbackInfo ci) {
        var client = MinecraftClient.getInstance();
        if (client.options == null) return;
        for (var binding : client.options.allKeys)
            if (binding != KEY_TO_BINDINGS.get(key) && KeyBindingHelper.getBoundKeyOf(binding).equals(key))
                binding.setPressed(pressed);
    }

    @Inject(method = "onKeyPressed", at = @At("TAIL"))
    private static void avatadrone$sharedPress(InputUtil.Key key, CallbackInfo ci) {
        var client = MinecraftClient.getInstance();
        if (client.options == null) return;
        for (var binding : client.options.allKeys)
            if (binding != KEY_TO_BINDINGS.get(key) && KeyBindingHelper.getBoundKeyOf(binding).equals(key))
                ((KeyBindingMixin)(Object)binding).timesPressed++;
    }
}
