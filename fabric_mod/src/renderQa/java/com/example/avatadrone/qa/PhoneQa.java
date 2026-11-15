package com.example.avatadrone.qa;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.entity.PowerStationEntity;
import com.example.avatadrone.item.DeviceCharging;
import com.example.avatadrone.item.PhoneApps;
import com.example.avatadrone.item.PhoneItem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Opt-in actual-client rendering and integrated-server phone/USB-C regression. */
public final class PhoneQa implements ClientModInitializer {
    private int tick;
    private PowerStationEntity station;
    private double before, energyBefore;
    private volatile Throwable failure;
    private volatile boolean passed;

    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("avatadrone.phoneQa")) return;
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (c.world == null || c.player == null || c.getServer() == null) return;
            c.options.pauseOnLostFocus = false;
            if (failure != null) throw new AssertionError("PHONE_QA failed", failure);
            try { run(c); } catch (Exception e) { throw new RuntimeException(e); }
        });
    }

    private void server(MinecraftClient c, Consumer<ServerPlayerEntity> action) {
        var id = c.player.getUuid();
        c.getServer().execute(() -> {
            try { action.accept(c.getServer().getPlayerManager().getPlayer(id)); }
            catch (Throwable e) { failure = e; e.printStackTrace(); }
        });
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static PhoneItem.Screen state(ServerPlayerEntity p) {
        return PhoneItem.screen(p.getMainHandStack(), p.getWorld().getTime());
    }
    private static void power(ServerPlayerEntity p) {
        p.setSneaking(true);
        p.getMainHandStack().use(p.getWorld(), p, Hand.MAIN_HAND);
        p.setSneaking(false);
    }
    private void shot(MinecraftClient c, String name) throws Exception {
        var dir = Path.of("screenshots");
        Files.createDirectories(dir);
        try (var image = ScreenshotRecorder.takeScreenshot(c.getFramebuffer())) {
            image.writeTo(dir.resolve("phone-" + name + ".png"));
        }
        System.out.println("PHONE_QA screenshot " + name);
    }
    private void run(MinecraftClient c) throws Exception {
        tick++;
        if (tick == 40) {
            c.setScreen(null);
            server(c, p -> {
                var w = p.getServerWorld();
                for (int x=0;x<8;x++) for(int z=0;z<8;z++) {
                    w.setBlockState(new BlockPos(x,99,z), Blocks.SMOOTH_STONE.getDefaultState());
                    for(int y=100;y<105;y++) w.setBlockState(new BlockPos(x,y,z), Blocks.AIR.getDefaultState());
                }
                w.setTimeOfDay(6000); w.setWeather(0,12000,false,false);
                p.changeGameMode(GameMode.CREATIVE);
                p.teleport(w,3.5,100,2,0,20); p.getInventory().clear();
                var s = new ItemStack(AvataDrone.REDMI_K90);
                DeviceCharging.setCharge(s,.35);
                p.setStackInHand(Hand.MAIN_HAND,s);
                check(state(p)==PhoneItem.Screen.OFF,"new phone off");
                check(DeviceCharging.capacityWh(s)==29.26 && DeviceCharging.maxInputW(s)==100,"phone power profile");
                station=AvataDrone.AURO2000.create(w);
                station.refreshPositionAndAngles(3.5,100,4,180,0); w.spawnEntity(station);
                power(p); check(state(p)==PhoneItem.Screen.BOOT,"power key boots");
            });
        }
        if (tick == 48) c.setScreen(new Preview());
        if (tick == 60) shot(c,"01-boot-redmi");
        if (tick == 85) shot(c,"02-boot-hyperos");
        if (tick == 140) {
            server(c,p -> {
                check(state(p)==PhoneItem.Screen.HOME,"boot reaches desktop");
                check(DeviceCharging.getCharge(p.getMainHandStack())<.35,"awake consumes battery");
                var s=p.getMainHandStack();
                check(PhoneApps.install(s,"compass",false),"install app");
                check(!PhoneApps.install(s,"compass",false),"duplicate rejected");
                check(PhoneApps.uninstall(s,"compass"),"uninstall app");
                c.getServer().getCommandManager().executeWithPrefix(p.getCommandSource(),"redmi app add recorder");
                check(PhoneApps.grid(s).contains("recorder"),"app command installs");
                PhoneApps.reset(s); check(PhoneApps.grid(s).equals(PhoneApps.DEFAULT_GRID),"reset apps");
                var saved=s.writeNbt(new net.minecraft.nbt.NbtCompound());
                var restored=ItemStack.fromNbt(saved);
                check(PhoneItem.isOn(restored) && DeviceCharging.getCharge(restored)==DeviceCharging.getCharge(s),"save restores power and charge");
            });
        }
        if (tick == 150) shot(c,"03-desktop");
        if (tick == 160) server(c,p -> {
            p.getMainHandStack().use(p.getWorld(),p,Hand.MAIN_HAND);
            check(state(p)==PhoneItem.Screen.SLEEP,"short press sleeps");
            before=DeviceCharging.getCharge(p.getMainHandStack());
        });
        if (tick == 190) server(c,p -> {
            double drain=before-DeviceCharging.getCharge(p.getMainHandStack());
            check(drain>0 && drain<1.0/14400,"sleep drains slowly");
            p.getMainHandStack().use(p.getWorld(),p,Hand.MAIN_HAND);
            check(state(p)==PhoneItem.Screen.HOME,"short press wakes");
            p.setSneaking(true); station.interact(p,Hand.MAIN_HAND); p.setSneaking(false);
        });
        if (tick == 230) server(c,p -> {
            station.interact(p,Hand.MAIN_HAND);
            check(station.chargesController(p,p.getMainHandStack()),"USB-C connects");
            before=DeviceCharging.getCharge(p.getMainHandStack()); energyBefore=station.getEnergyWh();
        });
        if (tick == 265) shot(c,"04-charging-desktop");
        if (tick == 300) server(c,p -> {
            var s=p.getMainHandStack();
            check(PhoneItem.isCharging(s) && PhoneItem.chargeWatts(s)==100,"100W reported");
            double gained=(DeviceCharging.getCharge(s)-before)*29.26;
            double stationLoss = (energyBefore-station.getEnergyWh())*PowerStationEntity.EFFICIENCY;
            check(gained>0 && Math.abs(gained-stationLoss)<0.005,
                    "USB-C conserves energy: phone="+gained+"Wh station="+stationLoss+"Wh start="+energyBefore+" end="+station.getEnergyWh());
            power(p); check(state(p)==PhoneItem.Screen.SHUTDOWN,"power key shuts down");
        });
        if (tick == 315) shot(c,"05-shutdown");
        if (tick == 350) server(c,p -> check(state(p)==PhoneItem.Screen.CHARGE_OFF,"off phone charging ring"));
        if (tick == 365) shot(c,"06-charging-off");
        if (tick == 380) { c.setScreen(null); server(c,p -> power(p)); }
        if (tick == 475) shot(c,"07-first-person");
        if (tick == 485) server(c,p -> {
            station.setEnergyWh(0); before=DeviceCharging.getCharge(p.getMainHandStack());
        });
        if (tick == 520) server(c,p -> {
            check(!PhoneItem.isCharging(p.getMainHandStack()),"empty source clears charging");
            check(DeviceCharging.getCharge(p.getMainHandStack())<before,"empty source does not suppress drain");
            station.setEnergyWh(100);
        });
        if (tick == 550) server(c,p -> {
            check(PhoneItem.isCharging(p.getMainHandStack()),"source resumes");
            station.interact(p,Hand.MAIN_HAND);
        });
        if (tick == 580) server(c,p -> {
            check(!PhoneItem.isCharging(p.getMainHandStack()),"unplug clears charging");
            DeviceCharging.setCharge(p.getMainHandStack(),0);
        });
        if (tick == 620) server(c,p -> {
            check(!PhoneItem.isOn(p.getMainHandStack()),"empty battery shuts down");
            power(p); check(!PhoneItem.isOn(p.getMainHandStack()),"empty battery cannot boot");
            passed=true;
        });
        if (tick == 640 && passed) {
            Files.writeString(Path.of("phone-qa-result.txt"),"PASS: actual client phone render, boot/home/sleep/shutdown, app command, save, battery drain, 100W USB-C energy conservation, off charging, empty-source drain/resume, unplug, empty battery\n");
            System.out.println("PHONE_QA PASS"); c.scheduleStop();
        }
    }
    private static final class Preview extends Screen {
        Preview() { super(Text.literal("Redmi K90 QA")); }
        @Override public boolean shouldPause() { return false; }
        @Override public void render(DrawContext context,int mx,int my,float delta) {
            context.fill(0,0,width,height,0xFF30343A);
            var matrices=context.getMatrices(); matrices.push();
            matrices.translate(width/2.0,height/2.0,0); matrices.scale(18,18,18);
            context.drawItem(client.player.getMainHandStack(),-8,-8); matrices.pop();
        }
    }
}
