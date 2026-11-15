package com.example.avatadrone.client;

import com.example.avatadrone.ModPillar;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

/** 在 ClientModInitializer.onInitializeClient() 里调用 ModPillarClient.init(); */
public final class ModPillarClient {
    public static void init() {
        BlockEntityRendererFactories.register(ModPillar.STEEL_PILLAR_BE, ctx -> new SteelPillarRenderer());
    }
}
