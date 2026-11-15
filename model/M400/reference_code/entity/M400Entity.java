package com.example.avatadrone.entity;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.item.M400BatteryItem;
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
 * DJI Matrice 400（经纬 M400）无人机实体，带 TB100 电池仓（机身后上方 34° 斜面）。
 * - 默认没有电池：battery 骨骼由 battery_empty 缩放为 0，无人机受重力、不执行飞行 AI。
 * - 手持 TB100 电池（M400BatteryItem）右键：插入电池（消耗 1 个，创造模式不消耗），播放 battery_insert。
 * - 潜行 + 空手右键：取出电池（播放 battery_remove，电池物品还给玩家，背包满则掉落）。
 * - 有电池且离地/移动：fly（悬停浮动 + 桨旋转）；否则 idle。
 * - takeoff / land 可通过 triggerAnim("flight_event", "takeoff" | "land") 触发。
 */
public class M400Entity extends PathAwareEntity implements GeoEntity {
    private static final TrackedData<Boolean> HAS_BATTERY =
            DataTracker.registerData(M400Entity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.m400.idle");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.m400.fly");
    private static final RawAnimation TAKEOFF = RawAnimation.begin().thenPlay("animation.m400.takeoff");
    private static final RawAnimation LAND = RawAnimation.begin().thenPlay("animation.m400.land");
    private static final RawAnimation BATTERY_EMPTY = RawAnimation.begin().thenPlayAndHold("animation.m400.battery_empty");
    private static final RawAnimation BATTERY_INSTALLED = RawAnimation.begin().thenPlayAndHold("animation.m400.battery_installed");
    private static final RawAnimation BATTERY_INSERT = RawAnimation.begin().thenPlayAndHold("animation.m400.battery_insert");
    private static final RawAnimation BATTERY_REMOVE = RawAnimation.begin().thenPlayAndHold("animation.m400.battery_remove");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public M400Entity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.moveControl = new FlightMoveControl(this, 10, true);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 30.0)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, 0.6)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(HAS_BATTERY, false);
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
        this.goalSelector.add(2, new LookAtEntityGoal(this, PlayerEntity.class, 12.0f));
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
        if (stack.getItem() instanceof M400BatteryItem && !hasBattery()) {
            if (!world.isClient) {
                this.dataTracker.set(HAS_BATTERY, true);
                if (!player.getAbilities().creativeMode) stack.decrement(1);
                this.triggerAnim("battery", "insert");
                world.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.NEUTRAL, 0.7f, 1.2f);
            }
            return ActionResult.success(world.isClient);
        }
        // 潜行 + 空手：取出电池
        if (stack.isEmpty() && player.isSneaking() && hasBattery()) {
            if (!world.isClient) {
                this.dataTracker.set(HAS_BATTERY, false);
                this.triggerAnim("battery", "remove");
                ItemStack bat = new ItemStack(AvataDrone.M400_BATTERY);
                if (!player.giveItemStack(bat)) player.dropItem(bat, false);
                world.playSound(null, this.getBlockPos(), SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.NEUTRAL, 0.7f, 1.2f);
            }
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
        if (hasBattery()) this.dropStack(new ItemStack(AvataDrone.M400_BATTERY));
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("HasBattery", hasBattery());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.dataTracker.set(HAS_BATTERY, nbt.getBoolean("HasBattery"));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "flight", 8, state -> {
            if (!hasBattery() || (this.isOnGround() && this.getVelocity().horizontalLengthSquared() < 1.0E-4)) {
                return state.setAndContinue(IDLE);
            }
            return state.setAndContinue(FLY);
        }));
        controllers.add(new AnimationController<>(this, "flight_event", 0, state -> PlayState.STOP)
                .triggerableAnim("takeoff", TAKEOFF)
                .triggerableAnim("land", LAND));
        // 电池：常态为 installed / empty（保持型），插拔时 triggerAnim 播放 insert / remove；
        // 两段动画的最后一帧与常态完全一致，结束后无缝衔接。
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
