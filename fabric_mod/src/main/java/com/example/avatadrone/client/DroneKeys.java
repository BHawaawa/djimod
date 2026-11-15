package com.example.avatadrone.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.InputUtil.Key;
import net.minecraft.client.util.InputUtil.Type;
import org.lwjgl.glfw.GLFW;

public final class DroneKeys {
   private static final Map<String, KeyBinding> ALL = new LinkedHashMap<>();
   private static boolean remoteMode;
   public static final KeyBinding ENTER = mouse("enter", 2);
   public static final KeyBinding ENTER_ALT = add("enter_alt", 77);
   public static final KeyBinding DISCONNECT = add("disconnect", 66);
   public static final KeyBinding FORWARD = add("forward", 87);
   public static final KeyBinding BACK = add("back", 83);
   public static final KeyBinding LEFT = add("left", 65);
   public static final KeyBinding RIGHT = add("right", 68);
   public static final KeyBinding UP = add("up", 32);
   public static final KeyBinding DOWN = add("down", 340);
   public static final KeyBinding UP_ALT = add("up_alt", 81);
   public static final KeyBinding DOWN_ALT = add("down_alt", 69);
   public static final KeyBinding YAW_LEFT = add("yaw_left", 90);
   public static final KeyBinding YAW_RIGHT = add("yaw_right", 88);
   public static final KeyBinding RETURN = add("return", 80);
   public static final KeyBinding MODE_MOD = add("mode_modifier", -1);
   public static final KeyBinding CINE = add("cine", 67);
   public static final KeyBinding NORMAL = add("normal", 78);
   public static final KeyBinding SPORT = add("sport", 82);
   public static final KeyBinding EXIT = add("exit", 256);
   public static final KeyBinding GIMBAL = add("gimbal", 71);
   public static final KeyBinding ZOOM = add("zoom", 73);
   public static final KeyBinding FOV = add("fov", 89);
   public static final KeyBinding ROLL = add("roll", 84);
   public static final KeyBinding GIMBAL_UP = add("gimbal_up", 70);
   public static final KeyBinding GIMBAL_DOWN = add("gimbal_down", 82);
   public static final KeyBinding GIMBAL_MAX = add("gimbal_max", 79);
   public static final KeyBinding MODE_CYCLE = add("mode_cycle", 86);
   public static final KeyBinding CRUISE = add("cruise", 72);
   public static final KeyBinding CUSTOMIZE = add("customize", 297);
   public static final KeyBinding SLOT_MOD = add("slot_modifier", -1);
   public static final KeyBinding SLOT1 = add("slot1", 49);
   public static final KeyBinding SLOT2 = add("slot2", 50);
   public static final KeyBinding SLOT3 = add("slot3", 51);

   private static KeyBinding add(String var0, int var1) {
      DroneKeyBinding var2 = new DroneKeyBinding(var0, Type.KEYSYM, var1, defaultModifiers(var0));
      ALL.put(var0, KeyBindingHelper.registerKeyBinding(var2));
      return var2;
   }

   private static KeyBinding mouse(String var0, int var1) {
      DroneKeyBinding var2 = new DroneKeyBinding(var0, Type.MOUSE, var1, defaultModifiers(var0));
      ALL.put(var0, KeyBindingHelper.registerKeyBinding(var2));
      return var2;
   }

   private static int defaultModifiers(String var0) {
      return switch (var0) {
         case "enter_alt" -> 2;
         case "cine", "normal", "sport", "slot1", "slot2", "slot3" -> 4;
         default -> 0;
      };
   }

   public static void register() {
   }

   public static boolean held(KeyBinding var0) {
      MinecraftClient var1 = MinecraftClient.getInstance();
      if (var0 instanceof DroneKeyBinding var2 && !var2.modifiersMatch()) {
         return false;
      }

      if (var1.getWindow() != null && var1.isWindowFocused() && !var0.isUnbound()) {
         Key var6 = InputUtil.fromTranslationKey(var0.getBoundKeyTranslationKey());
         long var3 = var1.getWindow().getHandle();
         if (var6.getCategory() == Type.MOUSE) {
            return GLFW.glfwGetMouseButton(var3, var6.getCode()) == 1;
         } else if (var6.getCategory() == Type.SCANCODE) {
            for (int var5 = 32; var5 <= 348; var5++) {
               if (GLFW.glfwGetKeyScancode(var5) == var6.getCode() && InputUtil.isKeyPressed(var3, var5)) {
                  return true;
               }
            }

            return false;
         } else {
            return var6.getCode() >= 0 && InputUtil.isKeyPressed(var3, var6.getCode());
         }
      } else {
         return false;
      }
   }

   public static boolean flightKey(int var0, int var1) {
      for (Entry var3 : ALL.entrySet()) {
         if (!((String)var3.getKey()).startsWith("slot") && ((KeyBinding)var3.getValue()).matchesKey(var0, var1)) {
            return true;
         }
      }

      return false;
   }

   public static boolean flightMouse(int var0) {
      for (Entry var2 : ALL.entrySet()) {
         if (!((String)var2.getKey()).startsWith("slot") && !((String)var2.getKey()).equals("disconnect") && ((KeyBinding)var2.getValue()).matchesMouse(var0)) {
            return true;
         }
      }

      return false;
   }

   public static boolean isRemoteMode() {
      return remoteMode;
   }

   public static void restoreDefaults() {
      ALL.values().forEach(var0x -> var0x.setBoundKey(var0x.getDefaultKey()));
      DroneKeyBinding.saveAll();
      KeyBinding.updateKeysByCode();
      MinecraftClient var0 = MinecraftClient.getInstance();
      if (var0 != null && var0.options != null) {
         var0.options.write();
      }
   }

   public static void applyControlMode(boolean var0) {
      remoteMode = var0;
      restoreDefaults();
      if (var0) {
         for (String var4 : new String[]{"forward", "back", "left", "right", "up", "down", "up_alt", "down_alt", "yaw_left", "yaw_right"}) {
            KeyBinding var5 = ALL.get(var4);
            if (var5 instanceof DroneKeyBinding var6) {
               var6.setBoundKey(InputUtil.UNKNOWN_KEY);
            }
         }
      }

      DroneKeyBinding.saveAll();
      KeyBinding.updateKeysByCode();
      MinecraftClient var7 = MinecraftClient.getInstance();
      if (var7 != null && var7.options != null) {
         var7.options.write();
      }
   }

   public static void loadBindings() {
      DroneKeyBinding.load(ALL.values());
   }

   public static String label(KeyBinding var0) {
      return var0.getBoundKeyLocalizedText().getString();
   }
}
