package com.example.avatadrone;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod("djimod")
public final class ForgeEntrypoint {
    public ForgeEntrypoint() { new AvataDrone().onInitialize(); }

    @Mod.EventBusSubscriber(modid = "djimod", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Client {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> new AvataDroneClient().onInitializeClient());
        }
    }
}
