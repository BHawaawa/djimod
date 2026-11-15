package com.example.avatadrone.compat;

import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Version-specific typed payload envelopes; the flight protocol stays shared. */
public final class DronePackets {
    public record Payload(CustomPayload.Id<Payload> id, byte[] bytes) implements CustomPayload {
        @Override public CustomPayload.Id<? extends CustomPayload> getId() { return id; }
        public PacketByteBuf buffer() { return new PacketByteBuf(Unpooled.wrappedBuffer(bytes)); }
    }
    private static final Map<Identifier, CustomPayload.Id<Payload>> ids = new HashMap<>();
    static {
        for (String path : new String[]{"charging_slot", "rc2_disconnect", "rc2_pair", "drone_chunk_view",
                "drone_chunk_data", "drone_control", "maintenance", "flight_settings", "flight_map_stations"}) {
            Identifier identifier = Identifier.of("djimod", path);
            CustomPayload.Id<Payload> id = new CustomPayload.Id<>(identifier);
            ids.put(identifier, id);
            PacketCodec<RegistryByteBuf, Payload> codec = PacketCodec.of(
                    (payload, buf) -> buf.writeBytes(payload.bytes()), buf -> {
                        int size = buf.readableBytes();
                        if (size > 1_048_576) throw new IllegalArgumentException("Drone packet exceeds payload limit");
                        byte[] bytes = new byte[size];
                        buf.readBytes(bytes);
                        return new Payload(id, bytes);
                    });
            PayloadTypeRegistry.playC2S().register(id, codec);
            PayloadTypeRegistry.playS2C().register(id, codec);
        }
    }
    private DronePackets() {}
    public static CustomPayload.Id<Payload> id(Identifier identifier) {
        CustomPayload.Id<Payload> id = ids.get(identifier);
        if (id == null) throw new IllegalArgumentException("Unregistered drone channel: " + identifier);
        return id;
    }
    public static Payload copy(Identifier identifier, PacketByteBuf buf) {
        try {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), bytes);
            return new Payload(id(identifier), bytes);
        } finally {
            buf.release();
        }
    }
}
