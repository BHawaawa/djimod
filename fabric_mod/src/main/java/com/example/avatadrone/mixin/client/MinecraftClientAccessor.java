package com.example.avatadrone.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {
    @Mutable
    @Accessor("framebuffer")
    void avatadrone$setFramebuffer(Framebuffer framebuffer);

    @org.spongepowered.asm.mixin.Mutable
    @Accessor("currentScreen")
    void avatadrone$setCurrentScreen(net.minecraft.client.gui.screen.Screen screen);
}
