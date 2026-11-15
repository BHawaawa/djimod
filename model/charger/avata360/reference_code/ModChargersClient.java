package com.example.avatadrone.client.charger;

import com.example.avatadrone.ModChargers;
import com.example.avatadrone.block.charger.ChargingHubType;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

/** 在 ClientModInitializer.onInitializeClient() 里调用 ModChargersClient.init(); */
public final class ModChargersClient {
    public static void init() {
        // 一个 BlockEntityType 对应一个渲染器；按方块类型挑选两个预建的渲染器实例
        ChargingHubRenderer avata = new ChargingHubRenderer(ChargingHubType.AVATA360);
        ChargingHubRenderer mini = new ChargingHubRenderer(ChargingHubType.MINI4PRO);
        BlockEntityRendererFactories.register(ModChargers.CHARGING_HUB_BE, ctx -> new DelegatingHubRenderer(avata, mini));
    }
}
