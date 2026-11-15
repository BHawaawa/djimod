package com.example.avatadrone.entity;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.goal.FlyGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import com.example.avatadrone.AvataDrone;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * DJI Avata 360 无人机实体。
 * - 悬停飞行（无重力 + FlightMoveControl + BirdNavigation）
 * - 空手右键：切换单镜头 / 360 全景模式（相机模组旋转 90°）
 */
public class AvataDroneEntity extends PathAwareEntity implements GeoEntity {

    private static final TrackedData<Boolean> MODE_360 =
            DataTracker.registerData(AvataDroneEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.avata360.idle");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.avata360.fly");
    private static final RawAnimation CAM_360 = RawAnimation.begin().thenPlayAndHold("animation.avata360.mode_360");
    private static final RawAnimation CAM_SINGLE = RawAnimation.begin().thenPlayAndHold("animation.avata360.mode_single");

    // ---- 电池：默认空仓；手持电池右键插入，潜行空手右键取出
    private static final TrackedData<Boolean> HAS_BATTERY =
            DataTracker.registerData(AvataDroneEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Long> BATTERY_CHANGED_AT =
            DataTracker.registerData(AvataDroneEntity.class, TrackedDataHandlerRegistry.LONG);
    private static final RawAnimation BAT_INSERT = RawAnimation.begin().thenPlay("animation.avata360.battery_insert");
    private static final RawAnimation BAT_REMOVE = RawAnimation.begin().thenPlay("animation.avata360.battery_remove");
    private static final RawAnimation BAT_INSTALLED = RawAnimation.begin().thenPlayAndHold("animation.avata360.battery_installed");
    private static final RawAnimation BAT_EMPTY = RawAnimation.begin().thenPlayAndHold("animation.avata360.battery_empty");
    /** battery_remove 长 1.5 s；客户端在这之后才隐藏 battery 骨骼 */
    public static final int BATTERY_REMOVE_TICKS = 32;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** 仅客户端使用：避免刚生成时播放一次“360→单镜头”的回正动画 */
    private boolean cameraWasRotated = false;

    public AvataDroneEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.moveControl = new FlightMoveControl(this, 20, true);
        this.setNoGravity(true);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 10.0)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 0.6)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(MODE_360, false);
        this.dataTracker.startTracking(HAS_BATTERY, false);
        this.dataTracker.startTracking(BATTERY_CHANGED_AT, -1000L);
    }

    public boolean hasBattery() { return dataTracker.get(HAS_BATTERY); }
    public long getBatteryChangedAt() { return dataTracker.get(BATTERY_CHANGED_AT); }
    public void setHasBattery(boolean value) {
        dataTracker.set(HAS_BATTERY, value);
        dataTracker.set(BATTERY_CHANGED_AT, getWorld().getTime());
    }

    public boolean isMode360() {
        return this.dataTracker.get(MODE_360);
    }

    public void setMode360(boolean value) {
        this.dataTracker.set(MODE_360, value);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new FlyGoal(this, 1.0));
        this.goalSelector.add(2, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(3, new LookAroundGoal(this));
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        BirdNavigation nav = new BirdNavigation(this, world);
        nav.setCanPathThroughDoors(false);
        nav.setCanSwim(false);
        nav.setCanEnterOpenDoors(true);
        return nav;
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        net.minecraft.item.ItemStack stack = player.getStackInHand(hand);
        if (stack.isOf(AvataDrone.AVATA360_BATTERY)) {                       // 插入电池
            if (hasBattery()) return ActionResult.PASS;
            if (!this.getWorld().isClient) {
                setHasBattery(true);
                if (!player.getAbilities().creativeMode) stack.decrement(1);
                triggerAnim("battery", "insert");
            }
            return ActionResult.success(this.getWorld().isClient);
        }
        if (hand == Hand.MAIN_HAND && stack.isEmpty() && player.isSneaking() && hasBattery()) {   // 取出电池
            if (!this.getWorld().isClient) {
                setHasBattery(false);
                triggerAnim("battery", "remove");
                player.getInventory().offerOrDrop(new net.minecraft.item.ItemStack(AvataDrone.AVATA360_BATTERY));
            }
            return ActionResult.success(this.getWorld().isClient);
        }
        if (hand == Hand.MAIN_HAND && stack.isEmpty()) {
            if (!this.getWorld().isClient) {
                setMode360(!isMode360());
            }
            return ActionResult.success(this.getWorld().isClient);
        }
        return super.interactMob(player, hand);
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    @Override
    protected void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Mode360", isMode360());
        nbt.putBoolean("HasBattery", hasBattery());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setMode360(nbt.getBoolean("Mode360"));
        dataTracker.set(HAS_BATTERY, nbt.getBoolean("HasBattery"));
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 主控制器：桨叶旋转 + 悬停微摆
        controllers.add(new AnimationController<>(this, "flight", 4, state -> {
            if (this.isOnGround() && this.getVelocity().horizontalLengthSquared() < 1.0E-4) {
                return state.setAndContinue(IDLE);
            }
            return state.setAndContinue(FLY);
        }));
        // 相机控制器：单镜头 / 360 模式
        controllers.add(new AnimationController<>(this, "camera", 0, state -> {
            if (isMode360()) {
                cameraWasRotated = true;
                return state.setAndContinue(CAM_360);
            }
            if (cameraWasRotated) {
                return state.setAndContinue(CAM_SINGLE);
            }
            return PlayState.STOP;
        }));
        // 电池控制器：静止状态跟随 HAS_BATTERY；插入 / 取出动画由服务端 triggerAnim 触发
        controllers.add(new AnimationController<>(this, "battery", 0,
                state -> state.setAndContinue(hasBattery() ? BAT_INSTALLED : BAT_EMPTY))
                .triggerableAnim("insert", BAT_INSERT).triggerableAnim("remove", BAT_REMOVE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
