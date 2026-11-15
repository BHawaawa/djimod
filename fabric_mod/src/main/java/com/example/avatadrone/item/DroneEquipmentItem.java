package com.example.avatadrone.item;

import com.example.avatadrone.client.DroneEquipmentRenderer;
import net.minecraft.item.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.*;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.function.*;

/** The item stores each aircraft's battery and power state, separately from its model. */
public class DroneEquipmentItem extends Item implements GeoItem {
    public static Consumer<Hand> openWheel = hand -> {};
    public final String model;
    public final boolean aircraft;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderer = GeoItem.makeRenderer(this);

    public DroneEquipmentItem(String model, boolean aircraft) {
        super(new Settings().maxCount(1));
        this.model = model;
        this.aircraft = aircraft;
    }

    public static boolean hasBattery(ItemStack stack) {
        return !stack.hasNbt() || !stack.getNbt().contains("HasBattery") || stack.getNbt().getBoolean("HasBattery");
    }
    public static boolean powered(ItemStack stack) {
        return hasBattery(stack) && stack.hasNbt() && stack.getNbt().getBoolean("Powered");
    }
    public static String action(ItemStack stack) {
        return stack.hasNbt() ? stack.getNbt().getString("MaintenanceAction") : "";
    }
    @Override public void appendTooltip(ItemStack stack, World world, java.util.List<net.minecraft.text.Text> tooltip,
            net.minecraft.client.item.TooltipContext context) {
        if (!aircraft) {
            tooltip.add(net.minecraft.text.Text.literal("独立电池物品 · 放入背包后，通过无人机轮盘安装"));
            tooltip.add(net.minecraft.text.Text.literal("适用机型：" + (model.startsWith("m400") ? "经纬 M400" : model.startsWith("mini4pro") ? "Mini 4 Pro" : "Avata 360")));
            {   // charge shown for every battery (AURO 2000 ELITE can charge all of them)
                double charge = stack.hasNbt() && stack.getNbt().contains("BatteryCharge")
                        ? stack.getNbt().getDouble("BatteryCharge") : 1;
                tooltip.add(net.minecraft.text.Text.literal("电量：" + Math.round(net.minecraft.util.math.MathHelper.clamp(charge, 0, 1) * 100) + "%"));
            }
            return;
        }
        tooltip.add(net.minecraft.text.Text.literal("右键：打开操作轮盘"));
        if (stack.hasNbt() && stack.getNbt().getCompound("AircraftData").getInt("AccidentReason") != 0)
            tooltip.add(net.minecraft.text.Text.literal("飞行器已损坏：无法连接或起飞").formatted(net.minecraft.util.Formatting.RED));
        if (hasBattery(stack) && stack.hasNbt() && (stack.getNbt().contains("AircraftData") || stack.getNbt().contains("InstalledBattery")))
            tooltip.add(net.minecraft.text.Text.literal("电池电量：" + Math.round(DeviceCharging.getCharge(stack) * 100) + "%"));
        if (model.equals("m400")) {
            tooltip.add(net.minecraft.text.Text.literal(!hasBattery(stack) ? "未安装 TB100 电池" : powered(stack) ? "已开机 · 可以放置" : "TB100 已安装 · 未开机"));
            return;
        }
        tooltip.add(net.minecraft.text.Text.literal(!hasBattery(stack) ? "未安装电池" : powered(stack)
                ? "已开机 · 保护罩已取下 · 可以放置" : "已安装电池 · 未开机 · 保护罩已安装"));
    }
    @Override public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (!aircraft) return TypedActionResult.pass(player.getStackInHand(hand));
        if (world.isClient) openWheel.accept(hand);
        return TypedActionResult.success(player.getStackInHand(hand), world.isClient);
    }
    @Override public ActionResult useOnBlock(ItemUsageContext context) {
        if (!aircraft) return ActionResult.PASS;
        use(context.getWorld(), context.getPlayer(), context.getHand());
        return ActionResult.SUCCESS;
    }
    @Override public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!world.isClient && aircraft && !action(stack).isEmpty()
                && !com.example.avatadrone.network.DroneMaintenance.isPending(stack)) {
            stack.removeSubNbt("MaintenanceAction");
            stack.removeSubNbt("MaintenanceStart");
        }
    }
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "maintenance", 0, state -> {
            ItemStack stack = state.getData(DataTickets.ITEMSTACK);
            String animation = "idle";
            if (aircraft && stack != null) {
                String action = action(stack);
                animation = action.isEmpty() ? (hasBattery(stack) ? "battery_installed" : "battery_empty") : action;
            }
            return state.setAndContinue(RawAnimation.begin().thenPlayAndHold("animation." + model + "." + animation));
        }));
    }
    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    @Override public Supplier<Object> getRenderProvider() { return renderer; }
    @Override public void createRenderer(Consumer<Object> consumer) {
        consumer.accept(new RenderProvider() {
            private DroneEquipmentRenderer instance;
            @Override public net.minecraft.client.render.item.BuiltinModelItemRenderer getCustomRenderer() {
                if (instance == null) instance = new DroneEquipmentRenderer();
                return instance;
            }
        });
    }
}
