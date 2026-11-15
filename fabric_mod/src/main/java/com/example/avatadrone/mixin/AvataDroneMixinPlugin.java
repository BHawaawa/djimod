package com.example.avatadrone.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Render-engine mixins are deliberately disabled when an engine owns the same
 * WorldRenderer methods. The RC2 feed uses the engine's normal camera path in
 * that case, so startup remains valid with Sodium/Iris.
 */
public final class AvataDroneMixinPlugin implements IMixinConfigPlugin {
    private static boolean alternateRenderer;

    @Override public void onLoad(String mixinPackage) {
        alternateRenderer = FabricLoader.getInstance().isModLoaded("sodium")
                || FabricLoader.getInstance().isModLoaded("iris");
    }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !(alternateRenderer && mixinClassName.endsWith(".WorldRendererMixin"));
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String targetClassName, ClassNode targetClass,
            String mixinClassName, IMixinInfo mixinInfo) {}
    @Override public void postApply(String targetClassName, ClassNode targetClass,
            String mixinClassName, IMixinInfo mixinInfo) {}
}
