package com.example.avatadrone.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/** Stores the actual battery item, including its name, charge and other item data. */
public final class DroneBatteryStorage {
    private DroneBatteryStorage() {}

    public static ItemStack one(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    public static ItemStack read(NbtCompound nbt, Item battery) {
        if (nbt != null && nbt.contains("InstalledBattery")) {
            ItemStack stored = ItemStack.fromNbt(nbt.getCompound("InstalledBattery"));
            return accepts(stored, battery) ? one(stored) : ItemStack.EMPTY;
        }
        // Old aircraft included one battery and saved only its tag.
        ItemStack legacy = new ItemStack(battery);
        if (nbt != null && nbt.contains("BatteryData"))
            legacy.setNbt(nbt.getCompound("BatteryData").copy());
        return legacy;
    }

    public static void write(NbtCompound nbt, ItemStack battery) {
        nbt.remove("BatteryData");
        if (battery.isEmpty()) nbt.remove("InstalledBattery");
        else nbt.put("InstalledBattery", one(battery).writeNbt(new NbtCompound()));
        nbt.putBoolean("HasBattery", !battery.isEmpty());
    }

    public static boolean accepts(ItemStack stack, Item expected) {
        return !stack.isEmpty() && (stack.isOf(expected)
                || expected == com.example.avatadrone.AvataDrone.MINI4PRO_BATTERY
                    && stack.isOf(com.example.avatadrone.AvataDrone.MINI4PRO_BATTERY_LONG));
    }

}
