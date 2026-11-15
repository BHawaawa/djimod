package com.example.avatadrone.mixin.client;

import com.example.avatadrone.client.Rc2View;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void avatadrone$renderController(DrawContext context, float tickDelta, CallbackInfo ci) {
        if (!Rc2View.isVisible()) return;
        Rc2View.renderHud(context, tickDelta);
        ci.cancel();
    }
}
