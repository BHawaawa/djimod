package com.example.avatadrone.signal;

public final class Coverage {
    public static final double RANGE = 500;
    public static final double HALF_ANGLE_DEGREES = 22.5;
    private static final double MIN_FORWARD_COMPONENT = Math.cos(Math.toRadians(HALF_ANGLE_DEGREES));
    private Coverage() {}
    /** Horizontal 45-degree sector facing the antenna front, with a 500-block 3D range. */
    public static boolean contains(double x, double y, double z, int facingX, int facingZ) {
        if (Math.abs(facingX) + Math.abs(facingZ) != 1) return false;
        double distanceSquared = x*x + y*y + z*z;
        if (!(distanceSquared <= RANGE*RANGE + 1e-8)) return false;
        // Local coverage includes positions directly above/below the antenna.
        if (distanceSquared <= 4 * 4) return true;
        double horizontalDistance = Math.hypot(x, z);
        double forward = x*facingX + z*facingZ;
        return forward > 0 && forward / horizontalDistance >= MIN_FORWARD_COMPONENT - 1e-12;
    }
}
