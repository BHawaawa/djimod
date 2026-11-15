package com.example.avatadrone.charging;

public final class ChargeIndicatorTest {
    private static void expect(double charge, boolean charging, long tick, String pattern) {
        String actual = "";
        for (int i = 0; i < 4; i++) actual += ChargeIndicator.lit(charge, i, charging, tick) ? "1" : "0";
        if (!actual.equals(pattern)) throw new AssertionError(charge + " " + charging + " " + tick + ": " + actual);
    }
    public static void main(String[] args) {
        expect(0, true, 0, "1000"); expect(0, true, 10, "0000");
        expect(0.24999, true, 10, "0000");
        expect(0.25, true, 0, "1100"); expect(0.25, true, 10, "1000");
        expect(0.50, true, 0, "1110"); expect(0.50, true, 10, "1100");
        expect(0.60, true, 0, "1110"); expect(0.60, true, 10, "1100");
        expect(0.75, true, 0, "1111"); expect(0.75, true, 10, "1110");
        expect(0.9999, true, 10, "1110");
        expect(1, true, 0, "1111"); expect(1, true, 10, "1111");
        expect(0.60, false, 0, "1100"); expect(0, false, 0, "0000");
        expect(Double.NaN, true, 0, "0000");
        System.out.println("ChargeIndicatorTest PASS: threshold boundaries, blink phases, full and disconnected");
    }
}
