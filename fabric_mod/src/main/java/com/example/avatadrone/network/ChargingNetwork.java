package com.example.avatadrone.network;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.block.charger.ChargingHubBlockEntity;
import com.example.avatadrone.entity.AvataDroneEntity;
import com.example.avatadrone.entity.PowerStationEntity;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.List;

/** All inventory and cable mutations are validated on the server. */
public final class ChargingNetwork {
    public static final Identifier SLOT = new Identifier(AvataDrone.MOD_ID, "charging_slot");
    private ChargingNetwork() {}
    private static List<PowerStationEntity> nearby(net.minecraft.world.World world, Vec3d pos) {
        return world.getEntitiesByClass(PowerStationEntity.class, new Box(pos, pos).expand(6), e -> !e.isRemoved());
    }
    public static PowerStationEntity controllerSource(PlayerEntity player, net.minecraft.item.ItemStack stack) {
        return nearby(player.getWorld(), player.getPos()).stream()
                .filter(s -> s.chargesController(player, stack)).findFirst().orElse(null);
    }
    public static PowerStationEntity held(PlayerEntity player) {
        return nearby(player.getWorld(), player.getPos()).stream()
                .filter(s -> s.getCableState() == PowerStationEntity.OUT && s.heldBy(player)).findFirst().orElse(null);
    }
    public static PowerStationEntity connected(AvataDroneEntity drone) {
        return nearby(drone.getWorld(), drone.getPos()).stream().filter(s ->
                s.getCableState() == PowerStationEntity.PLUGGED && (s.targets(drone)
                || drone.getWorld().isClient && s.getTargetKind() == 2 && s.getTargetEntityId() == drone.getId()))
                .findFirst().orElse(null);
    }
    public static PowerStationEntity connected(ChargingHubBlockEntity hub) {
        return nearby(hub.getWorld(), Vec3d.ofCenter(hub.getPos())).stream().filter(s ->
                s.getCableState() == PowerStationEntity.PLUGGED && s.getTargetKind() == 1
                && s.getTargetBlock().equals(hub.getPos())).findFirst().orElse(null);
    }
    public static void toggleHub(PlayerEntity player, ChargingHubBlockEntity hub) {
        if (player.isSpectator() || !player.isAlive() || !player.canModifyBlocks()
                || player.squaredDistanceTo(Vec3d.ofCenter(hub.getPos())) > 36
                || !player.getWorld().canPlayerModifyAt(player, hub.getPos())) return;
        PowerStationEntity source = connected(hub);
        if (source != null) { source.disconnect(player); return; }
        source = held(player);
        if (source != null && source.canConnect(player, Vec3d.ofCenter(hub.getPos()))) source.connect(hub.getPos());
        else player.sendMessage(Text.literal("先对移动电源 Shift+右键取线，等待抽线完成；管家须在电源5格内"), true);
    }
    public static void toggleDrone(PlayerEntity player, AvataDroneEntity drone) {
        if (player.isSpectator() || !player.isAlive() || player.squaredDistanceTo(drone) > 36
                || !player.canSee(drone) || !player.canModifyBlocks()) return;
        PowerStationEntity source = connected(drone);
        if (source != null) { source.disconnect(player); return; }
        if (!drone.hasBattery() || drone.areMotorsArmed() || !drone.isOnGround()
                || drone.getVelocity().lengthSquared() > 0.01) {
            player.sendMessage(Text.literal("无人机必须装有电池、停稳在地面并关闭电机才能充电"), true); return;
        }
        source = held(player);
        if (source != null && source.canConnect(player, drone.getPos())) source.connect(drone);
        else player.sendMessage(Text.literal("先对移动电源 Shift+右键取线，等待抽线完成；无人机须在电源5格内"), true);
    }
    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(SLOT, (server, player, handler, buf, response) -> {
            var pos = buf.readBlockPos(); int slot = buf.readUnsignedByte();
            server.execute(() -> {
                if (slot > 2 || player.isSpectator() || !player.isAlive() || !player.canModifyBlocks()
                        || !player.getWorld().canPlayerModifyAt(player, pos)
                        || player.squaredDistanceTo(Vec3d.ofCenter(pos)) > 36) return;
                HitResult hit = player.raycast(6, 0, false);
                if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK
                        || !blockHit.getBlockPos().equals(pos)) return;
                if (!(player.getWorld().getBlockEntity(pos) instanceof ChargingHubBlockEntity hub)) return;
                var stack = player.getMainHandStack();
                if (stack.isEmpty()) hub.remove(slot, player, Hand.MAIN_HAND);
                else if (hub.accepts(stack)) hub.insert(slot, stack, player);
                else player.sendMessage(Text.literal("请手持对应型号电池插入，或空手取出"), true);
            });
        });
        // Run before vanilla sneak-with-item placement bypasses the block's onUse.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!player.isSpectator() && player.isSneaking() && hand == Hand.MAIN_HAND
                    && world.getBlockEntity(hit.getBlockPos()) instanceof ChargingHubBlockEntity hub) {
                if (!world.isClient) toggleHub(player, hub);
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!player.isSpectator() && player.isSneaking() && entity instanceof AvataDroneEntity drone) {
                if (!world.isClient) toggleDrone(player, drone);
                return ActionResult.SUCCESS; // consume attack: charging must never damage the aircraft
            }
            return ActionResult.PASS;
        });
    }
}
