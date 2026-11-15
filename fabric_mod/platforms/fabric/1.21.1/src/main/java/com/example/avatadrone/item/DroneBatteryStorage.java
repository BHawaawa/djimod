package com.example.avatadrone.item;

import com.example.avatadrone.compat.ItemData;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;

/** Keeps the whole installed stack, including custom name and modern components. */
public final class DroneBatteryStorage {
    private DroneBatteryStorage() {}
    public static ItemStack one(ItemStack stack) { return stack.copyWithCount(1); }
    public static ItemStack read(NbtCompound nbt, Item battery, RegistryWrapper.WrapperLookup registries) {
        if (nbt != null && nbt.contains("InstalledBattery")) {
            ItemStack stored = ItemData.decode(registries, nbt.getCompound("InstalledBattery"));
            return accepts(stored, battery) ? one(stored) : ItemStack.EMPTY;
        }
        ItemStack legacy = new ItemStack(battery);
        if (nbt != null && nbt.contains("BatteryData")) ItemData.set(legacy, nbt.getCompound("BatteryData"));
        return legacy;
    }
    public static void write(NbtCompound nbt, ItemStack battery, RegistryWrapper.WrapperLookup registries) {
        nbt.remove("BatteryData");
        if (battery.isEmpty()) nbt.remove("InstalledBattery");
        else nbt.put("InstalledBattery", one(battery).encode(registries));
        nbt.putBoolean("HasBattery", !battery.isEmpty());
    }
    public static void writeItem(ItemStack aircraft, ItemStack battery, RegistryWrapper.WrapperLookup registries) {
        ItemData.update(aircraft, nbt -> write(nbt, battery, registries));
    }
    public static boolean accepts(ItemStack stack, Item expected) {
        return !stack.isEmpty() && (stack.isOf(expected)
                || expected == com.example.avatadrone.AvataDrone.MINI4PRO_BATTERY
                && stack.isOf(com.example.avatadrone.AvataDrone.MINI4PRO_BATTERY_LONG));
    }
}
