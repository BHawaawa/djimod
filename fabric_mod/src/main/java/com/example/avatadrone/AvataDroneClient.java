package com.example.avatadrone;

import com.example.avatadrone.client.AvataDroneRenderer;
import com.example.avatadrone.client.Rc2View;
import com.example.avatadrone.client.RcnSerialInput;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class AvataDroneClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(AvataDrone.AVATA360, AvataDroneRenderer::new);
        EntityRendererRegistry.register(AvataDrone.MINI4PRO, com.example.avatadrone.client.Mini4ProRenderer::new);
        EntityRendererRegistry.register(AvataDrone.M400, com.example.avatadrone.client.M400Renderer::new);
        EntityRendererRegistry.register(AvataDrone.AURO2000, com.example.avatadrone.client.PowerStationRenderer::new);
        com.example.avatadrone.client.FlightSettingsClient.register();
        com.example.avatadrone.item.PhoneItem.clientTime = () -> {
            var w = net.minecraft.client.MinecraftClient.getInstance().world;
            return w == null ? 0L : w.getTime();
        };
        Rc2View.registerControls();
        com.example.avatadrone.client.VoiceAnnouncer.register();
        RcnSerialInput.start();
        com.example.avatadrone.client.FlightMapStations.register();
        com.example.avatadrone.client.charger.ModChargersClient.init();
        com.example.avatadrone.client.charger.ChargingHud.register();
        com.example.avatadrone.client.DroneChunkClient.register();
        com.example.avatadrone.item.DroneEquipmentItem.openWheel = hand -> {
            var client = net.minecraft.client.MinecraftClient.getInstance();
            if (client.currentScreen == null) client.setScreen(new com.example.avatadrone.client.DroneWheelScreen(hand));
        };
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> com.example.avatadrone.client.DroneKeySetup.onJoin());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            com.example.avatadrone.client.DroneKeySetup.onDisconnect();
            Rc2View.close();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { Rc2View.close(); RcnSerialInput.stop(); });
    }
}
