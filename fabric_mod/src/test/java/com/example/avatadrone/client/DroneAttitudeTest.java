package com.example.avatadrone.client;

public final class DroneAttitudeTest {
    private static void near(double actual, double expected) {
        if (Math.abs(actual - expected) > 0.0001) {
            throw new AssertionError(actual + " != " + expected);
        }
    }

    public static void main(String[] args) {
        for (boolean avata : new boolean[]{true, false}) {
            for (int heading = 0; heading < 360; heading += 15) {
                double yaw = Math.toRadians(heading);
                DroneAttitude forward = DroneAttitude.calculate(-0.1 * Math.sin(yaw),
                        0.1 * Math.cos(yaw), heading, true, avata);
                near(forward.cameraPitch(0, true), 0);
                near(forward.cameraRoll(true), 0);
                DroneAttitude right = DroneAttitude.calculate(-0.1 * Math.cos(yaw),
                        -0.1 * Math.sin(yaw), heading, true, avata);
                DroneAttitude left = DroneAttitude.calculate(0.1 * Math.cos(yaw),
                        0.1 * Math.sin(yaw), heading, true, avata);
                near(right.cameraRoll(true), -left.cameraRoll(true));
                near(right.cameraRoll(true), Math.toDegrees(avata ? 0.18 : 0.12));
                near(right.cameraRoll(false), 0);
                near(forward.cameraPitch(0, false), 0);
                near(forward.cameraPitch(90, false), 90);
                near(forward.cameraPitch(90, true), 90 + forward.cameraPitch(0, true));
                DroneAttitude disarmed = DroneAttitude.calculate(1, 1, heading, false, avata);
                near(disarmed.cameraPitch(0, true), 0);
                near(disarmed.cameraRoll(true), 0);
            }
        }
        DroneAttitude fast = DroneAttitude.calculate(10, 10, 0, true, false);
        near(Math.hypot(fast.pitch(), fast.roll()), Math.toRadians(35));
        DroneAttitude avata = DroneAttitude.calculate(10, 10, 0, true, true);
        near(avata.pitch(), -0.45);
        near(avata.roll(), -0.35);
        System.out.println("Gimbal tests passed: stable/FPV, 24 headings, look direction, disarmed and tilt limits.");
    }
}
