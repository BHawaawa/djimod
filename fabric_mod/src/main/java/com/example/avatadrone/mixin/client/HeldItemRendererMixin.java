package com.example.avatadrone.mixin.client;

import com.example.avatadrone.client.Rc2View;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @org.spongepowered.asm.mixin.Shadow
    protected abstract void renderArmHoldingItem(MatrixStack matrices, VertexConsumerProvider vertices,
            int light, float equipProgress, float swingProgress, net.minecraft.util.Arm arm);

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void avatadrone$hideHands(AbstractClientPlayerEntity player, float tickDelta, float pitch,
            Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
            VertexConsumerProvider vertices, int light, CallbackInfo ci) {
        if (Rc2View.isVisible()) { ci.cancel(); return; }
        ItemStack held = player.getStackInHand(hand);
        boolean mainDrone = player.getMainHandStack().getItem() instanceof com.example.avatadrone.item.DroneEquipmentItem main && main.aircraft;
        if (hand == Hand.OFF_HAND && mainDrone) { ci.cancel(); return; }
        if (!(held.getItem() instanceof com.example.avatadrone.item.DroneEquipmentItem equipment) || !equipment.aircraft) return;
        // Keep the whole aircraft and the extracted battery in view, rather than
        // clipping the animation against the normal bottom-right item position.
        if (!player.isInvisible()) {
            String action = com.example.avatadrone.item.DroneEquipmentItem.action(held);
            double seconds = held.hasNbt() ? (player.getWorld().getTime() + tickDelta
                    - held.getNbt().getLong("MaintenanceStart")) / 20.0 : 0;
            for (net.minecraft.util.Arm arm : net.minecraft.util.Arm.values()) {
                matrices.push();
                matrices.translate(arm == net.minecraft.util.Arm.RIGHT ? 0.12 : -0.12, -0.1, -0.25);
                float motion = action.isEmpty() || arm == net.minecraft.util.Arm.LEFT ? 0
                        : (float) (0.045 * Math.pow(Math.sin(Math.min(1, seconds / 3) * Math.PI), 2));
                renderArmHoldingItem(matrices, vertices, light, 0, motion, arm);
                matrices.pop();
            }
        }
        matrices.push();
        matrices.translate(0, -0.23, -1.35);
        net.minecraft.client.MinecraftClient.getInstance().getItemRenderer().renderItem(player, held,
                net.minecraft.client.render.model.json.ModelTransformationMode.FIRST_PERSON_RIGHT_HAND,
                false, matrices, vertices, player.getWorld(), light,
                net.minecraft.client.render.OverlayTexture.DEFAULT_UV, player.getId());
        matrices.pop();
        ci.cancel();
    }
}
