package com.example.avatadrone.client;

import com.example.avatadrone.entity.M400Entity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

public class M400Renderer extends GeoEntityRenderer<M400Entity> {
    public M400Renderer(EntityRendererFactory.Context ctx) {
        super(ctx, new M400Model());
        // 模型 1 像素 = 15 mm，桨尖到桨尖约 66 像素；0.4 倍 ≈ 1.65 格宽、0.8 格高
        this.withScale(0.4f);
        this.shadowRadius = 0.8f;
        // 电机状态灯 / 电池电量灯 / 补光灯发光：需要 textures/entity/m400_glowmask.png
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
