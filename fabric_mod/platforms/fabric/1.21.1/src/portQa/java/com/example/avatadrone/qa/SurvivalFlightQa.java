package com.example.avatadrone.qa;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.client.Rc2View;
import com.example.avatadrone.compat.ClientPlayNetworking;
import com.example.avatadrone.compat.ItemData;
import com.example.avatadrone.entity.AvataDroneEntity;
import com.example.avatadrone.item.DeviceCharging;
import com.example.avatadrone.item.Rc2Item;
import com.example.avatadrone.network.DroneControlNetworking;
import com.example.avatadrone.network.DroneControlPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import java.util.UUID;

/** Exercises the actual C2S pairing/control protocol with a survival player. */
public final class SurvivalFlightQa implements ClientModInitializer {
    private int tick;
    private volatile UUID droneId;
    private volatile Throwable failure;
    private volatile boolean passed;

    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("djimod.survivalQa")) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null || client.getServer() == null) return;
            if (failure != null) throw new AssertionError("SURVIVAL_QA failed", failure);
            client.options.pauseOnLostFocus = false;
            tick++;
            if (tick == 60) {
                client.setScreen(null);
                server(client, () -> {
                    var world = client.getServer().getOverworld();
                    var player = client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                    for (int x = 75; x <= 90; x++) for (int z = 75; z <= 90; z++) {
                        world.setBlockState(new BlockPos(x, 99, z), Blocks.STONE.getDefaultState());
                        for (int y = 100; y < 108; y++) world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState());
                    }
                    player.teleport(world, 80, 100, 78, 0, 0);
                    player.changeGameMode(GameMode.SURVIVAL);
                    player.setHealth(player.getMaxHealth());
                    player.fallDistance = 0;
                    AvataDroneEntity drone = AvataDrone.MINI4PRO.create(world);
                    drone.refreshPositionAndAngles(82, 100, 82, 0, 0);
                    drone.installBattery(new ItemStack(AvataDrone.MINI4PRO_BATTERY));
                    world.spawnEntity(drone);
                    droneId = drone.getUuid();
                    player.setStackInHand(Hand.MAIN_HAND, new ItemStack(AvataDrone.DJI_RC2));
                });
            }
            if (tick == 140) {
                var buf = PacketByteBufs.create();
                buf.writeUuid(droneId);
                ClientPlayNetworking.send(DroneControlNetworking.PAIR, buf);
            }
            if (tick == 180) {
                var data = ItemData.read(client.player.getMainHandStack());
                check(data != null && droneId.equals(data.getUuid(Rc2Item.DRONE)), "pairing reply reached client");
                Rc2View.requestEnterFromKey();
            }
            if (tick == 220) {
                check(Rc2View.isVisible() && Rc2View.isFeedReady(), "survival flight view opened");
                send(0, true, false);
            }
            if (tick >= 240 && tick < 280) send(1, false, false);
            if (tick == 285) send(0, false, true);
            if (tick == 300) server(client, () -> {
                var world = client.getServer().getOverworld();
                var player = client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                var drone = (AvataDroneEntity) world.getEntity(droneId);
                check(!player.isCreative() && player.isAlive(), "survival pilot remained alive");
                check(drone != null && drone.areMotorsArmed() && drone.getY() > 100.5, "network arm and ascent");
                check(DeviceCharging.getCharge(drone.getInstalledBattery()) < 1, "flight consumed battery");
                check(!drone.isReturningHome(), "emergency hover did not start return");
                System.out.println("SURVIVAL_QA PASS: pairing, flight view, C2S arm/ascent/hover, battery consumption");
                passed = true;
            });
            if (tick == 320) Rc2View.onEscape();
            if (tick >= 360 && passed) {
                check(!Rc2View.isVisible() && client.getCameraEntity() == client.player, "camera restored");
                client.scheduleStop();
            }
        });
    }
    private void send(float throttle, boolean arm, boolean hover) {
        var buf = PacketByteBufs.create();
        DroneControlPayload.write(buf, new DroneControlPayload(droneId, 0, throttle, 0, 0, 0, 1, 1, arm, hover, false));
        ClientPlayNetworking.send(DroneControlPayload.ID, buf);
    }
    private void server(MinecraftClient client, Runnable action) {
        client.getServer().execute(() -> { try { action.run(); } catch (Throwable error) { failure = error; } });
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
