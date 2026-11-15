package com.example.avatadrone.client;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.item.RC2Item;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class RC2ItemModel extends GeoModel<RC2Item> {
    private static final Identifier MODEL = new Identifier(AvataDrone.MOD_ID, "geo/rc2.geo.json");
    private static final Identifier TEXTURE = new Identifier(AvataDrone.MOD_ID, "textures/item/rc2.png");
    private static final Identifier ANIMATION = new Identifier(AvataDrone.MOD_ID, "animations/rc2.animation.json");

    @Override
    public Identifier getModelResource(RC2Item animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(RC2Item animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(RC2Item animatable) {
        return ANIMATION;
    }
}
