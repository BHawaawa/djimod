package com.example.avatadrone.client.charger;

import com.example.avatadrone.block.charger.ChargingHubBlockEntity;
import com.example.avatadrone.charging.ChargeIndicator;
import com.example.avatadrone.network.ChargingNetwork;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import org.lwjgl.glfw.GLFW;

public final class ChargingHud {
    public static boolean slotKey(long window, int key, int scan, int action, int mods) {
        var keys = new net.minecraft.client.option.KeyBinding[]{com.example.avatadrone.client.DroneKeys.SLOT1,com.example.avatadrone.client.DroneKeys.SLOT2,com.example.avatadrone.client.DroneKeys.SLOT3};
        int slot=-1;
        for(int i=0;i<3;i++) if(keys[i].matchesKey(key,scan))slot=i;
        return slotAction(window,slot,action);
    }
    public static boolean slotMouse(long window,int button,int action) {
        var keys = new net.minecraft.client.option.KeyBinding[]{com.example.avatadrone.client.DroneKeys.SLOT1,com.example.avatadrone.client.DroneKeys.SLOT2,com.example.avatadrone.client.DroneKeys.SLOT3};
        for(int i=0;i<3;i++) if(keys[i].matchesMouse(button))return slotAction(window,i,action);
        return false;
    }
    private static boolean slotAction(long window,int slot,int action) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (window != mc.getWindow().getHandle() || mc.currentScreen != null || mc.world == null
                || slot < 0 || (!com.example.avatadrone.client.DroneKeys.SLOT_MOD.isUnbound() && !com.example.avatadrone.client.DroneKeys.held(com.example.avatadrone.client.DroneKeys.SLOT_MOD))
                || !(mc.crosshairTarget instanceof BlockHitResult hit)
                || !(mc.world.getBlockEntity(hit.getBlockPos()) instanceof ChargingHubBlockEntity)) return false;
        if (action == GLFW.GLFW_PRESS) {
            var buf = PacketByteBufs.create(); buf.writeBlockPos(hit.getBlockPos()); buf.writeByte(slot);
            ClientPlayNetworking.send(ChargingNetwork.SLOT, buf);
        }
        return true;
    }
    public static void register() {
        HudRenderCallback.EVENT.register((ctx, delta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null || mc.player == null || mc.currentScreen != null || mc.options.hudHidden
                    || !(mc.crosshairTarget instanceof BlockHitResult hit)
                    || !(mc.world.getBlockEntity(hit.getBlockPos()) instanceof ChargingHubBlockEntity hub)) return;
            int width = Math.min(190, mc.getWindow().getScaledWidth() - 16);
            int x = 8;
            int y = 8;
            ctx.fill(x, y, x + width, y + 118, 0xEB101A20);
            ctx.fill(x, y, x + 2, y + 118, 0xFF65E69A);
            ctx.drawText(mc.textRenderer, hub.getCachedState().getBlock().getName(), x + 9, y + 8, 0xFFFFFF, false);
            boolean connected = ChargingNetwork.connected(hub) != null;
            for (int i = 0; i < 3; i++) {
                int row = y + 26 + i * 21;
                String state = !hub.has(i) ? "空槽" : hub.isRemoving(i) ? "正在取出" : hub.chargeFraction(i) >= 1 ? "已充满"
                        : hub.isCharging(i) ? "充电中" : connected ? "等待供电 / 排队" : "未接电源";
                String label = "槽 " + (i + 1) + "  " + (hub.has(i) ? String.format(java.util.Locale.ROOT, "%.1f%%  ", hub.chargeFraction(i) * 100) : "") + state;
                ctx.drawText(mc.textRenderer, label, x + 9, row, 0xDFE8EB, false);
                for (int k = 0; k < 4; k++) {
                    boolean lit = hub.has(i) && ChargeIndicator.lit(hub.chargeFraction(i), k, hub.isCharging(i), mc.world.getTime());
                    ctx.fill(x + 10 + k * 12, row + 11, x + 18 + k * 12, row + 14, lit ? 0xFF51F58D : 0xFF30443A);
                }
            }
            ctx.drawText(mc.textRenderer, (com.example.avatadrone.client.DroneKeys.SLOT_MOD.isUnbound() ? "" : com.example.avatadrone.client.DroneKeys.label(com.example.avatadrone.client.DroneKeys.SLOT_MOD) + "+")
                    + com.example.avatadrone.client.DroneKeys.label(com.example.avatadrone.client.DroneKeys.SLOT1) + "/"
                    + com.example.avatadrone.client.DroneKeys.label(com.example.avatadrone.client.DroneKeys.SLOT2) + "/"
                    + com.example.avatadrone.client.DroneKeys.label(com.example.avatadrone.client.DroneKeys.SLOT3) + " 插入 / 取出", x + 9, y + 94, 0x92A9B3, false);
            ctx.drawText(mc.textRenderer, "Shift+右键 插入 / 拔出充电线", x + 9, y + 106, 0x92A9B3, false);
        });
    }
}
