package com.example.avatadrone.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.InputUtil.Key;
import net.minecraft.client.util.InputUtil.Type;
import net.minecraft.text.Text;
import org.slf4j.LoggerFactory;

public final class DroneKeyBinding extends KeyBinding {
   private static final int[] MODIFIER_KEYS = new int[]{341, 345, 340, 344, 342, 346};
   private final Key defaultPrimary;
   private final int defaultModifiers;
   private Key primary;
   private int modifiers;
   private static boolean loaded;
   private static Collection<KeyBinding> bindings;

   public DroneKeyBinding(String var1, Type var2, int var3, int var4) {
      super("key.djimod." + var1, Type.KEYSYM, -1, "key.categories.djimod");
      this.primary = this.defaultPrimary = var3 < 0 ? InputUtil.UNKNOWN_KEY : var2.createFromCode(var3);
      this.modifiers = this.defaultModifiers = var4;
   }

   public static int modifierBit(int var0) {
      return switch (var0) {
         case 340, 344 -> 1;
         case 341, 345 -> 2;
         case 342, 346 -> 4;
         default -> 0;
      };
   }

   public static int currentModifiers() {
      long var0 = MinecraftClient.getInstance().getWindow().getHandle();
      int var2 = 0;

      for (int var6 : MODIFIER_KEYS) {
         if (InputUtil.isKeyPressed(var0, var6)) {
            var2 |= modifierBit(var6);
         }
      }

      return var2;
   }

   public boolean modifiersMatch() {
      int var1 = currentModifiers();
      if (!this.acceptsModifiers(var1)) {
         return false;
      } else {
         if (bindings != null) {
            for (KeyBinding var3 : bindings) {
               DroneKeyBinding var4 = (DroneKeyBinding)var3;
               if (this.sameContext(var4) && this.primary.equals(var4.primary) && moreSpecificActive(this.modifiers, var4.modifiers, var1)) {
                  return false;
               }
            }
         }

         return true;
      }
   }

   public boolean acceptsModifiers(int var1) {
      return (var1 & this.modifiers) == this.modifiers;
   }

   public static boolean moreSpecificActive(int var0, int var1, int var2) {
      return var0 != var1 && (var1 & var0) == var0 && (var2 & var1) == var1;
   }

   private boolean sameContext(DroneKeyBinding var1) {
      return this.context().equals(var1.context());
   }

   private String context() {
      String var1 = this.getTranslationKey();
      if (var1.contains("slot")) {
         return "charger";
      } else {
         return !var1.endsWith(".enter") && !var1.endsWith(".enter_alt") && !var1.endsWith(".disconnect") ? "flight" : "controller";
      }
   }

   public void setChord(Key var1, int var2) {
      this.primary = var1;
      this.modifiers = this.isUnbound() ? 0 : var2 & 7;
      if (var1.getCategory() == Type.KEYSYM) {
         this.modifiers = this.modifiers & ~modifierBit(var1.getCode());
      }

      save();
   }

   @Override
   public void setBoundKey(Key boundKey) {
      this.primary = boundKey;
      this.modifiers = boundKey.equals(this.defaultPrimary) ? this.defaultModifiers : 0;
      save();
   }

   @Override
   public Key getDefaultKey() {
      return this.defaultPrimary;
   }

   @Override
   public String getBoundKeyTranslationKey() {
      return this.primary.getTranslationKey();
   }

   @Override
   public boolean isUnbound() {
      return this.primary.equals(InputUtil.UNKNOWN_KEY);
   }

   @Override
   public boolean isDefault() {
      return this.primary.equals(this.defaultPrimary) && this.modifiers == this.defaultModifiers;
   }

   @Override
   public boolean matchesKey(int keyCode, int scanCode) {
      return !this.isUnbound()
         && this.modifiersMatch()
         && (
            this.primary.getCategory() == Type.KEYSYM
               ? this.primary.getCode() == keyCode
               : this.primary.getCategory() == Type.SCANCODE && this.primary.getCode() == scanCode
         );
   }

   @Override
   public boolean matchesMouse(int code) {
      return !this.isUnbound() && this.modifiersMatch() && this.primary.getCategory() == Type.MOUSE && this.primary.getCode() == code;
   }

   @Override
   public Text getBoundKeyLocalizedText() {
      return Text.literal(((this.modifiers & 2) != 0 ? "Ctrl+" : "") + ((this.modifiers & 4) != 0 ? "Alt+" : "") + ((this.modifiers & 1) != 0 ? "Shift+" : ""))
         .append(this.primary.getLocalizedText());
   }

   @Override
   public boolean equals(KeyBinding other) {
      if (!(other instanceof DroneKeyBinding var2)) {
         return false;
      } else {
         return !this.sameContext(var2) ? false : !this.isUnbound() && this.primary.equals(var2.primary) && this.modifiers == var2.modifiers;
      }
   }

   public static void saveAll() {
      save();
   }

   public static void load(Collection<KeyBinding> var0) {
      if (!loaded) {
         bindings = var0;
         Path var1 = FabricLoader.getInstance().getConfigDir().resolve("avatadrone-keybindings.properties");
         boolean var2 = var0.stream().allMatch(KeyBinding::isUnbound);
         boolean var3 = var0.stream().anyMatch(var0x -> !var0x.isDefault());
         boolean var4 = false;

         try {
            Path var5 = MinecraftClient.getInstance().runDirectory.toPath().resolve("options.txt");
            var4 = Files.exists(var5) && Files.readString(var5).contains("key_key.avatadrone.");
         } catch (IOException var15) {
            LoggerFactory.getLogger("djimod").warn("Cannot inspect legacy drone bindings", var15);
         }

         Properties var16 = new Properties();
         if (Files.exists(var1)) {
            try (BufferedReader var6 = Files.newBufferedReader(var1)) {
               var16.load(var6);
            } catch (Exception var14) {
               LoggerFactory.getLogger("djimod").warn("Cannot load drone bindings", var14);
            }
         }

         for (KeyBinding var7 : var0) {
            DroneKeyBinding var8 = (DroneKeyBinding)var7;
            String var9 = var8.getTranslationKey();

            try {
               if (var16.containsKey(var9)) {
                  var8.setChord(InputUtil.fromTranslationKey(var16.getProperty(var9)), Integer.parseInt(var16.getProperty(var9 + ".modifiers", "0")));
               } else if (var2) {
                  var8.setBoundKey(var8.defaultPrimary);
               } else if (var4 && var3) {
                  var8.modifiers = 0;
               }
            } catch (Exception var11) {
               LoggerFactory.getLogger("djimod").warn("Invalid drone binding: {}", var9, var11);
            }
         }

         loaded = true;
         save();
         KeyBinding.updateKeysByCode();
      }
   }

   private static void save() {
      if (loaded) {
         Properties var0 = new Properties();

         for (KeyBinding var2 : bindings) {
            DroneKeyBinding var3 = (DroneKeyBinding)var2;
            var0.setProperty(var3.getTranslationKey(), var3.primary.getTranslationKey());
            var0.setProperty(var3.getTranslationKey() + ".modifiers", Integer.toString(var3.modifiers));
         }

         Path var9 = FabricLoader.getInstance().getConfigDir().resolve("avatadrone-keybindings.properties");
         Path var10 = var9.resolveSibling(var9.getFileName() + ".tmp");

         try {
            Files.createDirectories(var9.getParent());

            try (BufferedWriter var11 = Files.newBufferedWriter(var10)) {
               var0.store(var11, "DJI keyboard and mouse chords");
            }

            Files.move(var10, var9, StandardCopyOption.REPLACE_EXISTING);
         } catch (Exception var8) {
            LoggerFactory.getLogger("djimod").warn("Cannot save drone bindings", var8);
         }
      }
   }
}
