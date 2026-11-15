package com.example.avatadrone.entity;

public final class DroneEnduranceTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void close(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1.0e-9, message + ": " + actual);
    }

    public static void main(String[] args) {
        close(EnduranceModel.targetFactor(0, 0, 20, 10, false), 1.0, "Hover");
        close(EnduranceModel.targetFactor(0, 0, 20, 10, true), 0.65, "Rain while hovering");
        close(EnduranceModel.targetFactor(20, 0, 20, 10, false), 0.5, "Fast horizontal flight");
        close(EnduranceModel.targetFactor(0, 10, 20, 10, false), 0.5, "Fast climb");
        close(EnduranceModel.targetFactor(20, 10, 20, 10, true), 0.25, "Weather and flight load floor");
        close(EnduranceModel.targetFactor(0, -10, 20, 10, false), 1.0, "Descent does not use climb penalty");
        System.out.println("DroneEnduranceTest PASS hover, rain, horizontal speed, climb, descent and floor");
    }
}
