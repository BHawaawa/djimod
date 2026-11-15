package com.example.avatadrone.client;

import com.example.avatadrone.entity.AvataDroneEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class AvataDroneRenderer extends GeoEntityRenderer<AvataDroneEntity> {
    public AvataDroneRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new AvataDroneModel());
        // 模型按 1 像素 ≈ 8 mm 建模（宽 30 px），缩放 0.5 后约 0.94 格宽。想要更接近真实尺寸可改成 0.25f
        this.withScale(0.5f);
        this.shadowRadius = 0.35f;
        // 电池电量灯发光：textures/entity/avata360_glowmask.png（只有电池尾部 4 颗 LED）
        addRenderLayer(new software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void render(AvataDroneEntity entity, float entityYaw, float tickDelta, MatrixStack matrixStack,
            VertexConsumerProvider vertexConsumerProvider, int packedLight) {
        if (Rc2View.isFeedTarget(entity)) return;
        super.render(entity, entityYaw, tickDelta, matrixStack, vertexConsumerProvider, packedLight);
    }
}
