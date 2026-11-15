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
 *  right click with a device : (cable out) plug the device in - batteries or aircraft with a battery installed
 *  right click, empty hand  : (device plugged) unplug and take the device back; otherwise show the status
 *  left click (punch)       : pick the station up (keeps its charge; a plugged device is returned)
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
    private int busyUntil, ghostUntil;          // ghostUntil: keep showing an unplugged device until the plug has left it
    private boolean announcedFull;

    public PowerStationEntity(EntityType<? extends PowerStationEntity> type, World world) {
        super(type, world);
        this.intersectionChecked = true;
    }

    // ------------------------------------------------------------------ data
    @Override protected void initDataTracker() {
        dataTracker.startTracking(CABLE, STOWED);
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
        int cable = MathHelper.clamp(nbt.getInt("Cable"), STOWED, PLUGGED);
        if (cable == PLUGGED && device.isEmpty()) cable = OUT;
        dataTracker.set(CABLE, cable);
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
    @Override public net.minecraft.util.math.Box getVisibilityBoundingBox() { return getBoundingBox().expand(1.0, 0.25, 1.0); }

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
        if (getCableState() == PLUGGED && !device.isEmpty() && DeviceCharging.isChargeable(device) && energyWh > 0
                && getWorld().getTime() >= busyUntil) {
            double cap = DeviceCharging.capacityWh(device), c = DeviceCharging.getCharge(device);
            if (c < 0.9995) {
                double w = Math.min(CABLE_MAX_W, DeviceCharging.maxInputW(device));
                if (c > 0.9) w *= Math.max(0.15, (1 - c) / 0.1);             // CV phase taper
                double wh = Math.min(w * dtH, Math.min((1 - c) * cap, energyWh * EFFICIENCY));
                DeviceCharging.setCharge(device, c + wh / cap);
                setEnergyWh(energyWh - wh / EFFICIENCY);
                out = (int) Math.round(w);
                announcedFull = false;
                if (age % 10 == 0) dataTracker.set(DEVICE, device.copy());
            } else if (!announcedFull) {
                announcedFull = true;
                DeviceCharging.setCharge(device, 1.0);
                dataTracker.set(DEVICE, device.copy());
                getWorld().playSound(null, getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.BLOCKS, 0.6f, 1.6f);
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
        busyUntil = (int) getWorld().getTime() + ticks;
    }
    private void sound(net.minecraft.sound.SoundEvent ev, float vol, float pitch) {
        getWorld().playSound(null, getX(), getY(), getZ(), ev, SoundCategory.BLOCKS, vol, pitch);
    }

    @Override public ActionResult interact(PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        boolean client = getWorld().isClient;
        if (player.isSneaking()) {
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (client) return ActionResult.SUCCESS;
            if (busy()) return ActionResult.CONSUME;
            switch (getCableState()) {
                case STOWED -> {
                    dataTracker.set(CABLE, OUT);
                    play("extract", T_EXTRACT);
                    sound(SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, 0.7f, 1.4f);
                    player.sendMessage(Text.translatable("message.avatadrone.auro.cable_out"), true);
                }
                case OUT -> {
                    dataTracker.set(CABLE, STOWED);
                    play("retract", T_RETRACT);
                    sound(SoundEvents.BLOCK_CHAIN_PLACE, 0.6f, 1.5f);
                    player.sendMessage(Text.translatable("message.avatadrone.auro.cable_in"), true);
                }
                default -> {
                    giveBack(player, device);
                    unplugKeepGhost();
                    dataTracker.set(CABLE, STOWED);
                    play("unplug_retract", T_UNPLUG_RETRACT);
                    sound(SoundEvents.BLOCK_TRIPWIRE_CLICK_OFF, 0.7f, 1.2f);
                    player.sendMessage(Text.translatable("message.avatadrone.auro.cable_in"), true);
                }
            }
            return ActionResult.CONSUME;
        }
        // plug a device in (cable out)
        if (getCableState() == OUT && DeviceCharging.isChargeable(stack)) {
            if (client) return ActionResult.SUCCESS;
            if (busy()) return ActionResult.CONSUME;
            ItemStack one = stack.copy();
            one.setCount(1);
            if (!player.getAbilities().creativeMode) stack.decrement(1);
            setDevice(one);
            announcedFull = false;
            dataTracker.set(CABLE, PLUGGED);
            play("plug", T_PLUG);
            sound(SoundEvents.BLOCK_TRIPWIRE_CLICK_ON, 0.8f, 1.3f);
            player.sendMessage(Text.translatable("message.avatadrone.auro.plugged", one.getName(),
                    Math.round(DeviceCharging.getCharge(one) * 100)), true);
            return ActionResult.CONSUME;
        }
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (DeviceCharging.isAircraftWithoutBattery(stack)) {
            if (!client) player.sendMessage(Text.translatable("message.avatadrone.auro.no_battery"), true);
            return ActionResult.success(client);
        }
        if (getCableState() == STOWED && DeviceCharging.isChargeable(stack)) {
            if (!client) player.sendMessage(Text.translatable("message.avatadrone.auro.need_cable"), true);
            return ActionResult.success(client);
        }
        if (getCableState() == PLUGGED && stack.isEmpty()) {
            if (client) return ActionResult.SUCCESS;
            if (busy()) return ActionResult.CONSUME;
            ItemStack d = device;
            unplugKeepGhost();
            dataTracker.set(CABLE, OUT);
            play("unplug", T_UNPLUG);
            sound(SoundEvents.BLOCK_TRIPWIRE_CLICK_OFF, 0.8f, 1.2f);
            player.sendMessage(Text.translatable("message.avatadrone.auro.unplugged", d.getName(),
                    Math.round(DeviceCharging.getCharge(d) * 100)), true);
            giveBack(player, d);
            return ActionResult.CONSUME;
        }
        if (stack.isEmpty()) {
            if (!client) player.sendMessage(statusText(), true);
            return ActionResult.success(client);
        }
        return ActionResult.PASS;
    }

    /** the device leaves the station's logic now, but stays visible until the unplug part of the animation ends. */
    private void unplugKeepGhost() {
        device = ItemStack.EMPTY;
        ghostUntil = (int) getWorld().getTime() + T_UNPLUG;
    }

    public Text statusText() {
        int pct = Math.round(getChargeFraction() * 100);
        String cable = switch (getCableState()) { case OUT -> "out"; case PLUGGED -> "plugged"; default -> "stowed"; };
        return Text.translatable("message.avatadrone.auro.status", pct, Math.round(energyWh), getInputWatts(), getOutputWatts(),
                Text.translatable("message.avatadrone.auro.cable." + cable));
    }

    private void giveBack(PlayerEntity player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.getInventory().insertStack(stack.copy())) dropStack(stack.copy(), 0.3f);
    }

    /** punch = pick up (into the inventory, else dropped); the plugged device is returned as well. */
    @Override public boolean damage(DamageSource source, float amount) {
        if (isInvulnerableTo(source) || getWorld().isClient || isRemoved()) return false;
        if (!(source.getAttacker() instanceof PlayerEntity player)) return false;
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
                .triggerableAnim("extract", RawAnimation.begin().thenPlayAndHold(A + "cable_extract"))
                .triggerableAnim("retract", RawAnimation.begin().thenPlayAndHold(A + "cable_retract"))
                .triggerableAnim("plug", RawAnimation.begin().thenPlayAndHold(A + "cable_plug"))
                .triggerableAnim("unplug", RawAnimation.begin().thenPlayAndHold(A + "cable_unplug"))
                .triggerableAnim("unplug_retract", RawAnimation.begin().thenPlayAndHold(A + "cable_unplug_retract")));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
