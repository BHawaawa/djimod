package com.example.avatadrone.mixin.client;

import com.example.avatadrone.client.Rc2View;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={BlockEntityRenderDispatcher.class})
public abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method={"render"}, at={@At(value="HEAD")}, cancellable=true)
    private void avatadrone$noAssistBlockEntities(BlockEntity blockEntity, float f, MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, CallbackInfo callbackInfo) {
        if (Rc2View.isRenderingAssist()) {
            callbackInfo.cancel();
        }
    }
}

