package com.example.avatadrone.client;

import com.example.avatadrone.entity.Mini4ProEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class Mini4ProRenderer extends GeoEntityRenderer<Mini4ProEntity> {
    public Mini4ProRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new Mini4ProModel());
        // 模型 1 像素 ≈ 6 mm，桨尖到桨尖约 60 像素；0.4 倍 ≈ 1.5 格宽（与 avata360 的 0.5 倍比例一致）
        this.withScale(0.4f);
        this.shadowRadius = 0.4f;
    }
}
