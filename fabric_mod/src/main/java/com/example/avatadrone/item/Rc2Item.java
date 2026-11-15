package com.example.avatadrone.item;

import com.example.avatadrone.entity.AvataDroneEntity;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import java.util.List;

public class Rc2Item extends Item implements software.bernie.geckolib.animatable.GeoItem {
    private final software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache cache =
            software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);
    private final java.util.function.Supplier<Object> renderProvider = software.bernie.geckolib.animatable.GeoItem.makeRenderer(this);
    public static boolean hasPower(ItemStack stack) {
        return stack.getItem() instanceof Rc2Item && DeviceCharging.getCharge(stack) > 0;
    }
    public static int batteryLights(ItemStack stack) {
        return (int) Math.ceil(DeviceCharging.getCharge(stack) * 4);
    }
    @Override public void inventoryTick(ItemStack stack, World world, net.minecraft.entity.Entity entity, int slot, boolean selected) {
        if (world.isClient || world.getTime() % 20 != 0 || !(entity instanceof PlayerEntity player)) return;
        if (player.getMainHandStack() != stack && player.getOffHandStack() != stack) return;
        if (stack.hasNbt() && stack.getNbt().containsUuid(DRONE)
                && world instanceof net.minecraft.server.world.ServerWorld server
                && server.getEntity(stack.getNbt().getUuid(DRONE)) instanceof AvataDroneEntity drone
                && drone.hasAccident()) drone.writeAccidentReport(stack);
        // Game estimate: six hours of powered handheld operation; stored controllers do not drain.
        DeviceCharging.setCharge(stack, DeviceCharging.getCharge(stack) - 1.0 / 21600);
    }
    @Override public void registerControllers(software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar controllers) {}
    @Override public software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public java.util.function.Supplier<Object> getRenderProvider() { return renderProvider; }
    @Override public void createRenderer(java.util.function.Consumer<Object> consumer) {
        consumer.accept(new software.bernie.geckolib.animatable.client.RenderProvider() {
            private com.example.avatadrone.client.RC2ItemRenderer renderer;
            @Override public net.minecraft.client.render.item.BuiltinModelItemRenderer getCustomRenderer() {
                if (renderer == null) renderer = new com.example.avatadrone.client.RC2ItemRenderer();
                return renderer;
            }
        });
    }

    public static final String DRONE = "DroneUuid";
    public static final String DIMENSION = "DroneDimension";

    public Rc2Item(Settings settings) { super(settings); }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (!(entity instanceof AvataDroneEntity)) return ActionResult.PASS;
        if (user.isSneaking()) return ((AvataDroneEntity) entity).collectIntoInventory(user);
        if (!hasPower(stack) || ((AvataDroneEntity) entity).hasAccident()) return ActionResult.FAIL;
        if (!user.getWorld().isClient) {
            stack.removeSubNbt("DroneAccident");
            stack.getOrCreateNbt().putUuid(DRONE, entity.getUuid());
            stack.getOrCreateNbt().putString(DIMENSION, entity.getWorld().getRegistryKey().getValue().toString());
            user.sendMessage(Text.translatable("message.djimod.rc2.paired"), true);
        }
        return ActionResult.success(user.getWorld().isClient);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient) {
            if (!stack.hasNbt() || !stack.getNbt().containsUuid(DRONE)) {
                user.sendMessage(Text.translatable("message.djimod.rc2.hint"), true);
            }
        }
        return TypedActionResult.success(stack, world.isClient);
    }

    public static void disconnect(ItemStack stack, PlayerEntity user) {
        if (user.getWorld().isClient || !stack.hasNbt() || !stack.getNbt().containsUuid(DRONE)) return;
        stack.removeSubNbt(DRONE);
        stack.removeSubNbt(DIMENSION);
        user.sendMessage(Text.translatable("message.djimod.rc2.unpaired"), true);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("遥控器电量：" + Math.round(DeviceCharging.getCharge(stack) * 100) + "%"));
        tooltip.add(Text.literal("移动电源取线后，手持遥控器右键电源连接／断开"));
        tooltip.add(Text.translatable("message.djimod.rc2.hint"));
        tooltip.add(Text.translatable("message.djimod.rc2.clear_hint"));
    }
}
