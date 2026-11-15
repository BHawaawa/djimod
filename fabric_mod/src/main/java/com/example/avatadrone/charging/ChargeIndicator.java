package com.example.avatadrone.charging;

/** Thresholds use precise charge, never rounded UI percentages. */
public final class ChargeIndicator {
    private ChargeIndicator() {}
    public static boolean lit(double charge, int index, boolean charging, long tick) {
        if (!Double.isFinite(charge) || index < 0 || index > 3) return false;
        double threshold = (index + 1) * 0.25;
        if (charge >= threshold) return true;
        return charging && charge >= index * 0.25 && (tick / 10) % 2 == 0;
    }
}
