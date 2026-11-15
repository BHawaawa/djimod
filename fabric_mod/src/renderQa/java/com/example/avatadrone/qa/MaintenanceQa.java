package com.example.avatadrone.qa;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.client.DroneWheelScreen;
import com.example.avatadrone.entity.AvataDroneEntity;
import com.example.avatadrone.item.DroneEquipmentItem;
import com.example.avatadrone.network.DroneMaintenance;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.*;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;
import java.nio.file.*;
import java.util.function.Consumer;
import net.minecraft.server.network.ServerPlayerEntity;

/** Real client/server inventory and visual regression run, isolated from release builds. */
public final class MaintenanceQa implements ClientModInitializer {
    private int ticks;
    private int ground;
    private ItemStack interrupted;
    private Item model;
    private String name;
    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("avatadrone.maintenanceQa")) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null || client.getServer() == null) return;
            try { tick(client); } catch (Exception e) { throw new IllegalStateException("Maintenance QA failed", e); }
        });
    }
    private void server(MinecraftClient client, Consumer<ServerPlayerEntity> action) {
        var id = client.player.getUuid();
        client.getServer().execute(() -> action.accept(client.getServer().getPlayerManager().getPlayer(id)));
    }
    private void check(boolean value, String message) { if (!value) throw new AssertionError(name + ": " + message); }
    private int count(ServerPlayerEntity p) { return p.getInventory().count(DroneMaintenance.battery((DroneEquipmentItem) model)); }
    private void open(MinecraftClient c) {
        c.setScreen(null);
        model.use(c.world, c.player, Hand.MAIN_HAND);
        check(c.currentScreen instanceof DroneWheelScreen, "right click must open wheel");
    }
    private void click(MinecraftClient c, int sector) {
        check(c.currentScreen instanceof DroneWheelScreen, "wheel should be open");
        double angle = -Math.PI / 2 + sector * Math.PI * 2 / 3;
        c.currentScreen.mouseClicked(c.currentScreen.width / 2.0 + 65 * Math.cos(angle),
                c.currentScreen.height / 2.0 + 65 * Math.sin(angle), 0);
    }
    private void shot(MinecraftClient c, String stage) throws Exception {
        Path dir = Path.of("screenshots"); Files.createDirectories(dir);
        try (var image = ScreenshotRecorder.takeScreenshot(c.getFramebuffer())) {
            image.writeTo(dir.resolve(name + "-" + stage + ".png"));
        }
        System.out.println("MAINTENANCE_QA screenshot " + name + " " + stage);
    }
    private void tick(MinecraftClient c) throws Exception {
        c.options.pauseOnLostFocus = false;
        ticks++;
        int step = ticks % 800;
        if (ticks == 1600) { System.out.println("MAINTENANCE_QA PASS both aircraft complete"); c.scheduleStop(); return; }
        if (step == 80) {
            model = ticks < 800 ? AvataDrone.AVATA360_ITEM : AvataDrone.MINI4PRO_ITEM;
            name = ((DroneEquipmentItem) model).model;
            c.options.setPerspective(net.minecraft.client.option.Perspective.FIRST_PERSON);
            server(c, p -> {
                var world = p.getServerWorld();
                ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, 20, 20);
                for (int x=15;x<=25;x++) for(int z=15;z<=30;z++)
                    world.setBlockState(new BlockPos(x,ground-1,z),net.minecraft.block.Blocks.SMOOTH_STONE.getDefaultState());
                world.setTimeOfDay(6000);
                p.teleport(world,20,ground,20,0,35);
                p.getInventory().clear(); p.getInventory().selectedSlot=0;
                p.setStackInHand(Hand.MAIN_HAND,new ItemStack(model));
                p.getMainHandStack().getOrCreateNbt().put("BatteryData", new net.minecraft.nbt.NbtCompound());
                p.getMainHandStack().getNbt().getCompound("BatteryData").putDouble("BatteryCharge", 0.61);
                p.currentScreenHandler.sendContentUpdates();
            });
        }
        if (step == 140) open(c);
        if (step == 170) shot(c,"01-wheel-off");
        if (step == 180) {
            click(c,2);
            check(c.currentScreen instanceof DroneWheelScreen,"disabled place must not close wheel");
            server(c,p->{DroneMaintenance.request(p,Hand.MAIN_HAND,"place");check(p.getMainHandStack().isOf(model),"cannot place while off");});
        }
        if (step == 190) click(c,1);
        if (step == 200) server(c,p->{
            check(count(p)==0 && DroneEquipmentItem.hasBattery(p.getMainHandStack()),"removal must wait for animation");
            DroneMaintenance.request(p,Hand.MAIN_HAND,"battery_remove");
        });
        if (step == 212) shot(c,"02-remove-animation");
        if (step == 240) {
            server(c,p->{
                check(!DroneEquipmentItem.hasBattery(p.getMainHandStack()) && count(p)==1,"exactly one removed battery");
                int slot = DroneMaintenance.batterySlot(p, (DroneEquipmentItem) model);
                ItemStack saved = p.getInventory().getStack(slot);
                check(saved.getNbt().getDouble("BatteryCharge") == 0.61, "battery metadata retained");
                p.getInventory().setStack(slot, ItemStack.EMPTY);
                DroneMaintenance.request(p, Hand.MAIN_HAND, "battery_insert");
                check(DroneEquipmentItem.action(p.getMainHandStack()).isEmpty(), "cannot install missing battery");
                p.getInventory().setStack(slot, new ItemStack(model == AvataDrone.AVATA360_ITEM ? AvataDrone.MINI4PRO_BATTERY : AvataDrone.AVATA360_BATTERY));
                DroneMaintenance.request(p, Hand.MAIN_HAND, "battery_insert");
                check(DroneEquipmentItem.action(p.getMainHandStack()).isEmpty(), "cannot install wrong model battery");
                p.getInventory().setStack(slot, saved);
                DroneMaintenance.request(p, Hand.MAIN_HAND, "power_on");
                DroneMaintenance.request(p, Hand.MAIN_HAND, "place");
                check(DroneEquipmentItem.action(p.getMainHandStack()).isEmpty(), "empty battery locks power and place");
            });
            open(c);
        }
        if (step == 260) shot(c,"03-wheel-empty");
        if (step == 270) click(c,1);
        if (step == 282) server(c,p->check(count(p)==1,"insert must not consume early"));
        if (step == 278) shot(c,"04-insert-animation");
        if (step == 330) { server(c,p->check(DroneEquipmentItem.hasBattery(p.getMainHandStack()) && count(p)==0,"one battery consumed")); open(c); }
        if (step == 340) click(c,0);
        if (step == 355) shot(c,"05-cover-remove");
        if (step == 385) shot(c,"06-power-button");
        if (step == 420) {
            server(c,p->{
                check(DroneEquipmentItem.powered(p.getMainHandStack()),"power on completion");
                DroneMaintenance.request(p,Hand.MAIN_HAND,"battery_remove");
                check(DroneEquipmentItem.action(p.getMainHandStack()).isEmpty(),"cannot remove powered battery");
            }); open(c);
        }
        if (step == 440) shot(c,"07-wheel-on");
        if (step == 450) click(c,0);
        if (step == 490) shot(c,"08-cover-install");
        if (step == 530) server(c,p->{
            check(!DroneEquipmentItem.powered(p.getMainHandStack()),"power off completion");
            DroneMaintenance.request(p,Hand.MAIN_HAND,"battery_remove");
            interrupted=p.getMainHandStack(); p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
        });
        if (step == 540) server(c,p->{ p.setStackInHand(Hand.MAIN_HAND,interrupted); p.currentScreenHandler.sendContentUpdates(); });
        if (step == 580) { server(c,p->check(DroneEquipmentItem.hasBattery(p.getMainHandStack()) && count(p)==0 && DroneEquipmentItem.action(p.getMainHandStack()).isEmpty(),"interrupted animation must not change inventory")); open(c); }
        if (step == 590) click(c,0);
        if (step == 670) open(c);
        if (step == 680) click(c,2);
        if (step == 720) server(c,p->{
            check(p.getMainHandStack().isEmpty(),"placement consumes held aircraft");
            var drones=p.getServerWorld().getEntitiesByClass(AvataDroneEntity.class,new Box(15,ground,20,25,ground+3,30),d->d.hasBattery());
            check(!drones.isEmpty(),"placed aircraft has battery");
            // Clear only the QA-created placement before the next model's run.
            drones.forEach(d->d.discard());
            System.out.println("MAINTENANCE_QA PASS " + name + " transitions, disabled actions, duplicate request, cancellation, counts and placement");
        });
    }
}
