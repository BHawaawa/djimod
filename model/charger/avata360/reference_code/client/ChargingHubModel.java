package com.example.avatadrone.client.charger;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.block.charger.ChargingHubType;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

/** 方块实体与方块物品共用：资源按 ChargingHubType.model 取。 */
public class ChargingHubModel<T extends GeoAnimatable> extends GeoModel<T> {
    private final Identifier geo, tex, anim;

    public ChargingHubModel(ChargingHubType type) {
        geo = new Identifier(AvataDrone.MOD_ID, "geo/block/" + type.model + ".geo.json");
        tex = new Identifier(AvataDrone.MOD_ID, "textures/block/" + type.model + ".png");
        anim = new Identifier(AvataDrone.MOD_ID, "animations/block/" + type.model + ".animation.json");
    }

    @Override public Identifier getModelResource(T a) { return geo; }
    @Override public Identifier getTextureResource(T a) { return tex; }
    @Override public Identifier getAnimationResource(T a) { return anim; }
}
