package com.example.avatadrone.mixin.client;

import com.example.avatadrone.client.DroneKeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.screen.option.ControlsListWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeybindsScreen.class)
public abstract class DroneKeybindsScreenMixin extends Screen {
    @Shadow public KeyBinding selectedKeyBinding;
    @Shadow public long lastKeyCodeUpdateTime;
    @Shadow private ControlsListWidget controlsList;
    @Unique private int avatadrone$pendingModifier = -1;
    protected DroneKeybindsScreenMixin(Text title) { super(title); }
    @Unique private void avatadrone$bind(InputUtil.Key key, int mods) {
        ((DroneKeyBinding)selectedKeyBinding).setChord(key,mods);
        selectedKeyBinding = null;
        avatadrone$pendingModifier = -1;
        lastKeyCodeUpdateTime = Util.getMeasuringTimeMs();
        controlsList.update();
        MinecraftClient.getInstance().options.write();
    }
    @Inject(method="keyPressed",at=@At("HEAD"),cancellable=true)
    private void avatadrone$capture(int key,int scan,int mods,CallbackInfoReturnable<Boolean> cir) {
        if(!(selectedKeyBinding instanceof DroneKeyBinding)) return;
        if(DroneKeyBinding.modifierBit(key) != 0) avatadrone$pendingModifier = key;
        else avatadrone$bind(key == GLFW.GLFW_KEY_ESCAPE ? InputUtil.UNKNOWN_KEY : InputUtil.fromKeyCode(key,scan),mods);
        cir.setReturnValue(true);
    }
    @Override public boolean keyReleased(int key,int scan,int mods) {
        if(selectedKeyBinding instanceof DroneKeyBinding && key == avatadrone$pendingModifier) {
            avatadrone$bind(InputUtil.fromKeyCode(key,scan),mods);
            return true;
        }
        return super.keyReleased(key,scan,mods);
    }
    @Inject(method="mouseClicked",at=@At("HEAD"),cancellable=true)
    private void avatadrone$captureMouse(double x,double y,int button,CallbackInfoReturnable<Boolean> cir) {
        if(!(selectedKeyBinding instanceof DroneKeyBinding)) return;
        avatadrone$bind(InputUtil.Type.MOUSE.createFromCode(button),DroneKeyBinding.currentModifiers());
        cir.setReturnValue(true);
    }
}
