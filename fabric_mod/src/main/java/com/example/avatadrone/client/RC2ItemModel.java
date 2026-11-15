package com.example.avatadrone.client;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.item.Rc2Item;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class RC2ItemModel extends GeoModel<Rc2Item> {
    private static final Identifier MODEL = new Identifier(AvataDrone.MOD_ID, "geo/rc2.geo.json");
    private static final Identifier TEXTURE = new Identifier(AvataDrone.MOD_ID, "textures/item/rc2.png");
    private static final Identifier ANIMATION = new Identifier(AvataDrone.MOD_ID, "animations/rc2.animation.json");

    @Override
    public void setCustomAnimations(Rc2Item item, long id,
            software.bernie.geckolib.core.animation.AnimationState<Rc2Item> state) {
        super.setCustomAnimations(item, id, state);
        var stack = state.getData(software.bernie.geckolib.constant.DataTickets.ITEMSTACK);
        int lights = stack == null ? 0 : Rc2Item.batteryLights(stack);
        for (int i = 0; i < 4; i++) {
            getAnimationProcessor().getBone("battery_" + i + "_on").setHidden(i >= lights);
            getAnimationProcessor().getBone("battery_" + i + "_off").setHidden(i < lights);
        }
        boolean connected = stack != null && Rc2Indicators.connected(stack);
        getAnimationProcessor().getBone("link_connected").setHidden(!connected);
        getAnimationProcessor().getBone("link_disconnected").setHidden(connected);
    }

    @Override
    public Identifier getModelResource(Rc2Item animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(Rc2Item animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(Rc2Item animatable) {
        return ANIMATION;
    }
}
