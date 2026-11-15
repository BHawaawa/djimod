package com.example.avatadrone.client.charger;

import com.example.avatadrone.block.charger.ChargingHubBlockEntity;
import com.example.avatadrone.block.charger.ChargingHubType;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * GeoBlockRenderer 会按方块的 HorizontalFacingBlock.FACING 旋转模型（模型正面 = -Z）。
 * AutoGlowingGeoLayer 读取 textures/block/<model>_glowmask.png，让充电 LED 发光。
 * 注意：同一个渲染器类给两种充电管家用时要各注册一个实例（模型资源不同）。
 */
public class ChargingHubRenderer extends GeoBlockRenderer<ChargingHubBlockEntity> {
    public ChargingHubRenderer(ChargingHubType type) {
        super(new ChargingHubModel<>(type));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public RenderLayer getRenderType(ChargingHubBlockEntity animatable, Identifier texture,
                                     @Nullable VertexConsumerProvider bufferSource, float partialTick) {
        return RenderLayer.getEntityCutoutNoCull(texture);
    }

    /** BakedGeoModel 与物品渲染器共享（物品渲染器会隐藏 slot 骨骼），所以这里每帧恢复可见；
     *  有无电池完全由 slotN_empty / slotN_installed 动画（scale 0 / 1）决定。 */
    @Override
    public void preRender(MatrixStack poseStack, ChargingHubBlockEntity animatable, BakedGeoModel model, VertexConsumerProvider bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        for (int i = 1; i <= 3; i++) {
            model.getBone("slot" + i).ifPresent(b -> b.setHidden(false));
            for (int k = 1; k <= 4; k++) model.getBone("s" + i + "_led" + k).ifPresent(b -> b.setHidden(false));
        }
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
