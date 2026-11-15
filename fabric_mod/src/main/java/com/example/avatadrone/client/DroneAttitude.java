package com.example.avatadrone.client;

/** The same velocity-derived body tilt is used by aircraft models and the FPV camera. */
public final class DroneAttitude {
    private final float pitch, roll;
    public DroneAttitude(float pitch, float roll) { this.pitch = pitch; this.roll = roll; }
    public float pitch() { return pitch; }
    public float roll() { return roll; }
    @Override public boolean equals(Object other) {
        if (!(other instanceof DroneAttitude)) return false;
        DroneAttitude value = (DroneAttitude) other;
        return Float.compare(pitch, value.pitch) == 0 && Float.compare(roll, value.roll) == 0;
    }
    @Override public int hashCode() { return 31 * Float.hashCode(pitch) + Float.hashCode(roll); }
    @Override public String toString() { return "DroneAttitude[pitch=" + pitch + ", roll=" + roll + "]"; }
    public float cameraPitch(float lookPitch, boolean fpv) {
        // FPV follows body roll while retaining the selected gimbal pitch.
        return lookPitch;
    }

    public float cameraRoll(boolean fpv) {
        return fpv ? (float) Math.toDegrees(roll) : 0;
    }

    public static DroneAttitude calculate(double vx, double vz, float yawDegrees,
                                          boolean armed, boolean avata) {
        if (!armed) return new DroneAttitude(0, 0);
        double yaw = Math.toRadians(yawDegrees);
        double forward = -vx * Math.sin(yaw) + vz * Math.cos(yaw);
        double strafe = -vx * Math.cos(yaw) - vz * Math.sin(yaw);
        if (avata) return new DroneAttitude(
                (float) Math.max(-0.45, Math.min(0.45, -forward * 1.8)),
                (float) Math.max(-0.35, Math.min(0.35, strafe * 1.8)));
        double pitch = -forward * 1.2, roll = strafe * 1.2;
        double tilt = Math.hypot(pitch, roll);
        double scale = tilt > 0 ? Math.min(1, Math.toRadians(35) / tilt) : 1;
        return new DroneAttitude((float) (pitch * scale), (float) (roll * scale));
    }
}
