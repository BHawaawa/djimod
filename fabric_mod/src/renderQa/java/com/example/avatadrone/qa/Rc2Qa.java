package com.example.avatadrone.qa;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.client.Rc2View;
import com.example.avatadrone.client.Rc2Indicators;
import com.example.avatadrone.entity.PowerStationEntity;
import com.example.avatadrone.item.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import java.nio.file.*;
import java.util.function.Consumer;

public final class Rc2Qa implements ClientModInitializer {
    private int tick;
    private long serverTick = Long.MIN_VALUE;
    private PowerStationEntity station;
    private ItemStack rc;
    private double before;
    private volatile Throwable failure;
    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("avatadrone.rc2Qa")) return;
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (c.world == null || c.player == null || c.getServer() == null) return;
            c.options.pauseOnLostFocus = false;
            if (tick < 270 && c.currentScreen != null) c.setScreen(null);
            if (failure != null) throw new AssertionError("RC2_QA failed", failure);
            long now = c.getServer().getOverworld().getTime();
            if (tick < 580 && now == serverTick) return;
            serverTick = now;
            try { run(c); } catch (Exception ex) { throw new RuntimeException(ex); }
        });
    }
    private void server(MinecraftClient c, Consumer<ServerPlayerEntity> action) {
        var id = c.player.getUuid();
        c.getServer().execute(() -> {
            try { action.accept(c.getServer().getPlayerManager().getPlayer(id)); }
            catch (Throwable ex) { failure = ex; ex.printStackTrace(); }
        });
    }
    private static void check(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); }
    private void shot(MinecraftClient c, String name) throws Exception {
        Files.createDirectories(Path.of("screenshots"));
        try (var image = ScreenshotRecorder.takeScreenshot(c.getFramebuffer())) {
            image.writeTo(Path.of("screenshots", "rc2-" + name + ".png"));
        }
    }
    private void cameraAndKeys(MinecraftClient c) throws Exception {
        var forward=com.example.avatadrone.client.DroneKeys.FORWARD;
        var previous=net.minecraft.client.util.InputUtil.fromTranslationKey(forward.getBoundKeyTranslationKey());
        forward.setBoundKey(net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode(org.lwjgl.glfw.GLFW.GLFW_KEY_P));
        check(forward.matchesKey(org.lwjgl.glfw.GLFW.GLFW_KEY_P,0) && !forward.matchesKey(org.lwjgl.glfw.GLFW.GLFW_KEY_W,0),"keyboard rebind replaces old key");
        forward.setBoundKey(net.minecraft.client.util.InputUtil.Type.MOUSE.createFromCode(4));
        check(forward.matchesMouse(4),"mouse rebind");
        forward.setBoundKey(net.minecraft.client.util.InputUtil.UNKNOWN_KEY);check(forward.isUnbound(),"unbind supported");
        forward.setBoundKey(previous);net.minecraft.client.option.KeyBinding.updateKeysByCode();
        check(java.util.Arrays.asList(c.options.allKeys).contains(com.example.avatadrone.client.DroneKeys.GIMBAL),"native controls registration");
        // Force the RC binding to win vanilla's key map, then verify the original still receives press/release.
        var vanilla = c.options.forwardKey;
        var physical = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.getBoundKeyOf(vanilla);
        forward.setBoundKey(physical);
        net.minecraft.client.option.KeyBinding.updateKeysByCode();
        while (vanilla.wasPressed()) {}
        net.minecraft.client.option.KeyBinding.setKeyPressed(physical, true);
        net.minecraft.client.option.KeyBinding.onKeyPressed(physical);
        check(vanilla.isPressed() && vanilla.wasPressed(), "drone context does not steal vanilla movement");
        net.minecraft.client.option.KeyBinding.setKeyPressed(physical, false);
        check(!vanilla.isPressed() && !forward.isPressed(), "shared release");
        forward.setBoundKey(previous);net.minecraft.client.option.KeyBinding.updateKeysByCode();
        var scroll=Rc2View.class.getDeclaredMethod("adjustCameraScroll",net.minecraft.client.option.KeyBinding.class,double.class);scroll.setAccessible(true);
        var mapField=Rc2View.class.getDeclaredField("cameraControls");mapField.setAccessible(true);
        var map=(java.util.Map<?,?>)mapField.get(null);
        var d=Rc2View.drone();check(d!=null,"camera target");
        scroll.invoke(null,com.example.avatadrone.client.DroneKeys.GIMBAL,-100.0);
        Object control=map.get(d.getUuid());var pitch=control.getClass().getDeclaredField("pitch");pitch.setAccessible(true);
        check(Math.abs(d.getCameraPitch(1)+pitch.getFloat(control)-90)<.01,"pitch lower limit");
        scroll.invoke(null,com.example.avatadrone.client.DroneKeys.GIMBAL,1.0);
        check(Math.abs(d.getCameraPitch(1)+pitch.getFloat(control)-87)<.01,"pitch reverses immediately at limit");
        scroll.invoke(null,com.example.avatadrone.client.DroneKeys.GIMBAL,100.0);
        check(Math.abs(d.getCameraPitch(1)+pitch.getFloat(control)+90)<.01,"pitch upper limit");
        scroll.invoke(null,com.example.avatadrone.client.DroneKeys.GIMBAL,-30.0);
        scroll.invoke(null,com.example.avatadrone.client.DroneKeys.ZOOM,5.0);check(Rc2View.cameraZoom()>1,"continuous zoom");
        scroll.invoke(null,com.example.avatadrone.client.DroneKeys.FOV,100.0);
        var fov=control.getClass().getDeclaredField("fov");fov.setAccessible(true);check(fov.getFloat(control)==120,"FOV bounded");
        scroll.invoke(null,com.example.avatadrone.client.DroneKeys.ROLL,10.0);
        var roll=control.getClass().getDeclaredField("roll");roll.setAccessible(true);check(roll.getFloat(control)==30,"Avata camera roll");
        System.out.println("RC2_INPUT_QA PASS native bindings, keyboard/mouse/unbound, pitch limits, zoom, FOV and roll");
    }
    private void run(MinecraftClient c) throws Exception {
        tick++;
        if (tick == 1) {
            c.setScreen(null);
            check(com.example.avatadrone.client.DroneKeys.FORWARD.getDefaultKey().equals(net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode(org.lwjgl.glfw.GLFW.GLFW_KEY_W)), "recommended movement default");
            if (c.currentScreen instanceof net.minecraft.client.gui.screen.ConfirmScreen) {
                shot(c, "first-key-setup");
                c.setScreen(null);
                System.out.println("KEY_SETUP_QA PASS initial settings prompt and recommended defaults");
            }
        }
        if (tick == 60) server(c, p -> {
            var w = p.getServerWorld();
            for (var old : w.getEntitiesByClass(PowerStationEntity.class,
                    new net.minecraft.util.math.Box(-10, 190, -10, 10, 210, 10), e -> true)) old.discard();
            p.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
            p.setHealth(p.getMaxHealth()); p.fallDistance = 0;
            for (int x=-5;x<=5;x++) for(int z=-5;z<=5;z++) {
                w.setBlockState(new BlockPos(x,199,z), Blocks.STONE.getDefaultState());
                for(int y=200;y<205;y++) w.setBlockState(new BlockPos(x,y,z), Blocks.AIR.getDefaultState());
            }
            p.teleport(w,0,200,-2,0,20);
            station = AvataDrone.AURO2000.create(w);
            station.refreshPositionAndAngles(0,200,0,0,0); w.spawnEntity(station);
            rc = new ItemStack(AvataDrone.DJI_RC2);
            check(DeviceCharging.getCharge(rc)==1, "legacy/default full charge");
            for (double value : new double[]{0,.001,.25,.251,.5,.501,.75,.751,1}) {
                DeviceCharging.setCharge(rc,value);
                check(Rc2Item.batteryLights(rc)==(int)Math.ceil(value*4), "LED boundary " + value);
            }
            DeviceCharging.setCharge(rc,.4);
            rc.getOrCreateNbt().putString("QaMetadata","retained");
            var drone = AvataDrone.AVATA360.create(w);
            drone.refreshPositionAndAngles(2,201,2,0,0);drone.setNoGravity(true);w.spawnEntity(drone);
            rc.getOrCreateNbt().putUuid(Rc2Item.DRONE,drone.getUuid());
            rc.getOrCreateNbt().putString(Rc2Item.DIMENSION,w.getRegistryKey().getValue().toString());
            p.setStackInHand(Hand.MAIN_HAND,rc);
            p.setSneaking(true);station.interact(p,Hand.MAIN_HAND);p.setSneaking(false);
        });
        if(tick==110) server(c,p->{
            check(station.canConnect(p,p.getPos()), "cable ready: state=" + station.getCableState()
                    + ", busy=" + station.isBusy() + ", held=" + station.heldBy(p)
                    + ", distance=" + station.distanceTo(p));
            station.interact(p,Hand.MAIN_HAND);before=DeviceCharging.getCharge(rc);
        });
        if(tick==185) server(c,p->{
            check(station.chargesController(p,rc),"controller connected: state=" + station.getCableState()
                    + ", sameStack=" + (p.getMainHandStack() == rc) + ", alive=" + p.isAlive()
                    + ", distance=" + station.distanceTo(p));
            check(DeviceCharging.getCharge(rc)>before,"charged real held controller");
            check(station.getEnergyWh()<PowerStationEntity.CAPACITY_WH,"station energy consumed");
            check(rc.getNbt().getString("QaMetadata").equals("retained"),"metadata retained");
            check(ItemStack.fromNbt(rc.writeNbt(new net.minecraft.nbt.NbtCompound())).getNbt().getDouble("BatteryCharge")
                    ==DeviceCharging.getCharge(rc),"charge survives serialization");
            p.setStackInHand(Hand.MAIN_HAND,rc.copy());
        });
        if(tick==220) server(c,p->{
            check(station.getCableState()!=PowerStationEntity.PLUGGED,"replacement does not steal charge");
            p.setStackInHand(Hand.MAIN_HAND,rc);
            p.setSneaking(true);station.interact(p,Hand.MAIN_HAND);p.setSneaking(false);
        });
        if(tick==270) {
            c.setScreen(null);
            check(Rc2Indicators.connected(c.player.getMainHandStack()),"green with actual signal");
            Rc2View.requestEnterFromKey();
        }
        if(tick==300) cameraAndKeys(c);
        if(tick==320) shot(c,"connected-two-lights");
        if(tick==330) server(c,p->{DeviceCharging.setCharge(rc,0);});
        if(tick==355) {
            check(!Rc2Indicators.connected(c.player.getMainHandStack()),"red when empty battery");
            shot(c,"empty-disconnected");
            // Headless/background QA must not depend on OS keyboard focus for Escape.
            var dismissed = Rc2View.class.getDeclaredField("dismissed");
            dismissed.setAccessible(true); dismissed.setBoolean(null,true); Rc2View.close();
        }
        if(tick==365) server(c,p->{DeviceCharging.setCharge(rc,.75);});
        if(tick==400) shot(c,"held-model");
        if(tick==410) c.setScreen(new net.minecraft.client.gui.screen.ingame.InventoryScreen(c.player));
        if(tick==435) shot(c,"inventory-model");
        if(tick==450) { c.setScreen(null); server(c,p->{
            rc = p.getMainHandStack();
            p.setSneaking(true); station.interact(p,Hand.MAIN_HAND); p.setSneaking(false);
        }); }
        if(tick==490) server(c,p->{rc = p.getMainHandStack(); station.interact(p,Hand.MAIN_HAND);});
        if(tick==525) server(c,p->{
            check(station.chargesController(p,rc),"reconnected");
            p.teleport(p.getServerWorld(),10,200,-2,0,20);
        });
        if(tick==560) server(c,p->{
            check(station.getCableState()!=PowerStationEntity.PLUGGED,"out of range disconnects");
            DeviceCharging.setCharge(rc,0);check(!Rc2Item.hasPower(rc),"empty controller disabled");
            DeviceCharging.setCharge(rc,1.2);check(DeviceCharging.getCharge(rc)==1,"full charge capped");
        });
        // ControlsOptionsScreen pauses the integrated server, so input UI is covered by KeybindingQa.
        if(tick==580) c.setScreen(null);
        if(tick==600) shot(c,"native-controls");
        if(tick==620) {System.out.println("RC2_QA PASS charge, exact item, persistence, LED thresholds, link state and model scenes");c.scheduleStop();}
    }
}
