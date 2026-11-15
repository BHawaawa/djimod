package com.example.avatadrone.network;

import java.util.UUID;

public final class DroneControlPayloadTest {
    public static void main(String[] args) {
        UUID id = UUID.randomUUID();
        for (float invalid : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            DroneControlPayload p = new DroneControlPayload(id, invalid, invalid, invalid, invalid, invalid,
                    invalid, 100, true, false, false).clamped();
            if (p.yaw() != 0 || p.throttle() != 0 || p.yawStick() != 0 || p.pitch() != 0 || p.roll() != 0
                    || p.groundDistance() != 0 || p.flightMode() != 2 || !p.arm() || !id.equals(p.droneId()))
                throw new AssertionError("Non-finite input must become neutral: " + p);
        }
        DroneControlPayload p = new DroneControlPayload(id, 270, 2, -2, .5f, -.25f, 600, -1,
                false, true, false).clamped();
        if (p.yaw() != 270 || p.throttle() != 1 || p.yawStick() != -1 || p.pitch() != .5f
                || p.roll() != -.25f || p.groundDistance() != 512 || p.flightMode() != 0 || !p.emergencyStop())
            throw new AssertionError("Finite inputs or flags changed unexpectedly: " + p);
        for (int avoidance : new int[]{-20, 0, 1, 2, 20}) {
            var sent = new DroneControlPayload(id, 90, .3f, -.2f, .7f, -.8f, 50, 2,
                    false, false, true, true, avoidance).clamped();
            var buffer = new net.minecraft.network.PacketByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                DroneControlPayload.write(buffer, sent);
                var received = DroneControlPayload.read(buffer);
                if (!sent.equals(received) || buffer.isReadable() || !received.cruise()
                        || received.avoidanceMode() != Math.max(0, Math.min(2, avoidance)))
                    throw new AssertionError("V2 flight controls lost in serialization: " + received);
            } finally { buffer.release(); }
        }
        System.out.println("DroneControlPayloadTest PASS: NaN/infinity neutralized, bounds and flags preserved");
    }
}
