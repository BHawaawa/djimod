package com.example.avatadrone.client;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.item.PhoneItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/** Screen layer visibility follows the phone state every frame (animations only move / scale inside a layer). */
public class PhoneItemModel extends GeoModel<PhoneItem> {
    private static final Identifier MODEL = new Identifier(AvataDrone.MOD_ID, "geo/item/redmi_k90.geo.json");
    private static final Identifier TEXTURE = new Identifier(AvataDrone.MOD_ID, "textures/item/redmi_k90.png");
    private static final Identifier ANIMATION = new Identifier(AvataDrone.MOD_ID, "animations/item/redmi_k90.animation.json");

    @Override public void setCustomAnimations(PhoneItem item, long id, AnimationState<PhoneItem> state) {
        super.setCustomAnimations(item, id, state);
        ItemStack stack = state.getData(DataTickets.ITEMSTACK);
        PhoneItem.Screen s = stack == null ? PhoneItem.Screen.OFF : PhoneItem.screen(stack, PhoneItem.clientTime.getAsLong());
        boolean home = s == PhoneItem.Screen.HOME || s == PhoneItem.Screen.BOOT || s == PhoneItem.Screen.SHUTDOWN;
        hide("scr_home", !home);
        hide("boot_redmi", s != PhoneItem.Screen.BOOT);
        hide("boot_hyperos", s != PhoneItem.Screen.BOOT);
        hide("shutdown_spin", s != PhoneItem.Screen.SHUTDOWN);
        hide("chg_ring", s != PhoneItem.Screen.CHARGE_OFF);
        hide("chg_bolt", s != PhoneItem.Screen.CHARGE_OFF);
        hide("chg_pop", s != PhoneItem.Screen.HOME);
    }

    private void hide(String bone, boolean hidden) {
        CoreGeoBone b = getAnimationProcessor().getBone(bone);
        if (b != null) b.setHidden(hidden);
    }

    @Override public Identifier getModelResource(PhoneItem animatable) { return MODEL; }
    @Override public Identifier getTextureResource(PhoneItem animatable) { return TEXTURE; }
    @Override public Identifier getAnimationResource(PhoneItem animatable) { return ANIMATION; }
}
