package com.example.avatadrone.qa;

import com.example.avatadrone.*;
import com.example.avatadrone.block.charger.*;
import com.example.avatadrone.entity.*;
import com.example.avatadrone.item.DeviceCharging;
import com.example.avatadrone.network.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.block.Blocks;
import java.nio.file.*;
import java.util.function.Consumer;

/** Opt-in real integrated-server charging regression and screenshots. */
public final class ChargingQa implements ClientModInitializer {
    private int tick;
    private volatile int ground;
    private PowerStationEntity station;
    private ChargingHubBlockEntity mini, avata;
    private AvataDroneEntity drone;
    private double before, storedBefore, stopped, droneBefore;
    private volatile Throwable failure;
    private volatile boolean passed;
    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("avatadrone.chargingQa")) return;
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (c.world == null || c.player == null || c.getServer() == null) return;
            c.options.pauseOnLostFocus = false;
            if (failure != null) throw new AssertionError("CHARGING_QA failed", failure);
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
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static ItemStack battery(net.minecraft.item.Item item, double charge) {
        var b = new ItemStack(item); DeviceCharging.setCharge(b, charge);
        b.getOrCreateNbt().putString("QaMetadata", "retained"); return b;
    }
    private void shot(MinecraftClient c, String name) throws Exception {
        Files.createDirectories(Path.of("screenshots"));
        try (var image = ScreenshotRecorder.takeScreenshot(c.getFramebuffer())) {
            image.writeTo(Path.of("screenshots", "charging-" + name + ".png"));
        }
        System.out.println("CHARGING_QA screenshot " + name);
    }
    private void run(MinecraftClient c) throws Exception {
        tick++;
        if (tick == 60) {
            c.setScreen(null);
            server(c, p -> {
                var w = p.getServerWorld(); ground = 100;
                for (int x = 30; x <= 40; x++) for (int z = 30; z <= 40; z++) {
                    w.setBlockState(new BlockPos(x, ground-1, z), Blocks.SMOOTH_STONE.getDefaultState());
                    for (int y = ground; y < ground+5; y++) w.setBlockState(new BlockPos(x,y,z), Blocks.AIR.getDefaultState());
                }
                // Repeated QA runs may leave drops when old hubs are replaced above.
                for (var dropped : w.getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
                        new Box(28,ground-2,28,42,ground+6,42), e -> true)) dropped.discard();
                w.setTimeOfDay(6000); w.setWeather(0,12000,false,false);
                p.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
                p.teleport(w, 34.5, ground, 32, 0, 25); p.getInventory().clear();
                var mp = new BlockPos(34,ground,35); var ap = new BlockPos(36,ground,35);
                w.setBlockState(mp, ModChargers.MINI4PRO_HUB.getDefaultState());
                w.setBlockState(ap, ModChargers.AVATA360_HUB.getDefaultState());
                mini = (ChargingHubBlockEntity)w.getBlockEntity(mp); avata = (ChargingHubBlockEntity)w.getBlockEntity(ap);
                station = AvataDrone.AURO2000.create(w); station.refreshPositionAndAngles(33,ground,35,180,0); w.spawnEntity(station);
                mini.insert(0,battery(AvataDrone.MINI4PRO_BATTERY,0.26),p);
                mini.insert(1,battery(AvataDrone.MINI4PRO_BATTERY,0.60),p);
                mini.insert(2,battery(AvataDrone.MINI4PRO_BATTERY,0.10),p);
                avata.insert(0,battery(AvataDrone.AVATA360_BATTERY,0.75),p);
                avata.insert(1,battery(AvataDrone.MINI4PRO_BATTERY,0.20),p);
                check(!avata.has(1),"wrong battery rejected");
                for (var old : w.getOtherEntities(null,new Box(30,ground,30,40,ground+5,40)))
                if (old instanceof PowerStationEntity && old != station || old instanceof AvataDroneEntity) old.discard();
            drone=AvataDrone.MINI4PRO.create(w); drone.refreshPositionAndAngles(34.5,ground,37,180,0);
                drone.installBattery(battery(AvataDrone.MINI4PRO_BATTERY,0.30)); drone.setNoGravity(false); w.spawnEntity(drone);
            });
        }
        if (tick == 120) server(c,p -> {
            check(mini.chargeFraction(1)==0.60,"unconnected hub must not charge");
            p.setSneaking(true); station.interact(p,Hand.MAIN_HAND); p.setSneaking(false);
            check(station.getCableState()==PowerStationEntity.OUT,"extract cable");
        });
        if (tick == 155) server(c,p -> {
            p.setStackInHand(Hand.MAIN_HAND,battery(AvataDrone.MINI4PRO_BATTERY,0.2));
            station.interact(p,Hand.MAIN_HAND);
            check(station.getCableState()==PowerStationEntity.OUT && !p.getMainHandStack().isEmpty(),"bare battery rejected");
            p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
            ChargingNetwork.toggleHub(p,mini);
            check(station.getCableState()==PowerStationEntity.PLUGGED,"hub connected");
            before=station.getEnergyWh(); storedBefore=mini.chargeFraction(1);
        });
        if (tick == 166) shot(c,"01-plug-animation");
        if (tick == 230) { c.options.hudHidden=true; shot(c,"02b-models-cable"); c.options.hudHidden=false; }
        if (tick == 220) server(c,p -> {
            check(mini.chargeFraction(1)>storedBefore && mini.chargeFraction(1)<0.7,"gradual charging");
            check(mini.chargeFraction(0)==0.26 && mini.chargeFraction(2)==0.1,"highest charge slot first");
            check(DeviceCharging.capacityWh(mini.makeStack(1)) == 18.96, "standard Mini battery capacity");
            double gained=(mini.chargeFraction(1)-storedBefore)*18.96;
            check(Math.abs(gained-(before-station.getEnergyWh())*PowerStationEntity.EFFICIENCY)<1e-7,"energy conservation");
            check(mini.isCharging(1),"active slot tracked");
            var saved=mini.createNbt(); var copy=new ChargingHubBlockEntity(mini.getPos(),mini.getCachedState());copy.readNbt(saved);
            check(copy.chargeFraction(1)==mini.chargeFraction(1),"NBT precision");
            check(copy.makeStack(1).getNbt().getString("QaMetadata").equals("retained"),"battery metadata");
        });
        if (tick == 225) shot(c,"02-hub-hud");
        if (tick == 235) shot(c,"03-led-other-phase");
        if (tick == 250) server(c,p -> { ChargingNetwork.toggleHub(p,mini); stopped=mini.chargeFraction(1); });
        if (tick == 260) shot(c,"04-unplug-animation");
        if (tick == 285) server(c,p -> {
            check(mini.chargeFraction(1)==stopped && !mini.isCharging(1),"disconnect stops output");
            mini.remove(1,p,Hand.MAIN_HAND);
            check(mini.has(1) && mini.isRemoving(1) && p.getMainHandStack().isEmpty(),"removal waits for animation");
            mini.remove(1,p,Hand.MAIN_HAND);
            p.teleport(p.getServerWorld(),33,ground,36, -45,25);
            ChargingNetwork.toggleDrone(p,drone);
            check(drone.isCableConnected(),"aircraft connected: battery="+drone.hasBattery()+" grounded="+drone.isOnGround()+" velocity="+drone.getVelocity()+" held="+ChargingNetwork.held(p)+" stationState="+station.getCableState());
            droneBefore=DeviceCharging.getCharge(drone.getInstalledBattery());
            drone.applyPilotCommand(new DroneControlPayload(drone.getUuid(),0,0,0,0,0,0,1,true,false,false),p);
            check(!drone.areMotorsArmed(),"charging aircraft cannot arm");
            drone.collectIntoInventory(p);check(!drone.isRemoved(),"connected aircraft cannot be collected");
        });
        if (tick == 375) server(c,p -> {
            check(!mini.has(1) && p.getInventory().count(AvataDrone.MINI4PRO_BATTERY)==1,"one battery after removal animation");
            check(DeviceCharging.getCharge(p.getMainHandStack())==stopped,"remove keeps exact charge");
            p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
            check(DeviceCharging.getCharge(drone.getInstalledBattery())>droneBefore,"aircraft battery receives energy");
            ChargingNetwork.toggleDrone(p,drone);
        });
        if (tick == 410) server(c,p -> {
            p.teleport(p.getServerWorld(),36.5,ground,32,0,25);
            ChargingNetwork.toggleHub(p,avata); check(ChargingNetwork.connected(avata)==station,"Avata hub connects");
            p.teleport(p.getServerWorld(),36.5,ground,32,0,25);
        });
        if (tick == 470) shot(c,"05-avata-hub");
        if (tick == 485) server(c,p -> {
            check(avata.chargeFraction(0)>0.75,"Avata hub receives energy");
            station.setEnergyWh(0); stopped=avata.chargeFraction(0);
        });
        if (tick == 515) server(c,p -> {
            check(avata.chargeFraction(0)==stopped,"empty station stops charging");
            check(!avata.isCharging(0),"empty station stops blink");
            p.setSneaking(true); station.interact(p,Hand.MAIN_HAND); p.setSneaking(false);
        });
        if (tick == 585) server(c,p -> {
            check(station.getCableState()==PowerStationEntity.STOWED,"cable retracts");
            var saved=station.getEnergyWh();station.damage(p.getDamageSources().playerAttack(p),1);
            check(station.isRemoved(),"station can be picked up after retract");
            passed=true;System.out.println("CHARGING_QA PASS: batteries, sequence, energy, NBT, animations, disconnect, aircraft lock, depletion, retract");
        });
        if (tick >= 610 && passed) c.scheduleStop();
    }
}
