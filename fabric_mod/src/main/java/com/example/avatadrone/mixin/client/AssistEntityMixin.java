package com.example.avatadrone.mixin.client;

import com.example.avatadrone.client.Rc2View;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={EntityRenderDispatcher.class})
public abstract class AssistEntityMixin {
    @Inject(method={"render"}, at={@At(value="HEAD")}, cancellable=true)
    private void avatadrone$hideAssistEntities(Entity entity, double d, double d2, double d3, float f, float f2, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int n, CallbackInfo callbackInfo) {
        if (Rc2View.isRenderingAssist()) {
            callbackInfo.cancel();
        }
    }
}

