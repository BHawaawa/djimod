package com.example.avatadrone;

import com.example.avatadrone.client.AvataDroneRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class AvataDroneClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(AvataDrone.AVATA360, AvataDroneRenderer::new);
    }
}
