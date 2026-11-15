package com.example.avatadrone.compat;

import java.nio.file.Path;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.LoadingModList;

/** Available during mixin selection, before the runtime ModList is constructed. */
public final class PlatformLoader {
    private static final PlatformLoader INSTANCE = new PlatformLoader();
    public static PlatformLoader getInstance() { return INSTANCE; }
    public Path getConfigDir() { return FMLPaths.CONFIGDIR.get(); }
    public boolean isModLoaded(String id) {
        // Embeddium/Oculus own the equivalent rendering paths on Forge.
        String platformId = id.equals("sodium") ? "embeddium" : id.equals("iris") ? "oculus" : id;
        return LoadingModList.get() != null && LoadingModList.get().getMods().stream()
                .anyMatch(mod -> id.equals(mod.getModId()) || platformId.equals(mod.getModId()));
    }
}
