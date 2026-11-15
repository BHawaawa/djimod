package com.example.avatadrone.qa;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.compat.ItemData;
import com.example.avatadrone.item.DeviceCharging;
import com.example.avatadrone.item.DroneBatteryStorage;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;

/** Exercises real Minecraft codecs, including pre-component stacks nested in mod NBT. */
public final class ComponentChecks {
    public static void run(RegistryWrapper.WrapperLookup registries) {
        ItemStack battery = new ItemStack(AvataDrone.MINI4PRO_BATTERY_LONG);
        battery.set(DataComponentTypes.CUSTOM_NAME, Text.literal("QA named battery"));
        DeviceCharging.setCharge(battery, .35);
        ItemStack copy = battery.copy();
        DeviceCharging.setCharge(battery, .6);
        near(.35, DeviceCharging.getCharge(copy), "component copy isolation");
        ItemStack aircraft = new ItemStack(AvataDrone.MINI4PRO_ITEM);
        DroneBatteryStorage.writeItem(aircraft, battery, registries);
        DeviceCharging.setCharge(aircraft, .8);
        ItemStack installed = DroneBatteryStorage.read(ItemData.read(aircraft), AvataDrone.MINI4PRO_BATTERY, registries);
        near(.8, DeviceCharging.getCharge(installed), "nested battery charging");
        near(28.41, DeviceCharging.capacityWh(aircraft), "long-range battery capacity");
        if (!"QA named battery".equals(installed.getName().getString())) throw new AssertionError("Battery name lost");
        ItemStack restored = ItemData.decode(registries, (NbtCompound) aircraft.encode(registries));
        near(.8, DeviceCharging.getCharge(restored), "aircraft save/load");

        NbtCompound legacy = new NbtCompound();
        legacy.putString("id", "djimod:mini4pro_battery_long");
        legacy.putByte("Count", (byte) 1);
        NbtCompound tag = new NbtCompound();
        tag.putDouble("BatteryCharge", .42);
        NbtCompound display = new NbtCompound();
        display.putString("Name", "{\"text\":\"Legacy QA\"}");
        tag.put("display", display);
        legacy.put("tag", tag);
        ItemStack migrated = ItemData.decode(registries, legacy);
        near(.42, DeviceCharging.getCharge(migrated), "legacy NBT migration");
        if (!"Legacy QA".equals(migrated.getName().getString())) throw new AssertionError("Legacy name lost");
        System.out.println("PORT_QA PASS: immutable components, nested charging, battery identity/name, modern and legacy save/load");
    }
    private static void near(double expected, double actual, String label) {
        if (Math.abs(expected - actual) > 1e-6) throw new AssertionError(label + ": " + actual + " != " + expected);
    }
}
