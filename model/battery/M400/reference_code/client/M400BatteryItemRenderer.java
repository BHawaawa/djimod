package com.example.avatadrone.client;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.item.M400BatteryItem;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * 手持/背包/展示框姿态由 assets/<modid>/models/item/m400_battery.json 的 display 决定。
 * 资源：geo/m400_battery.geo.json、textures/item/m400_battery.png、animations/m400_battery.animation.json
 */
public class M400BatteryItemRenderer extends GeoItemRenderer<M400BatteryItem> {
    public M400BatteryItemRenderer() {
        super(new GeoModel<M400BatteryItem>() {
            private final Identifier model = new Identifier(AvataDrone.MOD_ID, "geo/m400_battery.geo.json");
            private final Identifier texture = new Identifier(AvataDrone.MOD_ID, "textures/item/m400_battery.png");
            private final Identifier animation = new Identifier(AvataDrone.MOD_ID, "animations/m400_battery.animation.json");

            @Override
            public Identifier getModelResource(M400BatteryItem a) { return model; }

            @Override
            public Identifier getTextureResource(M400BatteryItem a) { return texture; }

            @Override
            public Identifier getAnimationResource(M400BatteryItem a) { return animation; }
        });
        // 电量灯发光：textures/item/m400_battery_glowmask.png
        this.addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}
