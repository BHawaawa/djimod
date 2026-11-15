package com.example.avatadrone.client;

import com.example.avatadrone.item.DroneEquipmentItem;
import com.example.avatadrone.network.DroneMaintenance;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;

/** Mouse-driven wheel; while open Minecraft handles neither attacks nor movement keys. */
public final class DroneWheelScreen extends Screen {
    private final Hand hand;
    private final net.minecraft.item.Item openedItem;
    private int hover = -1;
    public DroneWheelScreen(Hand hand) {
        super(Text.literal("无人机操作"));
        this.hand = hand;
        this.openedItem = net.minecraft.client.MinecraftClient.getInstance().player.getStackInHand(hand).getItem();
    }
    private ItemStack stack() { return client.player == null ? ItemStack.EMPTY : client.player.getStackInHand(hand); }
    private String[] actions() {
        return new String[]{DroneEquipmentItem.powered(stack()) ? "power_off" : "power_on",
                DroneEquipmentItem.hasBattery(stack()) ? "battery_remove" : "battery_insert", "place"};
    }
    private String[] labels() {
        return new String[]{DroneEquipmentItem.powered(stack()) ? "关机" : "开机",
                DroneEquipmentItem.hasBattery(stack()) ? "拔出电池" : "安装电池", "放置"};
    }
    private int batteryCount() {
        if (!(stack().getItem() instanceof DroneEquipmentItem equipment) || client.player == null) return 0;
        int count = 0;
        for (int i = 0; i < client.player.getInventory().size(); i++) {
            var battery = client.player.getInventory().getStack(i);
            if (com.example.avatadrone.item.DroneBatteryStorage.accepts(battery, DroneMaintenance.battery(equipment)))
                count += battery.getCount();
        }
        return count;
    }

    private boolean enabled(int i) {
        if (!DroneEquipmentItem.action(stack()).isEmpty()) return false;
        return switch (i) {
            case 0 -> DroneEquipmentItem.hasBattery(stack());
            case 1 -> !DroneEquipmentItem.powered(stack()) && (DroneEquipmentItem.hasBattery(stack())
                    ? client.player.getInventory().getEmptySlot() >= 0 : batteryCount() > 0);
            case 2 -> DroneEquipmentItem.powered(stack());
            default -> false;
        };
    }
    private int sector(double x, double y) {
        double dx = x - width / 2.0, dy = y - height / 2.0;
        double radius = Math.hypot(dx, dy);
        if (radius < 38 || radius > radius()) return -1;
        double angle = (Math.atan2(dy, dx) + Math.PI / 2 + Math.PI / 3 + Math.PI * 2) % (Math.PI * 2);
        return (int) (angle / (Math.PI * 2 / 3));
    }
    private int radius() { return Math.min(112, Math.min(width, height) / 2 - 22); }
    @Override public void tick() {
        if (client.player == null || !client.player.isAlive() || stack().getItem() != openedItem) close();
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        hover = sector(mouseX, mouseY);
        ctx.fill(0, 0, width, height, 0x45040810);
        int cx = width / 2, cy = height / 2, r = radius();
        // Scanline annular sectors avoid GUI-scale-dependent triangle seams.
        for (int y = -r; y <= r; y++) {
            int run = -r, last = -2;
            for (int x = -r; x <= r + 1; x++) {
                int current = x > r ? -2 : sector(cx + x, cy + y);
                if (current != last) {
                    if (last >= 0) ctx.fill(cx + run, cy + y, cx + x, cy + y + 1,
                            !enabled(last) ? 0xBB171C24 : last == hover ? 0xE02D8794 : 0xDD243440);
                    run = x; last = current;
                }
            }
        }
        String[] labels = labels();
        for (int i = 0; i < 3; i++) {
            double angle = -Math.PI / 2 + i * Math.PI * 2 / 3;
            int x = cx + (int) (Math.cos(angle) * (r + 38) / 2);
            int y = cy + (int) (Math.sin(angle) * (r + 38) / 2);
            ctx.drawCenteredTextWithShadow(textRenderer, labels[i], x, y - 4, enabled(i) ? 0xFFFFFF : 0x737B86);
        }
        ctx.drawCenteredTextWithShadow(textRenderer, DroneEquipmentItem.powered(stack()) ? "已开机" : "未开机", cx, cy - 10, 0xE7F1F8);
        ctx.drawCenteredTextWithShadow(textRenderer, DroneEquipmentItem.hasBattery(stack()) ? "电池已安装" : "无电池", cx, cy + 5, 0x9EB5C7);
        String hint = "背包电池：" + batteryCount() + " · 左键选择 · ESC 返回";
        if (!DroneEquipmentItem.action(stack()).isEmpty()) hint = "正在操作，请等待动画完成";
        else if (hover == 2 && !enabled(2)) hint = openedItem instanceof DroneEquipmentItem equipment && equipment.model.equals("m400")
                ? "安装 TB100 电池并开机后才能放置" : "开机并取下保护罩后才能放置";
        else if (hover == 1 && !enabled(1)) hint = DroneEquipmentItem.powered(stack())
                ? "请先关机，再拔出电池" : DroneEquipmentItem.hasBattery(stack())
                ? "背包已满，请留出一个空格存放电池" : "背包中没有对应型号的电池";
        else if (hover == 0 && !enabled(0)) hint = "请先安装电池";
        ctx.drawCenteredTextWithShadow(textRenderer, hint, cx, cy + r + 8, 0xD4E0E9);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        int selected = sector(x, y);
        if (button != 0 || selected < 0 || !enabled(selected)) return true;
        if (openedItem instanceof DroneEquipmentItem equipment && "power_on".equals(actions()[selected])) {
            VoiceAnnouncer.schedulePowerOn(equipment.model);
        }
        var packet = PacketByteBufs.create();
        packet.writeBoolean(hand == Hand.OFF_HAND);
        packet.writeString(actions()[selected]);
        ClientPlayNetworking.send(DroneMaintenance.ACTION, packet);
        close();
        return true;
    }
}
