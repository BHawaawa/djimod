package com.example.avatadrone.client;

import com.example.avatadrone.client.DroneKeys;
import com.example.avatadrone.client.Rc2FlightUi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.text.Text;

public final class DroneKeySetup {
    private static boolean pending = true;

    private DroneKeySetup() {
    }

    public static void onJoin() {
        pending = true;
    }

    public static void onDisconnect() {
        pending = true;
    }

    public static void promptIfNeeded(MinecraftClient minecraftClient) {
        if (pending && minecraftClient.player != null && minecraftClient.currentScreen == null) {
            pending = false;
            minecraftClient.setScreen(new ConfirmScreen(bl -> {
                DroneKeys.applyControlMode(bl);
                Rc2FlightUi.toast(bl ? "本次使用：DJI 遥控器操控" : "本次使用：键盘操控");
                minecraftClient.setScreen(null);
            }, Text.literal("选择本次操控设备"), Text.literal("请选择本次游戏使用的操控设备。\n\nDJI 遥控器：Xbox 手柄控制飞行，键盘只保留功能键，WASD 等飞行键不再占用。\n键盘：使用原来的键盘飞行按键。\n\n每次进入游戏都会重新询问。\n如果遇到不能移动等问题，请按下 Shift 切换成英文输入法后再试。\n\n需要自定义按键时，进入游戏后按 F8 可直接打开按键绑定页面。"), Text.literal("DJI 遥控器操控"), Text.literal("键盘操控")));
        }
    }

    public static void tick(MinecraftClient minecraftClient) {
        if (minecraftClient.currentScreen == null && DroneKeys.CUSTOMIZE.isPressed()) {
            minecraftClient.setScreen(new KeybindsScreen(null, minecraftClient.options));
        }
    }
}

