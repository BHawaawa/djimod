package com.example.avatadrone;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@Mod("djimod")
public final class NeoForgeEntrypoint {
    public NeoForgeEntrypoint() { new AvataDrone().onInitialize(); }

    @EventBusSubscriber(modid = "djimod", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Client {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> new AvataDroneClient().onInitializeClient());
        }
    }
}
