package com.example.avatadrone.client;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.item.Avata360BatteryItem;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * 手持/背包/展示框姿态由 assets/<modid>/models/item/avata360_battery.json 的 display 决定。
 * 资源：geo/avata360_battery.geo.json、textures/item/avata360_battery.png、animations/avata360_battery.animation.json
 */
public class Avata360BatteryItemRenderer extends GeoItemRenderer<Avata360BatteryItem> {
    public Avata360BatteryItemRenderer() {
        super(new GeoModel<Avata360BatteryItem>() {
            private final Identifier model = new Identifier(AvataDrone.MOD_ID, "geo/avata360_battery.geo.json");
            private final Identifier texture = new Identifier(AvataDrone.MOD_ID, "textures/item/avata360_battery.png");
            private final Identifier animation = new Identifier(AvataDrone.MOD_ID, "animations/avata360_battery.animation.json");

            @Override
            public Identifier getModelResource(Avata360BatteryItem a) { return model; }

            @Override
            public Identifier getTextureResource(Avata360BatteryItem a) { return texture; }

            @Override
            public Identifier getAnimationResource(Avata360BatteryItem a) { return animation; }
        });
        // 电量灯：仅当物品 NBT Powered=true 时绘制发光层
        addRenderLayer(new AutoGlowingGeoLayer<>(this) {
            @Override
            public void render(MatrixStack poseStack, Avata360BatteryItem animatable, BakedGeoModel bakedModel,
                               RenderLayer renderType, VertexConsumerProvider bufferSource, VertexConsumer buffer,
                               float partialTick, int packedLight, int packedOverlay) {
                if (Avata360BatteryItem.isPowered(Avata360BatteryItemRenderer.this.getCurrentItemStack()))
                    super.render(poseStack, animatable, bakedModel, renderType, bufferSource, buffer, partialTick,
                            packedLight, packedOverlay);
            }
        });
    }
}
