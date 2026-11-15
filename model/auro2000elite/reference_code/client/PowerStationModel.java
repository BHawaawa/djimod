package com.example.avatadrone.client;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.entity.PowerStationEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

/** DJI Power AURO 2000 ELITE: geometry / texture / cable animations (model/auro2000elite). */
public class PowerStationModel extends GeoModel<PowerStationEntity> {
    public static final Identifier MODEL = new Identifier(AvataDrone.MOD_ID, "geo/auro2000elite.geo.json");
    public static final Identifier TEXTURE = new Identifier(AvataDrone.MOD_ID, "textures/entity/auro2000elite.png");
    public static final Identifier ANIMATION = new Identifier(AvataDrone.MOD_ID, "animations/auro2000elite.animation.json");

    @Override public Identifier getModelResource(PowerStationEntity animatable) { return MODEL; }
    @Override public Identifier getTextureResource(PowerStationEntity animatable) { return TEXTURE; }
    @Override public Identifier getAnimationResource(PowerStationEntity animatable) { return ANIMATION; }
}
