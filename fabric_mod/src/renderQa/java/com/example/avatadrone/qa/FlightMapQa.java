package com.example.avatadrone.qa;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.block.FourGBaseStationBlock;
import com.example.avatadrone.client.*;
import com.example.avatadrone.item.Rc2Item;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import java.nio.file.Path;

/** Isolated live-world check: disk-backed Xaero tiles, server station sync, and map toggle screenshots. */
public final class FlightMapQa implements ClientModInitializer {
    private int tick;
    private long serverTick = Long.MIN_VALUE;
    private volatile Throwable failure;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void field(String name, int value) throws Exception {
        var f = Rc2FlightUi.class.getDeclaredField(name); f.setAccessible(true); f.setInt(null, value);
    }
    private static void shot(net.minecraft.client.MinecraftClient c, String name) throws Exception {
        check(Rc2View.isVisible() && Rc2View.isFeedReady(), "Flight screen/feed missing: " + name);
        try (var image = ScreenshotRecorder.takeScreenshot(c.getFramebuffer())) {
            image.writeTo(Path.of(name + ".png"));
        }
    }
    public void onInitializeClient() {
        if (!Boolean.getBoolean("avatadrone.flightMapQa")) return;
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (c.world == null || c.player == null || c.getServer() == null) return;
            c.options.pauseOnLostFocus = false;
            if (tick < 100 && c.currentScreen != null) c.setScreen(null);
            if (failure != null) throw new AssertionError("Server QA failed", failure);
            long now = c.getServer().getOverworld().getTime();
            if (now == serverTick) return;
            serverTick = now;
            tick++;
            try {
                if (tick == 1) c.setScreen(null);
                if (tick == 1) {
                    var id = c.player.getUuid();
                    c.getServer().execute(() -> {
                        try {
                        var p = c.getServer().getPlayerManager().getPlayer(id);
                        var world = p.getServerWorld();
                        for (int x = -64; x <= -56; x++) for (int z = -34; z <= -26; z++)
                            world.setBlockState(new BlockPos(x, 199, z), net.minecraft.block.Blocks.STONE.getDefaultState());
                        p.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
                        p.setHealth(p.getMaxHealth());
                        p.fallDistance = 0;
                        p.teleport(world, -60, 200, -30, 0, 0);
                        var d = AvataDrone.AVATA360.create(world);
                        d.refreshPositionAndAngles(20, 202, 20, 90, 0);
                        d.setNoGravity(true);
                        d.installBattery(new ItemStack(AvataDrone.AVATA360_BATTERY));
                        world.spawnEntity(d);
                        var rc = new ItemStack(AvataDrone.DJI_RC2);
                        rc.getOrCreateNbt().putUuid(Rc2Item.DRONE, d.getUuid());
                        rc.getOrCreateNbt().putString(Rc2Item.DIMENSION, world.getRegistryKey().getValue().toString());
                        p.setStackInHand(Hand.MAIN_HAND, rc);
                        world.setBlockState(new BlockPos(-140, 200, -140), AvataDrone.FOUR_G_STATION.getDefaultState().with(FourGBaseStationBlock.FACING, Direction.SOUTH));
                        world.setBlockState(new BlockPos(140, 200, 100), AvataDrone.FOUR_G_STATION.getDefaultState().with(FourGBaseStationBlock.FACING, Direction.WEST));
                        ImportGameplayChecks.run(p);
                        } catch (Throwable ex) { failure = ex; }
                    });
                }
                if (tick == 100) { c.setScreen(null); Rc2View.requestEnterFromKey(); }
                if (tick == 160) {
                    check(Rc2View.isVisible(), "Survival flight entry failed: screen=" + c.currentScreen
                            + ", alive=" + c.player.isAlive() + ", hand=" + c.player.getMainHandStack()
                            + ", canEnter=" + Rc2View.canEnterFromKey());
                    boolean focused = c.isWindowFocused();
                    c.onWindowFocusChanged(true);
                    check(Rc2View.onScreenOnlyKey(257, 1), "Enter must be consumed in flight view");
                    Rc2View.onScreenOnlyKey(257, 0);
                    Rc2View.onScreenOnlyKey(257, 1);
                    var full = Rc2View.class.getDeclaredField("screenOnly"); full.setAccessible(true);
                    check(full.getBoolean(null), "Double Enter did not enter fullscreen");
                    Rc2View.onScreenOnlyKey(257, 0);
                    Rc2View.onScreenOnlyKey(257, 2);
                    check(full.getBoolean(null), "Key repeat must not toggle fullscreen");
                    Rc2View.onScreenOnlyKey(257, 1); Rc2View.onScreenOnlyKey(257, 0); Rc2View.onScreenOnlyKey(257, 1);
                    check(!full.getBoolean(null) && c.currentScreen == null, "Fullscreen restoration / chat interception");
                    c.onWindowFocusChanged(focused);
                    System.out.println("V201_CLIENT_QA PASS double Enter, repeat suppression and restoration");
                    var panel = Rc2FlightUi.class.getDeclaredField("panel"); panel.setAccessible(true); panel.setInt(null, 5);
                }
                if (tick == 200) com.example.avatadrone.client.UiPerformanceChecks.run(c);
                if (tick == 500 || tick == 560) {
                    if (FlightMapStations.current().size() < 2) throw new AssertionError("Station index was not synchronized");
                    SurfaceMap.beginFrame();
                    check(SurfaceMap.colorAt(c.world, -60, -30) != 0, "Fallback terrain sample missing");
                    shot(c, "flight-map-" + (tick == 500 ? "off" : "on"));
                    var toggle = Rc2FlightUi.class.getDeclaredField("showCoverage"); toggle.setAccessible(true); toggle.setBoolean(null, true);
                }
                if (tick == 580) {
                    System.out.println("FLIGHT_MAP_QA PASS survival entry, feed, stations and coverage states");
                    field("panel", 0); field("mapType", 2);
                    var expanded = Rc2FlightUi.class.getDeclaredField("mapExpanded"); expanded.setAccessible(true); expanded.setBoolean(null, true);
                    c.options.setPerspective(net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT);
                }
                if (tick >= 600 && tick <= 800 && (tick - 600) % 40 == 0)
                    Rc2View.lockAssistDir((tick - 600) / 40);
                if (tick >= 630 && tick <= 830 && (tick - 630) % 40 == 0) {
                    check(Rc2View.isAssistReady() && !Rc2View.isRenderingAssist(), "Assist frame/state missing");
                    check(c.getCameraEntity() == c.player, "Pilot camera not restored");
                    check(c.options.getPerspective() == net.minecraft.client.option.Perspective.THIRD_PERSON_FRONT, "Perspective not restored");
                    shot(c, "assist-" + (tick - 630) / 40);
                    var f = Rc2View.class.getDeclaredField("assistFeed"); f.setAccessible(true);
                    try (var image = ScreenshotRecorder.takeScreenshot((net.minecraft.client.gl.Framebuffer)f.get(null))) {
                        image.writeTo(Path.of("assist-feed-" + (tick - 630) / 40 + ".png"));
                    }
                }
                if (tick == 850) { field("panel", 4); field("settingsTab", 3); }
                if (tick == 880) { shot(c, "flight-settings"); field("panel", 0); }
                if (tick == 900) {
                    System.out.println("IMPORT_RENDER_QA PASS six assist directions, settings, third-person restoration");
                    // Release the controller even if the unattended QA window has no input focus.
                    var f = Rc2View.class.getDeclaredField("dismissed"); f.setAccessible(true); f.setBoolean(null, true);
                }
                if (tick == 940) {
                    check(!Rc2View.isVisible() && !Rc2View.isRenderingFeed() && !Rc2View.isAssistReady(), "Flight view did not release buffers");
                    System.out.println("IMPORT_QA PASS exit and framebuffer cleanup"); c.scheduleStop();
                }
            } catch (Exception ex) { throw new RuntimeException(ex); }
        });
    }
}
