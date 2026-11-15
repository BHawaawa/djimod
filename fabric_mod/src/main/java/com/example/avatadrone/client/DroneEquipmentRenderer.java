package com.example.avatadrone.client;

import com.example.avatadrone.item.DroneEquipmentItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;

public final class DroneEquipmentRenderer extends GeoItemRenderer<DroneEquipmentItem> {
    public DroneEquipmentRenderer() { super(new EquipmentModel()); }
    private static class EquipmentModel extends GeoModel<DroneEquipmentItem> {
        @Override public Identifier getModelResource(DroneEquipmentItem item) {
            return new Identifier("djimod", "geo/item/" + item.model + ".geo.json");
        }
        @Override public Identifier getTextureResource(DroneEquipmentItem item) {
            return new Identifier("djimod", "textures/item/" + item.model + ".png");
        }
        @Override public Identifier getAnimationResource(DroneEquipmentItem item) {
            return new Identifier("djimod", "animations/item/" + item.model + ".animation.json");
        }
        @Override public void setCustomAnimations(DroneEquipmentItem item, long id, AnimationState<DroneEquipmentItem> state) {
            super.setCustomAnimations(item, id, state);
            if (!item.aircraft) return;
            ItemStack stack = state.getData(DataTickets.ITEMSTACK);
            if (stack == null) return;
            String action = DroneEquipmentItem.action(stack);
            var battery = getAnimationProcessor().getBone("battery");
            if (battery != null) battery.setHidden(!DroneEquipmentItem.hasBattery(stack) && !action.equals("battery_insert"));
            var guard = getAnimationProcessor().getBone("gimbal_guard");
            if (guard != null) guard.setHidden(DroneEquipmentItem.powered(stack) && !action.equals("power_off"));
        }
    }
}
