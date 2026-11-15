package com.example.avatadrone.network;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.entity.AvataDroneEntity;
import com.example.avatadrone.item.Rc2Item;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

public final class DroneControlNetworking {
    public static final net.minecraft.util.Identifier DISCONNECT =
            new net.minecraft.util.Identifier(AvataDrone.MOD_ID, "rc2_disconnect");
    public static final net.minecraft.util.Identifier PAIR =
            new net.minecraft.util.Identifier(AvataDrone.MOD_ID, "rc2_pair");
    private DroneControlNetworking() {}

    public static void register() {
        FlightMapNetworking.register();
        ServerPlayNetworking.registerGlobalReceiver(PAIR, (server, player, handler, buf, responseSender) -> {
            java.util.UUID droneId = buf.readUuid();
            server.execute(() -> pair(player, droneId));
        });
        ServerPlayNetworking.registerGlobalReceiver(DISCONNECT, (server, player, handler, buf, responseSender) -> {
            java.util.UUID paired = buf.readUuid();
            server.execute(() -> {
                ItemStack rc = player.getMainHandStack().isOf(AvataDrone.DJI_RC2) ? player.getMainHandStack()
                        : player.getOffHandStack().isOf(AvataDrone.DJI_RC2) ? player.getOffHandStack() : ItemStack.EMPTY;
                if (rc.hasNbt() && rc.getNbt().containsUuid(Rc2Item.DRONE)
                        && paired.equals(rc.getNbt().getUuid(Rc2Item.DRONE))) Rc2Item.disconnect(rc, player);
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(DroneControlPayload.ID,
                (server, player, handler, buf, responseSender) -> {
                    DroneControlPayload packet = DroneControlPayload.read(buf).clamped();
                    server.execute(() -> control(player, packet));
                });
    }

    private static void pair(ServerPlayerEntity player, java.util.UUID droneId) {
        ItemStack rc = player.getMainHandStack().isOf(AvataDrone.DJI_RC2) ? player.getMainHandStack()
                : player.getOffHandStack().isOf(AvataDrone.DJI_RC2) ? player.getOffHandStack() : ItemStack.EMPTY;
        if (!Rc2Item.hasPower(rc)) return;
        Entity found = player.getServerWorld().getEntity(droneId);
        if (!(found instanceof AvataDroneEntity drone) || !drone.isAlive() || !drone.hasControlSignal(player)) {
            player.sendMessage(net.minecraft.text.Text.translatable("message.djimod.rc2.pair_failed"), false);
            return;
        }
        rc.removeSubNbt("DroneAccident");
        rc.getOrCreateNbt().putUuid(Rc2Item.DRONE, droneId);
        rc.getOrCreateNbt().putString(Rc2Item.DIMENSION,
                player.getWorld().getRegistryKey().getValue().toString());
        player.currentScreenHandler.sendContentUpdates();
        player.sendMessage(net.minecraft.text.Text.translatable("message.djimod.rc2.paired"), true);
    }

    private static void control(ServerPlayerEntity player, DroneControlPayload packet) {
        ItemStack rc = player.getMainHandStack().isOf(AvataDrone.DJI_RC2) ? player.getMainHandStack()
                : player.getOffHandStack().isOf(AvataDrone.DJI_RC2) ? player.getOffHandStack() : ItemStack.EMPTY;
        if (!Rc2Item.hasPower(rc) || !rc.hasNbt() || !rc.getNbt().containsUuid(Rc2Item.DRONE)
                || !packet.droneId().equals(rc.getNbt().getUuid(Rc2Item.DRONE))
                || !player.getWorld().getRegistryKey().getValue().toString()
                    .equals(rc.getNbt().getString(Rc2Item.DIMENSION))) return;
        Entity found = player.getServerWorld().getEntity(packet.droneId());
        AvataDroneEntity drone = found instanceof AvataDroneEntity candidate ? candidate : null;
        if (drone == null || !drone.isAlive() || !drone.hasControlSignal(player)) return;
        drone.applyPilotCommand(packet, player);
    }

    public static void stopPilot(ServerPlayerEntity player, java.util.UUID id) {
        if (!ServerPlayNetworking.canSend(player, DroneControlPayload.ID)) return;
        ItemStack rc = player.getMainHandStack().isOf(AvataDrone.DJI_RC2) ? player.getMainHandStack()
                : player.getOffHandStack().isOf(AvataDrone.DJI_RC2) ? player.getOffHandStack() : ItemStack.EMPTY;
        if (!Rc2Item.hasPower(rc) || !rc.hasNbt() || !rc.getNbt().containsUuid(Rc2Item.DRONE)
                || !id.equals(rc.getNbt().getUuid(Rc2Item.DRONE))) return;
        Entity entity = player.getServerWorld().getEntity(id);
        if (entity instanceof AvataDroneEntity drone) drone.setPilotInput(0, 0, 0, 0);
    }
}
