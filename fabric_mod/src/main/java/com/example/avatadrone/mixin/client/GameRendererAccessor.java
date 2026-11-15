package com.example.avatadrone.mixin.client;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Allows the aerial pass to include the ground at altitude in its fog distance. */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("viewDistance")
    float avatadrone$getViewDistance();

    @Accessor("viewDistance")
    void avatadrone$setViewDistance(float viewDistance);
}
