package com.example.avatadrone.entity;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.item.DeviceCharging;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * DJI Power AURO 2000 ELITE portable power station (placeable entity).
 *
 *  Shift + right click      : pull out / retract the built-in 140 W retractable USB-C cable
 *  Shift + right click hub : connect / disconnect a placed three-slot battery hub
 *  Shift + left click drone: connect / disconnect a grounded aircraft with an installed battery
 *  left click (punch)       : pick the station up after retracting the cable; keeps energy
 *  redstone power on / under the station = mains (AC) input, 1440 W
 *
 * Real data: 2016 Wh LiFePO4, 456 x 225 x 245 mm, built-in USB-C cable 140 W max, AC input 1440 W max.
 */
public class PowerStationEntity extends Entity implements GeoEntity {
    public static final double CAPACITY_WH = 2016, CABLE_MAX_W = 140, AC_INPUT_W = 1440, EFFICIENCY = 0.92;
    /** gameplay speed-up of every charge / discharge process (1 = real time). */
    public static final double TIME_SCALE = 20;
    public static final int STOWED = 0, OUT = 1, PLUGGED = 2;

    private static final TrackedData<Integer> CABLE = DataTracker.registerData(PowerStationEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> CHARGE = DataTracker.registerData(PowerStationEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> OUTPUT_W = DataTracker.registerData(PowerStationEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> INPUT_W = DataTracker.registerData(PowerStationEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<ItemStack> DEVICE = DataTracker.registerData(PowerStationEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);

    private static final String A = "animation.auro2000elite.";
    private static final RawAnimation STATE_STOWED = RawAnimation.begin().thenPlayAndHold(A + "cable_stowed");
    private static final RawAnimation STATE_OUT = RawAnimation.begin().thenPlayAndHold(A + "cable_out");
    private static final RawAnimation STATE_PLUGGED = RawAnimation.begin().thenPlayAndHold(A + "cable_plugged");
    // animation lengths in ticks (see tools/auro_cable.py)
    public static final int T_EXTRACT = 32, T_RETRACT = 27, T_PLUG = 22, T_UNPLUG = 19, T_UNPLUG_RETRACT = 46;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private double energyWh = CAPACITY_WH;
    private ItemStack device = ItemStack.EMPTY;
    private long busyUntil, ghostUntil;          // ghostUntil: keep showing an unplugged device until the plug has left it
    private java.util.UUID cableHolder;
    private net.minecraft.util.math.BlockPos hubTarget;
    private java.util.UUID droneTarget;
    private java.util.UUID controllerHolder;
    private ItemStack controllerStack = ItemStack.EMPTY;
    public boolean chargesController(PlayerEntity player, ItemStack stack) {
        return getCableState() == PLUGGED && player.getUuid().equals(controllerHolder) && controllerStack == stack;
    }
    private void connectController(PlayerEntity player, ItemStack stack) {
        controllerHolder = player.getUuid(); controllerStack = stack;
        hubTarget = null; droneTarget = null;
        dataTracker.set(TARGET_ENTITY, player.getId()); dataTracker.set(TARGET_KIND, 3);
        beginConnection();
    }
    private static final TrackedData<Integer> TARGET_KIND = DataTracker.registerData(PowerStationEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> TARGET_ENTITY = DataTracker.registerData(PowerStationEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<net.minecraft.util.math.BlockPos> TARGET_BLOCK = DataTracker.registerData(PowerStationEntity.class, TrackedDataHandlerRegistry.BLOCK_POS);
    private static final TrackedData<Long> CONNECTION_TIME = DataTracker.registerData(PowerStationEntity.class, TrackedDataHandlerRegistry.LONG);
    public static final double CABLE_REACH = 5;
    public int getTargetKind() { return dataTracker.get(TARGET_KIND); }
    public int getTargetEntityId() { return dataTracker.get(TARGET_ENTITY); }
    public net.minecraft.util.math.BlockPos getTargetBlock() { return dataTracker.get(TARGET_BLOCK); }
    public long getConnectionTime() { return dataTracker.get(CONNECTION_TIME); }
    public boolean heldBy(PlayerEntity player) { return player.getUuid().equals(cableHolder); }
    public boolean targets(AvataDroneEntity drone) { return drone.getUuid().equals(droneTarget); }
    public boolean targets(net.minecraft.util.math.BlockPos pos) { return pos.equals(hubTarget); }
    public boolean canConnect(PlayerEntity player, net.minecraft.util.math.Vec3d pos) {
        return !busy() && getCableState() == OUT && heldBy(player) && squaredDistanceTo(pos) <= CABLE_REACH * CABLE_REACH;
    }
    public void connect(net.minecraft.util.math.BlockPos pos) {
        hubTarget = pos.toImmutable(); droneTarget = null;
        dataTracker.set(TARGET_BLOCK, pos); dataTracker.set(TARGET_KIND, 1);
        beginConnection();
    }
    public void connect(AvataDroneEntity drone) {
        droneTarget = drone.getUuid(); hubTarget = null;
        dataTracker.set(TARGET_ENTITY, drone.getId()); dataTracker.set(TARGET_KIND, 2);
        beginConnection();
    }
    private void beginConnection() {
        dataTracker.set(CABLE, PLUGGED);
        dataTracker.set(CONNECTION_TIME, getWorld().getTime());
        play("plug", T_PLUG);
        sound(SoundEvents.BLOCK_TRIPWIRE_CLICK_ON, 0.7f, 1.3f);
    }
    public void disconnect(PlayerEntity player) {
        if (busy()) return;
        hubTarget = null; droneTarget = null;
        controllerHolder = null; controllerStack = ItemStack.EMPTY;
        cableHolder = player == null ? null : player.getUuid();
        dataTracker.set(CABLE, OUT);
        dataTracker.set(CONNECTION_TIME, getWorld().getTime());
        play("unplug", T_UNPLUG);
        sound(SoundEvents.BLOCK_TRIPWIRE_CLICK_OFF, 0.7f, 1.2f);
    }
    public boolean isBusy() { return busy(); }


    public PowerStationEntity(EntityType<? extends PowerStationEntity> type, World world) {
        super(type, world);
        this.intersectionChecked = true;
    }

    // ------------------------------------------------------------------ data
    @Override protected void initDataTracker() {
        dataTracker.startTracking(CABLE, STOWED);
        dataTracker.startTracking(TARGET_KIND, 0);
        dataTracker.startTracking(TARGET_ENTITY, -1);
        dataTracker.startTracking(TARGET_BLOCK, net.minecraft.util.math.BlockPos.ORIGIN);
        dataTracker.startTracking(CONNECTION_TIME, 0L);
        dataTracker.startTracking(CHARGE, 1f);
        dataTracker.startTracking(OUTPUT_W, 0);
        dataTracker.startTracking(INPUT_W, 0);
        dataTracker.startTracking(DEVICE, ItemStack.EMPTY);
    }
    public int getCableState() { return dataTracker.get(CABLE); }
    public float getChargeFraction() { return dataTracker.get(CHARGE); }
    public int getOutputWatts() { return dataTracker.get(OUTPUT_W); }
    public int getInputWatts() { return dataTracker.get(INPUT_W); }
    public ItemStack getDevice() { return dataTracker.get(DEVICE); }
    public double getEnergyWh() { return energyWh; }
    public void setEnergyWh(double wh) {
        energyWh = Double.isFinite(wh) ? MathHelper.clamp(wh, 0, CAPACITY_WH) : CAPACITY_WH;
        dataTracker.set(CHARGE, (float) (energyWh / CAPACITY_WH));
    }
    private void setDevice(ItemStack stack) {
        device = stack;
        dataTracker.set(DEVICE, stack.copy());
    }

    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {
        setEnergyWh(nbt.contains("EnergyWh") ? nbt.getDouble("EnergyWh") : CAPACITY_WH);
        device = nbt.contains("Device") ? ItemStack.fromNbt(nbt.getCompound("Device")) : ItemStack.EMPTY;
        // Connections are released on load; battery inventories and stored energy remain persistent.
        dataTracker.set(CABLE, STOWED);
        dataTracker.set(DEVICE, device.copy());
    }
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putDouble("EnergyWh", energyWh);
        nbt.putInt("Cable", getCableState());
        if (!device.isEmpty()) nbt.put("Device", device.writeNbt(new NbtCompound()));
    }

    // ------------------------------------------------------------------ physics
    @Override public boolean isCollidable() { return true; }      // can be stood on, like a boat / shulker
    @Override public boolean canHit() { return !isRemoved(); }
    @Override public boolean isPushable() { return false; }
    /** the pulled-out cable and the plugged device reach ~0.7 block in front of the hitbox: don't frustum-cull them away. */
    @Override public net.minecraft.util.math.Box getVisibilityBoundingBox() { return getBoundingBox().expand(CABLE_REACH); }

    @Override public void tick() {
        super.tick();
        if (!hasNoGravity()) setVelocity(getVelocity().add(0, -0.04, 0));
        move(MovementType.SELF, getVelocity());
        setVelocity(getVelocity().multiply(0.98));
        if (isOnGround()) setVelocity(getVelocity().multiply(0.6, -0.5, 0.6));
        if (getWorld().isClient) return;
        tickPower();
    }

    /** server: AC input from redstone, 140 W cable output into the plugged device. */
    private void tickPower() {
        double dtH = TIME_SCALE / 20.0 / 3600.0;                      // hours represented by one tick
        boolean mains = getWorld().isReceivingRedstonePower(getBlockPos()) || getWorld().isReceivingRedstonePower(getBlockPos().down());
        int in = 0;
        if (mains && energyWh < CAPACITY_WH) {
            // 0-80 % in ~85 min (DJI spec) -> constant power, tapering above 80 %
            double frac = energyWh / CAPACITY_WH;
            double w = AC_INPUT_W * (frac < 0.8 ? 1.0 : Math.max(0.12, (1 - frac) / 0.2));
            in = (int) Math.round(w);
            setEnergyWh(energyWh + w * dtH);
        }
        int out = 0;
        if (getCableState() == PLUGGED) {
            double wh = 0;
            boolean valid = false;
            if (hubTarget != null && getWorld().isChunkLoaded(hubTarget)
                    && squaredDistanceTo(net.minecraft.util.math.Vec3d.ofCenter(hubTarget)) <= CABLE_REACH * CABLE_REACH
                    && getWorld().getBlockEntity(hubTarget) instanceof com.example.avatadrone.block.charger.ChargingHubBlockEntity hub) {
                valid = true;
                if (!busy() && energyWh > 0) wh = hub.receiveEnergy(energyWh * EFFICIENCY, CABLE_MAX_W, dtH);
            } else if (droneTarget != null && getWorld() instanceof net.minecraft.server.world.ServerWorld sw
                    && sw.getEntity(droneTarget) instanceof AvataDroneEntity drone
                    && drone.isAlive() && drone.hasBattery() && !drone.areMotorsArmed() && drone.isOnGround()
                    && squaredDistanceTo(drone) <= CABLE_REACH * CABLE_REACH) {
                valid = true;
                if (!busy() && energyWh > 0) {
                    ItemStack battery = drone.getInstalledBattery();
                    double c = DeviceCharging.getCharge(battery), cap = DeviceCharging.capacityWh(battery);
                    double watts = Math.min(CABLE_MAX_W, DeviceCharging.maxInputW(battery));
                    if (c > 0.9) watts *= Math.max(0.15, (1 - c) / 0.1);
                    wh = Math.min(energyWh * EFFICIENCY, Math.min(watts * dtH, (1 - c) * cap));
                    if (wh > 0) { DeviceCharging.setCharge(battery, c + wh / cap); drone.updateChargingBattery(battery); }
                }
            }
            if (controllerHolder != null) {
                PlayerEntity player = getWorld().getPlayerByUuid(controllerHolder);
                if (player != null && player.isAlive() && !player.isSpectator()
                        && squaredDistanceTo(player) <= CABLE_REACH * CABLE_REACH
                        && player.getMainHandStack() == controllerStack
                        && DeviceCharging.isHandheld(controllerStack)) {
                    valid = true;
                    if (!busy() && energyWh > 0) {
                        double c = DeviceCharging.getCharge(controllerStack), cap = DeviceCharging.capacityWh(controllerStack);
                        double watts = Math.min(CABLE_MAX_W, DeviceCharging.maxInputW(controllerStack));
                        if (c > 0.9) watts *= Math.max(0.15, (1 - c) / 0.1);
                        wh = Math.min(energyWh * EFFICIENCY, Math.min(watts * dtH, (1 - c) * cap));
                        if (wh > 0) DeviceCharging.setCharge(controllerStack, c + wh / cap);
                    }
                    if (!busy() && controllerStack.getItem() instanceof com.example.avatadrone.item.PhoneItem)
                        com.example.avatadrone.item.PhoneItem.reportCharging(controllerStack, (int) Math.round(wh / dtH), getWorld().getTime());
                }
            }
            if (!valid && !busy()) disconnect(null);
            if (wh > 0) { setEnergyWh(energyWh - wh / EFFICIENCY); out = (int)Math.round(wh / dtH); }
        }
        if (getCableState() != PLUGGED && getTargetKind() != 0 && !busy()) dataTracker.set(TARGET_KIND, 0);
        if (getCableState() == OUT && cableHolder != null && !busy()) {
            PlayerEntity holder = getWorld().getPlayerByUuid(cableHolder);
            if (holder == null || !holder.isAlive() || squaredDistanceTo(holder) > CABLE_REACH * CABLE_REACH) {
                cableHolder = null; dataTracker.set(CABLE, STOWED); play("retract", T_RETRACT);
            }
        }
        if (device.isEmpty() && !getDevice().isEmpty() && getWorld().getTime() >= ghostUntil) dataTracker.set(DEVICE, ItemStack.EMPTY);
        if (in != getInputWatts()) dataTracker.set(INPUT_W, in);
        if (out != getOutputWatts()) dataTracker.set(OUTPUT_W, out);
    }

    // ------------------------------------------------------------------ interaction
    private boolean busy() { return getWorld().getTime() < busyUntil; }
    private void play(String trigger, int ticks) {
        triggerAnim("cable", trigger);
        busyUntil = getWorld().getTime() + ticks;
    }
    private void sound(net.minecraft.sound.SoundEvent ev, float vol, float pitch) {
        getWorld().playSound(null, getX(), getY(), getZ(), ev, SoundCategory.BLOCKS, vol, pitch);
    }

    @Override public ActionResult interact(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND || player.isSpectator()) return ActionResult.PASS;
        if (getWorld().isClient) return ActionResult.SUCCESS;
        if (busy()) return ActionResult.CONSUME;
        if (!player.isAlive() || !player.canModifyBlocks() || squaredDistanceTo(player) > CABLE_REACH * CABLE_REACH) return ActionResult.FAIL;
        ItemStack held = player.getStackInHand(hand);
        if (!player.isSneaking() && DeviceCharging.isHandheld(held)) {
            if (chargesController(player, held)) disconnect(player);
            else if (canConnect(player, player.getPos())) {
                var other = com.example.avatadrone.network.ChargingNetwork.controllerSource(player, held);
                if (other == null) connectController(player, held);
                else player.sendMessage(Text.literal("该设备已连接另一台移动电源"), true);
            } else player.sendMessage(Text.literal("先 Shift+右键移动电源取线，等待抽线完成，再手持遥控器/手机右键连接"), true);
            return ActionResult.CONSUME;
        }
        // Recover pre-hub-version saves without silently deleting an old stored device.
        if (!device.isEmpty()) { giveBack(player, device); setDevice(ItemStack.EMPTY); }
        if (player.isSneaking()) {
            if (getCableState() == STOWED) {
                var previous = com.example.avatadrone.network.ChargingNetwork.held(player);
                if (previous != null && previous != this) {
                    player.sendMessage(Text.literal("请先收回手中的另一根充电线"), true);
                    return ActionResult.CONSUME;
                }
                cableHolder = player.getUuid();
                dataTracker.set(CABLE, OUT);
                play("extract", T_EXTRACT);
                player.sendMessage(Text.literal("已取出充电线：Shift+右键管家 / Shift+左键无人机 / 手持遥控器/手机右键电源（5格内）"), true);
            } else {
                hubTarget = null; droneTarget = null; cableHolder = null;
                controllerHolder = null; controllerStack = ItemStack.EMPTY;
                dataTracker.set(CONNECTION_TIME, getWorld().getTime());
                boolean plugged = getCableState() == PLUGGED;
                dataTracker.set(CABLE, STOWED);
                play(plugged ? "unplug_retract" : "retract", plugged ? T_UNPLUG_RETRACT : T_RETRACT);
                player.sendMessage(Text.literal("正在收回充电线"), true);
            }
        } else {
            if (DeviceCharging.isChargeable(player.getStackInHand(hand)))
                player.sendMessage(Text.literal("裸电池必须放入充电管家；无人机请先放置后连接充电线"), true);
            else player.sendMessage(statusText(), true);
        }
        return ActionResult.CONSUME;
    }

    /** the device leaves the station's logic now, but stays visible until the unplug part of the animation ends. */
    private void unplugKeepGhost() {
        device = ItemStack.EMPTY;
        ghostUntil = (int) getWorld().getTime() + T_UNPLUG;
    }

    public Text statusText() {
        int pct = Math.round(getChargeFraction() * 100);
        String cable = switch (getCableState()) { case OUT -> "out"; case PLUGGED -> "plugged"; default -> "stowed"; };
        return Text.translatable("message.djimod.auro.status", pct, Math.round(energyWh), getInputWatts(), getOutputWatts(),
                Text.translatable("message.djimod.auro.cable." + cable));
    }

    private void giveBack(PlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemStack remaining = stack.copy();
        if (!player.getInventory().insertStack(remaining)) dropStack(remaining, 0.3f);
    }

    /** punch = pick up (into the inventory, else dropped); the plugged device is returned as well. */
    @Override public boolean damage(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || getWorld().isClient || isRemoved()) return false;
        if (!(source.getAttacker() instanceof PlayerEntity player) || player.isSpectator()) return false;
        if (busy() || getCableState() != STOWED) {
            player.sendMessage(Text.literal("请先收回充电线，再收起移动电源"), true); return false;
        }
        ItemStack item = new ItemStack(AvataDrone.AURO2000_ITEM);
        item.getOrCreateNbt().putDouble("EnergyWh", energyWh);
        if (hasCustomName()) item.setCustomName(getCustomName());
        if (!device.isEmpty()) giveBack(player, device);
        device = ItemStack.EMPTY;
        if (!player.getAbilities().creativeMode || player.isSneaking()) giveBack(player, item);
        sound(SoundEvents.ITEM_ARMOR_EQUIP_IRON, 0.8f, 0.9f);
        discard();
        return true;
    }

    // ------------------------------------------------------------------ GeckoLib
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "cable", 0, state -> state.setAndContinue(switch (getCableState()) {
                    case OUT -> STATE_OUT;
                    case PLUGGED -> STATE_PLUGGED;
                    default -> STATE_STOWED;
                }))
                .triggerableAnim("extract", RawAnimation.begin().then(A + "cable_extract", software.bernie.geckolib.core.animation.Animation.LoopType.PLAY_ONCE))
                .triggerableAnim("retract", RawAnimation.begin().then(A + "cable_retract", software.bernie.geckolib.core.animation.Animation.LoopType.PLAY_ONCE))
                .triggerableAnim("plug", RawAnimation.begin().then(A + "cable_plug", software.bernie.geckolib.core.animation.Animation.LoopType.PLAY_ONCE))
                .triggerableAnim("unplug", RawAnimation.begin().then(A + "cable_unplug", software.bernie.geckolib.core.animation.Animation.LoopType.PLAY_ONCE))
                .triggerableAnim("unplug_retract", RawAnimation.begin().then(A + "cable_unplug_retract", software.bernie.geckolib.core.animation.Animation.LoopType.PLAY_ONCE)));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
