package com.example.avatadrone;

import net.minecraft.item.ItemStack;

/** Gameplay tuning carried over from YueFei V1.0.32, including legacy battery tags. */
public final class DroneTuning {
    private DroneTuning() {}
    public static boolean isLongRangeBattery(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.isOf(AvataDrone.MINI4PRO_BATTERY_LONG)
                || stack.isOf(AvataDrone.MINI4PRO_BATTERY) && stack.hasNbt() && stack.getNbt().getBoolean("LongRangeBattery"));
    }
    public static double miniHorizontalSpeed(int mode) {
        return mode == 0 ? 6 : mode == 2 ? 16 : 12;
    }
}
