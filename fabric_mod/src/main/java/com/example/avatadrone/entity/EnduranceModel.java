package com.example.avatadrone.entity;

/** Pure flight-load model, kept separate so its factors can be tested without Minecraft runtime classes. */
final class EnduranceModel {
    private EnduranceModel() {}

    static double targetFactor(double horizontalSpeed, double verticalSpeed, double maxHorizontalSpeed,
            double maxVerticalSpeed, boolean raining) {
        double horizontalLoad = clamp(horizontalSpeed / Math.max(1.0e-4, maxHorizontalSpeed), 0.0, 1.0);
        double horizontalFactor = 1.0 - 0.5 * clamp((horizontalLoad - 0.1) / 0.9, 0.0, 1.0);
        double verticalFactor = 1.0;
        if (verticalSpeed > 0.0) {
            double verticalLoad = clamp(verticalSpeed / Math.max(1.0e-4, maxVerticalSpeed), 0.0, 1.0);
            verticalFactor = 1.0 - 0.5 * clamp((verticalLoad - 0.1) / 0.9, 0.0, 1.0);
        }
        double weatherFactor = raining ? 0.65 : 1.0;
        return clamp(horizontalFactor * verticalFactor * weatherFactor, 0.25, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
