package com.example.avatadrone.qa;

import com.example.avatadrone.client.DroneKeys;
import com.example.avatadrone.client.DroneKeyBinding;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Files;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

/** Isolated actual-client regression for mixin capture and persistent chord configuration. */
public final class KeybindingQa implements ClientModInitializer {
    private boolean ran;
    private static void check(boolean condition,String message) {
        if(!condition) throw new AssertionError(message);
    }
    @Override public void onInitializeClient() {
        if(!Boolean.getBoolean("avatadrone.keybindingQa")) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if(ran || client.currentScreen == null || client.getOverlay() != null) return;
            ran = true;
            try {
                DroneKeys.loadBindings();
                var screen = new KeybindsScreen(client.currentScreen,client.options);
                client.setScreen(screen);
                screen.selectedKeyBinding = DroneKeys.ENTER_ALT;
                screen.keyPressed(GLFW.GLFW_KEY_LEFT_CONTROL,0,2);
                check(screen.selectedKeyBinding != null,"modifier press waits for primary");
                screen.keyPressed(GLFW.GLFW_KEY_K,0,3);
                check(screen.selectedKeyBinding == null,"chord finishes capture");
                var chord = (DroneKeyBinding)DroneKeys.ENTER_ALT;
                check(chord.acceptsModifiers(3) && !chord.acceptsModifiers(2),"Ctrl+Shift recorded");
                check(DroneKeys.label(chord).startsWith("Ctrl+Shift+"),"visible chord label");
                var props = new Properties();
                try(var reader = Files.newBufferedReader(FabricLoader.getInstance().getConfigDir().resolve("avatadrone-keybindings.properties"))) { props.load(reader); }
                check("3".equals(props.getProperty(chord.getTranslationKey()+".modifiers")),"modifiers saved");
                check("key.keyboard.k".equals(props.getProperty(chord.getTranslationKey())),"primary saved");
                screen.selectedKeyBinding = chord;
                screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);
                check(chord.isUnbound(),"Escape clears");
                screen.selectedKeyBinding = chord;
                screen.keyPressed(GLFW.GLFW_KEY_LEFT_ALT,0,4);
                screen.keyReleased(GLFW.GLFW_KEY_LEFT_ALT,0,0);
                check(screen.selectedKeyBinding == null && chord.getBoundKeyTranslationKey().equals("key.keyboard.left.alt"),"standalone modifier on release");
                screen.selectedKeyBinding = chord;
                screen.mouseClicked(0,0,1);
                check(chord.getBoundKeyTranslationKey().equals("key.mouse.right"),"mouse capture");
                DroneKeys.restoreDefaults();
                check(chord.isDefault() && !chord.acceptsModifiers(0),"restore default chord");
                Files.writeString(client.runDirectory.toPath().resolve("keybinding-qa-result.txt"),"PASS: actual screen capture, Ctrl+Shift, modifier release, mouse, clear, persistence, reset");
                client.scheduleStop();
            } catch(Exception | AssertionError e) {
                throw new RuntimeException("Drone keybinding QA failed",e);
            }
        });
    }
}
