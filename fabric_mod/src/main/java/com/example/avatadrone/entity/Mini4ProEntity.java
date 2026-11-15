package com.example.avatadrone.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/** Mini 4 Pro shares pairing, pilot ownership, return and RC networking with Avata. */
public class Mini4ProEntity extends AvataDroneEntity {
    private static final TrackedData<Float> BATTERY =
            DataTracker.registerData(Mini4ProEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Boolean> LONG_RANGE_BATTERY =
            DataTracker.registerData(Mini4ProEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.mini4pro.idle");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.mini4pro.fly");
    private static final RawAnimation DOWN = RawAnimation.begin().thenPlayAndHold("animation.mini4pro.gimbal_down");
    private static final RawAnimation FORWARD = RawAnimation.begin().thenPlayAndHold("animation.mini4pro.gimbal_forward");
    private boolean gimbalMoved;
    private double charge = 1;

    public Mini4ProEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        setNoGravity(false);
    }

    @Override protected double modeHorizontalSpeed() { return com.example.avatadrone.DroneTuning.miniHorizontalSpeed(getFlightMode()); }
    @Override protected double modeVerticalSpeed() { return getFlightMode() == 0 ? 3 : 5; }
    @Override protected double modeDescentSpeed() { return getFlightMode() == 0 ? 3 : 5; }
    @Override public String getModelLabel() { return "MINI 4 PRO"; }
    @Override public float getCameraPitch(float tickDelta) { return isGimbalDown() ? 90 : 0; }
    @Override public double getCameraVerticalFov() {
        // Manufacturer's 82.1 degree diagonal FOV converted to vertical for the 16:9 feed.
        return Math.toDegrees(2 * Math.atan(Math.tan(Math.toRadians(82.1 / 2)) / Math.hypot(16.0 / 9.0, 1)));
    }

    @Override protected double horizontalSensorRange(double forwardDot) {
        return forwardDot > 0.5 ? 18 : forwardDot < -0.5 ? 15 : 12;
    }
    @Override protected double obstacleSensorRangeForDirection(int direction) {
        return switch (direction) { case 0 -> 18; case 1 -> 15; default -> 10; };
    }
    @Override protected double verticalSensorRange(boolean upward) { return upward ? 15 : 12; }
    @Override protected boolean horizontalAvoidanceAvailable() {
        return getFlightMode() != 2 && avoidanceMode != 2;
    }
    @Override protected boolean verticalAvoidanceAvailable() {
        return getFlightMode() != 2 && avoidanceMode != 2;
    }
    public boolean isGimbalDown() { return isMode360(); }
    public boolean hasLongRangeBattery() { return dataTracker.get(LONG_RANGE_BATTERY); }
    public float getBatteryPercent() { return dataTracker.get(BATTERY); }
    @Override public void installBattery(net.minecraft.item.ItemStack battery) {
        super.installBattery(battery);
        var data = battery.getNbt();
        restoreBattery(data != null && data.contains("BatteryCharge") ? data.getDouble("BatteryCharge") : 1,
                com.example.avatadrone.DroneTuning.isLongRangeBattery(battery));
    }
    @Override public net.minecraft.item.ItemStack getInstalledBattery() {
        var battery = super.getInstalledBattery();
        if (!battery.isEmpty()) {
            battery.getOrCreateNbt().putDouble("BatteryCharge", charge);
            battery.getOrCreateNbt().putBoolean("LongRangeBattery", hasLongRangeBattery());
        }
        return battery;
    }
    public void restoreBattery(double charge, boolean longRange) {
        this.charge = net.minecraft.util.math.MathHelper.clamp(charge, 0, 1);
        dataTracker.set(BATTERY, (float) (this.charge * 100));
        dataTracker.set(LONG_RANGE_BATTERY, longRange);
    }
    @Override protected boolean canArm() { return hasBattery() && charge > 0.08; }
    public int getRemainingFlightSeconds() {
        return (int) ((int) (getBatteryPercent() / 100 * enduranceSeconds(getVelocity().horizontalLength() * 20 < 0.1)) * enduranceFactor());
    }
    private int enduranceSeconds(boolean hovering) {
        return 60 * (hasLongRangeBattery() ? (hovering ? 49 : 49) : (hovering ? 36 : 36));
    }

    @Override protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(BATTERY, 100f);
        dataTracker.startTracking(LONG_RANGE_BATTERY, false);
    }
    @Override public void tick() {
        super.tick();
        if (!getWorld().isClient && areMotorsArmed()) {
            charge = Math.max(0, charge - 1.0 / (20 * enduranceSeconds(getVelocity().horizontalLength() * 20 < 0.1)) / Math.max(0.25, enduranceFactor()));
            dataTracker.set(BATTERY, (float) (charge * 100));
            if (charge <= 0.08) requestLowBatteryLanding();
        }
    }
    @Override public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putDouble("BatteryCharge", charge);
        nbt.putBoolean("LongRangeBattery", hasLongRangeBattery());
    }
    @Override public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        charge = nbt.contains("BatteryCharge") ? net.minecraft.util.math.MathHelper.clamp(nbt.getDouble("BatteryCharge"), 0, 1) : 1;
        dataTracker.set(BATTERY, (float) (charge * 100));
        dataTracker.set(LONG_RANGE_BATTERY, nbt.getBoolean("LongRangeBattery")
                || com.example.avatadrone.DroneTuning.isLongRangeBattery(super.getInstalledBattery()));
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "flight", 6, state ->
                state.setAndContinue(areMotorsArmed() ? FLY : IDLE)));
        controllers.add(new AnimationController<>(this, "battery", 0, state -> state.setAndContinue(
                RawAnimation.begin().thenPlayAndHold("animation.mini4pro.battery_" + (hasBattery() ? "installed" : "empty"))))
                .triggerableAnim("insert", RawAnimation.begin().thenPlay("animation.mini4pro.battery_insert"))
                .triggerableAnim("remove", RawAnimation.begin().thenPlay("animation.mini4pro.battery_remove")));
        controllers.add(new AnimationController<>(this, "gimbal", 0, state -> {
            if (isGimbalDown()) { gimbalMoved = true; return state.setAndContinue(DOWN); }
            return gimbalMoved ? state.setAndContinue(FORWARD) : PlayState.STOP;
        }));
    }
}
