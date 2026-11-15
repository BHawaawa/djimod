package com.example.avatadrone.qa;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.ModPillar;
import com.example.avatadrone.client.Rc2View;
import com.example.avatadrone.entity.AvataDroneEntity;
import com.example.avatadrone.item.Rc2Item;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Opt-in in-game visual regression run against a copied QA save. */
public final class RenderQa implements ClientModInitializer {
    private int ticks;
    private volatile UUID droneId;
    private volatile int ground;
    private final Path output = Path.of("screenshots");

    @Override public void onInitializeClient() {
        if (Boolean.getBoolean("avatadrone.phoneQa")) return;
        if (Boolean.getBoolean("avatadrone.keybindingQa") || Boolean.getBoolean("avatadrone.flightMapQa") || Boolean.getBoolean("avatadrone.accidentQa") || Boolean.getBoolean("avatadrone.rc2Qa") || Boolean.getBoolean("avatadrone.maintenanceQa") || Boolean.getBoolean("avatadrone.chargingQa")) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null || client.getServer() == null) return;
            try { tick(client); }
            catch (Throwable e) { throw new IllegalStateException("Render QA failed", e); }
        });
    }

    private void tick(MinecraftClient client) throws Exception {
        client.options.pauseOnLostFocus = false;
        ticks++;
        if (ticks == 80) {
            client.getServer().execute(() -> {
                var world = client.getServer().getOverworld();
                var player = client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
                ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, 0, 0);
                world.setTimeOfDay(6000);
                world.setWeather(0, 12000, false, false);
                for (int x = -48; x <= 48; x++) for (int z = -48; z <= 96; z++) {
                    world.setBlockState(new BlockPos(x, ground - 1, z),
                            ((Math.floorDiv(x, 8) + Math.floorDiv(z, 8)) % 2 == 0
                                    ? Blocks.WHITE_CONCRETE : Blocks.GREEN_CONCRETE).getDefaultState());
                }
                for (int x : new int[]{-12, 0, 12}) for (int z : new int[]{4, 20, 40}) {
                    var cow = EntityType.COW.create(world);
                    cow.setAiDisabled(true);
                    cow.setPersistent();
                    cow.refreshPositionAndAngles(x, ground, z, 0, 0);
                    world.spawnEntity(cow);
                }
                var pillar = new BlockPos(4, ground, 5);
                var station = new BlockPos(4, ground + 3, 5);
                for (int y = 0; y < 3; y++) world.setBlockState(pillar.up(y), ModPillar.STEEL_PILLAR.getDefaultState());
                world.setBlockState(station, AvataDrone.FOUR_G_STATION.getDefaultState());
                if (world.getBlockEntity(pillar) != null || world.getBlockEntity(station) != null
                        || ModPillar.STEEL_PILLAR.getDefaultState().hasBlockEntity()
                        || AvataDrone.FOUR_G_STATION.getDefaultState().hasBlockEntity())
                    throw new AssertionError("Signal infrastructure still has block entities");
                System.out.println("RENDER_QA ordinary blocks verified: station and pillar have no block entities");
                player.changeGameMode(GameMode.CREATIVE);
                player.teleport(world, 0, ground, -30, 0, 0);
                AvataDroneEntity target = AvataDrone.AVATA360.create(world);
                target.setAiDisabled(true);
                target.setNoGravity(true);
                target.refreshPositionAndAngles(0, ground + 5, -20, 0, 15);
                world.spawnEntity(target);
                droneId = target.getUuid();
                ItemStack rc = new ItemStack(AvataDrone.DJI_RC2);
                rc.getOrCreateNbt().putUuid(Rc2Item.DRONE, droneId);
                rc.getOrCreateNbt().putString(Rc2Item.DIMENSION, world.getRegistryKey().getValue().toString());
                player.setStackInHand(Hand.MAIN_HAND, rc);
                player.currentScreenHandler.sendContentUpdates();
            });
        }
        if (ticks == 150) {
            client.setScreen(null);
            Rc2View.requestEnterFromKey();
        }
        if (ticks == 350) snapshot(client, "01-low");
        if (ticks == 360) move(client, 40, 30);
        if (ticks == 560) snapshot(client, "02-medium");
        if (ticks == 570) move(client, 100, 55);
        if (ticks == 770) snapshot(client, "03-high");
        if (ticks == 780) move(client, 400, 75);
        if (ticks == 980) snapshot(client, "04-above-world");
        if (ticks == 990) move(client, 100, 0);
        if (ticks == 1190) snapshot(client, "05-high-horizon");
        if (ticks == 1200) org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1100, 700);
        if (ticks == 1300) snapshot(client, "06-resized");
        boolean compat = Boolean.getBoolean("avatadrone.compatQa");
        if (compat && ticks == 1310) {
            client.getServer().execute(() -> {
                var world = client.getServer().getOverworld();
                for (int x = 272; x <= 368; x++) for (int z = -48; z <= 96; z++) {
                    world.setBlockState(new BlockPos(x, ground - 1, z),
                            ((Math.floorDiv(x, 8) + Math.floorDiv(z, 8)) % 2 == 0
                                    ? Blocks.WHITE_CONCRETE : Blocks.GREEN_CONCRETE).getDefaultState());
                }
                var target = world.getEntity(droneId);
                target.refreshPositionAndAngles(320, ground + 40, -20, 0, 65);
                target.setVelocity(0, 0, 0);
                System.out.println("COMPAT_QA moved camera 320 blocks away from pilot");
            });
        }
        if (compat && ticks == 1710) {
            snapshot(client, "07-remote-terrain");
            if (client.getCameraEntity() != client.player) throw new AssertionError("Pilot camera entity not restored");
            if (Math.abs(Rc2View.feedCameraPosition().x - 320) > 2) throw new AssertionError("Remote camera did not follow drone");
            if (com.example.avatadrone.client.DroneChunkClient.extra(client.world, 20, -2) == null)
                throw new AssertionError("Remote chunk missing");
        }
        if (ticks == (compat ? 1720 : 1310)) Rc2View.onEscape();
        if (ticks == (compat ? 1820 : 1410)) {
            Files.createDirectories(output);
            try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
                image.writeTo(output.resolve("07-normal-view.png"));
            }
            if (Rc2View.isVisible() || Rc2View.isRenderingFeed() || client.getCameraEntity() != client.player)
                throw new AssertionError("Flight view did not release the camera");
            System.out.println("RENDER_QA completed all camera scenes and exit-to-player view");
            if (compat) System.out.println("COMPAT_QA PASS: low/high/above-world/resize/remote/exit");
            client.scheduleStop();
        }
    }

    private void move(MinecraftClient client, int height, float pitch) {
        client.getServer().execute(() -> {
            var target = client.getServer().getOverworld().getEntity(droneId);
            if (target == null) throw new AssertionError("QA drone missing");
            target.refreshPositionAndAngles(0, ground + height, -20, 0, pitch);
            target.setVelocity(0, 0, 0);
            System.out.println("RENDER_QA camera height=" + height + " pitch=" + pitch);
        });
    }

    private void snapshot(MinecraftClient client, String label) throws Exception {
        Files.createDirectories(output);
        var field = Rc2View.class.getDeclaredField("feed");
        field.setAccessible(true);
        Framebuffer feed = (Framebuffer) field.get(null);
        if (!Rc2View.isVisible() || !Rc2View.isFeedReady() || Rc2View.drone() == null || !Rc2View.drone().isAlive() || Rc2View.drone().isRemoved() || feed == null) throw new AssertionError("Feed is not visible: " + label);
        try (var image = ScreenshotRecorder.takeScreenshot(feed)) { image.writeTo(output.resolve(label + "-feed.png")); }
        try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) { image.writeTo(output.resolve(label + "-screen.png")); }
        System.out.println("RENDER_QA captured " + label + " camera=" + Rc2View.feedCameraPosition());
    }
}
