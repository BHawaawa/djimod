package com.example.avatadrone.qa;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.entity.AvataDroneEntity;
import com.example.avatadrone.item.DeviceCharging;
import com.example.avatadrone.network.DroneControlPayload;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;

/** Exercises imported flight behavior with registered entities on the integrated server. */
final class V201GameplayChecks {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static Object call(AvataDroneEntity drone, String name) throws ReflectiveOperationException {
        var method = AvataDroneEntity.class.getDeclaredMethod(name);
        method.setAccessible(true);
        return method.invoke(drone);
    }
    private static boolean cruise(AvataDroneEntity drone) throws ReflectiveOperationException {
        var field = AvataDroneEntity.class.getDeclaredField("cruiseActive");
        field.setAccessible(true);
        return field.getBoolean(drone);
    }
    private static DroneControlPayload command(AvataDroneEntity drone, int mode, int avoidance,
            boolean arm, boolean cruise, boolean stop, boolean rth) {
        return new DroneControlPayload(drone.getUuid(), 0, 0, 0, 0, 0, 10, mode, arm, stop, rth, cruise, avoidance);
    }
    static void run(ServerPlayerEntity player) {
        try {
            var world = player.getServerWorld();
            var avata = AvataDrone.AVATA360.create(world);
            var mini = AvataDrone.MINI4PRO.create(world);
            var m400 = AvataDrone.M400.create(world);
            AvataDroneEntity[] drones = {avata, mini, m400};
            for (var drone : drones) {
                drone.refreshPositionAndAngles(player.getX(), player.getY() + 10, player.getZ(), 0, 0);
                drone.installBattery(new ItemStack(drone.batteryItem()));
                drone.applyPilotCommand(command(drone, 1, 1, true, false, false, false), player);
                check(drone.areMotorsArmed(), "V2 arm " + drone.getModelLabel());
                drone.setVelocity(.2, 0, .3);
                drone.applyPilotCommand(command(drone, 1, 1, false, true, false, false), player);
                check(cruise(drone), "Cruise activation " + drone.getModelLabel());
                call(drone, "tickCruise");
                check(drone.getVelocity().horizontalLength() > .1, "Cruise retains speed with neutral sticks");
                drone.applyPilotCommand(command(drone, 2, 1, false, true, false, false), player);
                check(!drone.isObstacleAvoidanceEnabled(), "Sport disables avoidance");
                drone.applyPilotCommand(command(drone, 1, 2, false, true, false, false), player);
                check(!drone.isObstacleAvoidanceEnabled(), "Avoidance off setting reaches server");
                drone.applyPilotCommand(command(drone, 1, 0, false, true, false, false), player);
                check(drone.isObstacleAvoidanceEnabled(), "Bypass setting reaches server");
                drone.applyPilotCommand(command(drone, 1, 1, false, true, true, false), player);
                check(!cruise(drone) && drone.areMotorsArmed() && drone.getVelocity().equals(Vec3d.ZERO), "Brake cancels cruise and hovers");
                drone.applyPilotCommand(command(drone, 1, 1, false, true, false, false), player);
                drone.applyPilotCommand(command(drone, 1, 1, false, false, false, true), player);
                check(!cruise(drone) && drone.isReturningHome(), "RTH cancels cruise");
            }
            for (var drone : drones) {
                drone.setVelocity(Vec3d.ZERO);
                for (int i = 0; i < 400; i++) call(drone, "tickEnduranceFactor");
                double hover = drone.getEnduranceFactor();
                int hoverSeconds = drone.getRemainingFlightSeconds();
                drone.setVelocity(1.5, .5, 0);
                for (int i = 0; i < 400; i++) call(drone, "tickEnduranceFactor");
                check(drone.getEnduranceFactor() < hover * .6, "Speed and climb consume endurance");
                if (drone != m400) check(drone.getRemainingFlightSeconds() < hoverSeconds * .6, "Remaining time reflects load");
                var battery = drone.getInstalledBattery();
                check(DeviceCharging.getCharge(battery) > .99, "Load estimate does not destroy battery charge");
            }
            System.out.println("V201_GAMEPLAY_QA PASS three models: cruise, brake, RTH, avoidance modes, speed/climb endurance");
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
}
