package com.example.avatadrone.entity;

import com.example.avatadrone.AvataDrone;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.*;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import software.bernie.geckolib.core.animation.*;

/** M400 flight envelope from the supplied specification; shared RC and failsafe logic. */
public class M400Entity extends AvataDroneEntity {
    public static final double BATTERY_WH = 977;
    // Gameplay estimates, not manufacturer endurance measurements.
    private static final TrackedData<Float> CHARGE = DataTracker.registerData(M400Entity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> REMAINING = DataTracker.registerData(M400Entity.class, TrackedDataHandlerRegistry.INTEGER);
    private double charge = 1;
    private double flightWatts = 993.5593, hoverWatts = 993.5593;
    private boolean animatedArmed;

    public M400Entity(EntityType<? extends PathAwareEntity> type, World world) { super(type, world); setNoGravity(false); }
    @Override public String getModelLabel() { return "MATRICE 400"; }
    @Override public Item batteryItem() { return AvataDrone.M400_BATTERY; }
    @Override protected double modeHorizontalSpeed() { return new double[]{13, 21, 27}[getFlightMode()]; }
    @Override protected double modeVerticalSpeed() { return new double[]{5, 8, 10}[getFlightMode()]; }
    @Override protected double modeDescentSpeed() { return new double[]{5, 8, 10}[getFlightMode()]; }
    @Override protected float yawDegreesPerCommand() { return 100f / 20f; }
    @Override protected boolean canArm() { return hasBattery() && charge > 0.08; }
    @Override protected boolean horizontalAvoidanceAvailable() { return getFlightMode() != 2 && avoidanceMode != 2; }
    @Override protected boolean verticalAvoidanceAvailable() { return getFlightMode() != 2 && avoidanceMode != 2; }
    // Ring lidar supplies longer-range horizontal braking than the 21m vision sensors.
    @Override protected double horizontalSensorRange(double forwardDot) { return 100; }
    @Override protected double verticalSensorRange(boolean upward) { return upward ? 25 : 19; }
    @Override protected double obstacleMargin() { return getWidth() / 2 + 0.3; }
    @Override public float getCameraPitch(float tickDelta) { return 0; }
    @Override public double getCameraVerticalFov() { return 95.3; }
    @Override public long getCameraFrameIntervalNanos() { return 0L; }

    @Override protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(CHARGE, 100f);
        dataTracker.startTracking(REMAINING, 3540);
    }
    private double powerWatts() { return (getVelocity().horizontalLength() * 20 < 0.1 ? hoverWatts : flightWatts) / Math.max(0.25, enduranceFactor()); }
    private void syncBattery() {
        dataTracker.set(CHARGE, (float)(charge * 100));
        dataTracker.set(REMAINING, (int)(charge * BATTERY_WH / powerWatts() * 3600));
    }
    @Override public float getBatteryPercent() { return hasBattery() ? dataTracker.get(CHARGE) : 0; }
    @Override public int getRemainingFlightSeconds() { return hasBattery() ? dataTracker.get(REMAINING) : 0; }
    @Override public void installBattery(ItemStack battery) {
        super.installBattery(battery);
        var nbt = battery.getNbt();
        charge = nbt != null && nbt.contains("BatteryCharge") ? finiteClamp(nbt.getDouble("BatteryCharge"), 0, 1, 1) : 1;
        syncBattery();
    }
    @Override public ItemStack getInstalledBattery() {
        var battery = super.getInstalledBattery();
        if (!battery.isEmpty()) battery.getOrCreateNbt().putDouble("BatteryCharge", charge);
        return battery;
    }
    @Override public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        if (areMotorsArmed()) {
            charge = Math.max(0, charge - powerWatts() / (BATTERY_WH * 3600 * 20));
            if (charge <= 0.08) requestLowBatteryLanding();
        }
        syncBattery();
    }
    @Override public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putDouble("BatteryCharge", charge);
        nbt.putDouble("FlightPowerWatts", flightWatts);
        nbt.putDouble("HoverPowerWatts", hoverWatts);
    }
    @Override public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        charge = nbt.contains("BatteryCharge") ? finiteClamp(nbt.getDouble("BatteryCharge"), 0, 1, 1) : 1;
        flightWatts = nbt.contains("FlightPowerWatts") ? finiteClamp(nbt.getDouble("FlightPowerWatts"), 100, 10000, 993.5593) : 993.5593;
        hoverWatts = nbt.contains("HoverPowerWatts") ? finiteClamp(nbt.getDouble("HoverPowerWatts"), 100, 10000, 993.5593) : 993.5593;
        syncBattery();
    }
    private static double finiteClamp(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? MathHelper.clamp(value, min, max) : fallback;
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "flight", 0, state -> {
            boolean armed = areMotorsArmed();
            if (armed != animatedArmed) {
                animatedArmed = armed;
                return state.setAndContinue(RawAnimation.begin()
                        .thenPlay("animation.m400." + (armed ? "takeoff" : "land"))
                        .thenLoop("animation.m400." + (armed ? "fly" : "idle")));
            }
            if (state.getController().getCurrentAnimation() == null)
                return state.setAndContinue(RawAnimation.begin().thenLoop("animation.m400." + (armed ? "fly" : "idle")));
            return software.bernie.geckolib.core.object.PlayState.CONTINUE;
        }));
        controllers.add(new AnimationController<>(this, "battery", 0, state -> state.setAndContinue(
                RawAnimation.begin().thenPlayAndHold("animation.m400.battery_" + (hasBattery() ? "installed" : "empty"))))
                .triggerableAnim("insert", RawAnimation.begin().thenPlay("animation.m400.battery_insert"))
                .triggerableAnim("remove", RawAnimation.begin().thenPlay("animation.m400.battery_remove")));
    }
}
