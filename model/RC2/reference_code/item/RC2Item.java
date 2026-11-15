package com.example.avatadrone.item;

import com.example.avatadrone.client.RC2ItemRenderer;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * DJI RC 2 遥控器（GeckoLib GeoItem）。
 * 右键：展开 / 收起天线；潜行 + 右键：播放一次摇杆演示动画。
 */
public class RC2Item extends Item implements GeoItem {
    private static final RawAnimation ANTENNA_OPEN = RawAnimation.begin().thenPlayAndHold("animation.rc2.antenna_open");
    private static final RawAnimation ANTENNA_CLOSE = RawAnimation.begin().thenPlayAndHold("animation.rc2.antenna_close");
    private static final RawAnimation STICKS = RawAnimation.begin().thenPlay("animation.rc2.sticks");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    public RC2Item(Settings settings) {
        super(settings);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world instanceof ServerWorld serverWorld) {
            long id = GeoItem.getOrAssignId(stack, serverWorld);
            if (user.isSneaking()) {
                triggerAnim(user, id, "sticks", "sticks");
            } else {
                NbtCompound nbt = stack.getOrCreateNbt();
                boolean open = !nbt.getBoolean("AntennaOpen");
                nbt.putBoolean("AntennaOpen", open);
                triggerAnim(user, id, "antenna", open ? "open" : "close");
                user.sendMessage(Text.translatable(open ? "item.avatadrone.rc2.antenna_open" : "item.avatadrone.rc2.antenna_close"), true);
            }
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        consumer.accept(new RenderProvider() {
            private RC2ItemRenderer renderer;

            @Override
            public BuiltinModelItemRenderer getCustomRenderer() {
                if (this.renderer == null) this.renderer = new RC2ItemRenderer();
                return this.renderer;
            }
        });
    }

    @Override
    public Supplier<Object> getRenderProvider() {
        return this.renderProvider;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "antenna", 0, state -> PlayState.STOP)
                .triggerableAnim("open", ANTENNA_OPEN)
                .triggerableAnim("close", ANTENNA_CLOSE));
        controllers.add(new AnimationController<>(this, "sticks", 0, state -> PlayState.STOP)
                .triggerableAnim("sticks", STICKS));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
