package com.example.avatadrone.network;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.entity.AvataDroneEntity;
import com.example.avatadrone.item.DroneEquipmentItem;
import com.example.avatadrone.item.DroneBatteryStorage;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.*;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import net.minecraft.text.Text;
import net.minecraft.world.RaycastContext;
import java.util.*;

/** Inventory transactions commit only after their server-timed animation completes. */
public final class DroneMaintenance {
    public static final Identifier ACTION = new Identifier(AvataDrone.MOD_ID, "maintenance");
    private static final Map<UUID, Pending> pending = new HashMap<>();
    private record Pending(ServerPlayerEntity player, Hand hand, ItemStack stack,
            String action, long end, net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimension,
            ItemStack batterySource, ItemStack batterySnapshot) {}
    private DroneMaintenance() {}
    public static boolean isPending(ItemStack stack) {
        return pending.values().stream().anyMatch(p -> p.stack == stack);
    }
    public static Item battery(DroneEquipmentItem item) {
        if (item.model.equals("m400")) return AvataDrone.M400_BATTERY;
        return item.model.equals("mini4pro") ? AvataDrone.MINI4PRO_BATTERY : AvataDrone.AVATA360_BATTERY;
    }
    public static int batterySlot(ServerPlayerEntity player, DroneEquipmentItem item) {
        for (int i = 0; i < player.getInventory().size(); i++)
            if (DroneBatteryStorage.accepts(player.getInventory().getStack(i), battery(item))) return i;
        return -1;
    }
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ACTION, (server, player, handler, buf, response) -> {
            boolean offhand = buf.readBoolean();
            String action = buf.readString(32);
            server.execute(() -> request(player, offhand ? Hand.OFF_HAND : Hand.MAIN_HAND, action));
        });
        ServerTickEvents.END_SERVER_TICK.register(DroneMaintenance::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> pending.clear());
    }
    public static void request(ServerPlayerEntity player, Hand hand, String action) {
        ItemStack stack = player.getStackInHand(hand);
        if (!player.isAlive() || player.isSpectator() || pending.containsKey(player.getUuid())
                || !(stack.getItem() instanceof DroneEquipmentItem item) || !item.aircraft) return;
        boolean installed = DroneEquipmentItem.hasBattery(stack), on = DroneEquipmentItem.powered(stack);
        boolean allowed = switch (action) {
            case "battery_remove", "power_on" -> installed && !on;
            case "battery_insert" -> !installed && !on;
            case "power_off", "place" -> on;
            default -> false;
        };
        if (!allowed) return;
        if (action.equals("place")) { place(player, hand, stack, item); return; }
        if (action.equals("battery_insert") && batterySlot(player, item) < 0) {
            player.sendMessage(Text.literal("背包中没有对应型号的电池"), true);
            return;
        }
        if (action.equals("battery_remove") && player.getInventory().getEmptySlot() < 0) {
            player.sendMessage(Text.literal("背包已满：请留出一个空格存放电池"), true);
            return;
        }
        ItemStack source = action.equals("battery_insert")
                ? player.getInventory().getStack(batterySlot(player, item)) : ItemStack.EMPTY;
        int duration = switch (action) {
            case "battery_insert" -> item.model.equals("m400") ? 36 : item.model.equals("mini4pro") ? 32 : 40;
            case "battery_remove" -> item.model.equals("mini4pro") ? 28 : 30;
            default -> 60;
        };
        software.bernie.geckolib.animatable.GeoItem.getOrAssignId(stack, player.getServerWorld());
        stack.getOrCreateNbt().putString("MaintenanceAction", action);
        stack.getOrCreateNbt().putLong("MaintenanceStart", player.getWorld().getTime());
        pending.put(player.getUuid(), new Pending(player, hand, stack, action,
                player.getServer().getTicks() + duration, player.getWorld().getRegistryKey(),
                source, source.isEmpty() ? ItemStack.EMPTY : DroneBatteryStorage.one(source)));
        player.sendMessage(Text.literal(switch (action) {
            case "battery_remove" -> "正在拔出电池…";
            case "battery_insert" -> "正在安装电池…";
            case "power_on" -> item.model.equals("m400") ? "正在按下 TB100 电源键开机…" : "取下云台保护罩，正在开机…";
            default -> item.model.equals("m400") ? "正在按下 TB100 电源键关机…" : "正在关机并装回云台保护罩…";
        }), true);
        player.currentScreenHandler.sendContentUpdates();
    }
    private static void tick(MinecraftServer server) {
        var iterator = pending.values().iterator();
        while (iterator.hasNext()) {
            Pending p = iterator.next();
            boolean valid = !p.player.isDisconnected() && p.player.isAlive() && !p.player.isSpectator()
                    && p.player.getStackInHand(p.hand) == p.stack
                    && p.player.getWorld().getRegistryKey().equals(p.dimension);
            if (valid && server.getTicks() < p.end) continue;
            iterator.remove();
            if (valid) complete(p);
            p.stack.removeSubNbt("MaintenanceAction");
            p.stack.removeSubNbt("MaintenanceStart");
            p.player.currentScreenHandler.sendContentUpdates();
        }
    }
    private static void complete(Pending p) {
        DroneEquipmentItem item = (DroneEquipmentItem) p.stack.getItem();
        var nbt = p.stack.getOrCreateNbt();
        switch (p.action) {
            case "battery_remove" -> {
                int slot = p.player.getInventory().getEmptySlot();
                if (slot < 0) {
                    p.player.sendMessage(Text.literal("拆卸取消：背包已满，电池仍保留在无人机内"), true);
                    return;
                }
                ItemStack removed = DroneBatteryStorage.read(nbt, battery(item));
                if (removed.isEmpty()) return;
                p.player.getInventory().setStack(slot, removed);
                DroneBatteryStorage.write(nbt, ItemStack.EMPTY);
                p.player.sendMessage(Text.literal("电池已放入背包 ×1"), true);
            }
            case "battery_insert" -> {
                int slot = -1;
                for (int i = 0; i < p.player.getInventory().size(); i++) {
                    ItemStack candidate = p.player.getInventory().getStack(i);
                    if (candidate == p.batterySource && !candidate.isEmpty()
                            && ItemStack.canCombine(candidate, p.batterySnapshot)) { slot = i; break; }
                }
                if (slot < 0) { p.player.sendMessage(Text.literal("安装取消：选中的电池已被移走或修改"), true); return; }
                ItemStack inserted = p.player.getInventory().getStack(slot);
                DroneBatteryStorage.write(nbt, inserted);
                inserted.decrement(1);
                p.player.sendMessage(Text.literal("已从背包安装电池 ×1"), true);
            }
            case "power_on" -> nbt.putBoolean("Powered", true);
            case "power_off" -> nbt.putBoolean("Powered", false);
        }
        p.player.getWorld().playSound(null, p.player.getBlockPos(),
                net.minecraft.sound.SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE,
                net.minecraft.sound.SoundCategory.PLAYERS, 0.5f, 1.5f);
    }
    private static void place(ServerPlayerEntity player, Hand hand, ItemStack stack, DroneEquipmentItem item) {
        var world = player.getServerWorld();
        Vec3d start = player.getEyePos();
        BlockHitResult hit = world.raycast(new RaycastContext(start, start.add(player.getRotationVec(1).multiply(5)),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK || hit.getSide() != Direction.UP
                || !world.canPlayerModifyAt(player, hit.getBlockPos()) || !world.getWorldBorder().contains(hit.getBlockPos())) {
            player.sendMessage(Text.literal("请瞄准 5 格内可放置的地面"), true); return;
        }
        AvataDroneEntity drone = item.model.equals("m400") ? AvataDrone.M400.create(world)
                : item.model.equals("mini4pro") ? AvataDrone.MINI4PRO.create(world) : AvataDrone.AVATA360.create(world);
        if (drone == null) return;
        if (stack.hasNbt() && stack.getNbt().contains("AircraftData"))
            drone.readCustomDataFromNbt(stack.getNbt().getCompound("AircraftData"));
        if (stack.hasCustomName()) drone.setCustomName(stack.getName());
        if (stack.hasNbt() && stack.getNbt().containsUuid("AircraftUuid")) {
            var id = stack.getNbt().getUuid("AircraftUuid");
            boolean exists = false;
            for (var dimension : player.getServer().getWorlds()) if (dimension.getEntity(id) != null) exists = true;
            if (!exists) drone.setUuid(id);
        }
        Vec3d pos = hit.getPos();
        drone.refreshPositionAndAngles(pos.x, pos.y + 0.01, pos.z, player.getYaw(), 0);
        drone.installBattery(DroneBatteryStorage.read(stack.getNbt(), battery(item)));
        if (!drone.hasBattery()) return;
        drone.setNoGravity(false);
        if (!world.isSpaceEmpty(drone) || !world.getOtherEntities(drone, drone.getBoundingBox()).isEmpty()) {
            player.sendMessage(Text.literal("放置空间不足"), true); return;
        }
        if (world.spawnEntity(drone)) {
            stack.decrement(1);
            player.currentScreenHandler.sendContentUpdates();
        }
    }
}
