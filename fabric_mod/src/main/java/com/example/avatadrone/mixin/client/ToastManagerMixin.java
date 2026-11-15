package com.example.avatadrone.mixin.client;

import com.example.avatadrone.client.Rc2View;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.toast.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla toasts (tutorial "Move with W, A, S and D", advancements, recipes) are drawn after the HUD
 *  and would sit on top of the RC 2 screen; hide them while the controller view is open. */
@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {
    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void avatadrone$hideOverController(DrawContext context, CallbackInfo ci) {
        if (Rc2View.isVisible()) ci.cancel();
    }
}
