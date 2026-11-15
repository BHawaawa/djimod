package com.example.avatadrone.client.charger;

import com.example.avatadrone.block.charger.ChargingHubType;
import com.example.avatadrone.item.ChargingHubBlockItem;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** 物品栏 / 手持时显示空的充电管家（3 个 slot 骨骼隐藏，LED 不亮）。 */
public class ChargingHubItemRenderer extends GeoItemRenderer<ChargingHubBlockItem> {
    public ChargingHubItemRenderer(ChargingHubType type) {
        super(new ChargingHubModel<>(type));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void preRender(MatrixStack poseStack, ChargingHubBlockItem animatable, BakedGeoModel model, VertexConsumerProvider bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
        for (int i = 1; i <= 3; i++) {
            model.getBone("slot" + i).ifPresent(b -> b.setHidden(true));
            for (int k = 1; k <= 4; k++) model.getBone("s" + i + "_led" + k).ifPresent(b -> b.setHidden(true));
        }
    }
}
