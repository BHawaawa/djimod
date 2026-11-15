package com.example.avatadrone.qa;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.entity.AvataDroneEntity;
import com.example.avatadrone.item.DeviceCharging;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

public final class ServerChecks implements ModInitializer {
    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            var world = server.getOverworld();
            ComponentChecks.run(world.getRegistryManager());
            EntityType<?>[] types = {AvataDrone.AVATA360, AvataDrone.MINI4PRO, AvataDrone.M400};
            Item[] batteries = {AvataDrone.AVATA360_BATTERY, AvataDrone.MINI4PRO_BATTERY_LONG, AvataDrone.M400_BATTERY};
            for (int i = 0; i < types.length; i++) {
                AvataDroneEntity drone = (AvataDroneEntity) types[i].create(world);
                ItemStack battery = new ItemStack(batteries[i]);
                DeviceCharging.setCharge(battery, .5);
                drone.installBattery(battery);
                NbtCompound saved = new NbtCompound();
                drone.writeCustomDataToNbt(saved);
                AvataDroneEntity restored = (AvataDroneEntity) types[i].create(world);
                restored.readCustomDataFromNbt(saved);
                if (!restored.hasBattery() || !restored.getInstalledBattery().isOf(batteries[i]))
                    throw new AssertionError("Aircraft battery identity not restored: " + types[i]);
                if (Math.abs(DeviceCharging.getCharge(restored.getInstalledBattery()) - .5) > .01)
                    throw new AssertionError("Aircraft charge not restored: " + types[i]);
            }
            if (AvataDrone.AURO2000.create(world) == null) throw new AssertionError("Power station creation failed");
            System.out.println("PORT_QA PASS: all aircraft trackers, batteries and save/load, power station creation");
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (Boolean.getBoolean("djimod.portServerQa") && server.getTicks() == 100) {
                System.out.println("PORT_SERVER_QA PASS: dedicated server started and ticked");
                server.stop(false);
            }
        });
    }
}
