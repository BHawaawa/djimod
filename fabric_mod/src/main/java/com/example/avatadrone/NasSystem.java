package com.example.avatadrone;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Small persistent NAS data/config store.  The file lives in the instance config directory. */
public final class NasSystem {
    private NasSystem() {}
    private static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("nas.properties"); }
    public static Properties load() {
        Properties p = new Properties();
        Path f = file();
        try {
            if (Files.exists(f)) try (var in = Files.newInputStream(f)) { p.load(in); }
            else { p.setProperty("username", "admin"); p.setProperty("password", "nas"); save(p); }
        } catch (IOException ignored) {}
        return p;
    }
    private static void save(Properties p) { try (var out = Files.newOutputStream(file())) { p.store(out, "NAS manager credentials"); } catch (IOException ignored) {} }
    public static boolean authenticate(String user, String password) {
        Properties p = load();
        return p.getProperty("username", "admin").equals(user) && p.getProperty("password", "nas").equals(password);
    }
}
