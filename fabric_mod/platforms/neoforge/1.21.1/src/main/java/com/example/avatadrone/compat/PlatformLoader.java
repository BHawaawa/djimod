package com.example.avatadrone.compat;

import java.nio.file.Path;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.fml.loading.LoadingModList;

public final class PlatformLoader {
    private static final PlatformLoader INSTANCE = new PlatformLoader();
    public static PlatformLoader getInstance() { return INSTANCE; }
    public Path getConfigDir() { return FMLPaths.CONFIGDIR.get(); }
    public boolean isModLoaded(String id) {
        String alias = id.equals("sodium") ? "embeddium" : id.equals("iris") ? "oculus" : id;
        return LoadingModList.get() != null && LoadingModList.get().getMods().stream()
                .anyMatch(mod -> id.equals(mod.getModId()) || alias.equals(mod.getModId()));
    }
}
