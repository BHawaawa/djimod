package com.example.avatadrone.entity;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.item.Mini4ProBatteryItem;
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
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * DJI Mini 4 Pro 无人机实体（v2：带电池仓）。
 * - 默认没有电池：电池骨骼由 battery_empty 动画缩放为 0，无人机不能起飞（无重力关闭、不执行飞行 AI）。
 * - 手持 Mini 4 Pro 电池右键：插入电池（消耗 1 个电池物品，播放 battery_insert）。
 * - 潜行 + 空手右键：取出电池（播放 battery_remove，电池物品还给玩家）。
 * - 空手右键（有电池时）：云台在平视 / 俯拍之间切换。
 */
public class Mini4ProEntity extends PathAwareEntity implements GeoEntity {
    private static final TrackedData<Boolean> GIMBAL_DOWN =
            DataTracker.registerData(Mini4ProEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> HAS_BATTERY =
            DataTracker.registerData(Mini4ProEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.mini4pro.idle");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.mini4pro.fly");
    private static final RawAnimation GIMBAL_DOWN_ANIM = RawAnimation.begin().thenPlayAndHold("animation.mini4pro.gimbal_down");
    private static final RawAnimation GIMBAL_FWD_ANIM = RawAnimation.begin().thenPlayAndHold("animation.mini4pro.gimbal_forward");
    private static final RawAnimation BATTERY_EMPTY = RawAnimation.begin().thenPlayAndHold("animation.mini4pro.battery_empty");
    private static final RawAnimation BATTERY_INSTALLED = RawAnimation.begin().thenPlayAndHold("animation.mini4pro.battery_installed");
    private static final RawAnimation BATTERY_INSERT = RawAnimation.begin().thenPlayAndHold("animation.mini4pro.battery_insert");
    private static final RawAnimation BATTERY_REMOVE = RawAnimation.begin().thenPlayAndHold("animation.mini4pro.battery_remove");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private boolean gimbalMoved = false; // client only

    public Mini4ProEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.moveControl = new FlightMoveControl(this, 20, true);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 8.0)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 0.7)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(GIMBAL_DOWN, false);
        this.dataTracker.startTracking(HAS_BATTERY, false);
    }

    public boolean isGimbalDown() {
        return this.dataTracker.get(GIMBAL_DOWN);
    }

    public boolean hasBattery() {
        return this.dataTracker.get(HAS_BATTERY);
    }

    @Override
    protected void initGoals() {
        // 只有装了电池才会飞
        this.goalSelector.add(1, new FlyGoal(this, 1.0) {
            @Override
            public boolean canStart() {
                return hasBattery() && super.canStart();
            }

            @Override
            public boolean shouldContinue() {
                return hasBattery() && super.shouldContinue();
            }
        });
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
    public void tick() {
        // 没电池：受重力落地，停止导航
        this.setNoGravity(hasBattery());
        if (!hasBattery() && !this.getWorld().isClient) this.getNavigation().stop();
        super.tick();
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND) return super.interactMob(player, hand);
        ItemStack stack = player.getStackInHand(hand);
        World world = this.getWorld();

        // 插入电池
        if (stack.getItem() instanceof Mini4ProBatteryItem && !hasBattery()) {
            if (!world.isClient) {
                this.dataTracker.set(HAS_BATTERY, true);
                if (!player.getAbilities().creativeMode) stack.decrement(1);
                this.triggerAnim("battery", "insert");
                world.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.NEUTRAL, 0.5f, 1.8f);
            }
            return ActionResult.success(world.isClient);
        }
        if (stack.isEmpty()) {
            // 取出电池
            if (player.isSneaking() && hasBattery()) {
                if (!world.isClient) {
                    this.dataTracker.set(HAS_BATTERY, false);
                    this.triggerAnim("battery", "remove");
                    ItemStack bat = new ItemStack(AvataDrone.MINI4PRO_BATTERY);
                    if (!player.giveItemStack(bat)) player.dropItem(bat, false);
                    world.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.NEUTRAL, 0.5f, 1.8f);
                }
                return ActionResult.success(world.isClient);
            }
            // 云台
            if (!world.isClient) this.dataTracker.set(GIMBAL_DOWN, !isGimbalDown());
            return ActionResult.success(world.isClient);
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
    protected void dropEquipment(DamageSource source, int lootingMultiplier, boolean allowDrops) {
        super.dropEquipment(source, lootingMultiplier, allowDrops);
        if (hasBattery()) this.dropStack(new ItemStack(AvataDrone.MINI4PRO_BATTERY));
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("GimbalDown", isGimbalDown());
        nbt.putBoolean("HasBattery", hasBattery());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.dataTracker.set(GIMBAL_DOWN, nbt.getBoolean("GimbalDown"));
        this.dataTracker.set(HAS_BATTERY, nbt.getBoolean("HasBattery"));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "flight", 6, state -> {
            if (!hasBattery() || (this.isOnGround() && this.getVelocity().horizontalLengthSquared() < 1.0E-4)) {
                return state.setAndContinue(IDLE);
            }
            return state.setAndContinue(FLY);
        }));
        controllers.add(new AnimationController<>(this, "gimbal", 0, state -> {
            if (isGimbalDown()) {
                gimbalMoved = true;
                return state.setAndContinue(GIMBAL_DOWN_ANIM);
            }
            return gimbalMoved ? state.setAndContinue(GIMBAL_FWD_ANIM) : PlayState.STOP;
        }));
        // 电池：常态为 installed / empty（两者都是保持型动画），插拔时由 triggerAnim 播放 insert / remove，
        // 两段动画的最后一帧与常态完全一致，所以结束后无缝衔接。
        controllers.add(new AnimationController<>(this, "battery", 0,
                state -> state.setAndContinue(hasBattery() ? BATTERY_INSTALLED : BATTERY_EMPTY))
                .triggerableAnim("insert", BATTERY_INSERT)
                .triggerableAnim("remove", BATTERY_REMOVE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
