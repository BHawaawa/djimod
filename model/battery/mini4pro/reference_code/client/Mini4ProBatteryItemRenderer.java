package com.example.avatadrone.client;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.item.Mini4ProBatteryItem;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * 手持/背包/展示框姿态由 assets/<modid>/models/item/mini4pro_battery.json 的 display 决定。
 * 资源：geo/mini4pro_battery.geo.json、textures/item/mini4pro_battery.png、animations/mini4pro_battery.animation.json
 */
public class Mini4ProBatteryItemRenderer extends GeoItemRenderer<Mini4ProBatteryItem> {
    public Mini4ProBatteryItemRenderer() {
        super(new GeoModel<Mini4ProBatteryItem>() {
            private final Identifier model = new Identifier(AvataDrone.MOD_ID, "geo/mini4pro_battery.geo.json");
            private final Identifier texture = new Identifier(AvataDrone.MOD_ID, "textures/item/mini4pro_battery.png");
            private final Identifier animation = new Identifier(AvataDrone.MOD_ID, "animations/mini4pro_battery.animation.json");

            @Override
            public Identifier getModelResource(Mini4ProBatteryItem a) { return model; }

            @Override
            public Identifier getTextureResource(Mini4ProBatteryItem a) { return texture; }

            @Override
            public Identifier getAnimationResource(Mini4ProBatteryItem a) { return animation; }
        });
    }
}
