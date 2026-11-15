package com.example.avatadrone.mixin;

import com.example.avatadrone.AvataDrone;
import com.example.avatadrone.entity.AvataDroneEntity;
import com.example.avatadrone.item.Rc2Item;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.world.EntityTrackingListener;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Set;

/** Keep paired telemetry available outside vanilla view distance, including during signal loss. */
@Mixin(targets = "net.minecraft.server.world.ThreadedAnvilChunkStorage$EntityTracker")
public abstract class DroneTrackingMixin {
    @Shadow @Final private Entity entity;
    @Shadow @Final private EntityTrackerEntry entry;
    @Shadow @Final private Set<EntityTrackingListener> listeners;
    @Inject(method = "updateTrackedStatus(Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At("HEAD"), cancellable = true)
    private void trackPairedDrone(ServerPlayerEntity player, CallbackInfo ci) {
        if (player.getWorld() != entity.getWorld() || player == entity) return;
        boolean pairedDrone = entity instanceof AvataDroneEntity
                && (paired(player.getMainHandStack()) || paired(player.getOffHandStack()));
        boolean remoteVisible = com.example.avatadrone.network.DroneChunkStreaming.watches(player, entity.getChunkPos())
                && entity.canBeSpectated(player);
        if (pairedDrone || remoteVisible) {
            if (listeners.add(player.networkHandler)) entry.startTracking(player);
            ci.cancel();
        }
    }
    private boolean paired(ItemStack stack) {
        return stack.isOf(AvataDrone.DJI_RC2) && stack.hasNbt() && stack.getNbt().containsUuid(Rc2Item.DRONE)
                && entity.getUuid().equals(stack.getNbt().getUuid(Rc2Item.DRONE));
    }
}
