package com.example.avatadrone.item;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;

/**
 * Devices that the DJI Power AURO 2000 ELITE can charge through its built-in 140 W USB-C cable.
 * Charge is stored as "BatteryCharge" (0..1) - the same tag the drones already read when a battery is installed.
 *  - the three flight batteries (Avata 360 / Mini 4 Pro / TB100)
 *  - an aircraft item with its battery installed (charges the battery inside: InstalledBattery + AircraftData)
 */
public final class DeviceCharging {
    private DeviceCharging() {}

    /** battery model id ("avata360_battery" ...) of the battery that actually receives the energy, or null. */
    public static String batteryModel(ItemStack stack) {
        if (stack.getItem() instanceof Rc2Item) return "rc2";
        if (stack.getItem() instanceof PhoneItem) return "redmi_k90";
        if (stack.isEmpty() || !(stack.getItem() instanceof DroneEquipmentItem item)) return null;
        if (!item.aircraft) return item.model;
        return DroneEquipmentItem.hasBattery(stack) ? item.model + "_battery" : null;
    }

    public static boolean isChargeable(ItemStack stack) { return batteryModel(stack) != null; }

    /** devices charged while held in the main hand (cable goes to the player): RC 2, Redmi K90. */
    public static boolean isHandheld(ItemStack stack) {
        return stack.getItem() instanceof Rc2Item || stack.getItem() instanceof PhoneItem;
    }

    public static boolean isAircraftWithoutBattery(ItemStack stack) {
        return stack.getItem() instanceof DroneEquipmentItem item && item.aircraft && !DroneEquipmentItem.hasBattery(stack);
    }

    /** nominal energy (Wh) */
    public static double capacityWh(ItemStack stack) {
        String m = batteryModel(stack);
        if (m == null) return 0;
        return switch (m) {
            case "rc2" -> 22.32; // gameplay battery capacity
            case "redmi_k90" -> 29.26;           // 7560 mAh x 3.87 V
            case "m400_battery" -> 977.0;        // TB100
            case "mini4pro_battery" -> longRange(stack) ? 28.41 : 18.96;    // BWX162-3850-7.38 Plus label
            default -> 31.7;                     // Avata-class 4S pack
        };
    }

    private static boolean longRange(ItemStack stack) {
        if (stack.getItem() instanceof DroneEquipmentItem item && item.aircraft) {
            return com.example.avatadrone.DroneTuning.isLongRangeBattery(
                DroneBatteryStorage.read(stack.getNbt(), com.example.avatadrone.AvataDrone.MINI4PRO_BATTERY));
        }
        return com.example.avatadrone.DroneTuning.isLongRangeBattery(stack);
    }

    /** maximum power the device accepts (W); the cable itself is limited to 140 W. */
    public static double maxInputW(ItemStack stack) {
        String m = batteryModel(stack);
        if (m == null) return 0;
        return switch (m) {
            case "rc2" -> 9.0;
            case "redmi_k90" -> 100.0;           // 100 W HyperCharge
            case "m400_battery" -> 140.0;
            case "mini4pro_battery" -> 45.0;
            default -> 65.0;
        };
    }

    public static double getCharge(ItemStack stack) {
        NbtCompound root = stack.getNbt();
        if (root == null) return 1.0;
        if (stack.getItem() instanceof DroneEquipmentItem item && item.aircraft) {
            // the root InstalledBattery is what the drone installs on placement (battery swaps only rewrite it)
            if (root.contains("InstalledBattery")) {
                NbtCompound inner = root.getCompound("InstalledBattery").getCompound("tag");
                return inner.contains("BatteryCharge") ? clamp(inner.getDouble("BatteryCharge")) : 1.0;
            }
            if (root.contains("BatteryData") && root.getCompound("BatteryData").contains("BatteryCharge"))   // legacy saves
                return clamp(root.getCompound("BatteryData").getDouble("BatteryCharge"));
            if (root.contains("AircraftData") && root.getCompound("AircraftData").contains("BatteryCharge"))
                return clamp(root.getCompound("AircraftData").getDouble("BatteryCharge"));
            return 1.0;
        }
        return root.contains("BatteryCharge") ? clamp(root.getDouble("BatteryCharge")) : 1.0;
    }

    public static void setCharge(ItemStack stack, double charge) {
        charge = clamp(charge);
        if (!(stack.getItem() instanceof DroneEquipmentItem item && item.aircraft)) {
            stack.getOrCreateNbt().putDouble("BatteryCharge", charge);
            return;
        }
        NbtCompound root = stack.getOrCreateNbt();
        if (root.contains("InstalledBattery")) putInner(root.getCompound("InstalledBattery"), charge);
        if (root.contains("BatteryData")) root.getCompound("BatteryData").putDouble("BatteryCharge", charge);   // legacy
        if (root.contains("AircraftData")) {
            NbtCompound data = root.getCompound("AircraftData");     // read by the drone entity on placement
            data.putDouble("BatteryCharge", charge);
            if (data.contains("InstalledBattery")) putInner(data.getCompound("InstalledBattery"), charge);
        }
    }

    private static void putInner(NbtCompound installedStack, double charge) {
        NbtCompound inner = installedStack.contains("tag") ? installedStack.getCompound("tag") : new NbtCompound();
        inner.putDouble("BatteryCharge", charge);
        installedStack.put("tag", inner);
    }

    private static double clamp(double v) { return Double.isFinite(v) ? MathHelper.clamp(v, 0, 1) : 1.0; }
}
