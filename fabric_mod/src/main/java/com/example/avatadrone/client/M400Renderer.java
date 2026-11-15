package com.example.avatadrone.client;

import com.example.avatadrone.entity.M400Entity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class M400Renderer extends GeoEntityRenderer<M400Entity> {
    public M400Renderer(EntityRendererFactory.Context ctx) {
        super(ctx, new M400Model());
        this.withScale(0.4f);
        this.shadowRadius = 0.8f;
        this.addRenderLayer(new software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer<>(this));
    }
    @Override public void render(M400Entity entity, float yaw, float delta,
            net.minecraft.client.util.math.MatrixStack matrices,
            net.minecraft.client.render.VertexConsumerProvider vertices, int light) {
        if (Rc2View.isFeedTarget(entity)) return;
        super.render(entity, yaw, delta, matrices, vertices, light);
    }
}
