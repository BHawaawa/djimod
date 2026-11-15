package com.example.avatadrone.network;

import java.util.UUID;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

public record DroneControlPayload(UUID droneId, float yaw, float throttle, float yawStick, float pitch, float roll, float groundDistance, int flightMode, boolean arm, boolean emergencyStop, boolean returnHome, boolean cruise, int avoidanceMode) {
    public DroneControlPayload(UUID droneId, float yaw, float throttle, float yawStick, float pitch,
            float roll, float groundDistance, int flightMode, boolean arm, boolean emergencyStop, boolean returnHome) {
        this(droneId, yaw, throttle, yawStick, pitch, roll, groundDistance, flightMode,
                arm, emergencyStop, returnHome, false, 1);
    }
    public static final Identifier ID = new Identifier("djimod", "drone_control");

    public static void write(PacketByteBuf packetByteBuf, DroneControlPayload droneControlPayload) {
        packetByteBuf.writeUuid(droneControlPayload.droneId);
        packetByteBuf.writeFloat(droneControlPayload.yaw);
        packetByteBuf.writeFloat(droneControlPayload.throttle);
        packetByteBuf.writeFloat(droneControlPayload.yawStick);
        packetByteBuf.writeFloat(droneControlPayload.pitch);
        packetByteBuf.writeFloat(droneControlPayload.roll);
        packetByteBuf.writeFloat(droneControlPayload.groundDistance);
        packetByteBuf.writeVarInt(droneControlPayload.flightMode);
        packetByteBuf.writeBoolean(droneControlPayload.arm);
        packetByteBuf.writeBoolean(droneControlPayload.emergencyStop);
        packetByteBuf.writeBoolean(droneControlPayload.returnHome);
        packetByteBuf.writeBoolean(droneControlPayload.cruise);
        packetByteBuf.writeVarInt(droneControlPayload.avoidanceMode);
    }

    public static DroneControlPayload read(PacketByteBuf packetByteBuf) {
        return new DroneControlPayload(packetByteBuf.readUuid(), packetByteBuf.readFloat(), packetByteBuf.readFloat(), packetByteBuf.readFloat(), packetByteBuf.readFloat(), packetByteBuf.readFloat(), packetByteBuf.readFloat(), packetByteBuf.readVarInt(), packetByteBuf.readBoolean(), packetByteBuf.readBoolean(), packetByteBuf.readBoolean(), packetByteBuf.readBoolean(), packetByteBuf.readVarInt());
    }

    public DroneControlPayload clamped() {
        return new DroneControlPayload(this.droneId, Float.isFinite(this.yaw) ? this.yaw : 0.0f, DroneControlPayload.clamp(this.throttle), DroneControlPayload.clamp(this.yawStick), DroneControlPayload.clamp(this.pitch), DroneControlPayload.clamp(this.roll), Float.isFinite(this.groundDistance) ? Math.max(0.0f, Math.min(512.0f, this.groundDistance)) : 0.0f, Math.max(0, Math.min(2, this.flightMode)), this.arm, this.emergencyStop, this.returnHome, this.cruise, Math.max(0, Math.min(2, this.avoidanceMode)));
    }

    private static float clamp(float f) {
        return Float.isFinite(f) ? Math.max(-1.0f, Math.min(1.0f, f)) : 0.0f;
    }
}

