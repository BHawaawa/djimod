package com.example.avatadrone.client;

import com.example.avatadrone.item.Rc2Item;
import com.example.avatadrone.entity.AvataDroneEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;

public final class Rc2Indicators {
    private Rc2Indicators() {}
    public static boolean connected(ItemStack stack) {
        var client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || !Rc2Item.hasPower(stack)
                || !stack.hasNbt() || !stack.getNbt().containsUuid(Rc2Item.DRONE)
                || !client.world.getRegistryKey().getValue().toString().equals(stack.getNbt().getString(Rc2Item.DIMENSION))) return false;
        var id = stack.getNbt().getUuid(Rc2Item.DRONE);
        for (var entity : client.world.getEntities()) {
            if (entity instanceof AvataDroneEntity drone && id.equals(drone.getUuid()))
                return drone.isAlive() && !drone.isRemoved() && drone.hasControlSignal(client.player);
        }
        return false;
    }
}
