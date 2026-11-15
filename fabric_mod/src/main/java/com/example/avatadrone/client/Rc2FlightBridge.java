package com.example.avatadrone.client;

import com.example.avatadrone.entity.AvataDroneEntity;
import net.minecraft.client.MinecraftClient;

/** Feeds {@link Rc2FlightUi} from the live game state and forwards UI actions to {@link Rc2View}. */
final class Rc2FlightBridge implements Rc2FlightUi.Bridge {
    static final Rc2FlightBridge INSTANCE = new Rc2FlightBridge();

    private Rc2FlightBridge() {}

    @Override public void fill(Rc2FlightUi.Telemetry t) {
        AvataDroneEntity drone = Rc2View.drone();
        MinecraftClient client = MinecraftClient.getInstance();
        t.connected = drone != null;
        t.telemetry = Rc2View.hasTelemetry();
        t.accident = Rc2View.accidentReport() != null;
        t.paired = Rc2View.hasPairing();
        t.feedReady = Rc2View.isFeedReady();
        t.feedFailed = Rc2View.isFeedFailed();
        t.mode = Rc2View.flightMode();
        t.state = Rc2View.flightState();
        t.battery = Rc2View.batteryPercent();
        t.remainingSec = Rc2View.remainingSeconds();
        t.rcBars = Rc2View.rcBars();
        t.fourGBars = Rc2View.fourGBars();
        t.satellites = Rc2View.satelliteCount();
        t.altitude = Rc2View.altitude();
        t.homeDistance = Rc2View.homeDistance();
        t.vSpeed = Rc2View.verticalSpeed();
        t.hSpeed = Rc2View.horizontalSpeed();
        t.zoom = Rc2View.cameraZoom();
        t.fpvGimbal = Rc2View.isFpvGimbal();
        t.autoReturnPct = Rc2View.autoReturnBatteryPercent();
        t.forcedLandPct = Rc2View.forcedLandingBatteryPercent();
        t.returnPct = Rc2View.returnBatteryPercent();
        t.returnSec = Rc2View.returnSeconds();
        t.autoReturnSec = Rc2View.autoReturnSeconds();
        t.forcedLandSec = Rc2View.forcedLandingSeconds();
        t.armed = drone != null && drone.areMotorsArmed();
        t.returning = drone != null && drone.isReturningHome();
        t.obstacleWarn = drone != null && drone.hasObstacleWarning();
        t.avoidanceOff = t.mode == 2 || Rc2FlightUi.avoidanceMode() == 2;
        t.model = drone == null ? "--" : drone.getModelLabel();
        t.m400 = drone instanceof com.example.avatadrone.entity.M400Entity;
        if (drone != null) {
            t.droneX = drone.getX();
            t.droneY = drone.getY();
            t.droneZ = drone.getZ();
            t.yaw = drone.getYaw();
            t.velX = drone.getVelocity().x * 20;
            t.velZ = drone.getVelocity().z * 20;
            t.homeX = drone.getHomeX();
            t.homeZ = drone.getHomeZ();
            t.obstacle[0] = drone.obstacleFrontDistance();
            t.obstacle[1] = drone.obstacleRightDistance();
            t.obstacle[2] = drone.obstacleBackDistance();
            t.obstacle[3] = drone.obstacleLeftDistance();
            t.obstacleRange = 25;
            t.obstacleUp = Rc2View.obstacleUp(); t.obstacleDown = Rc2View.obstacleDown();
        }
        t.pilotKnown = client.player != null;
        if (client.player != null) {
            t.pilotX = client.player.getX();
            t.pilotY = client.player.getY();
            t.pilotZ = client.player.getZ();
            t.pilotYaw = client.player.getYaw();
            if (drone == null) {
                t.droneX = t.pilotX;
                t.droneY = t.pilotY;
                t.droneZ = t.pilotZ;
            }
        }
        t.stations.clear();
        for (var station : FlightMapStations.current().entrySet()) {
            var pos = station.getKey();
            var facing = station.getValue();
            t.stations.add(new double[]{pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5,
                    facing.getOffsetX(), facing.getOffsetZ()});
        }
        t.keyHint = "档位 " + DroneKeys.label(DroneKeys.MODE_CYCLE) + " · 云台上下 " + DroneKeys.label(DroneKeys.GIMBAL_UP) + "/" + DroneKeys.label(DroneKeys.GIMBAL_DOWN) + " · " + "云台 " + DroneKeys.label(DroneKeys.GIMBAL) + " / 缩放 " + DroneKeys.label(DroneKeys.ZOOM)
                + " + 滚轮 · " + DroneKeys.label(DroneKeys.EXIT) + " 退出";
    }

    @Override public void exit() { Rc2View.onEscape(); }
    @Override public void takeoffOrLand() { Rc2View.requestTakeoffOrLand(); }
    @Override public void returnHome() { Rc2View.requestReturnHome(); }
    @Override public void emergencyStop() { Rc2View.requestEmergencyStop(); }
    @Override public void setMode(int mode) { Rc2View.setFlightMode(mode); }
    @Override public void setZoom(float zoom) { Rc2View.setCameraZoom(zoom); }
    @Override public void setFpvGimbal(boolean fpv) { Rc2View.setFpvGimbal(fpv); }
    @Override public String modeDescription(int mode) { return Rc2View.modeDescription(mode); }
}
