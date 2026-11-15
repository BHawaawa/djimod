package com.example.avatadrone.item;

import com.example.avatadrone.client.Avata360BatteryItemRenderer;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.client.render.item.BuiltinModelItemRenderer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * DJI Avata 360 智能飞行电池（GeckoLib GeoItem）。
 * 右键：按电源键，切换电量灯（NBT "Powered"，开机时 4 颗 LED 通过 glowmask 发光）；
 * 潜行 + 右键：按压两侧卡扣。
 */
public class Avata360BatteryItem extends Item implements GeoItem {
    private static final RawAnimation BUTTON = RawAnimation.begin().thenPlay("animation.avata360_battery.button_press");
    private static final RawAnimation LATCH = RawAnimation.begin().thenPlay("animation.avata360_battery.latch_press");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    public Avata360BatteryItem(Settings settings) {
        super(settings);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public static boolean isPowered(ItemStack stack) {
        return stack.hasNbt() && stack.getNbt().getBoolean("Powered");
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world instanceof ServerWorld serverWorld) {
            long id = GeoItem.getOrAssignId(stack, serverWorld);
            if (user.isSneaking()) {
                triggerAnim(user, id, "controls", "latch");
            } else {
                NbtCompound nbt = stack.getOrCreateNbt();
                nbt.putBoolean("Powered", !nbt.getBoolean("Powered"));
                triggerAnim(user, id, "controls", "button");
                world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON, SoundCategory.PLAYERS, 0.4f, 1.8f);
            }
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("2700 mAh · 14.32 V · 38.67 Wh").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable(isPowered(stack) ? "item.avatadrone.battery.on" : "item.avatadrone.battery.off")
                .formatted(isPowered(stack) ? Formatting.GREEN : Formatting.DARK_GRAY));
    }

    // ------------------------------------------------------------------ GeckoLib
    @Override
    public void createRenderer(Consumer<Object> consumer) {
        consumer.accept(new RenderProvider() {
            private Avata360BatteryItemRenderer renderer;

            @Override
            public BuiltinModelItemRenderer getCustomRenderer() {
                if (this.renderer == null) this.renderer = new Avata360BatteryItemRenderer();
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
        controllers.add(new AnimationController<>(this, "controls", 0, state -> PlayState.STOP)
                .triggerableAnim("button", BUTTON)
                .triggerableAnim("latch", LATCH));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
