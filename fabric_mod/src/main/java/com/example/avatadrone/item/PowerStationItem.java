package com.example.avatadrone.item;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.client.PowerStationItemRenderer;
import com.example.avatadrone.entity.PowerStationEntity;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Item form of the AURO 2000 ELITE: right-click a block face to place it (front facing you); keeps its energy. */
public class PowerStationItem extends Item implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    public PowerStationItem(Settings settings) { super(settings); }

    public static double energyWh(ItemStack stack) {
        return stack.hasNbt() && stack.getNbt().contains("EnergyWh") ? stack.getNbt().getDouble("EnergyWh")
                : PowerStationEntity.CAPACITY_WH;
    }

    @Override public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity player = context.getPlayer();
        Vec3d pos = context.getHitPos();
        if (context.getSide().getOffsetY() == 0) pos = pos.add(Vec3d.of(context.getSide().getVector()).multiply(0.5));
        else if (context.getSide().getOffsetY() < 0) pos = pos.add(0, -AvataDrone.AURO2000.getHeight(), 0);
        Box box = AvataDrone.AURO2000.getDimensions().getBoxAt(pos);
        if (!world.isSpaceEmpty(null, box) || !world.getOtherEntities(null, box).isEmpty()) return ActionResult.FAIL;
        if (world instanceof ServerWorld server) {
            PowerStationEntity station = AvataDrone.AURO2000.create(server);
            if (station == null) return ActionResult.FAIL;
            float yaw = player != null ? player.getYaw() + 180f : 0f;
            yaw = Math.round(yaw / 15f) * 15f;                              // snap to 15 deg like a placed block
            station.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0f);
            station.setEnergyWh(energyWh(context.getStack()));
            if (context.getStack().hasCustomName()) station.setCustomName(context.getStack().getName());
            server.spawnEntity(station);
            world.playSound(null, station.getBlockPos(), SoundEvents.BLOCK_NETHERITE_BLOCK_PLACE, SoundCategory.BLOCKS, 0.8f, 1.2f);
            if (player != null) player.sendMessage(Text.translatable("message.djimod.auro.placed"), true);
            if (player == null || !player.getAbilities().creativeMode) context.getStack().decrement(1);
        }
        return ActionResult.success(world.isClient);
    }

    @Override public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        double wh = energyWh(stack);
        tooltip.add(Text.translatable("tooltip.djimod.auro.energy", Math.round(wh / PowerStationEntity.CAPACITY_WH * 100),
                Math.round(wh)));
        tooltip.add(Text.translatable("tooltip.djimod.auro.help1"));
        tooltip.add(Text.translatable("tooltip.djimod.auro.help2"));
        tooltip.add(Text.translatable("tooltip.djimod.auro.help3"));
    }

    // ------------------------------------------------------------------ GeckoLib item rendering
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public Supplier<Object> getRenderProvider() { return renderProvider; }
    @Override public void createRenderer(Consumer<Object> consumer) {
        consumer.accept(new RenderProvider() {
            private PowerStationItemRenderer renderer;
            @Override public net.minecraft.client.render.item.BuiltinModelItemRenderer getCustomRenderer() {
                if (renderer == null) renderer = new PowerStationItemRenderer();
                return renderer;
            }
        });
    }
}
