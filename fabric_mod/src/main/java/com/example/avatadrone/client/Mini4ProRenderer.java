package com.example.avatadrone.client;

import com.example.avatadrone.entity.Mini4ProEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class Mini4ProRenderer extends GeoEntityRenderer<Mini4ProEntity> {
    public Mini4ProRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new Mini4ProModel());
        // 模型 1 像素 ≈ 6 mm，桨尖到桨尖约 60 像素；0.4 倍 ≈ 1.5 格宽（与 avata360 的 0.5 倍比例一致）
        this.withScale(0.4f);
        this.shadowRadius = 0.4f;
    }

    @Override public void render(Mini4ProEntity entity, float yaw, float delta, MatrixStack matrices,
            VertexConsumerProvider vertices, int light) {
        if (Rc2View.isFeedTarget(entity)) return;
        super.render(entity, yaw, delta, matrices, vertices, light);
    }

}
