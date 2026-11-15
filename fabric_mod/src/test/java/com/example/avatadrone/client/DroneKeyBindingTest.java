package com.example.avatadrone.client;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class DroneKeyBindingTest {
    private static void check(boolean value, String message) {
        if(!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var vanilla = new KeyBinding("test.vanilla", GLFW.GLFW_KEY_W, "test");
        var drone = new DroneKeyBinding("test", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_W, GLFW.GLFW_MOD_CONTROL);
        KeyBinding.updateKeysByCode();
        KeyBinding.onKeyPressed(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_W));
        check(vanilla.wasPressed(), "drone registration must not steal vanilla W");
        check(!drone.acceptsModifiers(0), "bare W must not trigger Ctrl+W");
        check(drone.acceptsModifiers(2) && drone.acceptsModifiers(3), "Ctrl chord accepts Shift for simultaneous descent");
        drone.setChord(InputUtil.Type.MOUSE.createFromCode(1), 6);
        check(!drone.acceptsModifiers(2) && drone.acceptsModifiers(6), "multi-modifier mouse chord");
        check(drone.getBoundKeyTranslationKey().equals("key.mouse.right"), "mouse persistence key");
        check(!drone.isDefault(), "modified chord enables reset");
        drone.setBoundKey(drone.getDefaultKey());
        check(drone.isDefault() && !drone.acceptsModifiers(0), "reset restores key and modifier");
        drone.setChord(InputUtil.UNKNOWN_KEY,7);
        check(drone.isUnbound(), "clear removes binding");
        var same = new DroneKeyBinding("same",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_W,2);
        drone.setChord(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_W),0);
        check(!drone.equals(same), "distinct modifiers are distinct chords");
        drone.setChord(InputUtil.Type.KEYSYM.createFromCode(GLFW.GLFW_KEY_W),2);
        check(drone.equals(same), "same chords report conflict");
        check(!drone.equals(vanilla), "flight context does not conflict with vanilla movement");
        check(DroneKeyBinding.moreSpecificActive(0,2,2), "Ctrl+K shadows K");
        check(DroneKeyBinding.moreSpecificActive(2,3,3), "Ctrl+Shift+K shadows Ctrl+K");
        check(!DroneKeyBinding.moreSpecificActive(0,2,1), "Shift+W keeps bare W for descent");
        System.out.println("DroneKeyBindingTest PASS: vanilla dispatch, modifiers, mouse, reset, clear, conflicts");
    }
}
