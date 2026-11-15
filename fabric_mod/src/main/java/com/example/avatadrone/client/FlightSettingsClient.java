package com.example.avatadrone.client;

import com.example.avatadrone.DroneSettings;
import com.example.avatadrone.network.DroneSettingsNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;

public final class FlightSettingsClient {
    private static boolean editable;
    private static long nextRequest;
    private static double pendingRange = Double.NaN;
    private FlightSettingsClient() {}
    public static void register() {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (Double.isFinite(pendingRange) && System.nanoTime() >= nextRequest) {
                double value = pendingRange;
                pendingRange = Double.NaN;
                requestRange(value);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(DroneSettingsNetworking.ID, (client, handler, buf, sender) -> {
            double range = DroneSettings.validatedRange(buf.readDouble());
            boolean allowed = buf.readBoolean();
            client.execute(() -> {
                DroneSettingsNetworking.clientRange = range;
                editable = allowed;
                Rc2FlightUi.syncRcRange((float)range);
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            DroneSettingsNetworking.clientRange = DroneSettings.DEFAULT_RC_RANGE;
            editable = false;
            nextRequest = 0; pendingRange = Double.NaN;
            SurfaceMap.clear();
        });
    }
    public static boolean editable() { return editable; }
    public static void requestRange(double range) {
        if (!editable || !ClientPlayNetworking.canSend(DroneSettingsNetworking.ID)) return;
        long now = System.nanoTime();
        if (now < nextRequest) { pendingRange = range; return; }
        nextRequest = now + 100_000_000L;
        var buf = PacketByteBufs.create();
        buf.writeDouble(DroneSettings.validatedRange(range));
        ClientPlayNetworking.send(DroneSettingsNetworking.ID, buf);
    }
}
